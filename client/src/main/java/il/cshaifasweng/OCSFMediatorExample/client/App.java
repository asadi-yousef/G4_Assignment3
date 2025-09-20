package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.Optional;

import javafx.util.Pair;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import javafx.geometry.Rectangle2D;  // Rectangle2D class
import javafx.stage.Screen;
import javafx.stage.Modality;
import il.cshaifasweng.OCSFMediatorExample.entities.Order;


/**
 * JavaFX App
 */
public class App extends Application {

    private static Scene scene;
    private static SimpleClient client;
    private static Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        // ---- Global uncaught exception handlers (show every crash with a clean tag) ----
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            System.err.println(java.time.LocalTime.now() + " [UNCAUGHT][" + t.getName() + "] " + e);
            e.printStackTrace();
        });
        // This runs on the JavaFX Application Thread, so bind a handler here too:
        Thread.currentThread().setUncaughtExceptionHandler((t, e) -> {
            System.err.println(java.time.LocalTime.now() + " [FX-UNCAUGHT][" + t.getName() + "] " + e);
            e.printStackTrace();
        });

        // App-level EventBus listener (you already use it for WarningEvent)
        EventBus.getDefault().register(this);

        // Stage basics
        stage.setTitle("Lilac");
        stage.getIcons().add(
                new javafx.scene.image.Image(
                        App.class.getResourceAsStream("/il/cshaifasweng/OCSFMediatorExample/client/images/Lilac1.png")
                )
        );

        // Optional: size to screen bounds up front (keeps later setRoot swaps smooth)
        javafx.geometry.Rectangle2D bounds = javafx.stage.Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());

        // *** Load the first screen via setRoot(...) so you get the absolute-path resolution + NAV logs ***
        try {
            setRoot("connection");  // prints: [NAV] App.setRoot(connection) -> /.../client/connection.fxml | url=...
        } catch (IOException e) {
            showErrorDialog("Startup Error", "Failed to load 'connection.fxml': " + e.getMessage());
            throw new RuntimeException(e);
        }
    }


    public static void setClient(SimpleClient client) {
        App.client = client;
    }
    // In App class:
    public static void setRoot(String fxml) throws IOException {
        String res = "/il/cshaifasweng/OCSFMediatorExample/client/" + fxml + ".fxml";
        URL u = App.class.getResource(res);
        System.out.println(java.time.LocalTime.now()+" [NAV]["
                +Thread.currentThread().getName()+"] App.setRoot("+fxml+") -> "+res+" | url="+u);
        if (u == null) throw new IOException("FXML not found: " + res);
        FXMLLoader loader = new FXMLLoader(u);
        Scene scene = new Scene(loader.load());
        primaryStage.setScene(scene);
        primaryStage.show();
    }


    private static Parent loadFXML(String fxml) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        return fxmlLoader.load();
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

    @Override
    public void stop() throws Exception {
        try {
            var current = SessionManager.getInstance().getCurrentUser();
            if (current != null && SimpleClient.getClient().isConnected()) {
                String username = current.getUsername();
                try {
                    SimpleClient.getClient().sendToServer(new Message("logout", username, null));
                } catch (Exception ignored) {}

                // best-effort: also ask server to remove this client
                try { SimpleClient.getClient().sendToServer("remove client"); } catch (Exception ignored) {}
            }
        } finally {
            try { EventBus.getDefault().unregister(this); } catch (Exception ignored) {}
            try { if (client != null && client.isConnected()) client.closeConnection(); } catch (Exception ignored) {}
            super.stop();
        }
    }


    public static void openPopup(String fxml, String title) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(App.class.getResource(fxml + ".fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        Stage stage = new Stage();
        stage.setTitle(title);
        stage.setScene(scene);
        stage.initModality(Modality.APPLICATION_MODAL); // blocks until closed
        stage.showAndWait();
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