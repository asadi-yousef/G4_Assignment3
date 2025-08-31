package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ComplaintsScreenController implements Initializable {

    @FXML private Label orderLabel;
    @FXML private TextArea complaintTextArea;
    @FXML private Label charCountLabel;

    private static final int MAX_LEN = 120;
    private Order order;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
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
    }

    @FXML
    private void handleSubmitComplaint(ActionEvent event) {
        try {
            User u = SessionManager.getInstance().getCurrentUser();
            if (!(u instanceof Customer)) {
                showAlert("Error", "Only customers can submit complaints.", Alert.AlertType.WARNING);
                return;
            }
            Customer customer = (Customer) u;

            if (order == null || order.getId() == null) {
                showAlert("Error", "No order selected.", Alert.AlertType.WARNING);
                return;
            }

            String text = complaintTextArea.getText().trim();
            if (text.isEmpty()) {
                showAlert("Error", "Complaint cannot be empty.", Alert.AlertType.WARNING);
                return;
            }

            // payload -> server creates & persists
            Map<String, Object> payload = new HashMap<>();
            payload.put("customerId", customer.getId());
            payload.put("orderId", order.getId());
            payload.put("text", text);

            SimpleClient.getClient().sendToServer(new il.cshaifasweng.OCSFMediatorExample.entities.Message(
                    "submit_complaint", null, new java.util.ArrayList<>(java.util.List.of(payload))
            ));

            showAlert("Success", "Complaint submitted successfully!", Alert.AlertType.INFORMATION);
            App.setRoot("ordersScreenView"); // back to orders page

        } catch (IOException e) {
            showAlert("Error", "Failed to submit complaint.", Alert.AlertType.ERROR);
        }
    }


    @FXML
    private void handleBack(ActionEvent event) {
        try {
            App.setRoot("ordersScreenView");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
