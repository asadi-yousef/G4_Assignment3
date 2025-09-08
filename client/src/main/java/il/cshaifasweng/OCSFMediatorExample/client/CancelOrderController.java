package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ResourceBundle;
import java.time.Duration;

public class CancelOrderController implements Initializable {
    @FXML
    private Label orderIdLabel;
    @FXML
    private Label orderDateLabel;
    @FXML
    private Label deliveryDateLabel;
    @FXML
    private Label compensationLabel; // placeholder for future compensation
    @FXML
    private Button confirmButton;
    @FXML
    private Button backButton;
    private Order selectedOrder;
    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        selectedOrder = SessionManager.getInstance().getSelectedOrder();
        if (selectedOrder != null) {
            orderIdLabel.setText("Order ID: " + selectedOrder.getId());

            // Format order date
            if (selectedOrder.getOrderDate() != null) {
                orderDateLabel.setText("Order Date: " +
                        selectedOrder.getOrderDate().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        )
                );
            } else {
                orderDateLabel.setText("Order Date: -");
            }

            // Format delivery date
            if (selectedOrder.getDeliveryDateTime() != null) {
                deliveryDateLabel.setText("Delivery Date: " +
                        selectedOrder.getDeliveryDateTime().format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        )
                );
            } else {
                deliveryDateLabel.setText("Delivery Date: -");
            }

            // Placeholder for future compensation info
            compensationLabel.setText("Compensation: -");
        } else {
            showAlert("Error", "No order selected.");
            confirmButton.setDisable(true);
        }
    }

    @FXML
    private void handleConfirmCancel() {
        if (selectedOrder == null) return;

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Customer customer)) {
            showAlert("Error", "Current user is not a customer.");
            return;
        }

        Budget budget = customer.getBudget();
        if (budget == null) {
            showAlert("Error", "Customer budget not found.");
            return;
        }

        try {
            double refund = selectedOrder.getTotalPrice();
            LocalDateTime deliveryTime = selectedOrder.getDeliveryDateTime();
            LocalDateTime currentTime = LocalDateTime.now();

            Duration diff = Duration.between(currentTime, deliveryTime);
            long hoursUntilDelivery = diff.toHours();

            if (hoursUntilDelivery > 0) {
                if (hoursUntilDelivery >= 24) {
                    showAlert("Success", "Full Refund, your refund is stored in your budget balance.");
                } else if (hoursUntilDelivery >= 3) {
                    showAlert("Success", "50% Refund, your refund is stored in your budget balance.");
                    refund /= 2;
                } else {
                    showAlert("Success", "No Refund");
                    refund = 0;
                }

                // Update local budget
                //budget.addFunds(refund);

                // Send cancel request along with refund amount to server
                ArrayList<Object> payload = new ArrayList<>();
                payload.add(selectedOrder.getId());
                payload.add(refund); // pass refund to server
                Message message = new Message("cancel_order", null, payload);
                SimpleClient.getClient().sendToServer(message);

                confirmButton.setDisable(true);
                confirmButton.setText("Cancelling...");
            } else {
                showAlert("Error", "Cannot cancel order, order date and time has already passed!");
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to send cancel request.");
            confirmButton.setDisable(false);
        }
    }

    @Subscribe
    public void onServerResponse(Message msg) {
        if ("order_cancelled_successfully".equals(msg.getMessage())) {
            Platform.runLater(() -> {
                // Extract updated budget from message
                Object budgetObj = msg.getObject();
                if (budgetObj instanceof Budget updatedBudget) {
                    Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();
                    customer.setBudget(updatedBudget); // update client-side object
                }

                showAlert("Success", "Order cancelled successfully.");
                SessionManager.getInstance().setSelectedOrder(null);
                goBack();
            });
        } else if ("order_cancel_error".equals(msg.getMessage())) {
            Platform.runLater(() -> {
                showAlert("Error", msg.getObject().toString());
                confirmButton.setDisable(false);
                confirmButton.setText("Confirm");
            });
        }
    }



    @FXML
    private void handleBack() {
        goBack();
    }

    private void goBack() {
        try {
            EventBus.getDefault().unregister(this);
            App.setRoot("ordersScreenView"); // Orders screen FXML
        } catch (IOException e) {
            showAlert("Error", "Failed to go back.");
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
