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

public class InboxController implements Initializable {

    @FXML private TabPane tabPane;
    @FXML private ListView<InboxItemDTO> personalList;
    @FXML private ListView<InboxItemDTO> broadcastList;

    @FXML private Label statusLabel;

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!EventBus.getDefault().isRegistered(this)) EventBus.getDefault().register(this);

        // cell renderers
        personalList.setCellFactory(v -> new NotificationCell(true));
        broadcastList.setCellFactory(v -> new NotificationCell(false));


        // fetch inbox on load
        var u = SessionManager.getInstance().getCurrentUser();
        if (!(u instanceof Customer)) {
            status("Login as a customer to view inbox.");
            return;
        }
        Map<String,Object> payload = Map.of("customerId", ((Customer) u).getId());
        try {
            SimpleClient.getClient().sendToServer(new Message("get_inbox", null, List.of(payload)));
            status("Loading inbox…");
        } catch (IOException e) {
            status("Failed to request inbox.");
        }
    }

    @Subscribe
    public void onServer(Message msg) {
        if ("inbox_list".equals(msg.getMessage())) {
            Platform.runLater(() -> {
                InboxListDTO payload = (InboxListDTO) msg.getObject();
                var personal = payload.getPersonal();
                var broadcast = payload.getBroadcast();

                personalList.getItems().setAll(personal);
                broadcastList.getItems().setAll(broadcast);
                status(String.format("Personal: %d (%d unread) • Broadcast: %d",
                        personal.size(),
                        (int) personal.stream().filter(n -> !n.isRead()).count(),
                        broadcast.size()));
            });
        } else if ("inbox_read_ack".equals(msg.getMessage())) {
            var u = SessionManager.getInstance().getCurrentUser();
            if (u instanceof Customer) {
                Map<String,Object> payload = Map.of("customerId", ((Customer) u).getId());
                try {
                    SimpleClient.getClient().sendToServer(new Message("get_inbox", null, java.util.List.of(payload)));
                } catch (IOException ignored) {}
            }
        } else if ("inbox_list_error".equals(msg.getMessage())) {
            Platform.runLater(() -> status("Error: " + java.util.Objects.toString(msg.getObject(), "unknown")));
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

    private void status(String s) { if (statusLabel != null) statusLabel.setText(s); }

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

    private class NotificationCell extends ListCell<InboxItemDTO> {
        private final boolean showUnreadBadge;

        NotificationCell(boolean showUnreadBadge) {
            this.showUnreadBadge = showUnreadBadge;
        }

        @Override
        protected void updateItem(InboxItemDTO n, boolean empty) {
            super.updateItem(n, empty);
            if (empty || n == null) {
                setText(null);
            } else {
                String badge = (showUnreadBadge && !n.isRead()) ? " • UNREAD" : "";
                String created = (n.getCreatedAt() == null) ? "" : n.getCreatedAt().format(fmt);
                setText(String.format("%s%s\n%s\n%s",
                        created, badge,
                        n.getTitle(),
                        java.util.Objects.toString(n.getBody(), "")));
            }
        }
    }}


