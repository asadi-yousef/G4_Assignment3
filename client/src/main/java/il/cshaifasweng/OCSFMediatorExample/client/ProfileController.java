package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.CreditCard;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class ProfileController implements Initializable {

    @FXML
    private Label welcomeLabel;
    @FXML
    private TextField nameField;
    @FXML
    private TextField usernameField;
    @FXML
    private TextField emailField;
    @FXML
    private TextField phoneField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField cityField;
    @FXML
    private TextField countryField;
    @FXML
    private TextField creditCardField;
    @FXML
    private TextField passwordField;
    @FXML
    private Button editButton;
    @FXML
    private Button saveButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button backButton;

    private User currentUser;
    private Customer currentCustomer;
    private boolean isEditing = false;

    private volatile boolean disposed = false;

    private final String TAG = "ProfileController@" + Integer.toHexString(System.identityHashCode(this));
    private static String ts() { return java.time.LocalTime.now().toString(); }
    private static String th() { return Thread.currentThread().getName(); }
    private void D(String msg)  { System.out.println(ts()+" [D]["+th()+"] "+TAG+" :: "+msg); }
    private void W(String msg)  { System.err.println(ts()+" [W]["+th()+"] "+TAG+" :: "+msg); }
    private void E(String msg, Throwable t) {
        System.err.println(ts()+" [E]["+th()+"] "+TAG+" :: "+msg+" -> "+t);
        if (t != null) t.printStackTrace();
    }

    private void safeUnregister() {
        boolean was = EventBus.getDefault().isRegistered(this);
        D("safeUnregister() called. wasRegistered="+was);
        if (was) {
            try {
                EventBus.getDefault().unregister(this);
                D("EventBus.unregister() OK");
            } catch (Throwable t) {
                E("EventBus.unregister FAILED", t);
            }
        }
    }



    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        disposed = false;
        D("initialize: disposed=false, url="+url);

        boolean needRegister = !EventBus.getDefault().isRegistered(this);
        D("EventBus registered? "+!needRegister);
        if (needRegister) {
            EventBus.getDefault().register(this);
            D("EventBus.register() OK");
        }

        try {
            currentUser = SessionManager.getInstance().getCurrentUser();
            D("Session user: "+(currentUser==null ? "null" : (currentUser.getClass().getSimpleName()+"/id?")));

            if (currentUser == null) {
                showAlert("Error", "No user logged in.");
                W("initialize aborted: no currentUser");
                return;
            }

            Message message = new Message("request_customer_data", currentUser, null);
            D("sendToServer: request_customer_data for user="+currentUser.getUsername());
            SimpleClient.getClient().sendToServer(message);

        } catch (Exception e) {
            E("initialize failed", e);
            showAlert("Error", "Failed to load profile.");
        }
    }

    private void loadUserProfile() {
        // If we truly have no model yet, skip.
        if (currentUser == null && currentCustomer == null) {
            D("loadUserProfile: no user/customer model yet");
            return;
        }

        final Customer c = currentCustomer;        // may be null
        final User u = (currentUser != null) ? currentUser : currentCustomer; // fallback

        D("loadUserProfile: username=" + (u != null ? u.getUsername() : "null")
                + " ; hasCustomer=" + (c != null));

        Platform.runLater(() -> {
            // Prefer Customer fields if present (covers server-refreshed snapshot after save)
            String firstName =
                    (c != null && c.getFirstName() != null && !c.getFirstName().isBlank())
                            ? c.getFirstName()
                            : (u != null ? u.getFirstName() : null);

            String username =
                    (c != null && c.getUsername() != null && !c.getUsername().isBlank())
                            ? c.getUsername()
                            : (u != null ? u.getUsername() : null);

            // Header + core identity
            welcomeLabel.setText("Welcome, " + (firstName != null && !firstName.isBlank() ? firstName : "User") + "!");
            nameField.setText(nvl(firstName));
            usernameField.setText(nvl(username));
            passwordField.setText("••••••••"); // we never echo real password

            // Customer-specific fields
            if (c != null) {
                emailField.setText(nvl(c.getEmail()));
                phoneField.setText(nvl(c.getPhone()));
                addressField.setText(nvl(c.getAddress()));
                cityField.setText(nvl(c.getCity()));
                countryField.setText(nvl(c.getCountry()));

                String masked = "";
                if (c.getCreditCard() != null
                        && c.getCreditCard().getCardNumber() != null
                        && !c.getCreditCard().getCardNumber().isBlank()) {
                    masked = maskCreditCard(c.getCreditCard().getCardNumber());
                }
                creditCardField.setText(masked);
                D("loadUserProfile: filled customer fields; maskedCC.len=" + masked.length());
            } else {
                // If we don't have a Customer yet, clear customer-only fields
                emailField.setText("");
                phoneField.setText("");
                addressField.setText("");
                cityField.setText("");
                countryField.setText("");
                creditCardField.setText("");
                D("loadUserProfile: no customer data -> cleared customer fields");
            }

            // Keep current editability/visibility aligned with state
            setFieldsEditable(isEditing);
            editButton.setVisible(!isEditing);
            saveButton.setVisible(isEditing);
            cancelButton.setVisible(isEditing);

            // Ensure Save/Cancel are clickable when we're in edit mode
            if (isEditing) {
                saveButton.setDisable(false);
                cancelButton.setDisable(false);
            }
        });
    }

    private static String nvl(String s) { return s==null?"":s; }

    private String maskCreditCard(String creditCard) {
        if (creditCard == null || creditCard.length() < 4) {
            return "****";
        }
        return "**** **** **** " + creditCard.substring(creditCard.length() - 4);
    }

    @FXML
    public void handleEdit(ActionEvent event) {
        isEditing = true;
        setFieldsEditable(true);
        editButton.setVisible(false);
        saveButton.setVisible(true);
        cancelButton.setVisible(true);
        saveButton.setDisable(false);
        cancelButton.setDisable(false);
    }

    @FXML
    public void handleSave(ActionEvent event) {
        try {
            // Lock UI during save
            setFieldsEditable(false);
            saveButton.setDisable(true);
            cancelButton.setDisable(true);

            // Read + normalize inputs
            String name     = trimOrNull(nameField.getText());
            String username = trimOrNull(usernameField.getText());
            String pwd      = passwordField.getText();
            // If someone still sets "••••••••" into the field, ignore it:
            if ("••••••••".equals(pwd)) pwd = "";
            pwd = trimOrNull(pwd);

            String email   = trimOrNull(emailField.getText());
            String phone   = trimOrNull(phoneField.getText());
            String address = trimOrNull(addressField.getText());
            String city    = trimOrNull(cityField.getText());
            String country = trimOrNull(countryField.getText());
            String ccInput = trimOrNull(creditCardField.getText());

            // Light validation
            if (email != null && !email.matches("^[^@\\n]+@[^@\\n]+\\.[^@\\n]+$")) {
                showAlert("Invalid email", "Please enter a valid email address.");
                restoreUiAfterSaveAttempt();
                return;
            }
            if (phone != null && !phone.matches("[+]?\\d{7,15}")) {
                showAlert("Invalid phone", "Use digits only (7–15), with optional leading +.");
                restoreUiAfterSaveAttempt();
                return;
            }

            // Decide whether to update CC: ignore masked, accept only digits length 12–19
            String newCardDigits = null;
            if (ccInput != null && !ccInput.startsWith("****")) {
                String digits = ccInput.replaceAll("\\D", "");
                if (digits.length() >= 12 && digits.length() <= 19) {
                    newCardDigits = digits;
                } else {
                    showAlert("Invalid card", "Card number length looks wrong.");
                    restoreUiAfterSaveAttempt();
                    return;
                }
            }

            // Build and send the payload
            if (currentUser instanceof Customer) {
                Customer c = (Customer) currentUser;

                if (name != null)     c.setFirstName(name);
                if (username != null) c.setUsername(username);
                if (pwd != null)      c.setPassword(pwd);

                // Keep your behavior: blanks allowed (clear value)
                c.setEmail(emptyToNull(email));
                c.setPhone(emptyToNull(phone));
                c.setAddress(emptyToNull(address));
                c.setCity(emptyToNull(city));
                c.setCountry(emptyToNull(country));

                if (newCardDigits != null) {
                    if (c.getCreditCard() == null) c.setCreditCard(new CreditCard());
                    c.getCreditCard().setCardNumber(newCardDigits);
                }

                SimpleClient.getClient().sendToServer(new Message("update_profile", c, null));

            } else {
                // Update the User part
                if (name != null)     currentUser.setFirstName(name);
                if (username != null) currentUser.setUsername(username);
                if (pwd != null)      currentUser.setPassword(pwd);

                // If you also have a Customer instance, send it (so customer details persist)
                if (currentCustomer != null) {
                    currentCustomer.setEmail(emptyToNull(email));
                    currentCustomer.setPhone(emptyToNull(phone));
                    currentCustomer.setAddress(emptyToNull(address));
                    currentCustomer.setCity(emptyToNull(city));
                    currentCustomer.setCountry(emptyToNull(country));

                    if (newCardDigits != null) {
                        if (currentCustomer.getCreditCard() == null) currentCustomer.setCreditCard(new CreditCard());
                        currentCustomer.getCreditCard().setCardNumber(newCardDigits);
                    }
                    SimpleClient.getClient().sendToServer(new Message("update_profile", currentCustomer, null));
                } else {
                    // No customer object — send just the User
                    SimpleClient.getClient().sendToServer(new Message("update_profile", currentUser, null));
                }
            }

        } catch (Exception e) {
            E("handleSave failed", e);
            showAlert("Error", "Failed to save changes: " + e.getMessage());
            restoreUiAfterSaveAttempt();
        }
    }
    @FXML
    public void handleCancel(ActionEvent event) {
        isEditing = false;
        loadUserProfile(); // Reset fields to original values
        setFieldsEditable(false);
        editButton.setVisible(true);
        saveButton.setVisible(false);
        cancelButton.setVisible(false);
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        D("handleBackToCatalog START (will dispose & unregister)");
        try {
            disposed = true;
            safeUnregister();

            // Try both relative and absolute to catch nulls in logs
            URL rel = getClass().getResource("primary.fxml");
            URL abs = ProfileController.class.getResource("/il/cshaifasweng/OCSFMediatorExample/client/primary.fxml");
            D("FXML lookup: relative="+rel+" | absolute="+abs);

            URL chosen = (abs != null) ? abs : rel;
            if (chosen == null) {
                E("BOTH FXML lookups returned null (primary.fxml not found!)", null);
                showAlert("Error", "Could not resolve primary.fxml (check resource path).");
                return;
            }

            FXMLLoader loader = new FXMLLoader(chosen);
            D("FXMLLoader(location="+chosen+") -> load()");
            Parent root = loader.load();

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            D("Stage.setScene(new Scene(root))");
            stage.setScene(new Scene(root));
            stage.show();
            D("handleBackToCatalog DONE");

        } catch (IOException e) {
            E("Failed to load catalog view", e);
            showAlert("Error", "Failed to load catalog view: " + e.getMessage());
        } catch (Exception e) {
            E("Unexpected error", e);
            showAlert("Error", "Unexpected error: " + e.getMessage());
        }
    }

    @Subscribe
    public void onMessageFromServer(Message msg) {
        boolean nodeAttached = (welcomeLabel != null && welcomeLabel.getScene() != null);
        D("onMessageFromServer: "+msg.getMessage()+" | disposed="+disposed+" | nodeAttached="+nodeAttached);

        if (disposed) { D("  -> IGNORED (disposed)"); return; }
        if (!nodeAttached) { D("  -> IGNORED (node not attached)"); return; }

        switch (msg.getMessage()) {
            case "customer_data_response":
                try {
                    Customer payload = (Customer) msg.getObject();
                    Platform.runLater(() -> {
                        this.currentCustomer = payload;       // assign model
                        loadUserProfile();                    // fills the fields
                        isEditing = false;                    // your state flag
                        setFieldsEditable(false);             // <-- now on FX thread
                        editButton.setVisible(true);
                        saveButton.setVisible(false);
                        cancelButton.setVisible(false);
                    });
                } catch (Throwable t) { E("customer_data_response handling failed", t); }
                break;

            case "profile_updated_success":
                Platform.runLater(() -> {
                    D("profile_updated_success: updating UI (has payload? " + (msg.getObject()!=null) + ")");
                    Customer updated = null;
                    try { updated = (Customer) msg.getObject(); } catch (ClassCastException ignored) {}

                    if (updated != null) {
                        // Replace local models with the server snapshot
                        this.currentCustomer = updated;
                        if (this.currentUser != null) {
                            this.currentUser.setFirstName(updated.getFirstName());
                            this.currentUser.setUsername(updated.getUsername());
                        }
                    } else {
                        try {
                            SimpleClient.getClient().sendToServer(new Message("request_customer_data", currentUser, null));
                        } catch (java.io.IOException ex) {
                            D("IOException while requesting fresh customer data: " + ex);
                            showAlert("Connection error", "Couldn't refresh your profile:\n" + ex.getMessage());
                            // if this ran inside a save flow, make sure the UI isn't stuck:
                            saveButton.setDisable(false);
                            cancelButton.setDisable(false);
                        }

                    }

                    showAlert("Success", "Profile updated successfully!");
                    isEditing = false;
                    setFieldsEditable(false);
                    editButton.setVisible(true);
                    saveButton.setVisible(false);
                    cancelButton.setVisible(false);

                    // Make sure next edit starts enabled
                    saveButton.setDisable(false);
                    cancelButton.setDisable(false);

                    loadUserProfile();
                });
                break;

            case "profile_update_failed":
                Platform.runLater(() -> {
                    String reason = (msg.getObject() instanceof String)
                            ? (String) msg.getObject()
                            : "Unknown error";
                    showAlert("Update failed", "Failed to update profile: " + reason);

                    // Stay in edit mode so the user can fix and retry
                    isEditing = true;
                    setFieldsEditable(true);

                    editButton.setVisible(false);
                    saveButton.setVisible(true);
                    cancelButton.setVisible(true);

                    // IMPORTANT: re-enable them (you disabled in handleSave)
                    saveButton.setDisable(false);
                    cancelButton.setDisable(false);

                    saveButton.setDisable(false);
                    cancelButton.setDisable(false);
                    loadUserProfile();
                });
                break;

        }
    }

    private void setFieldsEditable(boolean editable) {
        nameField.setEditable(editable);
        usernameField.setEditable(editable);
        emailField.setEditable(editable);
        phoneField.setEditable(editable);
        addressField.setEditable(editable);
        cityField.setEditable(editable);
        countryField.setEditable(editable);
        creditCardField.setEditable(editable);
        passwordField.setEditable(editable);
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
    private String trimOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
    private void restoreUiAfterSaveAttempt() {
        setFieldsEditable(true);
        saveButton.setDisable(false);
        cancelButton.setDisable(false);
    }
}