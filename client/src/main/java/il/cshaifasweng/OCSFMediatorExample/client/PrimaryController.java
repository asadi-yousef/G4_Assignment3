package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import java.util.stream.Collectors;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class PrimaryController implements Initializable {

	@FXML private Button loginButton;
	@FXML private Label userStatusLabel;
	@FXML private HBox customerMenuBar;
	@FXML private HBox employeeMenuBar;
	@FXML private Button logoutButton;
	@FXML private Button employeeLogoutButton;
	@FXML private GridPane catalogGrid;
	@FXML private ProgressIndicator loadingIndicator;
	@FXML private Label catalogLabel;
	@FXML private TextField searchTextField;

	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}
		updateUIBasedOnUserStatus();
		loadCatalogData();

		searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			renderCatalog();
		});
	}

	private void loadCatalogData() {
		loadingIndicator.setVisible(true);
		catalogGrid.getChildren().clear();

		Task<Void> loadTask = new Task<>() {
			@Override
			protected Void call() throws Exception {
				if (!SimpleClient.getClient().isConnected()) {
					SimpleClient.getClient().openConnection();
				}
				SimpleClient.getClient().sendToServer("request_catalog");
				return null;
			}
		};

		loadTask.setOnFailed(e -> {
			loadingIndicator.setVisible(false);
			showAlert("Connection Error", "Failed to connect to the server. Please check your connection.");
		});

		new Thread(loadTask).start();
	}

	private void updateUIBasedOnUserStatus() {
		boolean isLoggedIn = SessionManager.getInstance().getCurrentUser() != null;
		boolean isEmployee = SessionManager.getInstance().isEmployee();

		loginButton.setVisible(!isLoggedIn);
		userStatusLabel.setVisible(isLoggedIn);
		customerMenuBar.setVisible(isLoggedIn && !isEmployee);
		employeeMenuBar.setVisible(isLoggedIn && isEmployee);
		logoutButton.setVisible(isLoggedIn);

		if (isLoggedIn) {
			userStatusLabel.setText("Hi, " + SessionManager.getInstance().getCurrentUser().getUsername());
		}
	}

	private void renderCatalog() {
		boolean isEmployee = SessionManager.getInstance().isEmployee();

		Platform.runLater(() -> {
			catalogGrid.getChildren().clear();
			if (catalog == null || catalog.getFlowers() == null) {
				loadingIndicator.setVisible(false);
				return;
			}

			List<Product> allProducts = new ArrayList<>(new LinkedHashSet<>(catalog.getFlowers()));
			String searchQuery = searchTextField.getText().toLowerCase().trim();

			List<Product> productsToRender;
			if (searchQuery.isEmpty()) {
				productsToRender = allProducts;
			} else {
				productsToRender = allProducts.stream()
						.filter(product -> product.getName().toLowerCase().contains(searchQuery))
						.collect(Collectors.toList());
			}

			int col = 0;
			int row = 0;

			for (Product product : productsToRender) {
				VBox productCard = new VBox(10);
				productCard.setAlignment(Pos.CENTER);
				productCard.setPadding(new Insets(15));
				productCard.setStyle("-fx-background-color: #ffffff; -fx-border-color: #dddddd; -fx-border-radius: 8; -fx-background-radius: 8; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 10, 0, 0, 5);");

				ImageView imageView = new ImageView();
				try {
					System.out.println(Objects.requireNonNull(PrimaryController.class.getResource(product.getImagePath())).toExternalForm());
					Image image = new Image(Objects.requireNonNull(PrimaryController.class.getResource(product.getImagePath())).toExternalForm());
					imageView.setImage(image);
				} catch (Exception e) {
					System.err.println("Failed to load image for " + product.getName() + " at path: " + product.getImagePath());
				}
				imageView.setFitWidth(150);
				imageView.setFitHeight(150);
				imageView.setPreserveRatio(true);

				Label name = new Label(product.getName());
				name.setFont(new Font("Bell MT Bold", 18));
				Label price = new Label(String.format("$%.2f", product.getPrice()));
				price.setFont(new Font("Bell MT", 16));
				Label category = new Label("Category: "+product.getType());
				category.setFont(new Font("Bell MT", 16));

				productCard.getChildren().addAll(imageView, name, category ,price);

				if (isEmployee) {
					HBox buttonBox = new HBox(10, createEditButton(product), createDeleteButton(product));
					buttonBox.setAlignment(Pos.CENTER);
					productCard.getChildren().add(buttonBox);
				} else {
					Button viewButton = new Button("View");
					viewButton.setOnAction(event -> handleViewProduct(product));

					Button addToCartButton = new Button("Add to Cart");
					addToCartButton.setOnAction(event -> {
						try {
							User currentUser = SessionManager.getInstance().getCurrentUser();
							List<Object> payload = new ArrayList<>();
							payload.add(currentUser);
							Message message = new Message("add_to_cart", product.getId(), payload);
							SimpleClient.getClient().sendToServer(message);
						} catch (IOException e) {
							showAlert("Error", "Failed to add item to cart.");
						}
					});

					HBox buttonBox = new HBox(10, viewButton, addToCartButton);
					buttonBox.setAlignment(Pos.CENTER);
					productCard.getChildren().add(buttonBox);
				}

				catalogGrid.add(productCard, col, row);

				col++;
				if (col == 3) {
					col = 0;
					row++;
				}
			}
		});
	}

	private Button createEditButton(Product product) {
		Button editButton = new Button("Edit");
		editButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
		editButton.setOnAction(event -> handleEditProduct(product));
		return editButton;
	}

	private Button createDeleteButton(Product product) {
		Button deleteButton = new Button("Delete");
		deleteButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
		deleteButton.setOnAction(event -> handleDeleteProduct(product));
		return deleteButton;
	}

	private void handleViewProduct(Product product) {
		ViewFlowerController.setSelectedFlower(product);
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("viewFlower");
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Could not open the product page.");
		}
	}

	private void handleEditProduct(Product product) {
		showAlert("Action", "Navigating to edit: " + product.getName());
	}

	private void handleDeleteProduct(Product product) {
		Alert confirmationAlert = new Alert(Alert.AlertType.CONFIRMATION);
		confirmationAlert.setTitle("Confirm Deletion");
		confirmationAlert.setHeaderText("Are you sure you want to delete '" + product.getName() + "'?");
		confirmationAlert.setContentText("This action cannot be undone.");

		Optional<ButtonType> result = confirmationAlert.showAndWait();
		if (result.isPresent() && result.get() == ButtonType.OK) {
			try {
				Message message = new Message("delete_product_by_id", product.getId(), null);
				SimpleClient.getClient().sendToServer(message);
				showAlert("Success", "Delete request for " + product.getName() + " has been sent.");
			} catch (IOException e) {
				showAlert("Error", "Failed to send delete request to server.");
			}
		}
	}

	@Subscribe
	public void onMessageFromServer(Message msg) {
		Platform.runLater(() -> {
			loadingIndicator.setVisible(false);
			switch (msg.getMessage()) {
				case "catalog":
					this.catalog = (Catalog) msg.getObject();
					renderCatalog();
					break;
				case "add_product":
				case "editProduct":
				case "delete_product_by_id":
					loadCatalogData();
					break;
				case "cart_updated":
					showAlert("Success", "Cart updated successfully!");
					break;
				case "cart_data":
					try {
						EventBus.getDefault().unregister(this);
						App.setRoot("cartView");
					} catch (IOException e) {
						showAlert("Error", "Failed to open cart page.");
					}
					break;
				default:
					System.out.println("Received unhandled message from server: " + msg.getMessage());
					break;
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

	// --- Navigation Handlers ---

	@FXML public void handleCatalog(ActionEvent actionEvent) { loadCatalogData(); }

	@FXML public void handleCart(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if (currentUser == null) {
				showAlert("Login Required", "Please login to view your cart.");
				return;
			}
			List<Object> payload = new ArrayList<>();
			payload.add(currentUser);
			Message message = new Message("request_cart", null, payload);
			SimpleClient.getClient().sendToServer(message);
		} catch (IOException e) {
			showAlert("Error", "Failed to request cart.");
		}
	}

	@FXML public void handleOrders(ActionEvent actionEvent) {
		try {
			App.setRoot("ordersView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open orders page.");
		}
	}

	@FXML public void handleComplaints(ActionEvent actionEvent) {
		try {
			App.setRoot("complaintsView");
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to open complaints page.");
		}
	}

	@FXML public void handleProfile(ActionEvent event) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("Profile.fxml"));
			Parent root = loader.load();
			Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
			stage.setScene(new Scene(root));
			stage.show();
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			showAlert("Error", "Failed to load profile page.");
		}
	}

	@FXML void handleManageCatalog(ActionEvent event) { loadCatalogData(); }

	@FXML void handleViewReports(ActionEvent event) { showAlert("Action", "View Reports clicked."); }

	@FXML void handleManageComplaints(ActionEvent event) { showAlert("Action", "Manage Complaints clicked."); }

	@FXML public void handleLogout(ActionEvent actionEvent) {
		SessionManager.getInstance().logout();
		updateUIBasedOnUserStatus();
		renderCatalog();
		showAlert("Success", "Logged out successfully!");
	}

	@FXML public void handleLogin(ActionEvent actionEvent) {
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("logInView");
		} catch (IOException e) {
			showAlert("Error", "Failed to open login page.");
		}
	}
}