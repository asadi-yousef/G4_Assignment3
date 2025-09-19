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
    private Label deliveryPriceLabel;
    @FXML
    private Label orderTotalWithDelivery;
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

    private Order pendingOrder = null;
    private double budgetAmountToUse = 0.0;
    private boolean usingBudget = false;
    private double currentOrderTotal;
    private double orderTotal;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        ///
        storeLocationChoice.getItems().setAll("Loading branches...");
        storeLocationChoice.setValue("Loading branches...");
        storeLocationChoice.setDisable(true);
        ///
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
        this.isNetworkCustomer = (current != null) && current.isNetworkAccount();

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
      //  storeLocationChoice.setDisable(!isNetworkCustomer);
      //  storeLocationChoice.setValue(isNetworkCustomer ? "Select a branch" : "Your branch");

        // Network account: enable, wait for server to populate
        if (isNetworkCustomer) {
            storeLocationChoice.setDisable(false);

            // Do not set value yet — will set to first branch when server responds
        } else {
            // Branch account: lock to assigned branch
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

        currentOrderTotal = SessionManager.getInstance().getOrderTotal();
        orderTotalLabel.setText("₪" + String.format("%.2f", currentOrderTotal));

        // React to delivery/pickup changes
        deliveryGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == deliveryRadio) {
                currentOrderTotal = SessionManager.getInstance().getOrderTotal();
                deliveryDetailsSection.setVisible(true);
                deliveryDetailsSection.setManaged(true);
                storeLocationSection.setVisible(false);
                storeLocationSection.setManaged(false);
                deliveryPriceLabel.setVisible(true);
                deliveryPriceLabel.setManaged(true);
                orderTotalWithDelivery.setVisible(true);
                orderTotalWithDelivery.setManaged(true);
                double deliveryPrice = 20;
                orderTotal = currentOrderTotal;
                orderTotal += deliveryPrice;
                currentOrderTotal = orderTotal;
                deliveryPriceLabel.setText("Delivery: 20₪");
                orderTotalWithDelivery.setText("Total with delivery: ₪" + String.format("%.2f", orderTotal));

            } else if (newToggle == pickupRadio) {
                currentOrderTotal = SessionManager.getInstance().getOrderTotal();
                deliveryDetailsSection.setVisible(false);
                deliveryDetailsSection.setManaged(false);
                storeLocationSection.setVisible(true);
                storeLocationSection.setManaged(true);

                // If the current customer has an assigned branch -> keep it locked; otherwise allow selection
                if (SessionManager.getInstance().getCurrentUser() instanceof Customer) {
                    Customer c = (Customer) SessionManager.getInstance().getCurrentUser();
                    storeLocationChoice.setDisable(getAssignedBranchName(c) != null && !getAssignedBranchName(c).isBlank());
                } else {
                    storeLocationChoice.setDisable(false);
                }
               // storeLocationChoice.setDisable((SessionManager.getInstance().getCurrentUser() instanceof Customer)
               //         ? !((Customer) SessionManager.getInstance().getCurrentUser()).isNetworkAccount()
                //        : true);

                // Clear delivery-specific fields
                deliveryDatePicker.setValue(null);
                differentRecipientCheck.setSelected(false);
                recipientPhoneField.clear();
                deliveryAddressField.clear();
                deliveryPriceLabel.setVisible(false);
                deliveryPriceLabel.setManaged(false);
                orderTotalWithDelivery.setVisible(false);
                orderTotalWithDelivery.setManaged(false);
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



        placeOrderButton.setOnAction(e -> placeOrder());
        cancelButton.setOnAction(e -> goBackToCart());
        deliveryPriceLabel.setVisible(false);
        deliveryPriceLabel.setManaged(false);
        orderTotalWithDelivery.setVisible(false);
        orderTotalWithDelivery.setManaged(false);
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
            double deliveryPrice = 20;
            orderTotal = SessionManager.getInstance().getOrderTotal();

            deliveryPriceLabel.setVisible(false);
            deliveryPriceLabel.setManaged(false);
            orderTotalWithDelivery.setVisible(false);
            orderTotalWithDelivery.setManaged(false);
            if ("Pickup".equals(deliveryMethod)) {
                orderTotal = SessionManager.getInstance().getOrderTotal();
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
                orderTotal = SessionManager.getInstance().getOrderTotal();
                deliveryPriceLabel.setVisible(true);
                deliveryPriceLabel.setManaged(true);
                orderTotalWithDelivery.setVisible(true);
                orderTotalWithDelivery.setManaged(true);
                orderTotal += deliveryPrice;
                deliveryPriceLabel.setText("Delivery: 20₪");
                orderTotalWithDelivery.setText("Total with delivery: ₪" + String.format("%.2f", orderTotal));
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
                    cardExpiryDate = customer.getCreditCard().getExpirationMonth() + "/" + customer.getCreditCard().getExpirationYear();
                    cardCVV = customer.getCreditCard().getCvv();
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
                    // switch to second payment method for the remainder
                    paymentMethod = secondPayment;
                    Budget budget = customer.getBudget();
                    if (budget == null || budget.getBalance() <= 0) {
                        showAlert("Insufficient Funds", "Your budget is empty. Please select another payment method.");
                        return;
                    }

                    // determine how much we will take from the budget (delta)
                    if (budget.getBalance() >= orderTotal) {
                        // budget will cover whole order
                        budgetAmountToUse = orderTotal;
                        orderTotal = 0;
                        isPaid = true;
                        usingBudget = true;

                    } else {
                        // budget only covers part -> use it, remainder must be paid by second payment
                        double fromBudget = budget.getBalance();
                        double remaining = orderTotal - fromBudget;
                        budgetAmountToUse = fromBudget;
                        orderTotal = remaining;

                        if (secondPayment == null) {
                            showAlert("Missing Data", "Your budget isn’t enough. Please select a second payment method.");
                            return;
                        }

                        // Validate/process second payment details (same as before)
                        if ("New Card".equals(secondPayment)) {
                            paymentDetails = newCardField.getText();
                            if (paymentDetails == null || paymentDetails.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the new card number.");
                                return;
                            }
                            cardExpiryDate = newCardExpiryDate.getText();
                            if (cardExpiryDate == null || cardExpiryDate.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the card expiry date.");
                                return;
                            }
                            cardCVV = newCardCVV.getText();
                            if (cardCVV == null || cardCVV.trim().isEmpty()) {
                                showAlert("Missing Data", "Please enter the card cvv.");
                                return;
                            }
                        } else if ("Saved Card".equals(secondPayment)) {
                            if (customer.getCreditCard() == null || customer.getCreditCard().getCardNumber() == null) {
                                showAlert("Missing Data", "No saved card on file.");
                                return;
                            }
                            paymentDetails = customer.getCreditCard().getCardNumber();
                            cardExpiryDate = customer.getCreditCard().getExpirationMonth() + "/" + customer.getCreditCard().getExpirationYear();
                            cardCVV = customer.getCreditCard().getCvv();
                        }
                        isPaid = true;
                        usingBudget = true;
                    }
                }
            }

            Order order = new Order();
            order.setCustomer(customer);
            order.setDelivery("Delivery".equals(deliveryMethod));
            order.setStoreLocation(storeLocation);
            order.setOrderDate(LocalDateTime.now());
            if("Delivery".equals(deliveryMethod)) {
                order.setDeliveryDateTime(deliveryTime);
                order.setPickupDateTime(null);
            } else {
                order.setPickupDateTime(deliveryTime);
                order.setDeliveryDateTime(null);
            }
            order.setRecipientPhone(recipientPhone);       // null for Pickup
            order.setDeliveryAddress(deliveryAddress);     // null for Pickup
            order.setNote(orderNoteField.getText());
            order.setPaymentMethod(paymentMethod);
            order.setPaymentDetails(paymentDetails);
            order.setCardExpiryDate(cardExpiryDate);
            order.setCardCVV(cardCVV);


