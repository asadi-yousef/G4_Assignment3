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
import java.math.RoundingMode;
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

            LocalDateTime deliveryTime;
            boolean delivery = selectedOrder.getDelivery();
            if(delivery) {
                deliveryTime = selectedOrder.getDeliveryDateTime();
            } else {
                deliveryTime = selectedOrder.getPickupDateTime();
            }
            if (deliveryTime != null) {
                deliveryDateLabel.setText("Delivery/Pickup Date: " +
                        deliveryTime.format(
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        )
                );
            } else {
                deliveryDateLabel.setText("Delivery/Pickup Date: -");
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

        // Block on non-cancelable statuses (mirror server-side rule)
        String status = selectedOrder.getStatus() == null
                ? ""
                : selectedOrder.getStatus().trim().toUpperCase();
        if ("DELIVERED".equals(status) || "COMPLETED".equals(status) || "CANCELLED".equals(status)) {
            showAlert("Error", "This order cannot be canceled because its status is: " + status + ".");
            return;
        }

        User user = SessionManager.getInstance().getCurrentUser();
        if (!(user instanceof Customer customer)) {
            showAlert("Error", "Current user is not a customer.");
            return;
        }

        // Delivery/pickup time must exist to proceed
        LocalDateTime deliveryTime = selectedOrder.getDelivery()
                ? selectedOrder.getDeliveryDateTime()
                : selectedOrder.getPickupDateTime();
        if (deliveryTime == null) {
            showAlert("Error", "Missing delivery/pickup time for this order.");
            return;
        }

        // Optional: do a *preview* for the user (server remains the authority)
        var now = LocalDateTime.now();
        var diff = java.time.Duration.between(now, deliveryTime);
        if (diff.isNegative()) {
            showAlert("Error", "Cannot cancel order: the scheduled time has already passed.");
            return;
        } else {
            long minutesUntil = diff.toMinutes();
            if (minutesUntil >= 180) {
                showAlert("Info", "Preview: Full refund (final amount set by server).");
            } else if (minutesUntil >= 60) {
                showAlert("Info", "Preview: 50% refund (final amount set by server).");
            } else {
                showAlert("Info", "Preview: No refund (final decision by server).");
            }
        }

        try {
            // Send ONLY the orderId; server computes refund and validates status
            var payload = new ArrayList<Object>();
            payload.add(selectedOrder.getId());

            var message = new Message("cancel_order", null, payload);
            SimpleClient.getClient().sendToServer(message);

            confirmButton.setDisable(true);
            confirmButton.setText("Cancelling...");
        } catch (Exception e) {
            showAlert("Error", "Failed to send cancel request.");
            confirmButton.setDisable(false);
            confirmButton.setText("Confirm");
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
