package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Product;
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

    private static Product selectedProduct;

    public static void setSelectedFlower(Product product) {
        selectedProduct = product;
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

                selectedProduct.setPrice(newPrice);

                App.setRoot("primary"); // return to catalog

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
        if (selectedProduct != null) {
            nameLabel.setText("Name: " + selectedProduct.getName());
            typeLabel.setText("Type: " + selectedProduct.getType());
            priceLabel.setText(String.format("Current Price: $%.2f", selectedProduct.getPrice()));
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

