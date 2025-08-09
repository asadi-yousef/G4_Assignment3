package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Cart;
import il.cshaifasweng.OCSFMediatorExample.entities.CartItem;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class CartController implements Initializable {

    @FXML
    private ListView<HBox> cartListView;
    @FXML
    private Label totalLabel;
    @FXML
    private Button backToCatalogButton;
    @FXML
    private Button proceedToOrderButton;

    private Cart cart;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        try {
            if (SessionManager.getInstance().getCurrentUser() == null) {
                showAlert("Login Required", "Please login to view your cart.");
                return;
            }

            // Wrap the user in the object list
            var payload = new java.util.ArrayList<Object>();
            payload.add(SessionManager.getInstance().getCurrentUser());

            Message message = new Message("request_cart", null, payload);
            SimpleClient.getClient().sendToServer(message);

        } catch (Exception e) {
            showAlert("Error", "Failed to load cart.");
        }
    }

    @Subscribe
    public void onMessageFromServer(Message msg) {
        if (msg.getMessage().startsWith("cart_data")) {
            this.cart = (Cart) msg.getObject();
            if (this.cart != null) {
                renderCart();
            } else {
                showAlert("Info", "Your cart is empty.");
            }
        } else if (msg.getMessage().startsWith("item_removed")) {
            // Refresh cart after item removal
            try {
                var payload = new java.util.ArrayList<Object>();
                payload.add(SessionManager.getInstance().getCurrentUser());
                Message message = new Message("request_cart", null, payload);
                SimpleClient.getClient().sendToServer(message);
            } catch (Exception e) {
                showAlert("Error", "Failed to refresh cart.");
            }
        }
    }

    public void setCart(Cart cart) {
        this.cart = cart;
        renderCart();
    }

    private void renderCart() {
        Platform.runLater(() -> {
            cartListView.getItems().clear();
            double total = 0;

            if (cart == null || cart.getItems().isEmpty()) {
                proceedToOrderButton.setDisable(true);
                totalLabel.setText("Total: $0.00");
                return;
            }

            for (CartItem item : cart.getItems()) {
                HBox itemBox = createCartItemBox(item);
                cartListView.getItems().add(itemBox);
                total += item.getProduct().getPrice() * item.getQuantity();
            }

            totalLabel.setText("Total: $" + String.format("%.2f", total));
            proceedToOrderButton.setDisable(false);
        });
    }

    private HBox createCartItemBox(CartItem item) {
        HBox itemBox = new HBox();
        itemBox.setAlignment(Pos.CENTER_LEFT);
        itemBox.setSpacing(10);
        itemBox.setPadding(new Insets(10));
        itemBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 5;");

        // Product info label
        Label itemLabel = new Label(item.getProduct().getName() +
                " - $" + String.format("%.2f", item.getProduct().getPrice()) +
                " x " + item.getQuantity() +
                " = $" + String.format("%.2f", item.getProduct().getPrice() * item.getQuantity()));
        itemLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Remove button
        Button removeButton = new Button("🗑️ Remove");
        removeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; " +
                "-fx-background-radius: 5; -fx-padding: 5 10 5 10; " +
                "-fx-cursor: hand; -fx-font-size: 12px;");
        removeButton.setOnAction(e -> removeItemFromCart(item));

        itemBox.getChildren().addAll(itemLabel, spacer, removeButton);
        return itemBox;
    }

    private void removeItemFromCart(CartItem item) {
        try {
            var payload = new java.util.ArrayList<Object>();
            payload.add(SessionManager.getInstance().getCurrentUser());
            payload.add(item);

            Message message = new Message("remove_cart_item", null, payload);
            SimpleClient.getClient().sendToServer(message);
        } catch (Exception e) {
            showAlert("Error", "Failed to remove item from cart.");
        }
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        System.out.println("Attempting to load primaryView.fxml...");
        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try {
                App.setRoot("primary");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println("Navigation completed successfully");
    }

    @FXML
    public void handleProceedToOrder(ActionEvent event) {
        if (cart == null || cart.getItems().isEmpty()) {
            showAlert("Empty Cart", "Your cart is empty. Add items before proceeding to order.");
            return;
        }

        System.out.println("Proceeding to order view...");
        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try {
                App.setRoot("orderView");
            } catch (IOException e) {
                showAlert("Error", "Failed to load order page.");
                throw new RuntimeException(e);
            }
        });
    }

    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}