//            if (usingBudget) {
//                // prepare minimal payload: Customer id + Budget.delta
//                Customer payload = new Customer();
//                payload.setId(customer.getId());
//                Budget b = new Budget();
//                b.setBalance(Math.min(customer.getBudget().getBalance(), order.getTotalPrice())); // delta
//                payload.setBudget(b);
//
//                // store order to send later, disable UI while waiting
//                pendingOrder = order;
//                placeOrderButton.setDisable(true);
//                placeOrderButton.setText("Processing...");
//
//                try {
//                    SimpleClient.getClient().sendToServer(new Message("update_budget_subtract", payload, null));
//                } catch (IOException e) {
//                    pendingOrder = null;
//                    placeOrderButton.setDisable(false);
//                    placeOrderButton.setText("Place order");
//                    showAlert("Error", "Failed to send budget update: " + e.getMessage());
//                }
//                return; // wait for server response to continue
//            }
//
//            placeOrderButton.setDisable(true);
//            placeOrderButton.setText("Processing...");
//            SimpleClient.getClient().sendToServer(new Message("place_order", order, null));

            try {
                if (usingBudget) {
                    // mark order to use budget; no need to send separate budget message
                    order.setPaymentMethod("BUDGET");
                }

                // disable UI while waiting for server response
                placeOrderButton.setDisable(true);
                placeOrderButton.setText("Processing...");

                // send the order to the server; server will handle budget subtraction
                SimpleClient.getClient().sendToServer(new Message("place_order", order, null));

            } catch (IOException ex) {
                placeOrderButton.setDisable(false);
                placeOrderButton.setText("Place order");
                showAlert("Error", "Failed to place order: " + ex.getMessage());
                ex.printStackTrace();
            }

        } catch (Exception ex) {
            showAlert("Error", "Failed to place order: " + ex.getMessage());
            ex.printStackTrace();
        }
    }


    @Subscribe
    public void onServerResponse(Message msg) {
        System.out.println("[OrderController] Received message: " + msg.getMessage());
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


        if (msg.getMessage().equals("budget_updated")) {
            Platform.runLater(() -> {
                Object obj = msg.getObject();
                if (obj instanceof Customer dbCustomer) {
                    // update global session user
                    try {
                        SessionManager.getInstance().setCurrentUser(dbCustomer);
                    } catch (Exception ignored) { }

                    // update local UI components that show budget (if present)
                    try {
                        double newBal = (dbCustomer.getBudget() != null) ? dbCustomer.getBudget().getBalance() : 0.0;
                        // OrderController has budgetBalanceLabel
                        if (budgetBalanceLabel != null) {
                            budgetBalanceLabel.setText("Budget Balance: ₪" + String.format("%.2f", newBal));
                        }
                        // AddBudgetController has budgetBalanceLabel (it will also receive this message)
                        // It will update itself in its handler (you already have identical code there)

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
            return;
        }


        if (msg.getMessage().equals("budget_insufficient")) {
            Platform.runLater(() -> {
                showAlert("Insufficient Budget", "Not enough budget to cover the requested amount.");
                // re-enable UI
                placeOrderButton.setDisable(false);
                placeOrderButton.setText("Place order");
                pendingOrder = null;
            });
            return;
        }

        if (msg.getMessage().equals("budget_update_failed")) {
            Platform.runLater(() -> {
                showAlert("Error", "Failed to update budget on server.");
                placeOrderButton.setDisable(false);
                placeOrderButton.setText("Place order");
                pendingOrder = null;
            });
            return;
        }


        if (msg.getMessage().equals("Branches")) {
            Platform.runLater(() -> {
                // update local branches list
                if (branches == null) branches = new ArrayList<>();
                branches.clear();

                if (msg.getObject() instanceof List<?>) {
                    for (Object o : (List<?>) msg.getObject()) {
                        if (o instanceof Branch) {
                            branches.add((Branch) o);
                        } else {
                            System.out.println("[OrderController] Branches list contains non-Branch item: " + o);
                        }
                    }
                }

                // build names list (plain style, no streams)
                List<String> names = new ArrayList<>();
                for (Branch b : branches) {
                    if (b != null && b.getName() != null) names.add(b.getName());
                }
                System.out.println("[OrderController] Parsed branch names: " + names);

                // If no branch names, show a clear placeholder and disable
                if (names.isEmpty()) {
                    storeLocationChoice.getItems().clear();
                    storeLocationChoice.getItems().add("No branches available");
                    storeLocationChoice.setValue("No branches available");
                    storeLocationChoice.setDisable(true);
                    System.out.println("[OrderController] No branches received -> disabled ChoiceBox");
                    return;
                }

                // populate ChoiceBox with received names
                storeLocationChoice.getItems().setAll(names);

                // decide based on controller flag and current session user
                Customer current = (SessionManager.getInstance().getCurrentUser() instanceof Customer)
                        ? (Customer) SessionManager.getInstance().getCurrentUser() : null;

                System.out.println("[OrderController] isNetworkCustomer=" + isNetworkCustomer + ", current=" + current);

                if (isNetworkCustomer) {
                    // network customer: enable selection and set default
                    storeLocationChoice.setDisable(false);
                    if (!names.isEmpty()) {
                        storeLocationChoice.setValue(names.get(0));
                    }
                } else if (current != null) {
                    // branch-account: lock to assigned branch (if present) or show 'No branch assigned'
                    String assigned = getAssignedBranchName(current);
                    if (assigned != null && !assigned.isBlank()) {
                        storeLocationChoice.getItems().setAll(assigned);
                        storeLocationChoice.setValue(assigned);
                    } else {
                        storeLocationChoice.getItems().setAll("No branch assigned");
                        storeLocationChoice.setValue("No branch assigned");
                    }
                    storeLocationChoice.setDisable(true);
                } else {
                    // no current user: keep items but disabled (user will be set later)
                    storeLocationChoice.setDisable(true);
                    if (!names.isEmpty()) storeLocationChoice.setValue(names.get(0));
                }

                System.out.println("[OrderController] ChoiceBox items after handling: " + storeLocationChoice.getItems());
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