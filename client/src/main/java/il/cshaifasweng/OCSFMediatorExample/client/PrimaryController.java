package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;

public class PrimaryController {

	@FXML
	private VBox productButtonContainer;

	@FXML
	private Button editPriceButton;

	private Catalog catalog;

	@FXML
	public void initialize() {
		EventBus.getDefault().register(this);

		try {
			SimpleClient.getClient().sendToServer("get_catalog");
		} catch (IOException e) {
			e.printStackTrace();
			showError("Failed to request catalog from server.");
		}

		editPriceButton.setOnAction(event -> {
			if (catalog != null) {
				SecondaryController.setCatalog(catalog); // ✅ Now defined
				Main.switchToSecondaryView();           // ✅ Now defined
			} else {
				showError("Catalog not loaded.");
			}
		});
	}

	@Subscribe
	public void onCatalogReceived(Catalog catalog) {
		this.catalog = catalog;

		Platform.runLater(() -> {
			productButtonContainer.getChildren().clear();

			for (Flower flower : catalog.getFlowers()) {
				Button button = new Button(flower.getName() + " | " + flower.getType() + " | $" + flower.getPrice());
				button.setOnAction(event -> showFlowerDetails(flower));
				button.setStyle("-fx-font-size: 14px; -fx-pref-width: 300px;");
				productButtonContainer.getChildren().add(button);
			}
		});
	}

	private void showFlowerDetails(Flower flower) {
		Alert alert = new Alert(Alert.AlertType.INFORMATION);
		alert.setTitle("Flower Details");
		alert.setHeaderText(null);
		alert.setContentText("Name: " + flower.getName() + "\n"
				+ "Type: " + flower.getType() + "\n"
				+ "Price: $" + flower.getPrice());
		alert.showAndWait();
	}

	private void showError(String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.setTitle("Error");
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}
}
