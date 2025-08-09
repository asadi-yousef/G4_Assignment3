package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Order;
import il.cshaifasweng.OCSFMediatorExample.entities.OrderItem;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.VBox;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class OrderController implements Initializable {

    @FXML
    private ChoiceBox<String> storeLocationChoice;

    @FXML
    private RadioButton deliveryRadio;

    @FXML
    private VBox deliveryDetailsSection;

    @FXML
    private RadioButton pickupRadio;

    @FXML
    private DatePicker deliveryDatePicker;

    @FXML
    private TextField recipientPhoneField;

    @FXML
    private CheckBox differentRecipientCheck;

    @FXML
    private Button placeOrderButton;

    @FXML
    private Button cancelButton;

    private ToggleGroup deliveryGroup;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        // Setup store locations
        storeLocationChoice.getItems().addAll("Downtown", "Mall Branch", "Westside Store");

        // Setup delivery/pickup group
        deliveryGroup = new ToggleGroup();
        deliveryRadio.setToggleGroup(deliveryGroup);
        pickupRadio.setToggleGroup(deliveryGroup);

        // By default, disable recipient phone unless differentRecipientCheck is checked
        recipientPhoneField.setDisable(true);
        differentRecipientCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            recipientPhoneField.setDisable(!newVal);
            if (!newVal) {
                recipientPhoneField.clear();
            }
        });


        // Show/hide delivery details based on delivery method selection
        deliveryGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == deliveryRadio) {
                // Show delivery details section
                deliveryDetailsSection.setVisible(true);
                deliveryDetailsSection.setManaged(true);
            } else if (newToggle == pickupRadio) {
                // Hide delivery details section
                deliveryDetailsSection.setVisible(false);
                deliveryDetailsSection.setManaged(false);
                // Clear delivery-related fields
                deliveryDatePicker.setValue(null);
                differentRecipientCheck.setSelected(false);
                recipientPhoneField.clear();
            }
        });

        // Button handlers
        placeOrderButton.setOnAction(e -> placeOrder());
        cancelButton.setOnAction(e -> goBackToCart());
    }

    private void placeOrder() {
        try {
            // Get current user and ensure it's a Customer
            if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                showAlert("Error", "Only customers can place orders.");
                return;
            }
            Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();

            // Validate store location
            String storeLocation = storeLocationChoice.getValue();
            if (storeLocation == null) {
                showAlert("Missing Data", "Please select a store location.");
                return;
            }

            // Validate delivery method
            String deliveryMethod = deliveryRadio.isSelected() ? "Delivery" : pickupRadio.isSelected() ? "Pickup" : null;
            if (deliveryMethod == null) {
                showAlert("Missing Data", "Please select delivery or pickup.");
                return;
            }

            // Validate delivery date/time if delivery
            LocalDateTime deliveryTime = null;
            if ("Delivery".equals(deliveryMethod)) {
                if (deliveryDatePicker.getValue() == null) {
                    showAlert("Missing Data", "Please select a delivery date.");
                    return;
                }
                if (deliveryDatePicker.getValue().isBefore(java.time.LocalDate.now())) {
                    showAlert("Invalid Date", "Delivery date cannot be in the past.");
                    return;
                }
                deliveryTime = deliveryDatePicker.getValue().atTime(12, 0); // Default to noon
            }

            // Recipient phone logic
            String recipientPhone;
            if ("Delivery".equals(deliveryMethod) && differentRecipientCheck.isSelected()) {
                recipientPhone = recipientPhoneField.getText();
                if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                    showAlert("Missing Data", "Please enter the recipient's phone number.");
                    return;
                }
            } else {
                recipientPhone = customer.getPhone();
            }

            // Build order items from session cart
            List<OrderItem> orderItems = new ArrayList<>();
            for (Product p : SessionManager.getInstance().getCart()) {
                OrderItem item = new OrderItem();
                item.setProduct(p);
                item.setQuantity(1); // Adjust if you track quantity elsewhere
                orderItems.add(item);
            }

            if (orderItems.isEmpty()) {
                showAlert("Empty Cart", "Your cart is empty.");
                return;
            }

            // Create order
            Order order = new Order();
            order.setCustomer(customer);
            order.setStoreLocation(storeLocation);
            boolean isDelivery = deliveryRadio.isSelected();
            order.setDelivery(isDelivery);
            order.setOrderDate(LocalDateTime.now());
            order.setDeliveryDate(deliveryTime);
            order.setRecipientPhone(recipientPhone);
            order.setItems(orderItems);

            // Link items to the order (bidirectional)
            for (OrderItem item : orderItems) {
                item.setOrder(order);
            }

            // Disable button to prevent double submission
            placeOrderButton.setDisable(true);
            placeOrderButton.setText("Processing...");

            // Send to server
            Message message = new Message("place_order", order, null);
            SimpleClient.getClient().sendToServer(message);

        } catch (Exception ex) {
            showAlert("Error", "Failed to place order: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    @Subscribe
    public void onServerResponse(Message msg) {
        if (msg.getMessage().equals("order_placed_successfully")) {
            Platform.runLater(() -> {
                showAlert("Success", "Your order has been placed.");
                SessionManager.getInstance().clearCart();
                goToCatalog();
            });
        } else if (msg.getMessage().startsWith("order_error")) {
            Platform.runLater(() -> showAlert("Error", msg.getMessage()));
        }
    }

    private void goBackToCart() {
        try {
            App.setRoot("cartView");
        } catch (Exception e) {
            showAlert("Error", "Failed to go back to cart.");
        }
    }

    private void goToCatalog() {
        try {
            App.setRoot("primary");
        } catch (Exception e) {
            showAlert("Error", "Failed to return to catalog.");
        }
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
