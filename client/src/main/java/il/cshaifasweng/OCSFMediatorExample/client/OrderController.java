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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class OrderController implements Initializable {

    @FXML
    private ChoiceBox<String> storeLocationChoice;

    @FXML
    private RadioButton deliveryRadio;

    @FXML
    private VBox deliveryDetailsSection;  // Contains only delivery address, different recipient, recipient phone

    @FXML
    private RadioButton pickupRadio;

    @FXML
    private DatePicker deliveryDatePicker;

    @FXML
    private Spinner<Integer> deliveryHourSpinner;

    @FXML
    private TextField recipientPhoneField;

    @FXML
    private CheckBox differentRecipientCheck;

    @FXML
    private TextField deliveryAddressField;

    @FXML
    private TextArea orderNoteField;

    @FXML
    private ChoiceBox<String> paymentMethodChoice;

    @FXML
    private TextField newCardField;

    @FXML
    private VBox newCardBox;

    @FXML
    private Button placeOrderButton;

    @FXML
    private Button cancelButton;

    private ToggleGroup deliveryGroup;

    @FXML
    private VBox storeLocationSection;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        // Initialize store locations
        storeLocationChoice.getItems().addAll("Downtown", "Mall Branch", "Westside Store");

        deliveryGroup = new ToggleGroup();
        deliveryRadio.setToggleGroup(deliveryGroup);
        pickupRadio.setToggleGroup(deliveryGroup);

        // Spinner for hours 0-23, default noon (12)
        deliveryHourSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 12));

        // Disable recipient phone unless checkbox is checked
        recipientPhoneField.setDisable(true);
        differentRecipientCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            recipientPhoneField.setDisable(!newVal);
            if (!newVal) {
                recipientPhoneField.clear();
            }
        });

        // Initially hide deliveryDetailsSection and show storeLocationSection (default pickup)
        deliveryDetailsSection.setVisible(false);
        deliveryDetailsSection.setManaged(false);

        storeLocationSection.setVisible(true);
        storeLocationSection.setManaged(true);

        // Listen to delivery method changes
        deliveryGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == deliveryRadio) {
                // Delivery selected
                deliveryDetailsSection.setVisible(true);
                deliveryDetailsSection.setManaged(true);

                storeLocationSection.setVisible(false);
                storeLocationSection.setManaged(false);

            } else if (newToggle == pickupRadio) {
                // Pickup selected
                deliveryDetailsSection.setVisible(false);
                deliveryDetailsSection.setManaged(false);

                storeLocationSection.setVisible(true);
                storeLocationSection.setManaged(true);

                // Clear delivery-specific fields
                deliveryDatePicker.setValue(null);
                differentRecipientCheck.setSelected(false);
                recipientPhoneField.clear();
                deliveryAddressField.clear();
            }
        });

        // Payment method options
        paymentMethodChoice.getItems().addAll("Saved Card", "New Card", "Pay Upon Delivery");
        paymentMethodChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("New Card".equals(newVal)) {
                newCardBox.setVisible(true);
                newCardBox.setManaged(true);
            } else {
                newCardBox.setVisible(false);
                newCardBox.setManaged(false);
            }
        });

        newCardBox.setVisible(false);
        newCardBox.setManaged(false);

        placeOrderButton.setOnAction(e -> placeOrder());
        cancelButton.setOnAction(e -> goBackToCart());
    }

    private void placeOrder() {
        try {
            if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                showAlert("Error", "Only customers can place orders.");
                return;
            }
            Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();

            String deliveryMethod = deliveryRadio.isSelected() ? "Delivery" :
                    pickupRadio.isSelected() ? "Pickup" : null;
            if (deliveryMethod == null) {
                showAlert("Missing Data", "Please select delivery or pickup.");
                return;
            }

            String storeLocation = null;
            if ("Pickup".equals(deliveryMethod)) {
                storeLocation = storeLocationChoice.getValue();
                if (storeLocation == null) {
                    showAlert("Missing Data", "Please select a store location.");
                    return;
                }
            }

            ZoneId israelZone = ZoneId.of("Asia/Jerusalem");

            LocalDate date = deliveryDatePicker.getValue();
            Integer hour = deliveryHourSpinner.getValue();
            if (date == null || date.isBefore(LocalDate.now())) {
                showAlert("Invalid Date", "Please select a valid delivery date.");
                return;
            }
            LocalDateTime deliveryTime = date.atTime(hour + 3, 0);
            //ZonedDateTime israelDateTime = deliveryTime.atZone(israelZone);
            //deliveryTime = israelDateTime.toLocalDateTime();



            String recipientPhone = null;
            if ("Delivery".equals(deliveryMethod)) {
                if (differentRecipientCheck.isSelected()) {
                    recipientPhone = recipientPhoneField.getText();
                    if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                        showAlert("Missing Data", "Please enter the recipient's phone number.");
                        return;
                    }
                } else {
                    recipientPhone = customer.getPhone();
                }
            }

            String deliveryAddress = null;
            if ("Delivery".equals(deliveryMethod)) {
                deliveryAddress = deliveryAddressField.getText();
                if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
                    showAlert("Missing Data", "Please enter the delivery address.");
                    return;
                }
            }

            String orderNote = orderNoteField.getText();

            String paymentMethod = paymentMethodChoice.getValue();
            if (paymentMethod == null) {
                showAlert("Missing Data", "Please select a payment method.");
                return;
            }
            String paymentDetails = null;
            if ("Saved Card".equals(paymentMethod)) {
                paymentDetails = customer.getCreditCard().getCardNumber();
            } else if ("New Card".equals(paymentMethod)) {
                paymentDetails = newCardField.getText();
                if (paymentDetails == null || paymentDetails.trim().isEmpty()) {
                    showAlert("Missing Data", "Please enter the new card number.");
                    return;
                }
            } else if ("Pay Upon Delivery".equals(paymentMethod)) {
                paymentDetails = "Cash on Delivery";
            }

            // Verify client-side SessionManager cart is not empty.
// (We won't set order items here. Server constructs items from DB cart.)
            if (SessionManager.getInstance().getCart() == null || SessionManager.getInstance().getCart().isEmpty()) {
                showAlert("Empty Cart", "Your cart is empty.");
                return;
            }

            Order order = new Order();
            order.setCustomer(customer);
            order.setDelivery("Delivery".equals(deliveryMethod));
            order.setStoreLocation(storeLocation);
            order.setOrderDate(LocalDateTime.now());
            order.setDeliveryDateTime(deliveryTime);
            order.setRecipientPhone(recipientPhone);
            order.setDeliveryAddress(deliveryAddress);
            order.setNote(orderNote);
            order.setPaymentMethod(paymentMethod);
            order.setPaymentDetails(paymentDetails);


            placeOrderButton.setDisable(true);
            placeOrderButton.setText("Processing...");

            // Send order to server
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
            EventBus.getDefault().unregister(this);
            App.setRoot("cartView");
        } catch (Exception e) {
            showAlert("Error", "Failed to go back to cart.");
        }
    }

    private void goToCatalog() {
        try {
            EventBus.getDefault().unregister(this);
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
