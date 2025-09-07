package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import javafx.scene.layout.HBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import java.time.LocalDate;



public class EmployeeScheduleController implements Initializable {

    @FXML private DatePicker dayPicker;
    @FXML private ChoiceBox<String> filterChoice; // All / Pickup / Delivery

    // Use DTO types
    @FXML private TableView<ScheduleOrderDTO> table;
    @FXML private TableColumn<ScheduleOrderDTO, Number> colId;
    @FXML private TableColumn<ScheduleOrderDTO, String> colCustomer;
    @FXML private TableColumn<ScheduleOrderDTO, String> colType;
    @FXML private TableColumn<ScheduleOrderDTO, String> colWhen;
    @FXML private TableColumn<ScheduleOrderDTO, String> colWhere;
    @FXML private TableColumn<ScheduleOrderDTO, String> colStatus;
    @FXML private TableColumn<ScheduleOrderDTO, Void>   colAction;

    @FXML private Label statusLabel;
    @FXML private Label noOrdersLabel;

    private final DateTimeFormatter dtFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final javafx.collections.ObservableList<ScheduleOrderDTO> master =
            javafx.collections.FXCollections.observableArrayList();
    private javafx.collections.transformation.FilteredList<ScheduleOrderDTO> filtered;
    private final ObservableList<ScheduleOrderDTO> masterData = FXCollections.observableArrayList();
    private final FilteredList<ScheduleOrderDTO> filteredData = new FilteredList<>(masterData, dto -> true);
    private final java.util.Set<Long> pending = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private javafx.animation.Timeline autoRefresh;
    private volatile boolean inflight = false;
    private final java.util.Set<Long> inFlight = new java.util.HashSet<>();

    private final javafx.collections.transformation.SortedList<ScheduleOrderDTO> sortedData =
            new javafx.collections.transformation.SortedList<>(filteredData);

    private Employee currentEmployee;

    // --- initialize ---
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (!EventBus.getDefault().isRegistered(this))
            EventBus.getDefault().register(this);

        try {
            if (!SimpleClient.getClient().isConnected()) SimpleClient.getClient().openConnection();
        } catch (IOException e) { status("Failed to connect to server."); return; }

        User u = SessionManager.getInstance().getCurrentUser();
        if (u instanceof Employee) currentEmployee = (Employee) u;

