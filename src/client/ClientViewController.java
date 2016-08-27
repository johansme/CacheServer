package client;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;

public class ClientViewController {
	
	@FXML private ListView<HBox> fileListView;
	@FXML private Button chooseButton;
	@FXML private Button refreshButton;
	
	private ClientProgram client;
	
	@FXML
	private void initialize() {
		
	}
	
	public void setEnvironment(ClientProgram client) {
		this.client = client;
		refreshButtonPressed();
	}
	
	@FXML
	private void chooseButtonPressed() {
		Label fileLabel = (Label) fileListView.getSelectionModel().getSelectedItem().getChildren().get(0);
		String fileName = fileLabel.getText().trim();
		File file = client.getFileFromServer(fileName);
		file.deleteOnExit();
		try {
			Desktop.getDesktop().open(file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@FXML
	private void refreshButtonPressed() {
		String[] fileNames = client.getFileListFromServer();
		fileListView.getItems().clear();
		for (String file : fileNames) {
			HBox line = new HBox();
			Label fileLabel = new Label();
			
			fileLabel.wrapTextProperty().set(true);
			
			fileLabel.setText(file.trim());
			line.getChildren().add(fileLabel);
			fileListView.getItems().add(line);
		}
	}

}
