package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class RegisterControl implements Initializable {

    // FXML field annotations for all form elements
    @FXML
    private TextField nameField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

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
    private TextField creditNumberField;

    @FXML
    private Button registerButton;

    @FXML
    private Button cancelButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventBus.getDefault().register(this);
        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Subscribe
    public void onMessageReceived(Object msg) throws IOException {
        if (msg instanceof Message) {
            Message message = (Message) msg;
            if ("registered".equals(message.getMessage())) {
                javafx.application.Platform.runLater(() -> {
                    try {
                        App.switchToLogIn();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }
    // Handle Register button click
    @FXML
    private void handleRegister(ActionEvent event) {
        if (validateFields()) {
            try {
                // Create new Customer object with form data
                Customer newCustomer = new Customer(
                        nameField.getText().trim(),
                        usernameField.getText().trim(),
                        passwordField.getText(),
                        false, // isSigned - set to false initially
                        false, // isSubbed - set to false initially
                        creditNumberField.getText().trim(),
                        emailField.getText().trim(),
                        phoneField.getText().trim(),
                        addressField.getText().trim(),
                        cityField.getText().trim(),
                        countryField.getText().trim()
                );

                // TODO: Add your logic here to save the customer
                // For example:
                // - Send to server via client communication
                // - Save to database
                // - Add to customer list
                Message msg = new Message("register",newCustomer,null);
                SimpleClient.getClient().sendToServer(msg);

                // Show success message
                showAlert(Alert.AlertType.INFORMATION, "Registration Successful",
                        "Customer registered successfully!");

                // Clear form after successful registration
                clearForm();

                // Optionally close the registration window
                // closeWindow();

            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Registration Error",
                        "An error occurred during registration: " + e.getMessage());
            }
        }
    }

    // Handle Cancel button click
    @FXML
    private void handleCancel(ActionEvent event) throws IOException {
        // Clear all fields
        clearForm();
        App.switchToLogIn();
        // Optionally close the window
        // closeWindow();
    }

    // Validate all required fields
    private boolean validateFields() {
        StringBuilder errorMessage = new StringBuilder();

        // Check required fields
        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            errorMessage.append("Name is required.\n");
        }

        if (usernameField.getText() == null || usernameField.getText().trim().isEmpty()) {
            errorMessage.append("Username is required.\n");
        }

        if (passwordField.getText() == null || passwordField.getText().trim().isEmpty()) {
            errorMessage.append("Password is required.\n");
        }

        if (emailField.getText() == null || emailField.getText().trim().isEmpty()) {
            errorMessage.append("Email is required.\n");
        } else if (!isValidEmail(emailField.getText().trim())) {
            errorMessage.append("Please enter a valid email address.\n");
        }

        if (phoneField.getText() == null || phoneField.getText().trim().isEmpty()) {
            errorMessage.append("Phone is required.\n");
        }

        if (addressField.getText() == null || addressField.getText().trim().isEmpty()) {
            errorMessage.append("Address is required.\n");
        }

        if (cityField.getText() == null || cityField.getText().trim().isEmpty()) {
            errorMessage.append("City is required.\n");
        }

        if (countryField.getText() == null || countryField.getText().trim().isEmpty()) {
            errorMessage.append("Country is required.\n");
        }

        if (creditNumberField.getText() == null || creditNumberField.getText().trim().isEmpty()) {
            errorMessage.append("Credit card number is required.\n");
        } else if (!isValidCreditCard(creditNumberField.getText().trim())) {
            errorMessage.append("Please enter a valid credit card number.\n");
        }

        // Show validation errors if any
        if (errorMessage.length() > 0) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", errorMessage.toString());
            return false;
        }

        return true;
    }

    // Simple email validation
    private boolean isValidEmail(String email) {
        return email.contains("@") && email.contains(".") && email.length() > 5;
    }

    // Simple credit card validation (basic length check)
    private boolean isValidCreditCard(String creditCard) {
        // Remove spaces and check if it's numeric and has appropriate length
        String cleanCard = creditCard.replaceAll("\\s+", "");
        return cleanCard.matches("\\d{13,19}"); // Most credit cards are 13-19 digits
    }

    // Clear all form fields
    private void clearForm() {
        nameField.clear();
        usernameField.clear();
        passwordField.clear();
        emailField.clear();
        phoneField.clear();
        addressField.clear();
        cityField.clear();
        countryField.clear();
        creditNumberField.clear();
    }

    // Show alert dialog
    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Close the current window
    private void closeWindow() {
        Stage stage = (Stage) registerButton.getScene().getWindow();
        stage.close();
    }
}