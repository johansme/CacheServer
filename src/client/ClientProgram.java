package client;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ClientProgram extends Application {

	private static final int BUFFER_SIZE = 4096;
	
	private ClientViewController controller;

	public File getFileFromServer(String fileName) {
		try {
			String url = "http://localhost:8099/" + fileName;
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection)obj.openConnection();
			int responseCode = con.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				InputStream in = con.getInputStream();
				int bytesRead = -1;
				byte[] buffer = new byte[BUFFER_SIZE];
				String filePath = "ClientTemp/" + fileName;
				File file = new File(filePath);
				file.createNewFile();
				FileOutputStream out = new FileOutputStream(file);
				while ((bytesRead = in.read(buffer)) != -1) {
					out.write(buffer, 0, bytesRead);
				}
				in.close();
				out.close();
				return file;
			} else {
				System.out.println(responseCode);
			}
			con.disconnect();			
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
		return null;
	}

	public String[] getFileListFromServer() {
		try {
			String url = "http://localhost:8099/info";
			URL obj = new URL(url);
			HttpURLConnection con = (HttpURLConnection)obj.openConnection();
			int responseCode = con.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
				StringBuilder builder = new StringBuilder();
				String line;
				while ((line = reader.readLine()) != null) {
					builder.append(line);
				}
				String[] fileNames = builder.toString().replaceAll("[\\[\\]]", "").split(",");
				return fileNames;
			} else {
				return null;
			}
		} catch (IOException ioe) {

		}
		return null;
	}

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/ClientView.fxml"));
		Parent root = loader.load();
		controller = loader.<ClientViewController>getController();
		Scene scene = new Scene(root,500,500);
		primaryStage.setScene(scene);
		primaryStage.titleProperty().setValue("File Overview");
		primaryStage.setResizable(false);
		controller.setEnvironment(this);
		primaryStage.show();
	}

}
