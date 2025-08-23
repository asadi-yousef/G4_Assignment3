package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Order;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class CancelOrderController implements Initializable {

    @FXML
    private VBox orderDetailsBox;

    @FXML
    private Label orderIdLabel;

    @FXML
    private Label orderDateLabel;

    @FXML
    private Button cancelOrderButton;

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

        // Get the currently selected order from SessionManager
        selectedOrder = SessionManager.getInstance().getSelectedOrder();
        if (selectedOrder != null) {
            orderIdLabel.setText("Order ID: " + selectedOrder.getId());
            orderDateLabel.setText("Order Date: " + selectedOrder.getOrderDate().toString());
        } else {
            showAlert("Error", "No order selected.");
            confirmButton.setDisable(true);
        }
    }

    @FXML
    private void handleConfirmCancel() {
        cancelOrder();
    }
    @FXML
    private void handleBack() {
        goBack();
    }
    private void cancelOrder() {
        try {
            if (selectedOrder == null) {
                showAlert("Error", "No order selected to cancel.");
                return;
            }

            ArrayList<Object> payload = new ArrayList<>();
            payload.add(selectedOrder.getId()); // just send order ID
            Message message = new Message("cancel_order", null, payload);
            SimpleClient.getClient().sendToServer(message);

            cancelOrderButton.setDisable(true);
            cancelOrderButton.setText("Cancelling...");

        } catch (Exception e) {
            showAlert("Error", "Failed to send cancel request.");
            e.printStackTrace();
        }
    }


    @Subscribe
    public void onServerResponse(Message msg) {
        if ("order_cancelled_successfully".equals(msg.getMessage())) {
            Long cancelledId = (Long) msg.getObject();
            if (SessionManager.getInstance().getSelectedOrder() != null &&
                    SessionManager.getInstance().getSelectedOrder().getId().equals(cancelledId)) {
                SessionManager.getInstance().setSelectedOrder(null);
            }

            Platform.runLater(() -> {
                showAlert("Success", "Order cancelled successfully.");
                goBack();
            });
        } else if ("order_cancel_error".equals(msg.getMessage())) {
            Platform.runLater(() -> showAlert("Error", msg.getObject().toString()));
            cancelOrderButton.setDisable(false);
            cancelOrderButton.setText("Cancel Order");
        }
    }

    private void goBack() {
        try {
            EventBus.getDefault().unregister(this);
            App.setRoot("orderListView"); // your previous view of orders
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
