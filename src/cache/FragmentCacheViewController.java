package cache;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;

public class FragmentCacheViewController {
	
	@FXML private ListView<HBox> fileListView;
	@FXML private Button refreshButton;
	@FXML private Button clearButton;
	@FXML private TextArea logArea;
	
	private FragmentCacheProgram cache;
	
	@FXML
	private void initialize() {
		
	}
	
	public void setEnvironment(FragmentCacheProgram cache) {
		this.cache = cache;
		refreshButtonPressed();
	}
	
	@FXML
	private void refreshButtonPressed() {
		readLog();
		makeFileList(cache.getCachedFileNames());
	}
	
	@FXML
	private void clearButtonPressed() {
		cache.clearCache();
		refreshButtonPressed();
	}
	
	private void readLog() {
		try {
			BufferedReader br = new BufferedReader(new FileReader("CacheFiles/fragmentLog.txt"));
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
	
	private void makeFileList(Collection<String> filenames) {
		//TODO Fix view
		fileListView.getItems().clear();
		for (String file : filenames) {
			HBox line = new HBox();
			Label fileLabel = new Label();
			fileLabel.wrapTextProperty().set(true);
			fileLabel.setText(file);
			
			List<byte[]> fragments = cache.getCachedFileFragments(file);
			int len = 0;
			for (byte[] a : fragments) {
				len += a.length;
			}
			Label bytesLabel = new Label();
			bytesLabel.wrapTextProperty().set(true);
			bytesLabel.setText("\tLength: " + len + " bytes");
			line.getChildren().add(fileLabel);
			line.getChildren().add(bytesLabel);
			fileListView.getItems().add(line);
		}
	}

}