        // --- Column factories (only if table exists in this FXML) ---
        if (colId != null) {
            colId.setCellValueFactory(c ->
                    new javafx.beans.property.ReadOnlyLongWrapper(c.getValue().getId()));
        }
        if (colCustomer != null) {
            colCustomer.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            Optional.ofNullable(c.getValue().getCustomerName()).orElse("-")));
        }
        if (colType != null) {
            colType.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(c.getValue().isDelivery() ? "Delivery" : "Pickup"));
        }
        if (colWhen != null) {
            colWhen.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            c.getValue().getScheduledAt()==null ? "-" : dtFmt.format(c.getValue().getScheduledAt())));
        }
        if (colWhere != null) {
            colWhere.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            Optional.ofNullable(c.getValue().getWhereText()).orElse("-")));
        }
        if (colStatus != null) {
            colStatus.setCellValueFactory(c ->
                    new javafx.beans.property.SimpleStringProperty(
                            Optional.ofNullable(c.getValue().getStatus()).orElse("PLACED")));
        }
        if (colAction != null) addActionColumn();

        if (table != null) {
            table.setItems(sortedData);
            sortedData.comparatorProperty().bind(table.comparatorProperty());
        }

        // UI defaults
        if (dayPicker != null) dayPicker.setValue(java.time.LocalDate.now());
        if (filterChoice != null) {
            filterChoice.getItems().setAll("All", "Pickup", "Delivery");
            filterChoice.setValue("All");
        }

        if (dayPicker != null) dayPicker.valueProperty().addListener((o, a, b) -> requestDay());
        if (filterChoice != null) filterChoice.valueProperty().addListener((o, a, b) -> updateFilter());

        // First load
        requestDay();

        if (openScheduleBtn != null) openScheduleBtn.setOnAction(e -> requestDay());

        // Auto refresh
        autoRefresh = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(10), e -> requestDay()));
        autoRefresh.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoRefresh.play();


    }



    private void addActionColumn() {
        colAction.setCellFactory(col -> new TableCell<>() {
            private final Button readyBtn   = new Button("Ready for pick-up");
            private final Button completeBtn= new Button(); // text set per row
            private final HBox box = new HBox(8, readyBtn, completeBtn);

            {
                readyBtn.setStyle("-fx-background-color:#27ae60; -fx-text-fill:white; -fx-background-radius:8;");
                completeBtn.setStyle("-fx-background-color:#2ecc71; -fx-text-fill:white; -fx-background-radius:8;");

                readyBtn.setOnAction(e -> {
                    var dto = getTableView().getItems().get(getIndex());
                    if (dto == null) return;
                    if (!pending.add(dto.getId())) return;
                    readyBtn.setDisable(true);
                    markReady(dto);
                });
                completeBtn.setOnAction(e -> {
                    var dto = getTableView().getItems().get(getIndex());
                    if (dto == null) return;
                    if (!pending.add(dto.getId())) return;
                    completeBtn.setDisable(true);
                    markCompleted(dto);
                });
            }

            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }

                var dto = getTableView().getItems().get(getIndex());
                if (dto == null) { setGraphic(null); return; }

                String status = java.util.Objects.toString(dto.getStatus(), "PLACED");
                boolean isPickup = !dto.isDelivery();
                boolean isPending = pending.contains(dto.getId());   // <— NEW

                // set labels
                completeBtn.setText(isPickup ? "Picked up" : "Delivered");

                boolean canReady             = isPickup && !"READY_FOR_PICKUP".equalsIgnoreCase(status) && !"PICKED_UP".equalsIgnoreCase(status);
                boolean canCompletePickup    = isPickup && "READY_FOR_PICKUP".equalsIgnoreCase(status);
                boolean canCompleteDelivery  = !isPickup && !"DELIVERED".equalsIgnoreCase(status);

                // disable while pending OR when not allowed
                readyBtn.setDisable(isPending || !canReady);
                completeBtn.setDisable(isPending || !(isPickup ? canCompletePickup : canCompleteDelivery));

                setGraphic(box);
            }});
    }

    private void setStatus(String s) { System.out.println("[CLI][schedule] " + s); }

    private void requestDay() {
        try {
            // open or re-open socket
            if (!SimpleClient.getClient().isConnected()) {
                System.out.println("[CLI][schedule] opening connection…");
                SimpleClient.getClient().openConnection();
            }

            // Build the tiniest serializable payload possible: a plain String "yyyy-MM-dd"
            String dateStr = (dayPicker != null && dayPicker.getValue() != null)
                    ? dayPicker.getValue().toString()
                    : LocalDate.now().toString();

            System.out.println("[CLI][schedule] SENDING key=request_orders_by_day obj=\"" + dateStr + "\" list=null");
            // IMPORTANT: objectList must be null to avoid serializing non-serializable stuff by mistake
            SimpleClient.getClient().sendToServer(new Message("request_orders_by_day", dateStr, null));

            setStatus("Loading schedule…");
        } catch (Exception e) {
            System.out.println("[CLI][schedule] send failed: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(); // this prints the exact cause to your client console
            setStatus("Failed to open schedule");
        }
    }


    private void updateFilter() {
        String f = filterChoice.getValue();
        if (f == null || "All".equals(f)) {
            filteredData.setPredicate(dto -> true);
        } else {
            boolean wantDelivery = "Delivery".equals(f);
            filteredData.setPredicate(dto -> dto.isDelivery() == wantDelivery);
        }
    }

    @FXML private Button openScheduleBtn;

    /** Try to reflect server ACK in the table immediately (no roundtrip). */
    private boolean applyAckToRow(Object payload) {
        if (!(payload instanceof il.cshaifasweng.OCSFMediatorExample.entities.Order o)) return false;
        Long id = o.getId();
        String newStatus = o.getStatus();
        boolean isDelivery = Boolean.TRUE.equals(o.getDelivery());

        for (int i = 0; i < masterData.size(); i++) {
            var dto = masterData.get(i);
            if (java.util.Objects.equals(dto.getId(), id)) {
                masterData.set(i, new ScheduleOrderDTO(
                        id, dto.getCustomerName(), isDelivery, dto.getScheduledAt(), dto.getWhereText(), newStatus
                ));
                pending.remove(id);      // <— NEW
                table.refresh();
                return true;
            }
        }
        pending.remove(id);              // ensure it’s cleared even if row not found
        return false;
    }



    private void markReady(ScheduleOrderDTO dto) {
        if (dto == null || inFlight.contains(dto.getId())) return;
        inFlight.add(dto.getId());
        try {
            if (!SimpleClient.getClient().isConnected()) SimpleClient.getClient().openConnection();
            Map<String,Object> p = Map.of("orderId", dto.getId());
            SimpleClient.getClient().sendToServer(new Message("mark_order_ready", null, List.of(p)));
            status("Marking order #" + dto.getId() + " ready…");
        } catch (IOException e) {
            inFlight.remove(dto.getId());
            status("Failed to mark ready.");
        }
    }


    private void markCompleted(ScheduleOrderDTO dto) {
        if (dto == null || inFlight.contains(dto.getId())) return;
        inFlight.add(dto.getId());
        try {
            if (!SimpleClient.getClient().isConnected()) SimpleClient.getClient().openConnection();
            Map<String,Object> p = Map.of("orderId", dto.getId());
            SimpleClient.getClient().sendToServer(new Message("mark_order_completed", null, List.of(p)));
            status((dto.isDelivery() ? "Marking delivered #" : "Marking picked up #") + dto.getId() + "…");
        } catch (IOException e) {
            inFlight.remove(dto.getId());
            status("Failed to update order.");
        }
    }


    @Subscribe
    public void onServer(Message msg) {
        switch (msg.getMessage()) {
            case "orders_for_day" -> Platform.runLater(() -> {
                @SuppressWarnings("unchecked")
                var list = (java.util.List<ScheduleOrderDTO>) msg.getObject();
                masterData.setAll(list == null ? java.util.List.of() : list);
                if (noOrdersLabel != null) {
                    boolean empty = masterData.isEmpty();
                    noOrdersLabel.setVisible(empty);
                    noOrdersLabel.setManaged(empty);
                }
                status("Loaded " + masterData.size() + " orders.");
                renderCards();
            });


            case "order_ready_ack" -> javafx.application.Platform.runLater(() -> {
                Long id = null;
                String newStatus = "READY_FOR_PICKUP";

                Object o = msg.getObject();
                if (o instanceof il.cshaifasweng.OCSFMediatorExample.entities.Order ord) {
                    id = ord.getId();
                    newStatus = java.util.Objects.toString(ord.getStatus(), newStatus);
                } else if (o instanceof Number n) {
                    id = n.longValue();
                }

                if (id != null) {
                    final Long idF = id;
                    final String stF = newStatus;
                    masterData.stream().filter(d -> java.util.Objects.equals(d.getId(), idF)).findFirst()
                            .ifPresent(d -> { d.setStatus(stF); table.refresh(); });
                    inFlight.remove(idF);
                    pending.remove(idF);
                }
                status("Order marked ready.");
                renderCards();
            });

            case "order_completed_ack" -> javafx.application.Platform.runLater(() -> {
                Long id = (msg.getObject() instanceof Number n) ? n.longValue() : null;
                if (id != null) {
                    final Long idF = id;
                    masterData.stream().filter(d -> java.util.Objects.equals(d.getId(), idF)).findFirst()
                            .ifPresent(d -> {
                                d.setStatus(d.isDelivery() ? "DELIVERED" : "PICKED_UP");
                                table.refresh();
                            });
                    inFlight.remove(idF);
                    pending.remove(idF);
                }
                status("Order completed.");
                renderCards();
            });

            // LIVE push from server when someone else changes a status
            case "orders_state_changed" -> Platform.runLater(() -> {
                var m = (java.util.Map<?,?>) msg.getObject();
                Long id  = (m.get("orderId") instanceof Number) ? ((Number)m.get("orderId")).longValue() : null;
                String st = java.util.Objects.toString(m.get("status"), null);
                if (id != null && st != null) {
                    final Long idF = id; final String stF = st; // effectively final for the lambda
                    masterData.stream().filter(d -> java.util.Objects.equals(d.getId(), idF)).findFirst()
                            .ifPresent(d -> { d.setStatus(stF); table.refresh(); });
                    pending.remove(idF);
                }
                renderCards();
            });
        }
    }

    private javafx.scene.layout.VBox buildCard(ScheduleOrderDTO dto) {
        var box = new javafx.scene.layout.VBox(10);
        box.setPadding(new javafx.geometry.Insets(15));
        box.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e1e8ed; -fx-border-width: 1; -fx-border-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.08), 5, 0, 0, 2);");

        var title = new javafx.scene.control.Label("Order #" + dto.getId() + " · " + (dto.isDelivery() ? "Delivery" : "Pickup"));
        title.setStyle("-fx-font-weight:bold; -fx-font-size:18px; -fx-text-fill:#2c3e50;");
        var when  = new javafx.scene.control.Label("Scheduled: " + (dto.getScheduledAt()==null ? "-" : dtFmt.format(dto.getScheduledAt())));
        when.setStyle("-fx-text-fill:#34495e;");
        var where = new javafx.scene.control.Label((dto.isDelivery() ? "Address: " : "Branch: ") + java.util.Objects.toString(dto.getWhereText(), "-"));
        where.setStyle("-fx-text-fill:#34495e;");
        var status = new javafx.scene.control.Label("Status: " + java.util.Objects.toString(dto.getStatus(),"PLACED"));
        status.setStyle("-fx-text-fill:#7f8c8d;");

        // items
        var itemsWrap = new javafx.scene.layout.VBox(6);
        itemsWrap.setStyle("-fx-background-color:#f8f9fa; -fx-background-radius:8; -fx-padding:10;");
        var itemsHdr = new javafx.scene.control.Label("Items:");
        itemsHdr.setStyle("-fx-font-weight:bold; -fx-text-fill:#2c3e50;");
        itemsWrap.getChildren().add(itemsHdr);

        if (dto.getItems()!=null) {
            for (ScheduleItemDTO it : dto.getItems()) {
                var row = new javafx.scene.layout.HBox(10);
                row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                var img = new javafx.scene.image.ImageView();
                img.setFitWidth(60); img.setFitHeight(60); img.setPreserveRatio(true);
                try { if (it.getImagePath()!=null) img.setImage(new javafx.scene.image.Image(it.getImagePath(), true)); } catch (Exception ignored) {}
                var name = new javafx.scene.control.Label(it.getName() + "  × " + it.getQuantity());
                name.setStyle("-fx-font-weight:bold; -fx-text-fill:#2c3e50;");
                row.getChildren().addAll(img, name);
                itemsWrap.getChildren().add(row);
            }
        }

        // actions (same logic as table buttons)
        var actions = new javafx.scene.layout.HBox(8);
        actions.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);

        var readyBtn = new javafx.scene.control.Button("Ready for pick-up");
        readyBtn.setStyle("-fx-background-color:#27ae60; -fx-text-fill:white; -fx-background-radius:8;");
        var completeBtn = new javafx.scene.control.Button(dto.isDelivery() ? "Delivered" : "Picked up");
        completeBtn.setStyle("-fx-background-color:#2ecc71; -fx-text-fill:white; -fx-background-radius:8;");

        String st = java.util.Objects.toString(dto.getStatus(), "PLACED");
        boolean isPickup = !dto.isDelivery();
        boolean canReady = isPickup && !st.equalsIgnoreCase("READY_FOR_PICKUP") && !st.equalsIgnoreCase("PICKED_UP");
        boolean canCompletePickup = isPickup && st.equalsIgnoreCase("READY_FOR_PICKUP");
        boolean canCompleteDelivery = !isPickup && !st.equalsIgnoreCase("DELIVERED");

        readyBtn.setDisable(!canReady || inFlight.contains(dto.getId()));
        completeBtn.setDisable(!(isPickup ? canCompletePickup : canCompleteDelivery) || inFlight.contains(dto.getId()));

        readyBtn.setOnAction(e -> { readyBtn.setDisable(true); markReady(dto); });
        completeBtn.setOnAction(e -> { completeBtn.setDisable(true); markCompleted(dto); });

        actions.getChildren().addAll(readyBtn, completeBtn);

        box.getChildren().addAll(title, when, where, status, itemsWrap, actions);
        return box;
    }


    private void status(String s) { if (statusLabel != null) statusLabel.setText(s);  System.out.println("[CLI][schedule] " + s);}

    // If your FXML has onAction="#prevDay"/"#nextDay"/"#refresh"
    @FXML private void prevDay() { dayPicker.setValue(dayPicker.getValue().minusDays(1)); }
    @FXML private void nextDay() { dayPicker.setValue(dayPicker.getValue().plusDays(1)); }
    @FXML private void refresh() { requestDay(); }
    @FXML private javafx.scene.control.ListView<javafx.scene.layout.VBox> cardList;

    private void renderCards() {
        Platform.runLater(() -> {
            cardList.getItems().clear();
            for (ScheduleOrderDTO dto : masterData) {
                cardList.getItems().add(buildCard(dto));
            }
            if (noOrdersLabel != null) {
                boolean empty = masterData.isEmpty();
                noOrdersLabel.setVisible(empty);
                noOrdersLabel.setManaged(empty);
            }
        });
    }


    @FXML private void handleBack() {
        try {
            if (autoRefresh != null) autoRefresh.stop();
            org.greenrobot.eventbus.EventBus.getDefault().unregister(this);
            App.setRoot("primary");
        } catch (Exception e) { status("Failed to return."); }
    }


}
