package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Complaint;
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

    @FXML private TableView<Complaint> complaintsTable;
    @FXML private TableColumn<Complaint, Integer> idColumn;
    @FXML private TableColumn<Complaint, String> customerColumn;
    @FXML private TableColumn<Complaint, String> orderColumn;
    @FXML private TableColumn<Complaint, String> textColumn;
    @FXML private TableColumn<Complaint, String> deadlineColumn;
    @FXML private TableColumn<Complaint, Boolean> resolvedColumn;

    @FXML private TextArea responseTextArea;
    @FXML private TextField compensationField;
    @FXML private CheckBox showResolvedCheck;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final List<Complaint> backing = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        idColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getId()));
        customerColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getCustomer() != null ? d.getValue().getCustomer().getName() : "—"));
        orderColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getOrder() != null ? String.valueOf(d.getValue().getOrder().getId()) : "—"));
        textColumn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getText()));
        deadlineColumn.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getDeadline() != null ? d.getValue().getDeadline().format(fmt) : "—"));
        resolvedColumn.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().isResolved()));

        complaintsTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Complaint c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) { setStyle(""); return; }
                if (!c.isResolved() && c.getDeadline() != null && c.getDeadline().isBefore(java.time.LocalDateTime.now())) {
                    setStyle("-fx-background-color: #ffefef;"); // overdue tint
                } else setStyle("");
            }
        });

        showResolvedCheck.selectedProperty().addListener((o, a, b) -> loadComplaints());
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
            Long employeeId = ((Employee) user).getId();
            boolean resolved = showResolvedCheck.isSelected();

            Map<String, Object> payload = new HashMap<>();
            payload.put("employeeId", employeeId);
            payload.put("resolved", resolved);

            SimpleClient.getClient().sendToServer(new Message(
                    "get_all_complaints", null, new ArrayList<>(List.of(payload)))
            );
        } catch (IOException e) {
            showAlert("Error", "Failed to load complaints.", Alert.AlertType.ERROR);
        }
    }


    @FXML
    private void handleResolveWithReply() {
        Complaint selected = complaintsTable.getSelectionModel().getSelectedItem();
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
        payload.put("compensation", comp);

        try {
            SimpleClient.getClient().sendToServer(new Message("resolve_complaint", null,
                    new ArrayList<>(List.of(payload))));
        } catch (IOException e) {
            showAlert("Error", "Failed to resolve complaint.", Alert.AlertType.ERROR);
            return;
        }
        showAlert("Success", "Reply sent and complaint resolved.", Alert.AlertType.INFORMATION);
        responseTextArea.clear();
        compensationField.clear();
        loadComplaints();
    }

    @FXML
    private void handleBack() {
        try { App.setRoot("primary"); } catch (IOException e) { throw new RuntimeException(e); }
    }

    @Subscribe
    public void onServerMessage(Message msg) {
        if ("complaints_list".equals(msg.getMessage()) || "complaints_history".equals(msg.getMessage())) {
            Platform.runLater(() -> {
                backing.clear();
                if (msg.getObject() instanceof List<?>) {
                    for (Object o : (List<?>) msg.getObject()) if (o instanceof Complaint) backing.add((Complaint) o);
                }
                complaintsTable.getItems().setAll(backing);
            });
        }
    }

    private void showAlert(String title, String msg, Alert.AlertType type) {
        Alert alert = new Alert(type, msg, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(title);
        alert.showAndWait();
    }
}
