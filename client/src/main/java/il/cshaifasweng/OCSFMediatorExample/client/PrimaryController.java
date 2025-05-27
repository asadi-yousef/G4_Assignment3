package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
import javafx.fxml.FXML;
import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;


public class PrimaryController implements Initializable {

	@FXML
	private GridPane catalogGrid;
	@FXML
	private AnchorPane mainAnchorPane;

	@FXML
	private Rectangle backgroundRect;
	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		backgroundRect.widthProperty().bind(mainAnchorPane.widthProperty());
		backgroundRect.heightProperty().bind(mainAnchorPane.heightProperty());
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

	@Subscribe
	public void onCatalogReceived(Catalog catalog) {
		System.out.println("Catalog received");
		this.catalog = catalog;
		List<Flower> flowers = new ArrayList<>(new LinkedHashSet<>(catalog.getFlowers()));

		Platform.runLater(() -> {
			catalogGrid.getChildren().clear();
			int col = 0, row = 0;

			for (Flower flower : flowers) {
				GridPane itemPane = new GridPane();
				itemPane.setHgap(10);
				itemPane.setVgap(5);

				// Image setup
				ImageView imageView = new ImageView();
				try {
					System.out.println(flower.getImagePath());
					Image image = new Image(String.valueOf(PrimaryController.class.getResource(flower.getImagePath()))); // Load from URL or file
					imageView.setImage(image);
					imageView.setFitWidth(120);
					imageView.setFitHeight(120);
					imageView.setPreserveRatio(true);
				} catch (Exception e) {
					System.out.println("Failed to load image for " + flower.getName());
				}

				Label name = new Label("Name: " + flower.getName());
				Label type = new Label("Type: " + flower.getType());
				Label price = new Label(String.format("Price: $%.2f", flower.getPrice()));
				Button viewEdit = new Button("View");
				Button editPrice = new Button("Edit Price");

				int flowerId = flower.getId();

				viewEdit.setOnAction((ActionEvent event) -> {
					System.out.println("View flower with ID: " + flowerId);
					ViewFlowerController.setSelectedFlower(flower);
                    try {
                        App.switchToViewFlowerView();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

				editPrice.setOnAction((ActionEvent event) -> {
					TextInputDialog dialog = new TextInputDialog(String.format("%.2f", flower.getPrice()));
					dialog.setTitle("Edit Flower Price");
					dialog.setHeaderText("Edit price for: " + flower.getName());
					dialog.setContentText("Enter new price:");

					dialog.showAndWait().ifPresent(input -> {
						try {
							double newPrice = Double.parseDouble(input);
							if (newPrice < 0) {
								showAlert("Invalid Input", "Price cannot be negative.");
								return;
							}
							flower.setPrice(newPrice);
							price.setText(String.format("Price: $%.2f", newPrice));

							SimpleClient.getClient().sendToServer("update_price:" + flower.getId() + ":" + newPrice);
						} catch (NumberFormatException e) {
							showAlert("Invalid Input", "Please enter a valid number.");
						} catch (IOException e) {
							showAlert("Server Error", "Failed to send new price to server.");
						}
					});
				});

				// Add elements to itemPane
				itemPane.add(imageView, 0, 0, 2, 1); // Image at the top, spanning two columns if needed
				itemPane.add(name, 0, 1);
				itemPane.add(type, 0, 2);
				itemPane.add(price, 0, 3);
				itemPane.add(viewEdit, 0, 4);
				itemPane.add(editPrice, 0, 5);

				catalogGrid.add(itemPane, col, row);

				col++;
				if (col == 3) {
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
}
