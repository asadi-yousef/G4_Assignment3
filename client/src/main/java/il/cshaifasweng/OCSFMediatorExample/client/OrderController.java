package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
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
    private VBox deliveryDetailsSection;

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
    private TextField newCardExpiryDate;
    @FXML
    private TextField newCardCVV;

    @FXML
    private VBox newCardBox;

    @FXML
    private Button placeOrderButton;

    @FXML
    private Button cancelButton;

    private ToggleGroup deliveryGroup;

    @FXML
    private VBox storeLocationSection;

    private List<Branch> branches = new ArrayList<>();

    private Customer currentCustomer;
    private boolean isNetworkCustomer;
    @FXML
    private Label orderTotalLabel;
    @FXML
    private HBox budgetBox;
    @FXML
    private VBox insufficientBudgetBox;
    @FXML
    private Label budgetBalanceLabel;
    @FXML
    private CheckBox useBudget;
    @FXML
    private ChoiceBox<String> secondPaymentMethod;



    private double currentOrderTotal;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        // Request branches from server
        try {
            SimpleClient.getClient().sendToServer(new Message("request_branches", null, null));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Resolve current customer + network-account capability
        Customer current = (SessionManager.getInstance().getCurrentUser() instanceof Customer)
                ? (Customer) SessionManager.getInstance().getCurrentUser()
                : null;
        boolean isNetworkCustomer = (current != null) && current.isNetworkAccount();

        // Toggle group
        deliveryGroup = new ToggleGroup();
        deliveryRadio.setToggleGroup(deliveryGroup);
        pickupRadio.setToggleGroup(deliveryGroup);

        // Hours spinner 0..23 (default 12)
        deliveryHourSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 12)
        );

        // 🔹 Disable past dates in DatePicker
        deliveryDatePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date.isBefore(LocalDate.now())) {
                    setDisable(true);
                    setStyle("-fx-background-color: #EEEEEE;");
                }
            }
        });

        // 🔹 Adjust available hours when a date is picked
        deliveryDatePicker.valueProperty().addListener((obs, oldDate, newDate) -> {
            if (newDate != null && newDate.equals(LocalDate.now())) {
                int currentHour = LocalDateTime.now().getHour();
                deliveryHourSpinner.setValueFactory(
                        new SpinnerValueFactory.IntegerSpinnerValueFactory(currentHour, 23, Math.max(currentHour, 12))
                );
            } else {
                deliveryHourSpinner.setValueFactory(
                        new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 12)
                );
            }
        });

        // Recipient phone enabled only if different recipient is checked
        recipientPhoneField.setDisable(true);
        differentRecipientCheck.selectedProperty().addListener((obs, oldVal, newVal) -> {
            recipientPhoneField.setDisable(!newVal);
            if (!newVal) {
                recipientPhoneField.clear();
            }
        });

        // Initial sections: default Pickup (as in your original)
        deliveryDetailsSection.setVisible(false);
        deliveryDetailsSection.setManaged(false);
        storeLocationSection.setVisible(true);
        storeLocationSection.setManaged(true);

        // Store branch choice setup (no hard-coded items)
        storeLocationChoice.getItems().clear();
        storeLocationChoice.setDisable(!isNetworkCustomer);
        storeLocationChoice.setValue(isNetworkCustomer ? "Select a branch" : "Your branch");

        // If not a network account, lock to assigned branch immediately (if available)
        if (!isNetworkCustomer) {
            String assigned = getAssignedBranchName(current);
            if (assigned != null && !assigned.isBlank()) {
                storeLocationChoice.getItems().setAll(assigned);
                storeLocationChoice.setValue(assigned);
            } else {
                storeLocationChoice.getItems().clear();
                storeLocationChoice.setValue("No branch assigned");
            }
        }

        // React to delivery/pickup changes
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

                // Only network customers may pick a branch
                storeLocationChoice.setDisable((SessionManager.getInstance().getCurrentUser() instanceof Customer)
                        ? !((Customer) SessionManager.getInstance().getCurrentUser()).isNetworkAccount()
                        : true);

                // Clear delivery-specific fields
                deliveryDatePicker.setValue(null);
                differentRecipientCheck.setSelected(false);
                recipientPhoneField.clear();
                deliveryAddressField.clear();
            }
        });

        // Payment method setup
        paymentMethodChoice.getItems().setAll("Saved Card", "New Card", "My Budget");
        secondPaymentMethod.getItems().setAll("Saved Card", "New Card");
        // Initially hide budget box and new card box
        budgetBox.setVisible(false);
        budgetBox.setManaged(false);
        newCardBox.setVisible(false);
        newCardBox.setManaged(false);


        currentOrderTotal = SessionManager.getInstance().getOrderTotal();
        // Show/hidudget or new card based on selection
        paymentMethodChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            newCardBox.setVisible("New Card".equals(newVal));
            newCardBox.setManaged("New Card".equals(newVal));

            if ("My Budget".equals(newVal)) {
                budgetBox.setVisible(true);
                budgetBox.setManaged(true);

                if (current != null && current.getBudget() != null) {
                    double balance = current.getBudget().getBalance();
                    budgetBalanceLabel.setText("Budget Balance: ₪" + String.format("%.2f", balance));

                    // 🔹 SHOW extra dropdown *immediately* if not enough
                    if (balance < currentOrderTotal) {
                        insufficientBudgetBox.setVisible(true);
                        insufficientBudgetBox.setManaged(true);
                    } else {
                        insufficientBudgetBox.setVisible(false);
                        insufficientBudgetBox.setManaged(false);
                    }
                } else {
                    budgetBalanceLabel.setText("Budget Balance: ₪0.00");
                    insufficientBudgetBox.setVisible(true);
                    insufficientBudgetBox.setManaged(true);
                }

                newCardBox.setVisible(false);
                newCardBox.setManaged(false);
            } else {
                budgetBox.setVisible(false);
                budgetBox.setManaged(false);
                insufficientBudgetBox.setVisible(false);
                insufficientBudgetBox.setManaged(false);
            }
        });

        secondPaymentMethod.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if ("New Card".equals(newVal)) {
                newCardBox.setVisible(true);
                newCardBox.setManaged(true);
            } else {
                newCardBox.setVisible(false);
                newCardBox.setManaged(false);
            }
        });

        orderTotalLabel.setText("₪" + String.format("%.2f", currentOrderTotal));

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
            boolean isNetworkCustomer = customer.isNetworkAccount();

            String deliveryMethod = deliveryRadio.isSelected() ? "Delivery"
                    : (pickupRadio.isSelected() ? "Pickup" : null);
            if (deliveryMethod == null) {
                showAlert("Missing Data", "Please select delivery or pickup.");
                return;
            }

            String storeLocation = null;
            LocalDateTime deliveryTime = null;
            String recipientPhone = null;
            String deliveryAddress = null;

            if ("Pickup".equals(deliveryMethod)) {
                if (isNetworkCustomer) {
                    storeLocation = storeLocationChoice.getValue();
                    if (storeLocation == null || storeLocation.isBlank()) {
                        showAlert("Missing Data", "Please select a store branch.");
                        return;
                    }
                } else {
                    storeLocation = getAssignedBranchName(customer);
                    if (storeLocation == null || storeLocation.isBlank()) {
                        showAlert("Missing Data", "Your account is not linked to a branch.");
                        return;
                    }
                }
            } else {

                deliveryAddress = deliveryAddressField.getText();
                if (deliveryAddress == null || deliveryAddress.trim().isEmpty()) {
                    showAlert("Missing Data", "Please enter the delivery address.");
                    return;
                }

                recipientPhone = differentRecipientCheck.isSelected()
                        ? recipientPhoneField.getText()
                        : customer.getPhone();
                if (recipientPhone == null || recipientPhone.trim().isEmpty()) {
                    showAlert("Missing Data", "Please enter the recipient's phone number.");
                    return;
                }
            }
            // Delivery date time
            LocalDate date = deliveryDatePicker.getValue();
            Integer hour = deliveryHourSpinner.getValue();
            if (date == null || date.isBefore(LocalDate.now())) {
                showAlert("Invalid Date", "Please select a valid Delivery/Pickup date.");
                return;
            }
            deliveryTime = date.atTime(hour != null ? hour : 12, 0);

            // 🔹 Final validation: block past times
            if (deliveryTime.isBefore(LocalDateTime.now())) {
                showAlert("Invalid Time", "Please select a future time.");
                return;
            }

            double orderTotal = SessionManager.getInstance().getOrderTotal();
            String paymentMethod = paymentMethodChoice.getValue();
            String secondPayment = secondPaymentMethod.getValue();
            if (paymentMethod == null) {
                showAlert("Missing Data", "Please select a payment method.");
                return;
            }
            String paymentDetails = null;
            String cardExpiryDate = null;
            String cardCVV = null;

            boolean isPaid = false;
            while(!isPaid) {
                if ("Saved Card".equals(paymentMethod)) {
                    if (customer.getCreditCard() == null || customer.getCreditCard().getCardNumber() == null) {
                        showAlert("Missing Data", "No saved card on file.");
                        return;
                    }
                    isPaid = true;
                    paymentDetails = customer.getCreditCard().getCardNumber();
                } else if ("New Card".equals(paymentMethod)) {
                    paymentDetails = newCardField.getText();
                    if (paymentDetails == null || paymentDetails.trim().isEmpty()) {
                        showAlert("Missing Data", "Please enter the new card number.");
                        return;
                    }
                    cardExpiryDate = newCardExpiryDate.getText();
                    if(cardExpiryDate == null || cardExpiryDate.trim().isEmpty()) {
                        showAlert("Missing Data", "Please enter the card expiry date.");
                        return;
                    }
                    cardCVV = newCardCVV.getText();
                    if(cardCVV == null || cardCVV.trim().isEmpty()) {
                        showAlert("Missing Data", "Please enter the card cvv.");
                        return;
                    }
                    isPaid = true;
                }
                else if ("My Budget".equals(paymentMethod)) {
                    Budget budget = customer.getBudget();
                    if (budget == null || budget.getBalance() <= 0) {
                        showAlert("Insufficient Funds", "Your budget is empty. Please select another payment method.");
                        return;
                    }

                    if (budget.getBalance() >= orderTotal) {
                        // Budget covers full order
                        budget.subtractFunds(orderTotal);
                        orderTotal = 0;
                        isPaid = true;

                        List<Object> payload = new ArrayList<>();
                        payload.add(customer);
                        SimpleClient.getClient().sendToServer(new Message("update_budget", null, payload));

                    } else {
                        double fromBudget = budget.getBalance();
                        double remaining = orderTotal - fromBudget;
                        budget.subtractFunds(fromBudget);

                        List<Object> payload = new ArrayList<>();
                        payload.add(customer);
                        SimpleClient.getClient().sendToServer(new Message("update_budget", null, payload));

                        if (secondPayment == null) {
                            showAlert("Missing Data", "Your budget isn’t enough. Please select a second payment method.");
                            return;
                        }

                        // Process second payment
                        if ("New Card".equals(secondPayment)) {
                            paymentDetails = newCardField.getText();
                            if (paymentDetails == null || paymentDetails.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the new card number.");
                                return;
                            }
                            cardExpiryDate = newCardExpiryDate.getText();
                            if(cardExpiryDate == null || cardExpiryDate.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the card expiry date.");
                                return;
                            }
                            cardCVV = newCardCVV.getText();
                            if(cardCVV == null || cardCVV.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the card cvv.");
                                return;
                            }
                        } else if ("Saved Card".equals(secondPayment)) {
                            if (customer.getCreditCard() == null || customer.getCreditCard().getCardNumber() == null) {
                                showAlert("Missing Data", "No saved card on file.");
                                return;
                            }
                            paymentDetails = customer.getCreditCard().getCardNumber();
                        }

                        orderTotal = remaining;
                        isPaid = true;
                    }
                }


            }

            Order order = new Order();
            order.setCustomer(customer);
            order.setDelivery("Delivery".equals(deliveryMethod));
            order.setStoreLocation(storeLocation);
            order.setOrderDate(LocalDateTime.now());
            order.setDeliveryDateTime(deliveryTime);
            order.setRecipientPhone(recipientPhone);       // null for Pickup
            order.setDeliveryAddress(deliveryAddress);     // null for Pickup
            order.setNote(orderNoteField.getText());
            order.setPaymentMethod(paymentMethod);
            order.setPaymentDetails(paymentDetails);
            order.setCardExpiryDate(cardExpiryDate);
            order.setCardCVV(cardCVV);

            placeOrderButton.setDisable(true);
            placeOrderButton.setText("Processing...");
            SimpleClient.getClient().sendToServer(new Message("place_order", order, null));

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
            return;
        }

        if (msg.getMessage().equals("order_error")) {
            String details = (msg.getObject() instanceof String) ? (String) msg.getObject() : "Order failed.";
            Platform.runLater(() -> showAlert("Error", details));
            return;
        }

        if (msg.getMessage().equals("Branches")) {
            Platform.runLater(() -> {
                // Ensure local list exists
                if (branches == null) branches = new ArrayList<>();
                branches.clear();

                // Safely copy Branch objects from payload
                if (msg.getObject() instanceof List<?>) {
                    for (Object o : (List<?>) msg.getObject()) {
                        if (o instanceof Branch) branches.add((Branch) o);
                    }
                }

                // Determine permission again (UI may have changed user state)
                Customer current = (SessionManager.getInstance().getCurrentUser() instanceof Customer)
                        ? (Customer) SessionManager.getInstance().getCurrentUser()
                        : null;
                boolean isNetworkCustomer = (current != null) && current.isNetworkAccount();

                // Populate names
                List<String> names = new ArrayList<>();
                for (Branch b : branches) {
                    if (b != null && b.getName() != null) names.add(b.getName());
                }

                if (isNetworkCustomer) {
                    // Network account: user may choose any branch
                    storeLocationChoice.getItems().setAll(names);

                    String currentSelection = storeLocationChoice.getValue();
                    if (currentSelection == null || !storeLocationChoice.getItems().contains(currentSelection)) {
                        String assigned = getAssignedBranchName(current);
                        if (assigned != null && storeLocationChoice.getItems().contains(assigned)) {
                            storeLocationChoice.setValue(assigned);
                        } else if (!storeLocationChoice.getItems().isEmpty()) {
                            storeLocationChoice.setValue(storeLocationChoice.getItems().get(0));
                        }
                    }
                    storeLocationChoice.setDisable(false);
                    storeLocationChoice.setValue("Select a branch");
                } else {
                    // Non-network account: lock to assigned branch
                    String assigned = getAssignedBranchName(current);
                    if (assigned != null && !assigned.isBlank()) {
                        storeLocationChoice.getItems().setAll(assigned);
                        storeLocationChoice.setValue(assigned);
                    } else {
                        storeLocationChoice.getItems().clear();
                        storeLocationChoice.setValue("No branch assigned");
                    }
                    storeLocationChoice.setDisable(true);
                }
            });
        }
    }

    private String getAssignedBranchName(Customer current) {
        try {
            return (current != null && current.getBranch() != null)
                    ? current.getBranch().getName()
                    : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unused")
    private Long getAssignedBranchId(Customer current) {
        try {
            return (current != null && current.getBranch() != null)
                    ? current.getBranch().getId()
                    : null;
        } catch (Exception e) {
            return null;
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