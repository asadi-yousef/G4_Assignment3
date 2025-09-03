package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.prefs.Preferences;

public class ConnectionControl {

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private Button connectButton;
    @FXML private CheckBox rememberCheck;
    @FXML private Label statusLabel;

    private static final String PREF_NODE = "ConnectionControlPrefs";
    private static final String KEY_HOST = "host";
    private static final String KEY_PORT = "port";
    private final Preferences prefs = Preferences.userRoot().node(PREF_NODE);
    private SimpleClient client;

    @FXML
    private void initialize() {
        // Load remembered values (if any)
        String savedHost = prefs.get(KEY_HOST, "");
        String savedPort = prefs.get(KEY_PORT, "");
        if (!savedHost.isEmpty()) hostField.setText(savedHost);
        if (!savedPort.isEmpty()) portField.setText(savedPort);
        rememberCheck.setSelected(!savedHost.isEmpty() || !savedPort.isEmpty());

        // Validate on edit
        hostField.textProperty().addListener((obs, oldV, newV) -> validate());
        portField.textProperty().addListener((obs, oldV, newV) -> validate());

        // Initial validation state
        validate();
    }

    @FXML
    private void handleConnect() {
        statusLabel.setText("");
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        if (!isValidHost(host)) {
            statusLabel.setText("Please enter a valid host or IPv4 address.");
            return;
        }

        Integer port = parsePort(portText);
        if (port == null) {
            statusLabel.setText("Port must be a number between 1 and 65535.");
            return;
        }

        // Persist if requested
        if (rememberCheck.isSelected()) {
            prefs.put(KEY_HOST, host);
            prefs.put(KEY_PORT, String.valueOf(port));
        } else {
            prefs.remove(KEY_HOST);
            prefs.remove(KEY_PORT);
        }

        // Disable UI while "connecting"
        setUiEnabled(false);
        statusLabel.setText("Connecting...");

        try {
            this.client = SimpleClient.getClient(host,port);
            this.client.openConnection();
            App.setClient(this.client);
            boolean ok = SimpleClient.getClient().isConnected();

            if (ok) {
                statusLabel.setText("");
                 App.setRoot("primary");
            } else {
                statusLabel.setText("Failed to connect. Please verify host/port and server status.");
            }
        } catch (Exception e) {
            statusLabel.setText("Connection error: " + e.getMessage());
        } finally {
            setUiEnabled(true);
        }
    }

    // --- Helpers ---

    private void validate() {
        String host = hostField.getText().trim();
        String portText = portField.getText().trim();

        boolean hostOk = isValidHost(host);
        boolean portOk = parsePort(portText) != null;

        connectButton.setDisable(!(hostOk && portOk));

        // Show lightweight inline hints (no CSS)
        if (!host.isEmpty() && !hostOk) {
            statusLabel.setText("Invalid host/IP format.");
        } else if (!portText.isEmpty() && !portOk) {
            statusLabel.setText("Port must be 3000-9000.");
        } else {
            // Clear only if we were showing validation messages
            if ("Invalid host/IP format.".equals(statusLabel.getText()) ||
                    "Port must be 3000-9000.".equals(statusLabel.getText())) {
                statusLabel.setText("");
            }
        }
    }

    private boolean isValidHost(String host) {
        if (host.isEmpty()) return false;

        // Accept hostnames (basic rule) or IPv4
        String hostnameRegex = "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$";
        String ipv4Regex =
                "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}" +
                        "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$";

        return host.matches(hostnameRegex) || host.matches(ipv4Regex) || "localhost".equalsIgnoreCase(host);
    }

    private Integer parsePort(String s) {
        try {
            int p = Integer.parseInt(s);
            return (p >= 3000 && p <= 9000) ? p : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void setUiEnabled(boolean enabled) {
        hostField.setDisable(!enabled);
        portField.setDisable(!enabled);
        rememberCheck.setDisable(!enabled);
        connectButton.setDisable(!enabled || !isValidHost(hostField.getText().trim()) || parsePort(portField.getText().trim()) == null);
    }

    /**
     * Stub for real connection. Replace with your socket/OCSF client call.
     * Return true on success, false on failure.
     */
    private boolean performConnection(String host, int port) throws Exception {
        // Example:
        // ClientManager.getInstance().connect(host, port);
        // return ClientManager.getInstance().isConnected();
        return false; // <-- Replace with real implementation
    }
}
