package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Cart;
import il.cshaifasweng.OCSFMediatorExample.entities.CartItem;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import java.io.IOException;
import javafx.scene.control.Button;

import java.net.URL;
import java.util.ResourceBundle;

public class CartController implements Initializable {

    @FXML
    private ListView<String> cartListView;
    @FXML
    private Label totalLabel;

    @FXML
    private Button backToCatalogButton;

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
        }
    }

    public void setCart(Cart cart) {
        this.cart = cart;
        renderCart(); // automatically refresh the view when cart is set
    }


    private void renderCart() {
        Platform.runLater(() -> {
            cartListView.getItems().clear();
            double total = 0;

            for (CartItem item : cart.getItems()) {
                String line = item.getProduct().getName() +
                        " - $" + item.getProduct().getPrice() +
                        " x " + item.getQuantity();
                cartListView.getItems().add(line);
                total += item.getProduct().getPrice() * item.getQuantity();
            }

            totalLabel.setText("Total: $" + String.format("%.2f", total));
        });
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        System.out.println("Attempting to load primaryView.fxml..."); // Debug line
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


    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }
}
