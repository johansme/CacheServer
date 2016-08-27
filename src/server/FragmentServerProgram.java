package server;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class FragmentServerProgram {
	
private Map<String, String> files;
	
	public FragmentServerProgram() {
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
		try {
			FragmentServerProgram program = new FragmentServerProgram();
			HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
			server.createContext("/info", new InfoHandler(program));
			server.createContext("/full/", new FullGetHandler(program));
			server.createContext("/", new GetHandler(program));
			server.start();
		} catch (IOException e) {
		}
	}
	
	static class InfoHandler implements HttpHandler {
		
		private FragmentServerProgram serverProgram;
		
		public InfoHandler(FragmentServerProgram p) {
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
	
	static class FullGetHandler implements HttpHandler {

		private FragmentServerProgram serverProgram;
		
		public FullGetHandler(FragmentServerProgram p) {
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
				exchange.sendResponseHeaders(200, bArray.length);
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
	
	static class GetHandler implements HttpHandler {

		private FragmentServerProgram serverProgram;
		
		public GetHandler(FragmentServerProgram p) {
			serverProgram = p;
		}
		
		private String get7DigitNumber(int number) {
			String s = String.valueOf(number);
			int l = s.length();
			if (l < 7) {
				for (int i = 0; i < 7-l; i++) {
					s = "0" + s;
				}
			}
			return s;
		}
		
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			String request = exchange.getRequestURI().toString();
			BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()));
			StringBuilder out = new StringBuilder();
			String hashes;
			while ((hashes = reader.readLine()) != null) {
				out.append(hashes);
			}
			hashes = out.toString();
			hashes = hashes.replaceAll("[\\[\\]]", "");
			List<String> hashList = new ArrayList<String>(Arrays.asList(hashes.split(",")));
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
				List<byte[]> fragments = new ArrayList<byte[]>();
				int startsAt = -1;
				for (int i = 0; i*2048 < bArray.length; i++) {
					byte[] b;
					if (bArray.length - i*2048 < 2048) {
						b = Arrays.copyOfRange(bArray, i*2048, bArray.length);
					} else {
						b = Arrays.copyOfRange(bArray, i*2048, (i+1)*2048);
					}
					String hash = FragmentServerProgram.getHash(b);
					if (! hashList.contains(hash)) {
						fragments.add(b);
						if (startsAt < 0) {
							startsAt = i;
						}
					}
				}
				if (startsAt >= 0) {
					byte[] num = get7DigitNumber(startsAt).getBytes();
					exchange.sendResponseHeaders(200, file.length() - startsAt*2048 + num.length);
					OutputStream os = exchange.getResponseBody();
					os.write(num);
					for (byte[] bs : fragments) {
						os.write(bs);
					}
					os.close();
				} else {
					exchange.sendResponseHeaders(200, 1);
					OutputStream os = exchange.getResponseBody();
					os.write(0);
					os.close();
				}
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
