package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Year;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class RegisterControl implements Initializable {

    // Existing FXML fields
    @FXML private TextField nameField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private TextField addressField;
    @FXML private TextField cityField;
    @FXML private TextField countryField;
    @FXML private Button registerButton;
    @FXML private Button cancelButton;

    // ADDED: New FXML fields
    @FXML private ComboBox<String> accountTypeComboBox;
    @FXML private Label accountTypeDescriptionLabel;
    @FXML private TextField creditNumberField;
    @FXML private ComboBox<Integer> expirationMonthComboBox;
    @FXML private ComboBox<Integer> expirationYearComboBox;
    @FXML private PasswordField cvvField;


    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Setup for new fields
        populateAccountTypes();
        populateExpirationDate();
        setupAccountTypeListener();
    }

    private void populateAccountTypes() {
        accountTypeComboBox.getItems().addAll("Regular Account", "Premium Subscription");
    }

    private void populateExpirationDate() {
        // Populate months 1-12
        expirationMonthComboBox.getItems().addAll(IntStream.rangeClosed(1, 12).boxed().toArray(Integer[]::new));
        // Populate years from current year to 15 years in the future
        int currentYear = Year.now().getValue();
        expirationYearComboBox.getItems().addAll(IntStream.rangeClosed(currentYear, currentYear + 15).boxed().toArray(Integer[]::new));
    }

    private void setupAccountTypeListener() {
        accountTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                switch (newVal) {
                    case "Regular Account":
                        accountTypeDescriptionLabel.setText("A free account with access to our full catalog of products.");
                        break;
                    case "Premium Subscription":
                        accountTypeDescriptionLabel.setText("A yearly subscription with a 10% discount on all purchases!");
                        break;
                    default:
                        accountTypeDescriptionLabel.setText("Please select an account type.");
                        break;
                }
            }
        });
    }

    @Subscribe
    public void onMessageReceived(Message msg) {
        Platform.runLater(() -> {
            if ("registered".equals(msg.getMessage())) {
                showAlert(Alert.AlertType.INFORMATION, "Registration Successful", "Customer registered successfully!");
                try {
                    EventBus.getDefault().unregister(this);
                    App.setRoot("logInView");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if ("user already exists".equals(msg.getMessage())) {
                showAlert(Alert.AlertType.ERROR, "Registration Failed", "A user with this username already exists.");
            }
        });
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        LocalDate date = LocalDate.now();
        if (validateFields()) {
            try {
                // Determine if user is subscribed based on ComboBox selection
                boolean isSubscribed = "Premium Subscription".equals(accountTypeComboBox.getValue());
                boolean isNetworkAccount = "Network Account".equals(accountTypeComboBox.getValue());

                // Create new Customer object with all form data
                Customer newCustomer = new Customer(
                        nameField.getText().trim(),
                        usernameField.getText().trim(),
                        passwordField.getText(),
                        isNetworkAccount,
                        isSubscribed,
                        date,
                        date.plusYears(1),
                        emailField.getText().trim(),
                        phoneField.getText().trim(),
                        addressField.getText().trim(),
                        cityField.getText().trim(),
                        countryField.getText().trim(),
                        creditNumberField.getText().trim(),
                        expirationMonthComboBox.getValue(),
                        expirationYearComboBox.getValue(),
                        cvvField.getText().trim(),null
                );

                Message msg = new Message("register", newCustomer, null);
                SimpleClient.getClient().sendToServer(msg);

            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Registration Error",
                        "An error occurred: " + e.getMessage()));
            }
        }
    }

    @FXML
    private void handleCancel(ActionEvent event) throws IOException {
        clearForm();
        EventBus.getDefault().unregister(this);
        App.setRoot("logInView");
    }

    private boolean validateFields() {
        StringBuilder errorMessage = new StringBuilder();

        if (nameField.getText() == null || nameField.getText().trim().isEmpty()) {
            errorMessage.append("Name is required.\n");
        }
        if (usernameField.getText() == null || usernameField.getText().trim().isEmpty()) {
            errorMessage.append("Username is required.\n");
        }
        if (passwordField.getText() == null || passwordField.getText().isEmpty()) {
            errorMessage.append("Password is required.\n");
        }
        if (accountTypeComboBox.getValue() == null) {
            errorMessage.append("Please select an account type.\n");
        }
        if (emailField.getText() == null || !isValidEmail(emailField.getText().trim())) {
            errorMessage.append("A valid email is required.\n");
        }
        if (phoneField.getText() == null || phoneField.getText().trim().isEmpty()) {
            errorMessage.append("Phone number is required.\n");
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
        if (creditNumberField.getText() == null || !isValidCreditCard(creditNumberField.getText().trim())) {
            errorMessage.append("A valid 13-19 digit credit card number is required.\n");
        }
        if (expirationMonthComboBox.getValue() == null || expirationYearComboBox.getValue() == null) {
            errorMessage.append("Credit card expiration date is required.\n");
        }
        if (cvvField.getText() == null || !cvvField.getText().trim().matches("\\d{3,4}")) {
            errorMessage.append("A valid 3 or 4 digit CVV is required.\n");
        }

        if (errorMessage.length() > 0) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", errorMessage.toString());
            return false;
        }
        return true;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }

    private boolean isValidCreditCard(String creditCard) {
        String cleanCard = creditCard.replaceAll("\\s+", "");
        return cleanCard.matches("\\d{13,19}");
    }

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
        cvvField.clear();
        accountTypeComboBox.getSelectionModel().clearSelection();
        expirationMonthComboBox.getSelectionModel().clearSelection();
        expirationYearComboBox.getSelectionModel().clearSelection();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}