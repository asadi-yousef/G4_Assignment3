package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.io.IOException;


public class Main extends Application {

	private static Stage primaryStage;

	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;
		switchToPrimaryView();
		stage.setTitle("Flower Catalog");
		stage.show();
	}

	public static void switchToPrimaryView() {
		try {
			FXMLLoader loader = new FXMLLoader(Main.class.getResource("primary.fxml"));
			Scene scene = new Scene(loader.load());
			primaryStage.setScene(scene);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void switchToSecondaryView() {
		try {
			FXMLLoader loader = new FXMLLoader(Main.class.getResource("/il/cshaifasweng/OCSFMediatorExample/client/secondary.fxml"));
			Scene scene = new Scene(loader.load());
			primaryStage.setScene(scene);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	public static void main(String[] args) {
		try {
			SimpleClient.getClient().openConnection();
		} catch (Exception e) {
			System.err.println("Failed to connect to server: " + e.getMessage());
		}
		launch(args);
	}
}
