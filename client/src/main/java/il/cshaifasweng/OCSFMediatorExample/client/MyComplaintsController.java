package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.ComplaintDTO;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
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
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MyComplaintsController implements Initializable {

    @FXML private TableView<ComplaintDTO> table;
    @FXML private TableColumn<ComplaintDTO, Integer> idCol;
    @FXML private TableColumn<ComplaintDTO, String> orderCol;
    @FXML private TableColumn<ComplaintDTO, String> textCol;
    @FXML private TableColumn<ComplaintDTO, String> submittedCol;
    @FXML private TableColumn<ComplaintDTO, Boolean> resolvedCol;
    @FXML private TableColumn<ComplaintDTO, String> responseCol;
    @FXML private TableColumn<ComplaintDTO, String> compensationCol;

    @FXML private CheckBox unresolvedOnly;
    @FXML private Button refreshBtn;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private boolean isCustomer = false;


    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        var u = SessionManager.getInstance().getCurrentUser();
        isCustomer = (u instanceof Customer);

        if (!isCustomer) {
            // If somehow opened by a non-customer, leave inert (no alerts, no bus)
            return;
        }
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        // ---- Column bindings (null-safe) ----
        idCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getId()));

        orderCol.setCellValueFactory(d -> {
            Long oid = d.getValue().getOrderId();
            return new SimpleStringProperty(oid == null ? "—" : String.valueOf(oid));
        });

        textCol.setCellValueFactory(d -> {
            String t = d.getValue().getText();
            return new SimpleStringProperty((t == null || t.isBlank()) ? "—" : t);
        });

        final java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        submittedCol.setCellValueFactory(d -> {
            var ts = d.getValue().getSubmittedAt();
            return new SimpleStringProperty(ts == null ? "—" : ts.format(fmt));
        });

        resolvedCol.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().isResolved()));
        resolvedCol.setCellFactory(col -> new TableCell<ComplaintDTO, Boolean>() {
            @Override protected void updateItem(Boolean val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty ? null : (Boolean.TRUE.equals(val) ? "Yes" : "No"));
            }
        });

        responseCol.setCellValueFactory(d -> {
            String r = d.getValue().getResponseText();
            return new SimpleStringProperty((r == null || r.isBlank()) ? "—" : r);
        });

        compensationCol.setCellValueFactory(d -> {
            var comp = d.getValue().getCompensationAmount();
            return new SimpleStringProperty(comp == null ? "—" : comp.toPlainString());
        });

        // UI actions
        unresolvedOnly.selectedProperty().addListener((o, a, b) -> loadMine());
        refreshBtn.setOnAction(e -> loadMine());

        // Default to only unresolved (uncheck if you want both)
        unresolvedOnly.setSelected(true);

        // Initial load
        loadMine();
    }


    private void loadMine() {
        if (!isCustomer) return;  // silent no-op
        try {
            var user = SessionManager.getInstance().getCurrentUser();
            Map<String, Object> payload = new HashMap<>();
            payload.put("customerId", ((Customer) user).getId());
            payload.put("unresolvedOnly", unresolvedOnly.isSelected());

            SimpleClient.getClient().sendToServer(new Message(
                    "get_my_complaints", null, new ArrayList<>(List.of(payload))
            ));
        } catch (IOException ignored) {}
    }


    @FXML
    private void handleBack() {
        try { App.setRoot("primary"); }  // ← catalog view in your app
        catch (IOException e) { throw new RuntimeException(e); }
    }


    @Subscribe
    public void onServer(Message msg) {
        if (!isCustomer) return;

        switch (msg.getMessage()) {
            case "complaints_history":
                Platform.runLater(() -> {
                    var incoming = msg.getObject();
                    java.util.List<ComplaintDTO> rows = new java.util.ArrayList<>();
                    if (incoming instanceof java.util.List<?>) {
                        for (Object o : (java.util.List<?>) incoming) {
                            if (o instanceof ComplaintDTO dto) rows.add(dto);
                        }
                    }
                    table.getItems().setAll(rows);
                });
                break;

            case "complaints_refresh":
                Platform.runLater(this::loadMine);
                break;
        }
    }

    private void alert(String title, String body, Alert.AlertType type) {
        Alert a = new Alert(type, body, ButtonType.OK);
        a.setHeaderText(null);
        a.setTitle(title);
        a.showAndWait();
    }
}
