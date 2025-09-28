package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ViewFlowerController {

    @FXML private BorderPane mainPane; // Assuming the root is a BorderPane
    @FXML private HBox buttonContainer; // A container in your FXML for the buttons

    private static Product selectedProduct;

    public static void setSelectedFlower(Product product) {
        selectedProduct = product;
    }

    @FXML
    public void initialize() {
        if (selectedProduct == null) {
            System.err.println("ViewFlowerController: No product was selected.");
            return;
        }

        try {
            // Load the reusable card component
            FXMLLoader loader = new FXMLLoader(getClass().getResource("productCard.fxml"));
            StackPane cardNode = loader.load();
            ProductCardController cardController = loader.getController();

            // --- Simplified for Customers Only ---

            // Define the action for adding to the cart
            Runnable addToCartAction = () -> handleAddToCart(selectedProduct);

            // The "View" action is empty because we are already on the view page
            Runnable viewAction = () -> {};

            // Call the simple, customer-specific setData method.
            // Note: This call will create both "View" and "Add to Cart" buttons.
            // Since "View" is redundant, we'll handle this in the next section.
            cardController.setData(selectedProduct, viewAction, addToCartAction);

            // Place the card in the center of the view
            mainPane.setCenter(cardNode);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Fatal Error", "Could not load the ProductCard component.");
        }
    }

    // This logic is needed here now for the "Add to Cart" button
    private void handleAddToCart(Product product) {
        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null) {
            showAlert("Login Required", "Please login to add items to your cart.");
            return;
        }
        try {
            List<Object> payload = new ArrayList<>();
            payload.add(currentUser);
            Message message = new Message("add_to_cart", product.getId(), payload);
            SimpleClient.getClient().sendToServer(message);
            showAlert("Success", product.getName() + " was added to your cart!");
        } catch (IOException e) {
            showAlert("Error", "Failed to add item to cart.");
        }
    }

    @FXML
    private void handleBack() {
        try {
            App.setRoot("primary");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}