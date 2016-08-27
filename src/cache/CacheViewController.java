package cache;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

public class CacheViewController {
	
	@FXML private ListView<HBox> fileListView;
	@FXML private Button refreshButton;
	@FXML private Button clearButton;
	@FXML private TextArea logArea;
	
	private CacheProgram cache;
	
	@FXML
	private void initialize() {
		
	}
	
	public void setEnvironment(CacheProgram cache) {
		this.cache = cache;
		refreshButtonPressed();
	}
	
	@FXML
	private void refreshButtonPressed() {
		readLog();
		cache.refillMap();
		makeFileList(cache.getCachedFileNames());
	}
	
	@FXML
	private void clearButtonPressed() {
		cache.clearCache();
		refreshButtonPressed();
	}
	
	private void readLog() {
		try {
			BufferedReader br = new BufferedReader(new FileReader("CacheFiles/log.txt"));
			String line = br.readLine();
			logArea.clear();
			while (line != null) {
				logArea.appendText(line + "\n");
				line = br.readLine();
			}
			br.close();
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void makeFileList(Collection<String> fileNames) {
		fileListView.getItems().clear();
		for (String file : fileNames) {
			HBox line = new HBox();
			Label fileLabel = new Label();
			
			fileLabel.wrapTextProperty().set(true);
			
			fileLabel.setText(file);
			line.getChildren().add(fileLabel);
			fileListView.getItems().add(line);
		}
	}

}
