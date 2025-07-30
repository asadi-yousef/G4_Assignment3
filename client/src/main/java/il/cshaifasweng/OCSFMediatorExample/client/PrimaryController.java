package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import il.cshaifasweng.OCSFMediatorExample.entities.Cart;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.Node;

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

	// Menu bar components
	@FXML
	private HBox customerMenuBar;
	@FXML
	private Button catalogButton;
	@FXML
	private Button cartButton;
	@FXML
	private Button ordersButton;
	@FXML
	private Button complaintsButton;
	@FXML
	private Button profileButton;
	@FXML
	private Button logoutButton;

	@FXML
	private GridPane catalogGrid;
	@FXML
	private AnchorPane mainAnchorPane;
	@FXML
	private Rectangle backgroundRect;
	@FXML
	private Label catalogLabel;

	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		backgroundRect.widthProperty().bind(mainAnchorPane.widthProperty());
		backgroundRect.heightProperty().bind(mainAnchorPane.heightProperty());
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}

		try {
			if (!SimpleClient.getClient().isConnected()) {
				SimpleClient.getClient().openConnection();
			}
			SimpleClient.getClient().sendToServer("request_catalog");
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Connection Error", "Failed to connect to server.");
		}

		updateUIBasedOnUserStatus();
	}

	private void updateUIBasedOnUserStatus() {
		if(SessionManager.getInstance().getCurrentUser() != null) {
			// User is logged in
			loginButton.setVisible(false);
			loginButton.setDisable(true);
			userStatusLabel.setVisible(true);
			userStatusLabel.setText("Hi " + SessionManager.getInstance().getCurrentUser().getUsername());

			// Show customer menu bar for customers only
			if(!SessionManager.getInstance().isEmployee()) {
				customerMenuBar.setVisible(true);
				logoutButton.setVisible(true);
			}
		} else {
			// User is not logged in
			loginButton.setVisible(true);
			loginButton.setDisable(false);
			userStatusLabel.setVisible(false);
			customerMenuBar.setVisible(false);
			logoutButton.setVisible(false);
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
					Image image = new Image(String.valueOf(PrimaryController.class.getResource(product.getImagePath())));
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
				Button addToCart = new Button("Add to Cart");

				Long flowerId = product.getId();

				viewEdit.setOnAction((ActionEvent event) -> {
					System.out.println("View flower with ID: " + flowerId);
					ViewFlowerController.setSelectedFlower(product);
					try {
						EventBus.getDefault().unregister(this);
						App.setRoot("viewFlower");
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});

				addToCart.setOnAction((ActionEvent event) -> {
					if (SessionManager.getInstance().getCurrentUser() != null && !SessionManager.getInstance().isEmployee()) {
						try {
							User currentUser = SessionManager.getInstance().getCurrentUser();

							List<Object> payload = new ArrayList<>();
							payload.add(currentUser);

							Message message = new Message("add_to_cart", product.getId(), payload);
							SimpleClient.getClient().sendToServer(message);

							showAlert("Success", product.getName() + " added to cart!");
						} catch (IOException e) {
							showAlert("Error", "Failed to add item to cart.");
						}
					} else {
						showAlert("Login Required", "Please login to add items to cart.");
					}
				});


				// Add elements to itemPane
				itemPane.add(imageView, 0, 0, 2, 1);
				itemPane.add(name, 0, 1);
				itemPane.add(type, 0, 2);
				itemPane.add(price, 0, 3);
				itemPane.add(viewEdit, 0, 4);
				itemPane.add(addToCart, 0, 5);

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
			try {
				SimpleClient.getClient().sendToServer("request_catalog");
			} catch (Exception e) {
				e.printStackTrace();
				showAlert("Error", "Failed to update product price.");
			}
		}
		else if(msg.getMessage().startsWith("catalog")) {
			this.catalog = (Catalog) msg.getObject();
			renderCatalog();
		}
		else if(msg.getMessage().startsWith("add_product")) {
			this.catalog.getFlowers().add((Product) msg.getObject());
			renderCatalog();
		}
		else if(msg.getMessage().startsWith("delete_product")) {
			catalog.getFlowers().removeIf(p->p.getId().equals(msg.getObject()));
			renderCatalog();
		}
		else if(msg.getMessage().startsWith("cart_updated")) {
			showAlert("Success", "Cart updated successfully!");
		}
		else if (msg.getMessage().startsWith("cart_data")) {
			Cart cart = (Cart) msg.getObject();
			Platform.runLater(() -> {
				try {
					FXMLLoader loader = new FXMLLoader(getClass().getResource("cartView.fxml"));
					Parent root = loader.load();

					CartController cartController = loader.getController();
					cartController.setCart(cart); // Pass the cart to the controller

					Stage stage = (Stage) catalogGrid.getScene().getWindow();
					stage.setScene(new Scene(root));
					stage.show();

					EventBus.getDefault().unregister(this);

				} catch (IOException e) {
					showAlert("Error", "Failed to open cart page.");
				}
			}); // pass to the cart controller
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

	// Menu navigation handlers
	@FXML
	public void handleCatalog(ActionEvent actionEvent) {
		// Already on catalog page, maybe refresh
		try {
			SimpleClient.getClient().sendToServer("request_catalog");
		} catch (IOException e) {
			showAlert("Error", "Failed to refresh catalog.");
		}
	}

	@FXML
	public void handleCart(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if (currentUser == null) {
				showAlert("Login Required", "Please login to view your cart.");
				return;
			}

			// Ask server for the cart
			List<Object> payload = new ArrayList<>();
			payload.add(currentUser);
			Message message = new Message("request_cart", null, payload);
			SimpleClient.getClient().sendToServer(message);

			// We'll wait for the response before switching view
		} catch (IOException e) {
			showAlert("Error", "Failed to request cart.");
		}
	}



	@FXML
	public void handleOrders(ActionEvent actionEvent) {
		try {
			App.setRoot("ordersView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open orders page.");
		}
	}

	@FXML
	public void handleComplaints(ActionEvent actionEvent) {
		try {
			App.setRoot("complaintsView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open complaints page.");
		}
	}

	@FXML
	public void handleProfile(ActionEvent actionEvent) {
		try {
			App.setRoot("profileView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open profile page.");
		}
	}

	@FXML
	public void handleLogout(ActionEvent actionEvent) {
		SessionManager.getInstance().logout();
		updateUIBasedOnUserStatus();
		showAlert("Success", "Logged out successfully!");
	}

	@FXML
	public void handleLogin(ActionEvent actionEvent) throws IOException {
		App.setRoot("logInView");
		EventBus.getDefault().unregister(this);
	}
}