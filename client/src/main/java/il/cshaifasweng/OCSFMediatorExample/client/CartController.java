package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
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
import java.math.BigDecimal;
import java.net.URL;
import java.util.Objects;
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
    @FXML
    private Label originalTotalLabel;
    @FXML
    private Label discountBadge;
    @FXML
    private HBox discountContainer;

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
        System.out.println("Message received on EventBus: " + msg.getMessage());
        if (msg.getMessage().equals("cart_data")) {
            Cart cart = (Cart) msg.getObject();
            System.out.println("Received cart id: " + cart.getId());
            System.out.println("Cart items count: " + cart.getItems().size());
            for (CartItem ci : cart.getItems()) {
                String what = (ci.getProduct() != null)
                        ? ("Product: " + safe(ci.getProduct().getName()))
                        : (ci.getCustomBouquet() != null ? "Custom Bouquet" : "Unknown");
                System.out.println(what + ", qty: " + ci.getQuantity());
            }
            this.cart = cart;
            SessionManager.getInstance().clearCart();
            if (this.cart != null && this.cart.getItems() != null) {
                for (CartItem ci : this.cart.getItems()) {
                    if (ci.getProduct() != null) {
                        Product p = ci.getProduct();
                        int qty = ci.getQuantity();
                        for (int i = 0; i < qty; i++) {
                            SessionManager.getInstance().addToCart(p);
                        }
                    }
                }
            }
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
        } else if (msg.getMessage().equals("editProduct")) {
            Product updated = safeCastProduct(msg.getObject());
            if (updated != null) {
                updateCartWithEditedProduct(updated);
                renderCart(); // Rebuild the cart UI
            } else {
                System.err.println("[CartController] Received editProduct with no payload.");
            }
        }
    }

    private Product safeCastProduct(Object o) {
        try {
            return (Product) o;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Update all cart items and bouquets to reflect the edited product.
     */
    private void updateCartWithEditedProduct(Product updated) {
        if (updated == null || this.cart == null || this.cart.getItems() == null) return;
        boolean anyChange = false;
        for (CartItem item : this.cart.getItems()) {
            // 1) Plain product line
            Product p = item.getProduct();
            if (p != null && Objects.equals(p.getId(), updated.getId())) {
                item.setProduct(updated);   // swap to the fresh product (name/price/images/etc.)
                anyChange = true;
            }

            // 2) Bouquet line(s)
            CustomBouquet bouquet = item.getCustomBouquet();
            if (bouquet != null && bouquet.getItems() != null) {
                boolean bouquetChanged = false;

                for (CustomBouquetItem line : bouquet.getItems()) {
                    Product flower = line.getFlower();
                    if (flower != null && Objects.equals(flower.getId(), updated.getId())) {
                        // keep the linkage to Product, but refresh snapshots so price/name show the edited values
                        line.setFlower(updated);
                        line.setFlowerNameSnapshot(updated.getName());
                        line.setUnitPriceSnapshot(BigDecimal.valueOf(updated.getSalePrice()));
                        bouquetChanged = true;
                    }
                }

                if (bouquetChanged) {
                    bouquet.recomputeTotalPrice(); // keeps total consistent with refreshed line snapshots
                    anyChange = true;
                }
            }
        }
    }

    public void setCart(Cart cart) {
        this.cart = cart;
        renderCart();
    }

    private void renderCart() {
        Platform.runLater(() -> {
            System.out.println("Rendering cart with " + (cart == null ? 0 : cart.getItems().size()) + " items");

            cartListView.getItems().clear();
            double total = 0;

            if (cart == null || cart.getItems().isEmpty()) {
                proceedToOrderButton.setDisable(true);
                totalLabel.setText("Total: ₪0.00");
                discountContainer.setVisible(false);
                discountContainer.setManaged(false);
                return;
            }

            for (CartItem item : cart.getItems()) {
                HBox itemBox = createCartItemBox(item);
                cartListView.getItems().add(itemBox);
                total += displayUnitPrice(item) * item.getQuantity();
            }

            applyDiscountAndUpdateTotal(total);
            proceedToOrderButton.setDisable(false);
        });
    }

    private HBox createCartItemBox(CartItem item) {
        HBox itemBox = new HBox();
        itemBox.setAlignment(Pos.CENTER_LEFT);
        itemBox.setSpacing(15);
        itemBox.setPadding(new Insets(15));
        itemBox.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; " +
                "-fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 8;");

        final String nameText = displayName(item);
        final double unit = displayUnitPrice(item);

        Label namePriceLabel = new Label(nameText + " - ₪" + String.format("%.2f", unit));
        namePriceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");
        Label totalItemLabel = new Label("₪" + String.format("%.2f", unit * item.getQuantity()));
        totalItemLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label qtyLbl = new Label("Qty:");
        qtyLbl.setStyle("-fx-text-fill: #2c3e50; -fx-font-size: 14px;");

        Spinner<Integer> quantitySpinner = new Spinner<>(1, 99, item.getQuantity());
        quantitySpinner.setEditable(false);
        quantitySpinner.setPrefWidth(80);
        quantitySpinner.setStyle("-fx-background-color: white; -fx-border-color: #ced4da; -fx-border-radius: 4;");

        quantitySpinner.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null && !newVal.equals(oldVal) && quantitySpinner.isFocused()) {
                double unitNow = displayUnitPrice(item);
                totalItemLabel.setText("₪" + String.format("%.2f", unitNow * newVal));
                updateCartTotalPreview(item, newVal);
                updateCartItemQuantity(item, newVal);
            }
        });

        HBox qtyBox = new HBox(6, qtyLbl, quantitySpinner);
        qtyBox.setAlignment(Pos.CENTER_RIGHT);

        Button removeButton = new Button("X Remove");
        removeButton.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; " +
                "-fx-background-radius: 8; -fx-padding: 8 16 8 16; " +
                "-fx-cursor: hand; -fx-font-size: 14px; -fx-font-weight: bold;");
        removeButton.setOnAction(e -> removeItemFromCart(item));

        itemBox.getChildren().addAll(namePriceLabel, totalItemLabel, spacer, qtyBox, removeButton);
        return itemBox;
    }

    private void updateCartTotalPreview(CartItem changedItem, int newQty) {
        if (cart == null || cart.getItems() == null) return;

        double total = 0.0;
        for (CartItem ci : cart.getItems()) {
            int qty = (ci == changedItem
                    || (ci.getId() != null && changedItem.getId() != null && ci.getId().equals(changedItem.getId())))
                    ? newQty
                    : ci.getQuantity();
            total += displayUnitPrice(ci) * qty;
        }

        applyDiscountAndUpdateTotal(total);
    }
    private void applyDiscountAndUpdateTotal(double total) {
        Customer customer = (Customer) (SessionManager.getInstance().getCurrentUser());
        if (customer.isSubscribed() && customer.getSubscription().isActive() && total > 50) {
            double discountedTotal = cart.getTotalWithDiscount();
            discountContainer.setVisible(true);
            discountContainer.setManaged(true);
            originalTotalLabel.setText("Total: ₪" + String.format("%.2f", total));
            originalTotalLabel.setStyle("-fx-background-color: transparent; -fx-strikethrough: true;");
            totalLabel.setText("Total: ₪" + String.format("%.2f", discountedTotal));
            totalLabel.setTextFill(javafx.scene.paint.Color.web("#27ae60")); // Green for savings

        } else {
            discountContainer.setVisible(false);
            discountContainer.setManaged(false);
            totalLabel.setText("Total: ₪" + String.format("%.2f", total));
            totalLabel.setTextFill(javafx.scene.paint.Color.web("#2c3e50")); // Dark color for regular price
        }
    }

    private void updateCartItemQuantity(CartItem item, int newQuantity) {
        try {
            if (!(SessionManager.getInstance().getCurrentUser() instanceof Customer)) {
                showAlert("Error", "Current user is not a customer.");
                return;
            }
            Customer customer = (Customer) SessionManager.getInstance().getCurrentUser();
            var payload = new java.util.ArrayList<Object>();
            payload.add(customer);
            payload.add(item);
            payload.add(newQuantity);

            Message message = new Message("update_cart_item_quantity", null, payload);
            SimpleClient.getClient().sendToServer(message);

            System.out.println("Requested quantity update for: " + displayName(item) + " to " + newQuantity);
        } catch (Exception e) {
            showAlert("Error", "Failed to update item quantity.");
        }
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

            System.out.println("Requested removal of: " + displayName(item));

        } catch (Exception e) {
            showAlert("Error", "Failed to remove item from cart.");
        }
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try { App.setRoot("primary"); } catch (IOException e) { throw new RuntimeException(e); }
        });
    }

    @FXML
    public void handleProceedToOrder(ActionEvent event) {
        if (cart == null || cart.getItems().isEmpty()) {
            showAlert("Empty Cart", "Your cart is empty. Add items before proceeding to order.");
            return;
        }

        // Keep SessionManager in sync for product items only; the server converts bouquets during checkout.
        SessionManager.getInstance().clearCart();
        for (CartItem ci : cart.getItems()) {
            if (ci.getProduct() != null) {
                for (int i = 0; i < ci.getQuantity(); i++) {
                    SessionManager.getInstance().addToCart(ci.getProduct());
                }
            }
        }

        SessionManager.getInstance().setOrderTotal(cart.getTotalWithDiscount());

        EventBus.getDefault().unregister(this);
        Platform.runLater(() -> {
            try { App.setRoot("orderView"); }
            catch (IOException e) { showAlert("Error", "Failed to load order page."); throw new RuntimeException(e); }
        });
    }

    /* -------------------- Helpers -------------------- */

    private static String safe(String s) { return s == null ? "" : s; }

    /** Display name for either a product item or a custom bouquet item. */
    private String displayName(CartItem item) {
        if (item == null) return "";
        if (item.getProduct() != null) return safe(item.getProduct().getName());
        if (item.getCustomBouquet() != null) return "Custom Bouquet";
        return "(Unknown Item)";
    }

    /** Unit price for either a product item or a custom bouquet item. */
    private double displayUnitPrice(CartItem item) {
        if (item == null) return 0.0;
        if (item.getProduct() != null) return item.getProduct().getSalePrice();
        if (item.getCustomBouquet() != null && item.getCustomBouquet().getTotalPrice() != null) {
            return item.getCustomBouquet().getTotalPrice().doubleValue();
        }
        return 0.0;
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
