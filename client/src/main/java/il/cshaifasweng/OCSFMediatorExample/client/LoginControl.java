package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import javafx.application.Platform;
import javafx.fxml.FXML;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import javafx.event.ActionEvent;
import java.util.LinkedHashSet;
import java.util.ArrayList;

import javafx.scene.layout.AnchorPane;
import javafx.scene.shape.Rectangle;

public class LoginControl implements Initializable {
    @FXML
    public TextField usernameField;
    public PasswordField passwordField;

    @FXML
    private Label errorLabel;


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

    public void handleLogin(ActionEvent actionEvent) throws IOException {
        SimpleClient.getClient().sendToServer("check existence: " + usernameField.getText() + " " + passwordField.getText());
    }
    @Subscribe
    public void onMessage(Object message) {
        Message tmp = (Message) message;
        String msg = tmp.getMessage();
        if(msg.equals("correct")) {
            User user = (User)(tmp.getObject());
            SessionManager.getInstance().setCurrentUser(user);
            Platform.runLater(() -> {
                try {
                    App.switchToPrimaryView();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        else if(msg.equals("incorrect")) {
            Platform.runLater(() -> {
                errorLabel.setText("incorrect");
                errorLabel.setVisible(true);
            });
        }
    }

    public void handleRegister(ActionEvent actionEvent) throws IOException {
        App.switchToRegister();
    }
}
