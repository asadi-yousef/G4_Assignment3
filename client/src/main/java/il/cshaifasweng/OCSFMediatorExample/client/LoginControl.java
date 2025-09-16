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
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        loadToggleImages();
        setupPasswordToggle();
    }

    private void safeUnregister() {
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
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

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            showError("Username and password cannot be empty.");
            return;
        }

        setLoading(true);

        Task<Void> loginTask = new Task<>() {
            @Override protected Void call() throws Exception {
                if (!SimpleClient.getClient().isConnected()) {
                    SimpleClient.getClient().openConnection(); // ideally done once app-wide
                }
                List<String> info = new ArrayList<>(2);
                info.add(username);
                info.add(password);
                SimpleClient.getClient().sendToServer(new Message("check existence", info, null));
                return null;
            }
        };
        loginTask.setOnFailed(e -> {
            setLoading(false);
            showError("Connection to server failed. Please try again later.");
        });
        new Thread(loginTask).start(); // short-lived background send
    }


    @Subscribe
    public void onMessage(Message message) {
        // Only react to the login flow messages
        String key = message.getMessage();
        if (!"correct".equals(key) && !"incorrect".equals(key)
                && !"already_logged".equals(key) && !"frozen".equals(key)) {
            return; // ignore unrelated traffic
        }

        Platform.runLater(() -> {
            setLoading(false);

            switch (key) {
                case "correct" -> {
                    try {
                        User user = (User) message.getObject();
                        SessionManager.getInstance().setCurrentUser(user);

                        // Decide target view ONCE
                        String target = "primary";
                        if (user instanceof Employee emp) {
                            String role = emp.getRole();
                            if (role != null) {
                                String r = role.trim().toLowerCase(Locale.ROOT);
                                if (r.equals("customerservice"))      target = "complaintsList";
                                else if (r.equals("systemadmin"))     target = "AdminUsersView";
                                else if (r.equals("driver"))          target = "employeeScheduleView";
                            }
                        }

                        // Unregister before navigation to avoid duplicate listeners
                        safeUnregister();
                        App.setRoot(target);
                    } catch (IOException e) {
                        showError("Failed to load the main page.");
                        e.printStackTrace();
                    }
                }
                case "incorrect" -> showError("Invalid username or password.");
                case "already_logged" -> showError("User already logged from another computer.");
                case "frozen" -> showError("This account is frozen, contact the administrator.");
            }
        });
    }


    @FXML
    void handleRegister(ActionEvent event) throws IOException {
        safeUnregister();
        App.setRoot("registerView");
    }

    @FXML
    public void handleBack(ActionEvent actionEvent) {
        safeUnregister();
        try { App.setRoot("primary"); }
        catch (Exception e) { throw new RuntimeException(e); }
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
}