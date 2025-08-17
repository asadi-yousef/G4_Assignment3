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

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        try {
            currentUser = SessionManager.getInstance().getCurrentUser();
            if (currentUser == null) {
                showAlert("Error", "No user logged in.");
                return;
            }

            // Request customer data from server - send user as the main object
            Message message = new Message("request_customer_data", currentUser, null);
            SimpleClient.getClient().sendToServer(message);

        } catch (Exception e) {
            showAlert("Error", "Failed to load profile.");
        }
    }

    private void loadUserProfile() {
        if (currentUser != null) {
            Platform.runLater(() -> {
                // Load User data
                welcomeLabel.setText("Welcome, " + (currentUser.getName() != null ? currentUser.getName() : "User") + "!");
                nameField.setText(currentUser.getName() != null ? currentUser.getName() : "");
                usernameField.setText(currentUser.getUsername() != null ? currentUser.getUsername() : "");
                passwordField.setText("••••••••"); // Always masked

                // Load Customer data if available
                if (currentCustomer != null) {
                    emailField.setText(currentCustomer.getEmail() != null ? currentCustomer.getEmail() : "");
                    phoneField.setText(currentCustomer.getPhone() != null ? currentCustomer.getPhone() : "");
                    addressField.setText(currentCustomer.getAddress() != null ? currentCustomer.getAddress() : "");
                    cityField.setText(currentCustomer.getCity() != null ? currentCustomer.getCity() : "");
                    countryField.setText(currentCustomer.getCountry() != null ? currentCustomer.getCountry() : "");
                    creditCardField.setText(maskCreditCard(currentCustomer.getCreditNumber() != null ? currentCustomer.getCreditNumber() : ""));
                } else {
                    // Clear customer fields if no customer data
                    emailField.setText("");
                    phoneField.setText("");
                    addressField.setText("");
                    cityField.setText("");
                    countryField.setText("");
                    creditCardField.setText("");
                }
            });
        }
    }

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
                    customerUser.setName(nameField.getText().trim());
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
                    customerUser.setCreditNumber(creditCardField.getText().trim());
                }

                // Send the updated customer (which is also a user)
                Message message = new Message("update_profile", customerUser, null);
                SimpleClient.getClient().sendToServer(message);

            } else {
                // Current user is just a User, not a Customer
                // Update User data
                if (!nameField.getText().trim().isEmpty()) {
                    currentUser.setName(nameField.getText().trim());
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
                        currentCustomer.setCreditNumber(creditCardField.getText().trim());
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
        try {
            System.out.println("Attempting to load primaryView.fxml..."); // Debug line

            FXMLLoader loader = new FXMLLoader(getClass().getResource("primary.fxml"));

            if (loader.getLocation() == null) {
                System.out.println("Could not find primary.fxml, trying different paths...");
                // Try without the leading slash
                loader = new FXMLLoader(getClass().getResource("primary.fxml"));
            }

            Parent root = loader.load();
            System.out.println("Successfully loaded FXML");

            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.show();

            EventBus.getDefault().unregister(this);
            System.out.println("Navigation completed successfully");

        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "Failed to load catalog view: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "Unexpected error occurred: " + e.getMessage());
        }
    }

    @Subscribe
    public void onMessageFromServer(Message msg) {
        if (msg.getMessage().equals("customer_data_response")) {
            this.currentCustomer = (Customer) msg.getObject();
            loadUserProfile();
            setFieldsEditable(false);
        } else if (msg.getMessage().equals("profile_updated_success")) {
            Platform.runLater(() -> {
                showAlert("Success", "Profile updated successfully!");

                isEditing = false;
                setFieldsEditable(false);
                editButton.setVisible(true);
                saveButton.setVisible(false);
                cancelButton.setVisible(false);

                // Refresh data
                loadUserProfile();
            });
        } else if (msg.getMessage().startsWith("error") || msg.getMessage().equals("profile_update_failed")) {
            Platform.runLater(() -> {
                showAlert("Error", "Failed to update profile: " + msg.getMessage());
            });
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