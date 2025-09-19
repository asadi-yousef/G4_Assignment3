package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Budget;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;

public class AddBudgetController {

    @FXML private Label budgetBalanceLabel;
    @FXML private TextField amountField;
    @FXML private ChoiceBox<String> paymentMethodChoice;
    @FXML private VBox newCardBox;
    @FXML private TextField cardNumberField;
    @FXML private TextField expiryField;
    @FXML private TextField cvvField;
    @FXML private Button addButton;
    @FXML private Button backToCatalogButton;

    private Customer currentCustomer;

    @FXML
    public void initialize() {
        // register for server messages
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        User user = SessionManager.getInstance().getCurrentUser();
        if (user instanceof Customer customer) {
            double bal = (customer.getBudget() != null) ? customer.getBudget().getBalance() : 0.0;
            budgetBalanceLabel.setText("Current Budget: ₪" + String.format("%.2f", bal));
            System.out.println("Budget loaded in initialize(): " + bal);
        }
        paymentMethodChoice.getItems().setAll("Saved Card", "New Card");
        paymentMethodChoice.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            boolean showNewCard = "New Card".equals(newVal);
            newCardBox.setVisible(showNewCard);
            newCardBox.setManaged(showNewCard);
        });

        // hide new card pane initially
        newCardBox.setVisible(false);
        newCardBox.setManaged(false);

        // load current customer from session
        if (SessionManager.getInstance().getCurrentUser() instanceof Customer) {
            currentCustomer = (Customer) SessionManager.getInstance().getCurrentUser();
            if (currentCustomer.getBudget() != null) {
                budgetBalanceLabel.setText("Current Budget: ₪" + String.format("%.2f", currentCustomer.getBudget().getBalance()));
            } else {
                budgetBalanceLabel.setText("Current Budget: ₪0.00");
            }
        } else {
            currentCustomer = null;
            budgetBalanceLabel.setText("Current Budget: ₪0.00");
            // optionally disable add button
            addButton.setDisable(true);
        }
    }

    @FXML
    private void handleAddToBudget() {
        if (currentCustomer == null) {
            showError("No logged-in customer.");
            return;
        }

        String amtStr = amountField.getText();
        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (Exception e) {
            showError("Please enter a valid number for amount.");
            return;
        }
        if (amount <= 0) {
            showError("Amount must be greater than 0.");
            return;
        }

        String method = paymentMethodChoice.getValue();
        if (method == null) {
            showError("Please select a payment method.");
            return;
        }

        // Validate payment method data
        if ("Saved Card".equals(method)) {
            if (currentCustomer.getCreditCard() == null || currentCustomer.getCreditCard().getCardNumber() == null) {
                showError("No saved card on file.");
                return;
            }
        } else { // "New Card"
            if (cardNumberField.getText() == null || cardNumberField.getText().trim().isEmpty()
                    || expiryField.getText() == null || expiryField.getText().trim().isEmpty()
                    || cvvField.getText() == null || cvvField.getText().trim().isEmpty()) {
                showError("Please fill in all new card fields.");
                return;
            }
        }

        // Locally update Customer's Budget object (so UI reflects change immediately)
     //   if (currentCustomer.getBudget() == null) {
      //      Budget b = new Budget();
      //      b.setCustomer(currentCustomer);
      //      b.setBalance(0.0);
      //      currentCustomer.setBudget(b);
      //  }

      //  currentCustomer.getBudget().addFunds(amount);
        Customer payload = new Customer();
        payload.setId(currentCustomer.getId());
        Budget b = new Budget();
        b.setBalance(amount); // delta
        payload.setBudget(b);

        // Disable UI while waiting server
        addButton.setDisable(true);

        // Send to server using your project's pattern: message with object = Customer
        try {
            SimpleClient.getClient().sendToServer(new Message("update_budget_add", payload, null));
        } catch (IOException e) {
            addButton.setDisable(false);
            e.printStackTrace();
            showError("Failed to send request to server: " + e.getMessage());
        }
    }


    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try {
                App.setRoot("primary");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @Subscribe
    public void onServerMessage(Message msg) {
        // server sends "budget_updated" with the Customer object, or "budget_update_failed"
        if (msg == null || msg.getMessage() == null) return;

        switch (msg.getMessage()) {
            case "budget_updated" -> {
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
            case "budget_update_failed" -> Platform.runLater(() -> {
                addButton.setDisable(false);
                showError("Failed to update budget on server.");
            });
            default -> {}
        }
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
}
