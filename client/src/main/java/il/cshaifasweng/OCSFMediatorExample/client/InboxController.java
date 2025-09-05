package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import il.cshaifasweng.OCSFMediatorExample.entities.InboxItemDTO;
import il.cshaifasweng.OCSFMediatorExample.entities.InboxListDTO;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.collections.ObservableSet;

import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListCell;

import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;


public class InboxController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private ListView<InboxItemDTO> personalList;
    @FXML private ListView<InboxItemDTO> broadcastList;
    @FXML private Label statusLabel;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final javafx.collections.ObservableSet<Long> selectedIds =
            javafx.collections.FXCollections.observableSet(); // NEW


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        personalList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        broadcastList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        personalList.setCellFactory(v -> new GmailCell(true));
        broadcastList.setCellFactory(v -> new GmailCell(false));

        // initialize() is called on the FX Application Thread, so this is safe
        handleRefresh();
    }


    @Subscribe
    public void onServer(Message msg) {
        switch (msg.getMessage()) {
            case "inbox_list" -> Platform.runLater(() -> {
                InboxListDTO payload = (InboxListDTO) msg.getObject();
                var personal = payload.getPersonal();
                var broadcast = payload.getBroadcast();

                personalList.getItems().setAll(personal);
                broadcastList.getItems().setAll(broadcast);

                status(String.format("Personal: %d (%d unread) • Announcements: %d",
                        personal.size(),
                        (int) personal.stream().filter(n -> !n.isRead()).count(),
                        broadcast.size()));
            });

            // ACKs arrive on the client/network thread. Hop to FX before touching UI.
            case "inbox_read_ack", "inbox_unread_ack" -> Platform.runLater(this::handleRefresh);

            case "inbox_list_error" -> Platform.runLater(() ->
                    status("Error: " + java.util.Objects.toString(msg.getObject(), "unknown")));
        }
    }


    @FXML
    private void handleMarkSelectedRead() {
        InboxItemDTO n = personalList.getSelectionModel().getSelectedItem();
        if (n == null) { status("Select a personal notification first."); return; }
        if (n.isBroadcast()) { status("Broadcast notifications don’t have a read flag."); return; }
        try {
            SimpleClient.getClient().sendToServer(new Message("mark_notification_read", null, java.util.List.of(n.getId())));
            status("Marked as read.");
        } catch (IOException e) { status("Failed to mark as read."); }
    }


    @FXML
    private void handleBack() {
        try {
            EventBus.getDefault().unregister(this);
            App.setRoot("primary");
        } catch (Exception e) {
            status("Failed to go back.");
        }
    }

    @FXML private void handleRefresh() {
        var u = SessionManager.getInstance().getCurrentUser();
        if (!(u instanceof Customer c)) { status("Login as a customer to view inbox."); return; }
        Map<String,Object> payload = Map.of("customerId", c.getId());
        try {
            SimpleClient.getClient().sendToServer(new Message("get_inbox", null, java.util.List.of(payload)));
            status("Loading inbox…");
        } catch (IOException e) { status("Failed to request inbox."); }
    }

    @FXML private void handleMarkRead()   { bulkMark(true); }
    @FXML private void handleMarkUnread() { bulkMark(false); }

    private void bulkMark(boolean markRead) {
        if (selectedIds.isEmpty()) { status("Select messages first."); return; }
        for (Long id : new java.util.ArrayList<>(selectedIds)) {
            try {
                String key = markRead ? "mark_notification_read" : "mark_notification_unread";
                SimpleClient.getClient().sendToServer(new Message(key, null, java.util.List.of(id)));
            } catch (IOException ignored) {}
        }
        status(markRead ? "Marked as read." : "Marked as unread.");
    }

    // Always marshal UI updates onto the FX Application Thread
    private void status(String s) {
        Platform.runLater(() -> { if (statusLabel != null) statusLabel.setText(s); });
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> castMap(Object o) {
        try { return (Map<String,Object>) o; } catch (Exception e) { return Map.of("personal", List.of(), "broadcast", List.of()); }
    }
    @SuppressWarnings("unchecked")
    private java.util.List<il.cshaifasweng.OCSFMediatorExample.entities.InboxItemDTO> castList(Object o) {
        try {
            return (java.util.List<il.cshaifasweng.OCSFMediatorExample.entities.InboxItemDTO>) o;
        } catch (Exception e) {
            return java.util.List.of();
        }
    }

    /** Gmail-like row with checkbox, unread dot, title/snippet, right-aligned date. */
    private class GmailCell extends ListCell<InboxItemDTO> {
        private final boolean allowCheckbox;
        private final CheckBox cb = new CheckBox();
        private final Label title = new Label();
        private final Label snippet = new Label();
        private final Label date = new Label();
        private final HBox dot = new HBox();
        private final HBox root = new HBox(10);

        GmailCell(boolean allowCheckbox) {
            this.allowCheckbox = allowCheckbox;

            title.getStyleClass().add("inbox-title");
            snippet.getStyleClass().add("inbox-snippet");
            date.getStyleClass().add("inbox-date");
            dot.getStyleClass().add("unread-dot");
            dot.setVisible(false);

            VBox textCol = new VBox(title, snippet);
            HBox.setHgrow(textCol, Priority.ALWAYS);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            if (allowCheckbox) root.getChildren().add(cb);
            root.getChildren().addAll(dot, textCol, spacer, date);
            root.getStyleClass().add("inbox-row");

            // ListCell extends Labeled, so this is valid:
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

            cb.selectedProperty().addListener((o, was, now) -> {
                InboxItemDTO n = getItem();
                if (n == null) return;
                if (now) selectedIds.add(n.getId()); else selectedIds.remove(n.getId());
            });
        }

        @Override protected void updateItem(InboxItemDTO n, boolean empty) {
            super.updateItem(n, empty);
            if (empty || n == null) { setGraphic(null); return; }

            title.setText(n.getTitle());
            snippet.setText(Objects.toString(n.getBody(), ""));
            date.setText(n.getCreatedAt() == null ? "" : fmt.format(n.getCreatedAt()));
            cb.setSelected(selectedIds.contains(n.getId()));
            cb.setVisible(allowCheckbox); // hide on announcements list

            dot.setVisible(!n.isRead());
            getStyleClass().removeAll("unread");
            if (!n.isRead()) getStyleClass().add("unread");

            setGraphic(root);
        }
    }
}
