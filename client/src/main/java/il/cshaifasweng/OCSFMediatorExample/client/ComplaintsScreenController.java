package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Complaint;
import il.cshaifasweng.OCSFMediatorExample.entities.Order;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ComplaintsScreenController implements Initializable {

    @FXML
    private Label orderLabel;

    @FXML
    private TextArea complaintTextArea;

    private Order order;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        order = SessionManager.getInstance().getSelectedOrder();
        if (order != null) {
            orderLabel.setText("Complaint for Order #" + order.getId());
        }
    }

    @FXML
    private void handleSubmitComplaint(ActionEvent event) {
        String text = complaintTextArea.getText().trim();

        if (text.isEmpty()) {
            showAlert("Error", "Complaint cannot be empty.", Alert.AlertType.WARNING);
            return;
        }

        if (text.length() > 100) {
            showAlert("Error", "Complaint cannot exceed 100 characters.", Alert.AlertType.WARNING);
            return;
        }

        Complaint complaint = new Complaint(
                (Customer) SessionManager.getInstance().getCurrentUser(),
                order,
                text
        );

        try {
            SimpleClient.getClient().sendToServer(new Message("submit_complaint", complaint,null));
            showAlert("Success", "Complaint submitted successfully!", Alert.AlertType.INFORMATION);
            App.setRoot("ordersScreenView"); // back to orders page
        } catch (IOException e) {
            showAlert("Error", "Failed to submit complaint.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleBack(ActionEvent event) {
        try {
            App.setRoot("orders");
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
