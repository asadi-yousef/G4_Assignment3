package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Cart;
import il.cshaifasweng.OCSFMediatorExample.entities.CartItem;
import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
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

            if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                showAlert("Error", "Current user is not a customer.");
                return;
            }

            Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();

            var payload = new java.util.ArrayList<Object>();
            payload.add(customer);

            Message message = new Message("request_cart", null, payload);
            SimpleClient.getClient().sendToServer(message);

        } catch (Exception e) {
            showAlert("Error", "Failed to load cart.");
        }
    }

    @Subscribe
    public void onMessageFromServer(Message msg) {
        ///
        System.out.println("Message received on EventBus: " + msg.getMessage());

        if (msg.getMessage().equals("cart_data")) {
            Cart cart = (Cart) msg.getObject();
            System.out.println("Received cart id: " + cart.getId());
            System.out.println("Cart items count: " + cart.getItems().size());
            for (CartItem ci : cart.getItems()) {
                System.out.println("Product: " + ci.getProduct().getName() + ", qty: " + ci.getQuantity());
            }

            this.cart = (Cart) msg.getObject();
            if (this.cart != null) {
                renderCart();
            } else {
                showAlert("Info", "Your cart is empty.");
            }
        } else if (msg.getMessage().startsWith("item_removed")) {
            try {
                if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                    showAlert("Error", "Current user is not a customer.");
                    return;
                }
                Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();
                var payload = new java.util.ArrayList<Object>();
                payload.add(customer);
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
            ///
            System.out.println("Rendering cart with " + (cart == null ? 0 : cart.getItems().size()) + " items");

            ///
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

        Label itemLabel = new Label(item.getProduct().getName() +
                " - $" + String.format("%.2f", item.getProduct().getPrice()) +
                " x " + item.getQuantity() +
                " = $" + String.format("%.2f", item.getProduct().getPrice() * item.getQuantity()));
        itemLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2c3e50;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

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
            if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                showAlert("Error", "Current user is not a customer.");
                return;
            }
            Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();
            var payload = new java.util.ArrayList<Object>();
            payload.add(customer);
            payload.add(item);

            Message message = new Message("remove_cart_item", null, payload);
            SimpleClient.getClient().sendToServer(message);
        } catch (Exception e) {
            showAlert("Error", "Failed to remove item from cart.");
        }
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try {
                App.setRoot("primary");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    @FXML
    public void handleProceedToOrder(ActionEvent event) {
        if (cart == null || cart.getItems().isEmpty()) {
            showAlert("Empty Cart", "Your cart is empty. Add items before proceeding to order.");
            return;
        }
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
