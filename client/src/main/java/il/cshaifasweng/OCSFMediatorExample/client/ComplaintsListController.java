package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.ComplaintDTO;
import il.cshaifasweng.OCSFMediatorExample.entities.Employee;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ComplaintsListController implements Initializable {

    @FXML private TableView<ComplaintDTO> complaintsTable;
    @FXML private TableColumn<ComplaintDTO, Integer> idColumn;
    @FXML private TableColumn<ComplaintDTO, String> customerColumn;
    @FXML private TableColumn<ComplaintDTO, String> orderColumn;
    @FXML private TableColumn<ComplaintDTO, String> textColumn;
    @FXML private TableColumn<ComplaintDTO, String> deadlineColumn;
    @FXML private TableColumn<ComplaintDTO, Boolean> resolvedColumn;

    // NEW: show resolver details when "Show resolved" is checked
    @FXML private TableColumn<ComplaintDTO, String> responseCol;
    @FXML private TableColumn<ComplaintDTO, String> compensationCol;

    @FXML private TextArea responseTextArea;
    @FXML private TextField compensationField;
    @FXML private CheckBox showResolvedCheck;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final List<ComplaintDTO> backing = new ArrayList<>();

    private volatile boolean disposed = false;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        disposed = false;

        idColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getId()));
        customerColumn.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getCustomerName() != null ? d.getValue().getCustomerName() : "—"));
        orderColumn.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getOrderId() != null ? String.valueOf(d.getValue().getOrderId()) : "—"));
        textColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getText()));
        deadlineColumn.setCellValueFactory(d ->
                new SimpleStringProperty(
                        d.getValue().getDeadline() != null ? d.getValue().getDeadline().format(fmt) : "—"));
        resolvedColumn.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().isResolved()));

        // NEW: resolver details (visible only when "show resolved" is on)
        if (responseCol != null) {
            responseCol.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().getResponseText() == null ? "—" : d.getValue().getResponseText()
            ));
        }
        if (compensationCol != null) {
            compensationCol.setCellValueFactory(d -> new SimpleStringProperty(
                    d.getValue().getCompensationAmount() == null ? "—" : d.getValue().getCompensationAmount().toPlainString()
            ));
        }

        complaintsTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(ComplaintDTO c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setStyle(""); return; }
                if (!c.isResolved() && c.getDeadline() != null
                        && c.getDeadline().isBefore(java.time.LocalDateTime.now())) {
                    setStyle("-fx-background-color: #ffefef;");
                } else setStyle("");
            }
        });

        // toggle extra columns
        showResolvedCheck.selectedProperty().addListener((o, oldV, show) -> {
            if (responseCol != null) responseCol.setVisible(show);
            if (compensationCol != null) compensationCol.setVisible(show);
            loadComplaints();
        });

        // default visibility
        if (responseCol != null) responseCol.setVisible(showResolvedCheck.isSelected());
        if (compensationCol != null) compensationCol.setVisible(showResolvedCheck.isSelected());

        loadComplaints();
    }

    @FXML
    private void handleRefresh() { loadComplaints(); }

    private void loadComplaints() {
        try {
            var user = SessionManager.getInstance().getCurrentUser();
            if (!(user instanceof Employee)) {
                showAlert("Error", "Employee login required.", Alert.AlertType.WARNING);
                return;
            }
            Map<String, Object> payload = new HashMap<>();
            payload.put("resolved", showResolvedCheck.isSelected());

            SimpleClient.getClient().sendToServer(new Message(
                    "get_all_complaints", null, new ArrayList<>(List.of(payload))));
        } catch (IOException e) {
            showAlert("Error", "Failed to load complaints.", Alert.AlertType.ERROR);
        }
    }

    @FXML
    private void handleResolveWithReply() {
        ComplaintDTO selected = complaintsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Select", "Please select a complaint.", Alert.AlertType.WARNING);
            return;
        }
        String reply = responseTextArea.getText() != null ? responseTextArea.getText().trim() : "";
        BigDecimal comp = null;
        if (compensationField.getText() != null && !compensationField.getText().isBlank()) {
            try { comp = new BigDecimal(compensationField.getText().trim()); }
            catch (NumberFormatException ex) {
                showAlert("Invalid", "Invalid compensation amount.", Alert.AlertType.ERROR);
                return;
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("complaintId", selected.getId());
        payload.put("responseText", reply);
        if (comp != null) payload.put("compensation", comp);
        else payload.put("compensation", 0);

        try {
            SimpleClient.getClient().sendToServer(new Message(
                    "resolve_complaint", null, new ArrayList<>(List.of(payload))
            ));
        } catch (IOException e) {
            showAlert("Error", "Failed to resolve complaint.", Alert.AlertType.ERROR);
            return;
        }
        // UX: clear & reload when the server confirms
        responseTextArea.clear();
        compensationField.clear();
    }

    public void onClose() {
        disposed = true;
        try { EventBus.getDefault().unregister(this); } catch (Throwable ignore) {}
    }

    @FXML
    private void handleBack() {
        onClose();
        try { App.setRoot("primary"); } catch (IOException e) { throw new RuntimeException(e); }
    }

    @Subscribe
    public void onServerMessage(Message msg) {
        boolean nodeAttached = (complaintsTable != null && complaintsTable.getScene() != null);
        if (disposed || !nodeAttached) return;

        switch (msg.getMessage()) {
            case "complaints_list":
                Platform.runLater(() -> {
                    backing.clear();
                    if (msg.getObject() instanceof List<?>) {
                        for (Object o : (List<?>) msg.getObject()) {
                            if (o instanceof ComplaintDTO dto) backing.add(dto);
                        }
                    }
                    complaintsTable.getItems().setAll(backing);
                });
                break;

            // refresh signals after resolution (from server broadcast)
            case "complaints_refresh":
                Platform.runLater(this::loadComplaints);
                break;

            // immediate feedback for this employee
            case "complaint_resolved":
                Platform.runLater(() -> {
                    showAlert("Resolved", "Reply sent and complaint marked as resolved.", Alert.AlertType.INFORMATION);
                    loadComplaints();
                });
                break;

            // (optional) errors
            case "complaint_resolve_error":
                Platform.runLater(() ->
                        showAlert("Error", String.valueOf(msg.getObject()), Alert.AlertType.ERROR));
                break;
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
