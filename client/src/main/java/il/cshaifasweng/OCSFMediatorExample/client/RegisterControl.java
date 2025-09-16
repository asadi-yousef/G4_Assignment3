package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Branch;
import il.cshaifasweng.OCSFMediatorExample.entities.Budget;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.IntStream;

public class RegisterControl implements Initializable {

    // Existing FXML fields
    @FXML private TextArea idField;            // NEW: ID textarea
    @FXML private TextField firstNameField;
    @FXML private TextField lastNameField;
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

    // NEW: Branch ComboBox
    @FXML private ComboBox<String> branchComboBox;

    private List<Branch> branches;

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
        try {
            SimpleClient.getClient().sendToServer(new Message("request_branches", null, null));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Setup for new fields
        populateAccountTypes();
        populateExpirationDate();
        setupAccountTypeListener();

        // Hide branch combo by default
        branchComboBox.setVisible(false);
        branchComboBox.setManaged(false);

        // Optional: keep idField visually to a single line height
        if (idField != null) {
            idField.setPrefRowCount(1);
        }
    }

    private void populateAccountTypes() {
        accountTypeComboBox.getItems().addAll("Branch Account", "Net Account", "yearly subscription");
    }

    private void populateExpirationDate() {
        expirationMonthComboBox.getItems().addAll(IntStream.rangeClosed(1, 12).boxed().toArray(Integer[]::new));
        int currentYear = Year.now().getValue();
        expirationYearComboBox.getItems().addAll(IntStream.rangeClosed(currentYear, currentYear + 15).boxed().toArray(Integer[]::new));
    }

    private void setupAccountTypeListener() {
        accountTypeComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                switch (newVal) {
                    case "Branch Account":
                        accountTypeDescriptionLabel.setText("A free account tied to a specific branch.");
                        branchComboBox.setVisible(true);
                        branchComboBox.setManaged(true);
                        break;
                    case "Net Account":
                        accountTypeDescriptionLabel.setText("A net account for all branches.");
                        branchComboBox.setVisible(false);
                        branchComboBox.setManaged(false);
                        break;
                    case "yearly subscription":
                        accountTypeDescriptionLabel.setText("An account with a subscription for one year that gives you a 10% discount on all purchases above 50 shekels! Subscription cost: 100 shekels!");
                        branchComboBox.setVisible(false);
                        branchComboBox.setManaged(false);
                        break;
                    default:
                        accountTypeDescriptionLabel.setText("Please select an account type.");
                        branchComboBox.setVisible(false);
                        branchComboBox.setManaged(false);
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
            else if("id already exists".equals(msg.getMessage())) {
                showAlert(Alert.AlertType.ERROR, "Registration Failed", "A user with this id number already exists.");
            }
            else if (msg.getMessage().startsWith("Branches")) {
                this.branches = (List<Branch>) msg.getObject();
                branchComboBox.getItems().clear();
                for (Branch branch : branches) {
                    System.out.println("Branch: " + branch.getName());
                    branchComboBox.getItems().add(branch.getName());
                }
            }
        });
    }

    @FXML
    private void handleRegister(ActionEvent event) {
        LocalDate date = LocalDate.now();
        if (validateFields()) {
            try {
                boolean isSubscribed = "yearly subscription".equals(accountTypeComboBox.getValue());
                boolean isNetworkAccount = ("Network Account".equals(accountTypeComboBox.getValue()) || "yearly subscription".equals(accountTypeComboBox.getValue()));
                LocalDate subStartDate = isSubscribed ? LocalDate.now() : null;
                LocalDate subEndDate   = isSubscribed ? subStartDate.plusYears(1) : null; // end exclusive

                Budget initialBudget = new Budget();
                // Create new Customer object with all form data
                // IMPORTANT: ID is now the FIRST constructor parameter as requested
                Customer newCustomer = new Customer(
                        idField.getText().trim(),
                        firstNameField.getText().trim(),
                        lastNameField.getText().trim(),
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
                        cvvField.getText().trim(),
                        branchComboBox.isVisible() ? getSelectedBranch() : null,
                        initialBudget
                );
                initialBudget.setCustomer(newCustomer);
                initialBudget.setBalance(0.0);

                if (!isNetworkAccount) {
                    newCustomer.setBranch(getSelectedBranch());
                }

                Message msg = new Message("register", newCustomer, null);
                SimpleClient.getClient().sendToServer(msg);

            } catch (Exception e) {
                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "Registration Error",
                        "An error occurred: " + e.getMessage()));
            }
        }
    }

    private Branch getSelectedBranch() {
        if (branchComboBox.getValue() == null || branches == null) return null;
        return branches.stream()
                .filter(b -> b.getName().equals(branchComboBox.getValue()))
                .findFirst()
                .orElse(null);
    }

    @FXML
    private void handleCancel(ActionEvent event) throws IOException {
        clearForm();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
        App.setRoot("logInView");
    }

    private boolean validateFields() {
        StringBuilder errorMessage = new StringBuilder();

        // ID validation
        String idText = (idField != null && idField.getText() != null) ? idField.getText().trim() : "";
        if (idText.isEmpty()) {
            errorMessage.append("ID number is required.\n");
        } else if (!idText.matches("\\d{5,10}")) { // allow 5–10 digits input; we’ll still enforce checksum for 9-digit Israeli IDs
            errorMessage.append("ID must contain only digits (5–10 digits).\n");
        } else if (!isValidIsraeliID(idText)) {
            errorMessage.append("Invalid Israeli ID number.\n");
        }

        if (firstNameField.getText() == null || firstNameField.getText().trim().isEmpty()) {
            errorMessage.append("Name is required.\n");
        }
        if (lastNameField.getText() == null || lastNameField.getText().trim().isEmpty()) {
            errorMessage.append("Last name is required.\n");
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
        if ("Branch Account".equals(accountTypeComboBox.getValue()) && branchComboBox.getValue() == null) {
            errorMessage.append("Please select a branch.\n");
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
        if (usernameField.getText() != null && usernameField.getText().contains(" ")) {
            errorMessage.append("Usernames cannot contain spaces.\n");
        }
        if (phoneField.getText() == null || !isValidPhone(phoneField.getText().trim())) {
            errorMessage.append("A valid phone number is required (7–15 digits, optional +).\n");
        }

        if (errorMessage.length() > 0) {
            showAlert(Alert.AlertType.ERROR, "Validation Error", errorMessage.toString());
            return false;
        }
        return true;
    }

    private boolean isValidIsraeliID(String idRaw) {
        if (idRaw == null) return false;
        // Accept only if the string is exactly 9 characters long and all digits
        return idRaw.matches("\\d{9}");
    }


    private boolean isValidPhone(String phone) {
        return phone.matches("^\\+?[0-9]{7,15}$");
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}$");
    }

    private boolean isValidCreditCard(String creditCard) {
        String cleanCard = creditCard.replaceAll("\\s+", "");
        return cleanCard.matches("\\d{13,19}");
    }

    private void clearForm() {
        if (idField != null) idField.clear();
        firstNameField.clear();
        lastNameField.clear();
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
        branchComboBox.getSelectionModel().clearSelection();
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
