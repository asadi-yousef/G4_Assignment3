package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Complaint;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class ComplaintsListController implements Initializable {

    @FXML
    private TableView<Complaint> complaintsTable;

    @FXML
    private TableColumn<Complaint, Integer> idColumn;

    @FXML
    private TableColumn<Complaint, String> customerColumn;

    @FXML
    private TableColumn<Complaint, String> orderColumn;

    @FXML
    private TableColumn<Complaint, String> textColumn;

    @FXML
    private TableColumn<Complaint, String> deadlineColumn;

    @FXML
    private TableColumn<Complaint, Boolean> resolvedColumn;

    private ObservableList<Complaint> complaints = FXCollections.observableArrayList();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        idColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().getId()));
        customerColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getCustomer().getName()));
        orderColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(String.valueOf(data.getValue().getOrder().getId())));
        textColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getText()));
        deadlineColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().getDeadline().toString()));
        resolvedColumn.setCellValueFactory(data -> new javafx.beans.property.SimpleObjectProperty<>(data.getValue().isResolved()));

        complaintsTable.setItems(complaints);

        loadComplaints();
    }

    private void loadComplaints() {
        try {
            SimpleClient.getClient().sendToServer(new Message("get_complaints", null,null));
        } catch (IOException e) {
            showAlert("Error", "Failed to load complaints.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleResolve() {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a complaint to resolve.", Alert.AlertType.WARNING);
            return;
        }
        selected.setResolved(true);
        try {
            SimpleClient.getClient().sendToServer(new Message("resolve_complaint", selected,null));
            showAlert("Success", "Complaint marked as resolved.", Alert.AlertType.INFORMATION);
            loadComplaints();
        } catch (IOException e) {
            showAlert("Error", "Failed to resolve complaint.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleBack() {
        try {
            App.setRoot("employee_dashboard"); // adjust if different
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

    // called from client listener
    public void setComplaints(List<Complaint> complaintList) {
        complaints.setAll(complaintList);
    }
}
