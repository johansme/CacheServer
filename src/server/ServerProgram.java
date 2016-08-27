package server;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ServerProgram {
	
	private Map<String, String> files;
	
	public ServerProgram() {
		files = new HashMap<String, String>();
		fillFileMap("ServerFiles");
	}
	
	public Map<String, String> getFileMap() {
		return files;
	}
	
	private void fillFileMap(String folderName) {
		File folder = new File(folderName);
		for (File file : folder.listFiles()) {
			if (file.isDirectory()) {
				fillFileMap(file.getAbsolutePath());
			} else {
				files.put(file.getName(), file.getAbsolutePath());				
			}
		}
	}

	public static void main(String[] args) {
		try {
			ServerProgram program = new ServerProgram();
			HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
			server.createContext("/info", new InfoHandler(program));
			server.createContext("/", new GetHandler(program));
			server.start();
		} catch (IOException e) {
		}
	}
	
	static class InfoHandler implements HttpHandler {
		
		private ServerProgram serverProgram;
		
		public InfoHandler(ServerProgram p) {
			serverProgram = p;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			byte[] response = serverProgram.getFileMap().keySet().toString().getBytes();
			exchange.sendResponseHeaders(200, response.length);
			OutputStream os = exchange.getResponseBody();
			os.write(response);
			os.close();
		}
		
	}
	
	static class GetHandler implements HttpHandler {

		private ServerProgram serverProgram;
		
		public GetHandler(ServerProgram p) {
			serverProgram = p;
		}
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String request = exchange.getRequestURI().toString();
			String fileRequest = request.substring(request.lastIndexOf('/') + 1);			
			String filePath = serverProgram.getFileMap().get(fileRequest);
			if (filePath != null) {
				Headers h = exchange.getResponseHeaders();
				h.add("Content-Type", "application/" + fileRequest.substring(fileRequest.lastIndexOf('.') + 1));
				File file = new File(filePath);
				byte[] bArray = new byte[(int)file.length()];
				FileInputStream fileIS = new FileInputStream(file);
				BufferedInputStream bis = new BufferedInputStream(fileIS);
				bis.read(bArray, 0, bArray.length);
				
				exchange.sendResponseHeaders(200, file.length());
				OutputStream os = exchange.getResponseBody();
				os.write(bArray);
				os.close();
				bis.close();
			} else {
				String resp = "Not Found";
				exchange.sendResponseHeaders(404, resp.getBytes().length);
				OutputStream os = exchange.getResponseBody();
				os.write(resp.getBytes());
				os.close();
			}
		}
		
	}

}
