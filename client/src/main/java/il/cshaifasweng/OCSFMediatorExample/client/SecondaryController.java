package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Flower;
import il.cshaifasweng.OCSFMediatorExample.entities.PriceUpdate;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class SecondaryController implements Initializable {

    @FXML
    private Label nameLabel;

    @FXML
    private Label typeLabel;

    @FXML
    private Label priceLabel;

    @FXML
    private TextField newPriceField;

    @FXML
    private Button confirmButton;

    private static Flower selectedFlower;

    public static void setSelectedFlower(Flower flower) {
        selectedFlower = flower;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // confirmButton setup is fine here
        confirmButton.setOnAction(event -> {
            try {
                String input = newPriceField.getText();
                if (input == null || input.isEmpty()) {
                    showAlert("Please enter a price.");
                    return;
                }

                double newPrice = Double.parseDouble(input);
                if (newPrice < 0) {
                    showAlert("Price must be positive.");
                    return;
                }

                selectedFlower.setPrice(newPrice);
                PriceUpdate update = new PriceUpdate(selectedFlower);

                SimpleClient.getClient().sendToServer(update);

                Main.switchToPrimaryView(); // return to catalog

            } catch (NumberFormatException e) {
                showAlert("Invalid price format.");
            } catch (IOException e) {
                e.printStackTrace();
                showAlert("Error communicating with server.");
            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Unexpected error.");
            }
        });
    }

    public void loadFlowerDetails() {
        if (selectedFlower != null) {
            nameLabel.setText("Name: " + selectedFlower.getName());
            typeLabel.setText("Type: " + selectedFlower.getType());
            priceLabel.setText(String.format("Current Price: $%.2f", selectedFlower.getPrice()));
        }
    }

    private void showAlert(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}

