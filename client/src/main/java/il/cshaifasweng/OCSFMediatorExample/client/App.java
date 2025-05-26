package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private SimpleClient client;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) throws IOException {
        primaryStage = stage;
        EventBus.getDefault().register(this);

        boolean connected = false;
        while (!connected) {
            TextInputDialog dialog = new TextInputDialog("127.0.0.1");
            dialog.setTitle("Connect to Server");
            dialog.setHeaderText("Enter Server IP Address");
            dialog.setContentText("IP:");

            Optional<String> result = dialog.showAndWait();
            if (result.isPresent()) {
                String ip = result.get().trim();
                try {
                    client = SimpleClient.getClient(ip, 3000); // use provided IP
                    client.openConnection();
                    connected = true;
                } catch (IOException e) {
                    showErrorDialog("Connection Failed", "Could not connect to " + ip + ":3000");
                    // Reset client to null to allow another attempt
                    resetClient();
                }
            } else {
                Platform.exit();
                return;
            }
        }

        try {
            switchToPrimaryView();
            primaryStage.show();
        } catch (IOException e) {
            showErrorDialog("Error", "Could not load the main view.");
            Platform.exit();
        }
    }
    private void showErrorDialog(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void resetClient() {
        try {
            java.lang.reflect.Field clientField = SimpleClient.class.getDeclaredField("client");
            clientField.setAccessible(true);
            clientField.set(null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void switchToPrimaryView() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("primary.fxml"));
        Parent root = loader.load();
        primaryStage.setTitle("Flower Catalog");
        primaryStage.setScene(new Scene(root));
    }

    public static void switchToSecondaryView() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("secondary.fxml"));
        Parent root = loader.load();

        SecondaryController controller = loader.getController();
        controller.loadFlowerDetails();

        primaryStage.setScene(new Scene(root));
    }

    public static void switchToViewFlowerView() throws IOException {
        FXMLLoader loader = new FXMLLoader(App.class.getResource("viewFlower.fxml"));
        Parent root = loader.load();

        ViewFlowerController controller = loader.getController();
        controller.loadFlowerDetails();

        primaryStage.setScene(new Scene(root));
    }

    @Override
    public void stop() throws Exception {
        EventBus.getDefault().unregister(this);
        client.sendToServer("remove client");
        client.closeConnection();
        super.stop();
    }

    @Subscribe
    public void onMessage(Object message) {
        // Add handling logic if needed
    }

    @Subscribe
    public void onWarningEvent(WarningEvent event) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.WARNING,
                    String.format("Message: %s\nTimestamp: %s\n",
                            event.getWarning().getMessage(),
                            event.getWarning().getTime().toString())
            );
            alert.show();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
