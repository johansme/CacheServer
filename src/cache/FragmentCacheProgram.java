package cache;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
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

public class FragmentCacheProgram extends Application {

	private HashMap<String, List<byte[]>> cachedFragments;
	private HashMap<String, List<String>> fragmentHashes;
	private File logFile;
	private FragmentCacheViewController controller;

	public FragmentCacheProgram() {
		cachedFragments = new HashMap<String, List<byte[]>>();
		fragmentHashes = new HashMap<String, List<String>>();
		logFile = new File("CacheFiles/fragmentLog.txt");
		if (! logFile.exists()) {
			try {
				logFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public void clearCache() {
		cachedFragments.clear();
		writeToLog("Cache cleared at " + LocalDateTime.now());
	}

	public Map<String, List<byte[]>> getCachedFiles() {
		return cachedFragments;
	}

	public List<String> getFragmentHashes(String filename) {
		return fragmentHashes.get(filename);
	}

	public List<byte[]> getCachedFileFragments(String filename) {
		return cachedFragments.get(filename);
	}

	public Collection<String> getCachedFileNames() {
		return cachedFragments.keySet();
	}

	public void putFileFragments(String fileName, List<byte[]> fragments) {
		cachedFragments.put(fileName, fragments);
		List<String> hashes = new ArrayList<String>();
		for (byte[] fragment : fragments) {
			hashes.add(getHash(fragment));
		}
		fragmentHashes.put(fileName, hashes);
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

	public static String getHash(byte[] a) {
		MessageDigest md;
		try {
			md = MessageDigest.getInstance("MD5");
			return byteArray2Hex(md.digest(a));
		} catch (NoSuchAlgorithmException e) {
		}
		return null;
	}

	private static String byteArray2Hex(final byte[] hash) {
		Formatter formatter = new Formatter();
		for (byte b : hash) {
			formatter.format("%02x", b);
		}
		String s = formatter.toString();
		formatter.close();
		return s;
	}

	public static void main(String[] args) {
		launch(args);
		System.exit(0);
	}

	static class InfoHandler implements HttpHandler {

		private FragmentCacheProgram cache;

		public InfoHandler(FragmentCacheProgram cache) {
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

		private FragmentCacheProgram cacheProgram;
		private static final int BUFFER_SIZE = 2048;

		public GetHandler(FragmentCacheProgram p) {
			cacheProgram = p;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String request = exchange.getRequestURI().toString();
			String fileRequest = request.substring(request.lastIndexOf('/') + 1);
			cacheProgram.writeToLog("User request: file " + fileRequest + " at " + LocalDateTime.now().toString());
			List<byte[]> fragments = cacheProgram.getCachedFiles().get(fileRequest);
			if (fragments != null) {
				Headers h = exchange.getResponseHeaders();
				h.add("Content-Type", "application/" + fileRequest.substring(fileRequest.lastIndexOf('.') + 1));
				List<String> hashes = cacheProgram.getFragmentHashes(fileRequest);
				String hashString = hashes.toString().replaceAll(" ", "");
				byte[] hashArray = hashString.getBytes();
				URL url = new URL("http://localhost:8080/" + fileRequest);
				HttpURLConnection con = (HttpURLConnection)url.openConnection();
				con.setDoOutput(true);
				con.setRequestMethod("POST");
				con.setRequestProperty( "Content-Length", Integer.toString(hashArray.length));
				OutputStream out = con.getOutputStream();
				out.write(hashArray);
				out.close();
				int responseCode = 0;
				try {
					responseCode = con.getResponseCode();
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
				if (responseCode == HttpURLConnection.HTTP_OK) {
					int cachedLength = 0;
					int totalLength = 0;
					if (con.getContentLength() == 1) {
						int len = 0;
						for (byte[] a : cacheProgram.getCachedFiles().get(fileRequest)) {
							len += a.length;
						}
						cachedLength = len;
						totalLength = len;
						exchange.sendResponseHeaders(200, len);
						OutputStream os = exchange.getResponseBody();
						for (byte[] a : cacheProgram.getCachedFiles().get(fileRequest)) {
							os.write(a);
						}
						os.close();
					} else {
						InputStream in = con.getInputStream();
						byte[] num = new byte["9999999".getBytes().length];
						in.read(num);
						int fromFragment = Integer.parseInt(new String(num));
						int i = 0;
						int bytesRead = -1;
						DataInputStream dip = new DataInputStream(in);
						byte[] buffer = new byte[BUFFER_SIZE];
						int priorLen = fragments.size();
						byte[] prevBuffer = new byte[BUFFER_SIZE];
						try {
							while (true) {
								dip.readFully(buffer);
								if (fromFragment + i < fragments.size()) {
									fragments.set(fromFragment + i, Arrays.copyOf(buffer, BUFFER_SIZE));
									i++;
								} else {
									fragments.add(Arrays.copyOf(buffer, BUFFER_SIZE));							
								}
								prevBuffer = buffer.clone();
							}
						} catch (EOFException eof) {
							for (int j = BUFFER_SIZE -1; j > -1; j--) {
								if (buffer[j] != prevBuffer[j]) {
									bytesRead = j;
									break;
								}
							}
							if (fromFragment + i < fragments.size()) {
								fragments.set(fromFragment + i, Arrays.copyOfRange(buffer, 0, bytesRead + 1));
								i++;
							} else {
								fragments.add(Arrays.copyOfRange(buffer, 0, bytesRead + 1));
							}
						}
						if (fromFragment + i < priorLen) {
							for (int j = fragments.size()-1; j > fromFragment + i - 1; j--) {
								fragments.remove(j);
							}
						}
						for (int j = 0; j < fromFragment; j++) {
							cachedLength += fragments.get(j).length;
						}
						int len = 0;
						for (byte[] bs : fragments) {
							len += bs.length;
						}
						totalLength = len;
						exchange.sendResponseHeaders(200, len);
						OutputStream os = exchange.getResponseBody();
						for (byte[] bs : fragments) {
							os.write(bs);
						}
						cacheProgram.putFileFragments(fileRequest, fragments);
						os.close();
					}
					cacheProgram.writeToLog("Response: " + (cachedLength*100)/totalLength + "% of file " + fileRequest + " was constucted with the cashed data");
				} else {
					String resp = "An error occured";
					exchange.sendResponseHeaders(responseCode, resp.getBytes().length);
					OutputStream os = exchange.getResponseBody();
					os.write(resp.getBytes());
					os.close();
				}
			} else {
				String url = "http://localhost:8080/full/" + fileRequest;
				URL obj = new URL(url);
				HttpURLConnection con = (HttpURLConnection)obj.openConnection();
				int responseCode = con.getResponseCode();
				fragments = new ArrayList<byte[]>();
				String contentType = null;
				if (responseCode == HttpURLConnection.HTTP_OK) {
					contentType = con.getContentType();
					InputStream in = con.getInputStream();
					int bytesRead = -1;
					byte[] buffer = new byte[BUFFER_SIZE];
					DataInputStream dip = new DataInputStream(in);
					byte[] prevBuffer = new byte[BUFFER_SIZE];
					try {
						while (true) {
							dip.readFully(buffer);
							fragments.add(Arrays.copyOf(buffer, BUFFER_SIZE));
							prevBuffer = buffer.clone();
						}
					} catch (EOFException eof) {
						for (int j = BUFFER_SIZE - 1; j > -1; j--) {
							if (buffer[j] != prevBuffer[j]) {
								bytesRead = j;
								break;
							}
						}
						fragments.add(Arrays.copyOfRange(buffer, 0, bytesRead + 1));
					}
					dip.close();

					Headers h = exchange.getResponseHeaders();
					h.add("Content-Type", contentType);
					int len = 0;
					for (byte[] a : fragments) {
						len += a.length;
					}

					exchange.sendResponseHeaders(200, len);
					OutputStream os = exchange.getResponseBody();
					for (byte[] a : fragments) {
						os.write(a);
					}
					cacheProgram.putFileFragments(fileRequest, fragments);
					os.close();
					cacheProgram.writeToLog("Response: full file " + fileRequest + " downloaded from server");
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
		HttpServer server = HttpServer.create(new InetSocketAddress(8099), 0);
		server.createContext("/info", new InfoHandler(this));
		server.createContext("/", new GetHandler(this));
		server.start();
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/cache/FragmentCacheView.fxml"));
		Parent root = loader.load();
		controller = loader.<FragmentCacheViewController>getController();
		Scene scene = new Scene(root,800,500);
		primaryStage.setScene(scene);
		primaryStage.titleProperty().setValue("Cache Overview");
		primaryStage.setResizable(false);
		controller.setEnvironment(this);
		primaryStage.show();
	}

}
