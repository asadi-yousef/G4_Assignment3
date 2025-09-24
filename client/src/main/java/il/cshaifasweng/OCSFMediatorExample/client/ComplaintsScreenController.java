package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ComplaintsScreenController implements Initializable {

    @FXML private Label orderLabel;
    @FXML private TextArea complaintTextArea;
    @FXML private Label charCountLabel;

    // NEW:
    @FXML private Button submitBtn;
    @FXML private Button viewResponseBtn;
    @FXML private Label statusLabel;

    private static final int MAX_LEN = 120;
    private Order order;
    private ComplaintDTO existingForOrder; // cache what server returns
    private volatile boolean disposed = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        disposed = false;

        order = SessionManager.getInstance().getSelectedOrder();
        if (order != null) {
            orderLabel.setText("Complaint for Order #" + order.getId());
        }
        complaintTextArea.textProperty().addListener((obs, oldVal, neu) -> {
            if (neu.length() > MAX_LEN) {
                complaintTextArea.setText(neu.substring(0, MAX_LEN));
            }
            charCountLabel.setText(complaintTextArea.getText().length() + "/" + MAX_LEN);
        });

        // Ask server if a complaint already exists for this order
        requestOrderComplaintStatus();
    }

    private void requestOrderComplaintStatus() {
        try {
            var u = SessionManager.getInstance().getCurrentUser();
            if (!(u instanceof Customer) || order == null || order.getId() == null) {
                applyNoExistingComplaintUI();
                return;
            }
            Map<String,Object> p = new HashMap<>();
            p.put("customerId", ((Customer)u).getId());
            p.put("orderId", order.getId());
            SimpleClient.getClient().sendToServer(new Message(
                    "get_order_complaint_status", null, new java.util.ArrayList<>(java.util.List.of(p))
            ));
        } catch (IOException e) {
            applyNoExistingComplaintUI();
        }
    }

    @Subscribe
    public void onServer(Message msg) {
        boolean nodeAttached = (orderLabel != null && orderLabel.getScene() != null);
        if (disposed || !nodeAttached) return;

        switch (msg.getMessage()) {
            case "order_complaint_status":
                javafx.application.Platform.runLater(() -> {
                    existingForOrder = (msg.getObject() instanceof ComplaintDTO) ? (ComplaintDTO) msg.getObject() : null;
                    if (existingForOrder == null) applyNoExistingComplaintUI();
                    else applyExistingComplaintUI(existingForOrder);
                });
                break;

            case "complaint_exists_for_order":
                javafx.application.Platform.runLater(() -> {
                    showAlert("Notice", "You’ve already submitted a complaint for this order.", Alert.AlertType.INFORMATION);
                    requestOrderComplaintStatus();
                });
                break;

            case "complaint_submitted":
                javafx.application.Platform.runLater(() -> {
                    // Optionally show a quick toast/alert; then go back to orders
                    // showAlert("Success", "Complaint submitted. Returning to your orders...", Alert.AlertType.INFORMATION);
                    try {
                        App.setRoot("ordersScreenView");   // << go back safely on FX thread
                    } catch (IOException e) {
                        // if navigation fails, at least disable submit and show pending
                        requestOrderComplaintStatus();
                    }
                });
                break;

            case "complaints_refresh":
                // if employee resolves while customer is on this screen, refresh status
                javafx.application.Platform.runLater(this::requestOrderComplaintStatus);
                break;
        }
    }

    private void applyNoExistingComplaintUI() {
        submitBtn.setDisable(false);
        viewResponseBtn.setVisible(false);
        complaintTextArea.setDisable(false);
        statusLabel.setText("");
    }

    private void applyExistingComplaintUI(ComplaintDTO dto) {
        submitBtn.setDisable(true);
        complaintTextArea.setDisable(true);

        if (dto.isResolved()) {
            viewResponseBtn.setVisible(true);
            statusLabel.setText("Response available.");
        } else {
            viewResponseBtn.setVisible(false);
            statusLabel.setText("Response pending.");
        }
    }

    @FXML
    private void handleViewResponse() {
        if (existingForOrder == null) return;
        String comp = existingForOrder.getCompensationAmount()==null ? "—"
                : existingForOrder.getCompensationAmount().toPlainString();
        String resp = existingForOrder.getResponseText()==null ? "—"
                : existingForOrder.getResponseText();
        showAlert("Complaint Response",
                "Response: " + resp + "\nCompensation: " + comp,
                Alert.AlertType.INFORMATION);
    }

    @FXML
    private void handleSubmitComplaint(ActionEvent event) {
        try {
            User u = SessionManager.getInstance().getCurrentUser();
            if (!(u instanceof Customer)) {
                showAlert("Error", "Only customers can submit complaints.", Alert.AlertType.WARNING);
                return;
            }
            if (order == null || order.getId() == null) {
                showAlert("Error", "No order selected.", Alert.AlertType.WARNING);
                return;
            }

            String text = complaintTextArea.getText().trim();
            if (text.isEmpty()) {
                showAlert("Error", "Complaint cannot be empty.", Alert.AlertType.WARNING);
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("customerId", ((Customer)u).getId());
            payload.put("orderId", order.getId());
            payload.put("text", text);

            SimpleClient.getClient().sendToServer(new Message(
                    "submit_complaint", null, new java.util.ArrayList<>(java.util.List.of(payload))
            ));

        } catch (IOException e) {
            showAlert("Error", "Failed to submit complaint.", Alert.AlertType.ERROR);
        }
    }

    public void onClose() {
        disposed = true;
        try { EventBus.getDefault().unregister(this); } catch (Throwable ignore) {}
    }

    @FXML
    private void handleBack(ActionEvent event) {
        onClose();
        try { App.setRoot("ordersScreenView"); } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
