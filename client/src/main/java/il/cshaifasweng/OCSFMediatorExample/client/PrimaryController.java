package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import java.util.LinkedHashSet;
import java.util.ArrayList;

public class PrimaryController implements Initializable {

	@FXML
	private GridPane catalogGrid;

	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		EventBus.getDefault().register(this);
		try {
			if (!SimpleClient.getClient().isConnected()) {
				SimpleClient.getClient().openConnection();
			}
			SimpleClient.getClient().sendToServer("request_catalog");
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Connection Error", "Failed to connect to server.");
		}
	}

	@FXML
	private void onEditPricesClicked(ActionEvent event) {
		// TODO: Add your logic here, for now just print to console
		System.out.println("Edit Prices button clicked!");
	}

	@Subscribe
	public void onCatalogReceived(Catalog catalog) {
		this.catalog = catalog;
		List<Flower> flowers = new ArrayList<>(new LinkedHashSet<>(catalog.getFlowers()));

		Platform.runLater(() -> {
			catalogGrid.getChildren().clear();
			int col = 0, row = 0;

			for (Flower flower : flowers) {
				GridPane itemPane = new GridPane();
				itemPane.setHgap(10);
				itemPane.setVgap(5);

				Label name = new Label("Name: " + flower.getName());
				Label type = new Label("Type: " + flower.getType());
				Label price = new Label(String.format("Price: $%.2f", flower.getPrice()));
				Button viewEdit = new Button("View");

				viewEdit.setOnAction(event -> {
					try {
						SecondaryController.setSelectedFlower(flower);
						Main.switchToSecondaryView();
					} catch (Exception e) {
						e.printStackTrace();
						showAlert("Navigation Error", "Could not switch to detail view.");
					}
				});

				itemPane.add(name, 0, 0);
				itemPane.add(type, 0, 1);
				itemPane.add(price, 0, 2);
				itemPane.add(viewEdit, 0, 3);

				catalogGrid.add(itemPane, col, row);

				col++;
				if (col == 2) {
					col = 0;
					row++;
				}
			}
		});
	}

	private void showAlert(String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}

	public void onClose() {
		EventBus.getDefault().unregister(this);
	}
}
