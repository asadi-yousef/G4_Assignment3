package il.cshaifasweng.OCSFMediatorExample.client;

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
        if (currentUser == null) { D("loadUserProfile: currentUser=null"); return; }
        D("loadUserProfile: currentUser="+currentUser.getUsername()+" ; hasCustomer="+(currentCustomer!=null));

        Platform.runLater(() -> {
            welcomeLabel.setText("Welcome, " + (currentUser.getFirstName() != null ? currentUser.getFirstName() : "User") + "!");
            nameField.setText(nvl(currentUser.getFirstName()));
            usernameField.setText(nvl(currentUser.getUsername()));
            passwordField.setText("••••••••");

            if (currentCustomer != null) {
                emailField.setText(nvl(currentCustomer.getEmail()));
                phoneField.setText(nvl(currentCustomer.getPhone()));
                addressField.setText(nvl(currentCustomer.getAddress()));
                cityField.setText(nvl(currentCustomer.getCity()));
                countryField.setText(nvl(currentCustomer.getCountry()));

                String masked = "";
                if (currentCustomer.getCreditCard() != null &&
                        currentCustomer.getCreditCard().getCardNumber() != null &&
                        !currentCustomer.getCreditCard().getCardNumber().isBlank()) {
                    masked = maskCreditCard(currentCustomer.getCreditCard().getCardNumber());
                }
                creditCardField.setText(masked);
                D("loadUserProfile: filled customer fields; maskedCC.len="+masked.length());
            } else {
                emailField.setText(""); phoneField.setText(""); addressField.setText("");
                cityField.setText("");  countryField.setText(""); creditCardField.setText("");
                D("loadUserProfile: no customer data -> cleared fields");
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
    }

    @FXML
    public void handleSave(ActionEvent event) {
        try {
            // Since Customer extends User, if currentUser is a Customer,
            // we can directly update it. Otherwise, we work with separate entities.

            if (currentUser instanceof Customer) {
                // Current user is already a Customer, update it directly
                Customer customerUser = (Customer) currentUser;

                // Update User fields
                if (!nameField.getText().trim().isEmpty()) {
                    customerUser.setFirstName(nameField.getText().trim());
                }
                if (!usernameField.getText().trim().isEmpty()) {
                    customerUser.setUsername(usernameField.getText().trim());
                }
                if (!passwordField.getText().equals("••••••••") && !passwordField.getText().trim().isEmpty()) {
                    customerUser.setPassword(passwordField.getText().trim());
                }

                // Update Customer fields
                customerUser.setEmail(emailField.getText().trim());
                customerUser.setPhone(phoneField.getText().trim());
                customerUser.setAddress(addressField.getText().trim());
                customerUser.setCity(cityField.getText().trim());
                customerUser.setCountry(countryField.getText().trim());

                if (!creditCardField.getText().startsWith("****") && !creditCardField.getText().trim().isEmpty()) {
                    customerUser.getCreditCard().setCardNumber(creditCardField.getText().trim());
                }

                // Send the updated customer (which is also a user)
                Message message = new Message("update_profile", customerUser, null);
                SimpleClient.getClient().sendToServer(message);

            } else {
                // Current user is just a User, not a Customer
                // Update User data
                if (!nameField.getText().trim().isEmpty()) {
                    currentUser.setFirstName(nameField.getText().trim());
                }
                if (!usernameField.getText().trim().isEmpty()) {
                    currentUser.setUsername(usernameField.getText().trim());
                }
                if (!passwordField.getText().equals("••••••••") && !passwordField.getText().trim().isEmpty()) {
                    currentUser.setPassword(passwordField.getText().trim());
                }

                // Update Customer data if customer exists
                if (currentCustomer != null) {
                    currentCustomer.setEmail(emailField.getText().trim());
                    currentCustomer.setPhone(phoneField.getText().trim());
                    currentCustomer.setAddress(addressField.getText().trim());
                    currentCustomer.setCity(cityField.getText().trim());
                    currentCustomer.setCountry(countryField.getText().trim());

                    if (!creditCardField.getText().startsWith("****") && !creditCardField.getText().trim().isEmpty()) {
                        currentCustomer.getCreditCard().setCardNumber(creditCardField.getText().trim());
                    }

                    // Send customer as main object since it has all the data
                    Message message = new Message("update_profile", currentCustomer, null);
                    SimpleClient.getClient().sendToServer(message);
                } else {
                    // Just send user data
                    Message message = new Message("update_profile", currentUser, null);
                    SimpleClient.getClient().sendToServer(message);
                }
            }

        } catch (Exception e) {
            showAlert("Error", "Failed to save changes: " + e.getMessage());
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
                    this.currentCustomer = (Customer) msg.getObject();
                    D("customer_data_response: currentCustomer="+(currentCustomer==null?"null":"ok"));
                    loadUserProfile();
                    setFieldsEditable(false);
                } catch (Throwable t) { E("customer_data_response handling failed", t); }
                break;

            case "profile_updated_success":
                Platform.runLater(() -> {
                    D("profile_updated_success: updating UI");
                    showAlert("Success", "Profile updated successfully!");
                    isEditing = false;
                    setFieldsEditable(false);
                    editButton.setVisible(true);
                    saveButton.setVisible(false);
                    cancelButton.setVisible(false);
                    loadUserProfile();
                });
                break;

            default:
                if (msg.getMessage().startsWith("error") || "profile_update_failed".equals(msg.getMessage())) {
                    D("profile update error: "+msg.getMessage());
                    Platform.runLater(() -> showAlert("Error", "Failed to update profile: " + msg.getMessage()));
                } else {
                    D("UNHANDLED msg: "+msg.getMessage());
                }
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
}