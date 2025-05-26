package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Optional;

import javafx.util.Pair;
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
            Dialog<Pair<String, String>> dialog = new Dialog<>();
            dialog.setTitle("Connect to Server");
            dialog.setHeaderText("Enter Server IP Address and Port");

            ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, ButtonType.CANCEL);

            GridPane grid = new GridPane();
            grid.setHgap(10);
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            TextField ipField = new TextField("127.0.0.1");
            TextField portField = new TextField("3000");

            grid.add(new Label("IP Address:"), 0, 0);
            grid.add(ipField, 1, 0);
            grid.add(new Label("Port:"), 0, 1);
            grid.add(portField, 1, 1);

            dialog.getDialogPane().setContent(grid);

            dialog.setResultConverter(dialogButton -> {
                if (dialogButton == connectButtonType) {
                    return new Pair<>(ipField.getText(), portField.getText());
                }
                return null;
            });

            Optional<Pair<String, String>> result = dialog.showAndWait();

            if (result.isEmpty()) {
                Platform.exit();
                return;
            }

            String ip = result.get().getKey().trim();
            String portInput = result.get().getValue().trim();

            try {
                int port = Integer.parseInt(portInput);
                if (port < 1 || port > 65535) throw new NumberFormatException();

                client = SimpleClient.getClient(ip, port);
                client.openConnection();
                connected = true;
            } catch (NumberFormatException e) {
                showErrorDialog("Invalid Port", "Please enter a valid port number (1-65535).");
            } catch (IOException e) {
                showErrorDialog("Connection Failed", "Could not connect to " + ip + ":" + portInput);
                resetClient();
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
            Alert alert = new Alert(Alert.AlertType.WARNING,
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
