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
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

public class PrimaryController implements Initializable {

	@FXML private Button loginButton;
	@FXML private Label userStatusLabel;
	@FXML private HBox customerMenuBar;
	@FXML private HBox employeeMenuBar;
	@FXML private Button logoutButton;
	@FXML private GridPane catalogGrid;
	@FXML private ProgressIndicator loadingIndicator;
	@FXML private Label catalogLabel;
	@FXML private TextField searchTextField;

	// NEW: Reports button (must exist in primary.fxml with fx:id="reportButton")
	@FXML private Button reportButton;

	private Catalog catalog;

	@Override
	public void initialize(URL location, ResourceBundle resources) {
		if (!EventBus.getDefault().isRegistered(this)) {
			EventBus.getDefault().register(this);
		}
		updateUIBasedOnUserStatus();
		loadCatalogData();
		searchTextField.textProperty().addListener((observable, oldValue, newValue) -> renderCatalog());
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
			String searchQuery = searchTextField.getText().toLowerCase().trim();
			if (!searchQuery.isEmpty()) {
				productsToRender = productsToRender.stream()
						.filter(p -> p.getName().toLowerCase().contains(searchQuery))
						.collect(Collectors.toList());
			}

			int col = 0;
			int row = 0;
			for (Product product : productsToRender) {
				try {
					FXMLLoader loader = new FXMLLoader(getClass().getResource("/il/cshaifasweng/OCSFMediatorExample/client/productCard.fxml"));
					StackPane cardNode = loader.load();
					ProductCardController cardController = loader.getController();

					if (isEmployee) {
						// EMPLOYEES get "Edit" and "Delete" buttons
						cardController.setData(
								product,
								updatedProduct -> { // The saveAction (Consumer)
									try {
										Message message = new Message("editProduct", updatedProduct, null);
										SimpleClient.getClient().sendToServer(message);
									} catch (IOException e) { e.printStackTrace(); }
								},
								() -> handleDeleteProduct(product) // The deleteAction (Runnable)
						);
					} else {
						// CUSTOMERS get "View" and "Add to Cart" buttons
						cardController.setData(
								product,
								() -> handleViewProduct(product),    // The viewAction (Runnable)
								() -> handleAddToCart(product)     // The addToCartAction (Runnable)
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
				default:
					System.out.println("Received unhandled message from server: " + msg.getMessage());
					break;
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
			showAlert("Connection Error", "Failed to connect to the server.");
		});
		new Thread(loadTask).start();
	}

	private boolean canSeeReports() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;
		else if (u instanceof Employee) {
			Employee e = (Employee) u;
			String role = e.getRole();
			return "manager".equalsIgnoreCase(role) ||
					"system_manager".equalsIgnoreCase(role);
		}
		else return false;
	}

	private String readRole(User u) {

		try { Object r = u.getClass().getMethod("getRole").invoke(u);
			return r == null ? "" : r.toString(); } catch (Exception e) { return ""; }
	}

	private void updateUIBasedOnUserStatus() {
		boolean isLoggedIn = SessionManager.getInstance().getCurrentUser() != null;
		boolean isEmployee = SessionManager.getInstance().isEmployee();
		boolean isManager = isManager();

		loginButton.setVisible(!isLoggedIn);
		logoutButton.setVisible(isLoggedIn);
		userStatusLabel.setVisible(isLoggedIn);
		customerMenuBar.setVisible(isLoggedIn && !isEmployee);
		employeeMenuBar.setVisible(isLoggedIn && isEmployee);

		// NEW: control visibility of Reports button
		if (reportButton != null) {
			reportButton.setVisible(isLoggedIn && canSeeReports());
			reportButton.setManaged(isLoggedIn && canSeeReports()); // hide from layout when not visible
		}

		if (isLoggedIn) {
			userStatusLabel.setText("Hi, " + SessionManager.getInstance().getCurrentUser().getUsername());
		}
	}

	/**
	 * Helper to determine if current user is a MANAGER.
	 * Tries several common patterns to avoid coupling to a specific model:
	 * - If user is an Employee with getRole() returning enum/string "MANAGER"
	 * - If User itself has getRole()
	 */
	private boolean isManager() {
		User u = SessionManager.getInstance().getCurrentUser();
		if (u == null) return false;

		// If Employee subclass with getRole()
		if (u instanceof Employee) {
			Employee e = (Employee) u;
			try {
				String role = e.getRole();
				return role != null && role.contains("manager");
			} catch (Exception ignored) { /* fallthrough */ }
		}

		// Generic getRole on User (string/enum)
		try {
			Method m = u.getClass().getMethod("getRole");
			Object role = m.invoke(u);
			return role != null && "MANAGER".equalsIgnoreCase(role.toString());
		} catch (Exception ignored) { }

		return false;
	}

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

	private void showAlert(String title, String message) {
		Platform.runLater(() -> {
			Alert alert = new Alert(Alert.AlertType.INFORMATION);
			alert.setTitle(title);
			alert.setHeaderText(null);
			alert.setContentText(message);
			alert.showAndWait();
		});
	}

	@FXML
	public void handleCatalog(ActionEvent actionEvent) {
		loadCatalogData();
	}

	@FXML
	public void handleCart(ActionEvent actionEvent) {
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
	public void handleProfile(ActionEvent event) {
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

	@FXML
	void handleManageCatalog(ActionEvent event) {
		loadCatalogData();
	}

	@FXML
	void handleViewReports(ActionEvent event) {
		// Only managers can open the reports view
		if (!isManager()) {
			showAlert("Permission Denied", "You do not have permission to view reports.");
			return;
		}
		try {
			App.setRoot("reportView"); // Ensure reportView.fxml is placed like other views
			EventBus.getDefault().unregister(this);
		} catch (IOException e) {
			e.printStackTrace();
			showAlert("Error", "Failed to open reports page:\n" + e.getMessage());
		}
	}

	@FXML
	void handleManageComplaints(ActionEvent event) {
		showAlert("Action", "Manage Complaints clicked.");
	}

	@FXML
	public void handleLogout(ActionEvent actionEvent) {
		SessionManager.getInstance().logout();
		updateUIBasedOnUserStatus();
		renderCatalog();
		showAlert("Success", "Logged out successfully!");
	}

	@FXML
	public void handleLogin(ActionEvent actionEvent) {
		try {
			EventBus.getDefault().unregister(this);
			App.setRoot("logInView");
		} catch (IOException e) {
			showAlert("Error", "Failed to open login page.");
		}
	}
}
