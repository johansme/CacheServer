package cache;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class CacheProgram extends Application {
	
	private HashMap<String, File> cachedFiles;
	private File logFile;
	private CacheViewController controller;
	
	public CacheProgram() {
		cachedFiles = new HashMap<String, File>();
		fillFileMap("CacheFiles");
		logFile = new File("CacheFiles/log.txt");
		if (! logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	private void fillFileMap(String folderName) {
		File folder = new File(folderName);
		cachedFiles.clear();
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				fillFileMap(file.getAbsolutePath());
			} else {
				if (! file.getName().equals("log.txt") && ! file.getName().equals("fragmentLog.txt")) {
					cachedFiles.put(file.getName(), file);									
				}
			}
		}
	}
	
	public void refillMap() {
		fillFileMap("CacheFiles");
		for (String fileName : cachedFiles.keySet()) {
			if (! cachedFiles.get(fileName).exists()) {
				cachedFiles.remove(fileName);
			}
		}
	}
	
	public void clearCache() {
		for (File file : cachedFiles.values()) {
			file.delete();
		}
		cachedFiles.clear();
		writeToLog("Cache cleared at " + LocalDateTime.now());
	}
	
	public Map<String, File> getCachedFiles() {
		return cachedFiles;
	}
	
	public Collection<String> getCachedFileNames() {
		return cachedFiles.keySet();
	}
	
	public void writeToLog(String inputString) {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(logFile, true));
			out.write(inputString);
			out.newLine();
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		try {
			CacheProgram program = new CacheProgram();
			HttpServer server = HttpServer.create(new InetSocketAddress(8099), 0);
			server.createContext("/info", new InfoHandler(program));
			server.createContext("/", new GetHandler(program));
			server.start();
			launch(args);
			server.stop(0);
		} catch (IOException e) {
		}
	}
	
	static class InfoHandler implements HttpHandler {
		
		private CacheProgram cache;
		
		public InfoHandler(CacheProgram cache) {
			this.cache = cache;
		}
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			cache.writeToLog("User request: file list at " + LocalDateTime.now().toString());
			String url = "http://localhost:8080/info";
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection)obj.openConnection();
			int responseCode = con.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
				StringBuilder resp = new StringBuilder();
				String inputLine;
				while ((inputLine = in.readLine()) != null) {
					resp.append(inputLine);
				}
				in.close();
				byte[] response = resp.toString().getBytes();
				exchange.sendResponseHeaders(200, response.length);
				OutputStream os = exchange.getResponseBody();
				os.write(response);
				os.close();
				cache.writeToLog("Response: file list downloaded from server");
			} else {
				String response = "Internal Server Error";
				byte[] bytes = response.getBytes();
				exchange.sendResponseHeaders(500, bytes.length);
				OutputStream os = exchange.getResponseBody();
				os.write(bytes);
				os.close();
			}
			con.disconnect();
		}
		
	}
	
	static class GetHandler implements HttpHandler {

		private CacheProgram cacheProgram;
		private static final int BUFFER_SIZE = 4096;
		
		public GetHandler(CacheProgram p) {
			cacheProgram = p;
		}
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String request = exchange.getRequestURI().toString();
			String fileRequest = request.substring(request.lastIndexOf('/') + 1);
			cacheProgram.writeToLog("User request: file " + fileRequest + " at " + LocalDateTime.now().toString());
			File file = cacheProgram.getCachedFiles().get(fileRequest);
			if (file != null && file.exists()) {
				Headers h = exchange.getResponseHeaders();
				h.add("Content-Type", "application/" + fileRequest.substring(fileRequest.lastIndexOf('.') + 1));
				byte[] bArray = new byte[(int)file.length()];
				FileInputStream fileIS = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fileIS);
				bis.read(bArray, 0, bArray.length);
				
				exchange.sendResponseHeaders(200, file.length());
				OutputStream os = exchange.getResponseBody();
				os.write(bArray);
				os.close();
				bis.close();
				cacheProgram.writeToLog("Response: cached file " + fileRequest);
			} else {
				String url = "http://localhost:8080/" + fileRequest;
				URL obj = new URL(url);
				HttpURLConnection con = (HttpURLConnection)obj.openConnection();
				int responseCode = con.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_OK) {
					String contentType = con.getContentType();
					InputStream in = con.getInputStream();
					String cacheFilePath = "CacheFiles/" + fileRequest;
					file = new File(cacheFilePath);
					FileOutputStream cacheStream = new FileOutputStream(file);
					
					int bytesRead = -1;
					byte[] buffer = new byte[BUFFER_SIZE];
					while ((bytesRead = in.read(buffer)) != -1) {
						cacheStream.write(buffer, 0, bytesRead);
					}
					in.close();
					cacheStream.close();
					
					cacheProgram.refillMap();
					
					Headers h = exchange.getResponseHeaders();
					h.add("Content-Type", contentType);
					byte[] bArray = new byte[(int)file.length()];
					FileInputStream fileIS = new FileInputStream(file);
					BufferedInputStream bis = new BufferedInputStream(fileIS);
					bis.read(bArray, 0, bArray.length);
					
					exchange.sendResponseHeaders(200, file.length());
					OutputStream os = exchange.getResponseBody();
					os.write(bArray);
					os.close();
					bis.close();
					cacheProgram.writeToLog("Response: file " + fileRequest + " downloaded from server");
				} else {
					String resp = "Not Found";
					exchange.sendResponseHeaders(responseCode, resp.getBytes().length);
					OutputStream os = exchange.getResponseBody();
					os.write(resp.getBytes());
					os.close();
				}
			}
		}
		
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/cache/CacheView.fxml"));
		Parent root = loader.load();
		controller = loader.<CacheViewController>getController();
		Scene scene = new Scene(root,800,500);
		primaryStage.setScene(scene);
		primaryStage.titleProperty().setValue("Cache Overview");
		primaryStage.setResizable(false);
		controller.setEnvironment(this);
		primaryStage.show();
	}
	

}
