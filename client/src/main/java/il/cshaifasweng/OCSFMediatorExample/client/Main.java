package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class Main extends Application {
	private static Stage primaryStage;

	@Override
	public void start(Stage stage) throws Exception {
		primaryStage = stage;
		switchToPrimaryView();
		primaryStage.show();
	}

	public static void switchToPrimaryView() throws Exception {
		FXMLLoader loader = new FXMLLoader(Main.class.getResource("/il/cshaifasweng/OCSFMediatorExample/client/primary.fxml"));
		Parent root = loader.load();
		primaryStage.setTitle("Flower Catalog");
		primaryStage.setScene(new Scene(root));
	}

	public static void switchToSecondaryView() throws IOException {
		FXMLLoader loader = new FXMLLoader(Main.class.getResource("secondary.fxml"));
		Parent root = loader.load();

		// Get controller and call the method to load flower data
		SecondaryController controller = loader.getController();
		controller.loadFlowerDetails();

		primaryStage.setScene(new Scene(root));
		primaryStage.show();

		//try {
			//FXMLLoader loader = new FXMLLoader(Main.class.getResource("/il/cshaifasweng/OCSFMediatorExample/client/secondary.fxml"));
			//Parent root = loader.load();
			//primaryStage.setTitle("Edit Flower Price");
			//primaryStage.setScene(new Scene(root));
		//} catch (Exception e) {
		//	e.printStackTrace();
		//}

	}

	public static void main(String[] args) {
		launch(args);
	}
}
