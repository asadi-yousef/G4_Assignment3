package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.function.Consumer;

public class ViewFlowerController {

    @FXML
    private BorderPane cardContainer; // The container from our new FXML

    private static Product selectedProduct;

    // This method remains the same
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
            // 1. Load the reusable card component from its FXML file
            FXMLLoader loader = new FXMLLoader(getClass().getResource("ProductCard.fxml"));
            StackPane cardNode = loader.load();

            // 2. Get the card's dedicated controller instance
            ProductCardController cardController = loader.getController();

            // 3. Define the actions the card will perform
            boolean isEmployee = SessionManager.getInstance().isEmployee();

            // Define what happens when an employee clicks "Save"
            Consumer<Product> saveAction = (updatedProduct) -> {
                System.out.println("Sending update for: " + updatedProduct.getName());
                try {
                    // Send the updated product to the server
                    Message message = new Message("edit_product", updatedProduct,null);
                    SimpleClient.getClient().sendToServer(message);
                    showAlert("Success", "Product details have been updated.");
                } catch (IOException e) {
                    showAlert("Error", "Failed to send update to the server.");
                    e.printStackTrace();
                }
            };

            // Define what happens when an employee clicks "Delete"
            Runnable deleteAction = () -> {
                System.out.println("Sending delete for: " + selectedProduct.getName());
                // Your existing delete logic
            };

            // This action isn't really used on this page, but the method requires it
            Runnable viewAction = () -> {};


            // 4. Pass the product data and actions to the card
            cardController.setData(selectedProduct, isEmployee, viewAction, deleteAction);

            // 5. Place the fully configured card into the container
            cardContainer.setCenter(cardNode);

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Fatal Error", "Could not load the ProductCard component.");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void handleBack() {
        // Unregister from EventBus if you are using it
        // EventBus.getDefault().unregister(this);
        try {
            App.setRoot("primary");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}