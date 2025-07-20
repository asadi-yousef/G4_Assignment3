package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
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

import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;


public class PrimaryController implements Initializable {

	public Button loginButton;
	public Label userStatusLabel;
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
		if(SessionManager.getInstance().getCurrentUser() != null) {
			loginButton.setVisible(false);
			loginButton.setDisable(true);
			userStatusLabel.setVisible(true);
			userStatusLabel.setText("Hi "+SessionManager.getInstance().getCurrentUser().getUsername());
		}
		else {
			loginButton.setVisible(true);
			userStatusLabel.setVisible(false);
		}
	}
	private void renderCatalog() {
		List<Product> products = new ArrayList<>(new LinkedHashSet<>(catalog.getFlowers()));

		Platform.runLater(() -> {
			catalogGrid.getChildren().clear();
			int col = 0, row = 0;

			for (Product product : products) {
				GridPane itemPane = new GridPane();
				itemPane.setHgap(10);
				itemPane.setVgap(5);

				// Image setup
				ImageView imageView = new ImageView();
				try {
					System.out.println(product.getImagePath());
					Image image = new Image(String.valueOf(PrimaryController.class.getResource(product.getImagePath()))); // Load from URL or file
					imageView.setImage(image);
					imageView.setFitWidth(120);
					imageView.setFitHeight(120);
					imageView.setPreserveRatio(true);
				} catch (Exception e) {
					System.out.println("Failed to load image for " + product.getName());
				}

				Label name = new Label("Name: " + product.getName());
				Label type = new Label("Type: " + product.getType());
				Label price = new Label(String.format("Price: $%.2f", product.getPrice()));
				Button viewEdit = new Button("View");
				Button editPrice = new Button("Edit Price");

				Long flowerId = product.getId();

				viewEdit.setOnAction((ActionEvent event) -> {
					System.out.println("View flower with ID: " + flowerId);
					ViewFlowerController.setSelectedFlower(product);
					try {
						App.setRoot("viewFlower");
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});

				editPrice.setOnAction((ActionEvent event) -> {
					TextInputDialog dialog = new TextInputDialog(String.format("%.2f", product.getPrice()));
					dialog.setTitle("Edit Flower Price");
					dialog.setHeaderText("Edit price for: " + product.getName());
					dialog.setContentText("Enter new price:");

					dialog.showAndWait().ifPresent(input -> {
						try {
							double newPrice = Double.parseDouble(input);
							if (newPrice < 0) {
								showAlert("Invalid Input", "Price cannot be negative.");
								return;
							}
							product.setPrice(newPrice);
							price.setText(String.format("Price: $%.2f", newPrice));

							SimpleClient.getClient().sendToServer("update_price:" + product.getId() + ":" + newPrice);
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
				if(SessionManager.getInstance().isEmployee()) {
					itemPane.add(editPrice, 0, 5);
				}

				catalogGrid.add(itemPane, col, row);

				col++;
				if (col == 3) {
					col = 0;
					row++;
				}
			}
		});
	}

	@Subscribe
	public void onMessageFromServer(Message msg) {
		System.out.println(msg.getMessage());
		if (msg.getMessage().startsWith("editProduct")) {
			System.out.println("wwww2");
			try {
				SimpleClient.getClient().sendToServer("request_catalog");
			} catch (Exception e) {
				e.printStackTrace();
				showAlert("Error", "Failed to update product price.");
			}
		}
		else if(msg.getMessage().startsWith("catalog")) {
			System.out.println("Received updated catalog from server");
			this.catalog = (Catalog) msg.getObject();
			renderCatalog();
		}
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

	public void handleLogin(ActionEvent actionEvent) throws IOException {
		App.setRoot("logInView");
		EventBus.getDefault().unregister(this);
	}
}
