package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Catalog;
import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import il.cshaifasweng.OCSFMediatorExample.entities.PriceUpdate;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SecondaryController {

    private static Catalog currentCatalog;

    private final HashMap<Integer, TextField> priceFields = new HashMap<>();

    public static void setCatalog(Catalog catalog) {
        currentCatalog = catalog;
    }

    @FXML
    private VBox priceUpdateContainer;

    @FXML
    private Button submitButton;

    @FXML
    public void initialize() {
        EventBus.getDefault().register(this);

        if (currentCatalog != null) {
            for (Flower flower : currentCatalog.getFlowers()) {
                Label label = new Label(flower.getName() + " (" + flower.getType() + ")");
                TextField priceField = new TextField(String.valueOf(flower.getPrice()));
                priceField.setPrefWidth(100);

                priceFields.put(flower.getId(), priceField);

                HBox row = new HBox(10, label, priceField);
                row.setStyle("-fx-padding: 5;");
                priceUpdateContainer.getChildren().add(row);
            }
        }

        submitButton.setOnAction(event -> {
            List<PriceUpdate> updates = new ArrayList<>();

            for (Flower flower : currentCatalog.getFlowers()) {
                TextField priceField = priceFields.get(flower.getId());
                try {
                    double newPrice = Double.parseDouble(priceField.getText());
                    if (newPrice != flower.getPrice()) {
                        Flower updated = new Flower(flower.getName(), flower.getType(), newPrice, null);
                        updated.setPrice(newPrice);
                        updated.setId(flower.getId());  // Set ID for reference
                        updates.add(new PriceUpdate(updated));
                    }
                } catch (NumberFormatException e) {
                    showError("Invalid price for " + flower.getName());
                    return;
                }
            }

            try {
                SimpleClient.getClient().sendToServer(updates);
            } catch (IOException e) {
                showError("Failed to send updates to server.");
                return;
            }

            // Go back to main screen after submitting
            Main.switchToPrimaryView();
        });
    }

    private void showError(String msg) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Input Error");
            alert.setHeaderText(null);
            alert.setContentText(msg);
            alert.showAndWait();
        });
    }
}
