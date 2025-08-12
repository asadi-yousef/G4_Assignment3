package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane; // needed for productCard.fxml
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

public class PrimaryController implements Initializable {

	// FXML Components
	@FXML private Button loginButton;
	@FXML private Label userStatusLabel;
	@FXML private HBox customerMenuBar;
	@FXML private HBox employeeMenuBar;
	@FXML private Button logoutButton;
	@FXML private Button employeeLogoutButton; // if present in FXML
	@FXML private GridPane catalogGrid;
	@FXML private ProgressIndicator loadingIndicator;
	@FXML private Label catalogLabel;
	@FXML private TextField searchTextField;

	// Reports button (must exist in primary.fxml with fx:id="reportButton")
	@FXML private Button reportButton;

	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}
		updateUIBasedOnUserStatus();
		loadCatalogData();

		if (searchTextField != null) {
			searchTextField.textProperty().addListener((obs, oldV, newV) -> renderCatalog());
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

			List<Product> productsToRender = new ArrayList<>(new LinkedHashSet<>(catalog.getFlowers()));
			String searchQuery = (searchTextField != null && searchTextField.getText() != null)
					? searchTextField.getText().toLowerCase().trim()
					: "";

			if (!searchQuery.isEmpty()) {
				productsToRender = productsToRender.stream()
						.filter(p -> p.getName() != null && p.getName().toLowerCase().contains(searchQuery))
						.collect(Collectors.toList());
			}

			int col = 0;
			int row = 0;
			for (Product product : productsToRender) {
				try {
					FXMLLoader loader = new FXMLLoader(getClass().getResource(
							"/il/cshaifasweng/OCSFMediatorExample/client/productCard.fxml"));
					StackPane cardNode = loader.load();
					ProductCardController cardController = loader.getController();

					if (isEmployee) {
						// EMPLOYEES get "Edit" and "Delete"
						cardController.setData(
								product,
								updatedProduct -> {
									try {
										Message message = new Message("editProduct", updatedProduct, null);
										SimpleClient.getClient().sendToServer(message);
									} catch (IOException e) { e.printStackTrace(); }
								},
								() -> handleDeleteProduct(product)
						);
					} else {
						// CUSTOMERS get "View" and "Add to Cart"
						cardController.setData(
								product,
								() -> handleViewProduct(product),
								() -> handleAddToCart(product)
						);
					}

					catalogGrid.add(cardNode, col, row);
					col = (col + 1) % 3;
					if (col == 0) row++;

				} catch (IOException e) {
					System.err.println("CRITICAL ERROR: Could not load productCard.fxml.");
					e.printStackTrace();
				}
			}
		});
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

				case "orders_data":
					try {
						EventBus.getDefault().unregister(this);
						App.setRoot("ordersScreenView");
					} catch (IOException e) {
						showAlert("Error", "Failed to open orders page.");
					}
					break;

				default:
					System.out.println("Received unhandled message from server: " + msg.getMessage());
			}
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

	// -------- Role helpers / UI state --------

	private boolean canSeeReports() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;
		if (u instanceof Employee) {
			String role = ((Employee) u).getRole();
			return "manager".equalsIgnoreCase(role) || "system_manager".equalsIgnoreCase(role);
		}
		return false;
	}

	private boolean isManager() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;

		if (u instanceof Employee) {
			try {
				String role = ((Employee) u).getRole();
				return role != null && role.toLowerCase().contains("manager");
			} catch (Exception ignored) { }
		}
		// fallback via reflection
		try {
			Method m = u.getClass().getMethod("getRole");
			Object role = m.invoke(u);
			return role != null && "MANAGER".equalsIgnoreCase(role.toString());
		} catch (Exception ignored) { }
		return false;
	}

	private void updateUIBasedOnUserStatus() {
		boolean isLoggedIn = SessionManager.getInstance().getCurrentUser() != null;
		boolean isEmployee = SessionManager.getInstance().isEmployee();

		loginButton.setVisible(!isLoggedIn);
		logoutButton.setVisible(isLoggedIn);
		userStatusLabel.setVisible(isLoggedIn);
		customerMenuBar.setVisible(isLoggedIn && !isEmployee);
		employeeMenuBar.setVisible(isLoggedIn && isEmployee);

		if (reportButton != null) {
			boolean showReports = isLoggedIn && canSeeReports();
			reportButton.setVisible(showReports);
			reportButton.setManaged(showReports);
		}

		if (isLoggedIn) {
			userStatusLabel.setText("Hi, " + SessionManager.getInstance().getCurrentUser().getUsername());
		}
	}

	// -------- Product actions --------

	private void handleAddToCart(Product product) {
		User currentUser = SessionManager.getInstance().getCurrentUser();
		if (currentUser == null) {
			showAlert("Login Required", "Please login to add items to your cart.");
			return;
		}
		try {
			List<Object> payload = new ArrayList<>();
			payload.add(currentUser);
			Message message = new Message("add_to_cart", product.getId(), payload);
			SimpleClient.getClient().sendToServer(message);
			// show alert on server confirmation if you prefer
			showAlert("Success", product.getName() + " was added to your cart!");
		} catch (IOException e) {
			showAlert("Error", "Failed to add item to cart.");
		}
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

	// -------- Alerts --------

	private void showAlert(String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}

	// -------- Navigation --------

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

	// optional second entry-point you had
	@FXML public void handleOrdersScreen(ActionEvent actionEvent) {
		try {
			User currentUser = SessionManager.getInstance().getCurrentUser();
			if (currentUser == null) {
				showAlert("Login Required", "Please login to view your orders.");
				return;
			}
			App.setRoot("ordersScreenView");
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

	@FXML void handleViewReports(ActionEvent event) {
		if (!isManager()) {
			showAlert("Permission Denied", "You do not have permission to view reports.");
			return;
		}
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("reportView"); // ensure your FXML file is reportView.fxml
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Failed to open reports page:\n" + e.getMessage());
		}
	}

	@FXML void handleManageComplaints(ActionEvent event) {
		showAlert("Action", "Manage Complaints clicked.");
	}

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
