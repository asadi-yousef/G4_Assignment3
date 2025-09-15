package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Employee;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.*;

public class LoginControl implements Initializable {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private ToggleButton togglePasswordButton;
    @FXML private Label errorLabel;
    @FXML private Button loginButton;
    @FXML private Button backButton;
    @FXML private ProgressIndicator loadingIndicator;

    private ImageView eyeOpenView;
    private ImageView eyeClosedView;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        EventBus.getDefault().register(this);
        loadToggleImages(); // Load images first
        setupPasswordToggle(); // Then set up the button
    }

    private void loadToggleImages() {
        try {
            // Change the path strings to the full path
            String openPath = "/il/cshaifasweng/OCSFMediatorExample/client/images/eye-open.png";
            String closedPath = "/il/cshaifasweng/OCSFMediatorExample/client/images/eye-closed.png";

            Image eyeOpen = new Image(Objects.requireNonNull(getClass().getResourceAsStream(openPath)));
            eyeOpenView = new ImageView(eyeOpen);
            eyeOpenView.setFitHeight(16);
            eyeOpenView.setFitWidth(16);

            Image eyeClosed = new Image(Objects.requireNonNull(getClass().getResourceAsStream(closedPath)));
            eyeClosedView = new ImageView(eyeClosed);
            eyeClosedView.setFitHeight(16);
            eyeClosedView.setFitWidth(16);

        } catch (Exception e) {
            System.err.println("Error loading toggle images. Make sure the path is correct!");
            e.printStackTrace(); // Print the full error
            togglePasswordButton.setText("S");
        }
    }

    private void setupPasswordToggle() {
        // Bind the visibility of the fields to the toggle button's selected state
        visiblePasswordField.managedProperty().bind(togglePasswordButton.selectedProperty());
        visiblePasswordField.visibleProperty().bind(togglePasswordButton.selectedProperty());
        passwordField.managedProperty().bind(togglePasswordButton.selectedProperty().not());
        passwordField.visibleProperty().bind(togglePasswordButton.selectedProperty().not());
        visiblePasswordField.textProperty().bindBidirectional(passwordField.textProperty());
        togglePasswordButton.setGraphic(eyeOpenView);
        togglePasswordButton.selectedProperty().addListener((observable, oldValue, isSelected) -> {
            if (isSelected) {
                togglePasswordButton.setGraphic(eyeClosedView);
            } else {
                togglePasswordButton.setGraphic(eyeOpenView);
            }
        });
    }

    @FXML
    void handleLogin(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Username and password cannot be empty.");
            return;
        }

        setLoading(true);

        Task<Void> loginTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                if (!SimpleClient.getClient().isConnected()) {
                    SimpleClient.getClient().openConnection();
                }
                List<String> info = new ArrayList<String>();
                info.add(username);
                info.add(password);
                SimpleClient.getClient().sendToServer( new Message("check existence", info , null));
                return null;
            }
        };

        loginTask.setOnFailed(e -> {
            setLoading(false);
            showError("Connection to server failed. Please try again later.");
        });

        new Thread(loginTask).start();
    }

    @Subscribe
    public void onMessage(Message message) {
        Platform.runLater(() -> { // Wrap UI updates in Platform.runLater()
            System.out.println(message.getMessage());
            setLoading(false);
            if ("correct".equals(message.getMessage())) {
                System.out.println("correct");
                try {
                    User user = (User) message.getObject();
                    SessionManager.getInstance().setCurrentUser(user);
                    EventBus.getDefault().unregister(this);
                    App.setRoot("primary");
                    if(user instanceof Employee) {
                        Employee employee = (Employee) user;
                        if(employee.getRole().equals("customerservice")) {
                            EventBus.getDefault().unregister(this);
                            App.setRoot("complaintsList");
                        }
                        else if(employee.getRole().equals("systemadmin")) {
                            EventBus.getDefault().unregister(this);
                            App.setRoot("AdminUsersView");
                        } else if (employee.getRole().equals("driver")) {
                            // Driver uses the *same* schedule screen, but it will lock itself to deliveries
                            EventBus.getDefault().unregister(this);
                            App.setRoot("employeeScheduleView"); }
                        else {
                            EventBus.getDefault().unregister(this);
                            App.setRoot("primary");
                        }
                        }
                } catch (IOException e) {
                    showError("Failed to load the main page.");
                    e.printStackTrace();
                }
            } else if ("incorrect".equals(message.getMessage())) {
                showError("Invalid username or password.");
            }
            else if ("already_logged".equals(message.getMessage())) {
                showError("User already logged from another computer.");
            }
            else if("frozen".equals(message.getMessage())) {
                showError("This account is frozen, contact the administrator.");
            }
        });
    }

    @FXML
    void handleRegister(ActionEvent event) throws IOException {
        EventBus.getDefault().unregister(this);
        App.setRoot("registerView");
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void setLoading(boolean isLoading) {
        loadingIndicator.setVisible(isLoading);
        loginButton.setDisable(isLoading);
        if (isLoading) {
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    @FXML
    public void handleBack(ActionEvent actionEvent) {
        EventBus.getDefault().unregister(this);
        try {
            App.setRoot("primary");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}