package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;


public class OrdersScreenController implements Initializable {

    @FXML
    private ListView<VBox> ordersListView; // Changed to VBox items for rich display

    @FXML
    private Label noOrdersLabel;

    @FXML
    private Button backToCatalogButton;

    private List<Order> orders;

    private final DateTimeFormatter dtFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        User currentUser = SessionManager.getInstance().getCurrentUser();
        if (currentUser == null || !(currentUser instanceof Customer)) {
            showAlert("Login Required", "Please login to view your orders.");
            return;
        }

        List<Object> payload = new ArrayList<>();
        payload.add(currentUser);
        Message requestOrdersMsg = new Message("request_orders", null, payload);
        try {
            SimpleClient.getClient().sendToServer(requestOrdersMsg);
        } catch (IOException e) {
            showAlert("Error", "Failed to request orders.");
        }
    }

    @Subscribe
    public void onMessageFromServer(Message msg) {
        if ("orders_data".equals(msg.getMessage())) {
            orders = (List<Order>) msg.getObject();
            SessionManager.getInstance().setOrders(orders);
            renderOrders();
        }
    }

    private void renderOrders() {
        Platform.runLater(() -> {
            if (orders == null || orders.isEmpty()) {
                noOrdersLabel.setVisible(true);
                ordersListView.setVisible(false);
            } else {
                noOrdersLabel.setVisible(false);
                ordersListView.setVisible(true);
                ordersListView.getItems().clear();

                // Loop through orders in reverse order (most recent first)
                for (int i = orders.size() - 1; i >= 0; i--) {
                    Order order = orders.get(i);
                    VBox orderBox = createOrderBox(order);
                    ordersListView.getItems().add(orderBox);
                }
            }
        });
    }

    private VBox createOrderBox(Order order) {
        VBox container = new VBox(8); // Increased spacing
        container.setPadding(new Insets(15)); // Increased padding
        container.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e1e8ed; -fx-border-width: 1; -fx-border-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        container.setMaxWidth(Double.MAX_VALUE);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE); // Use computed size

        // Order header: ID and Order Date - make them larger
        Label orderIdLabel = new Label("Order #" + order.getId());
        orderIdLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: #2c3e50;"); // Increased font size

        Label orderDateLabel = new Label("Order Date: " + order.getOrderDate().format(dtFormatter));
        orderDateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;"); // Added styling

        // Delivery or Pickup info - make larger
        String typeText = order.getDelivery() ? "Delivery" : "Self Pickup";
        Label deliveryTypeLabel = new Label("Type: " + typeText);
        deliveryTypeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #27ae60; -fx-font-weight: bold;"); // Added styling

        String deliveryDateStr = order.getDeliveryDateTime() != null ? order.getDeliveryDateTime().format(dtFormatter) : "N/A";
        Label deliveryDateLabel = new Label((order.getDelivery() ? "Delivery" : "Pickup") + " Date: " + deliveryDateStr);
        deliveryDateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;"); // Added styling

        // Payment Method + masked card if applicable - make larger
        String paymentMethod = order.getPaymentMethod() == null ? "Unknown" : order.getPaymentMethod();
        String paymentDetails = order.getPaymentDetails() == null ? "" : order.getPaymentDetails();

        String paymentText;
        if ("SavedCard".equalsIgnoreCase(paymentMethod) || "NewCard".equalsIgnoreCase(paymentMethod)) {
            paymentText = "Payment: Card ending with " + maskCard(paymentDetails);
        } else {
            paymentText = "Payment: " + paymentMethod;
        }
        Label paymentLabel = new Label(paymentText);
        paymentLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8e44ad; -fx-font-weight: bold;"); // Added styling

        // Recipient phone - make larger
        String recipientPhone = order.getRecipientPhone();
        String customerPhone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";
        Label recipientPhoneLabel = null;
        if (recipientPhone != null && !recipientPhone.isEmpty() && !recipientPhone.equals(customerPhone)) {
            recipientPhoneLabel = new Label("Recipient Phone: " + recipientPhone);
            recipientPhoneLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;"); // Added styling
        }

        // Note - make larger
        Label noteLabel = null;
        if (order.getNote() != null && !order.getNote().trim().isEmpty()) {
            noteLabel = new Label("Note: " + order.getNote());
            noteLabel.setStyle("-fx-font-style: italic; -fx-font-size: 16px; -fx-text-fill: #7f8c8d;"); // Increased font size
            noteLabel.setWrapText(true); // Allow text wrapping
        }

        // Items list with pictures and quantity - make much bigger
        VBox itemsContainer = new VBox(8); // Increased spacing
        itemsContainer.setPadding(new Insets(10, 0, 0, 0)); // Adjusted padding
        itemsContainer.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; -fx-padding: 10;"); // Added background

        // Add items header
        Label itemsHeaderLabel = new Label("Order Items:");
        itemsHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #2c3e50;");
        itemsContainer.getChildren().add(itemsHeaderLabel);

        for (OrderItem item : order.getItems()) {
            HBox itemBox = createOrderItemBox(item);
            itemsContainer.getChildren().add(itemBox);
        }

        // Add all to container
        container.getChildren().addAll(orderIdLabel, orderDateLabel, deliveryTypeLabel, deliveryDateLabel, paymentLabel);
        if (recipientPhoneLabel != null) container.getChildren().add(recipientPhoneLabel);
        if (noteLabel != null) container.getChildren().add(noteLabel);
        container.getChildren().add(itemsContainer);


        // Add Complaint Button if eligible
        if (order.getDeliveryDateTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deliveryTime = order.getDeliveryDateTime();

            // Check if delivered in the past 14 days
            if (deliveryTime.isBefore(now) && ChronoUnit.DAYS.between(deliveryTime, now) <= 14) {
                Button complaintButton = new Button("Make Complaint");
                complaintButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 16;");
                complaintButton.setCursor(javafx.scene.Cursor.HAND);

                complaintButton.setOnAction(e -> openComplaintPage(order));

                container.getChildren().add(complaintButton);
            }
        }

        return container;
    }

    private void openComplaintPage(Order order) {
        try {
            // Option A: Pass order via SessionManager (easier)
            SessionManager.getInstance().setSelectedOrder(order);

            // Load complaints FXML
            App.setRoot("complaints");  // assumes you have complaints.fxml set up
        } catch (IOException e) {
            showAlert("Error", "Failed to open complaints page.");
        }
    }


    private HBox createOrderItemBox(OrderItem item) {
        HBox itemBox = new HBox(15); // Increased spacing
        itemBox.setAlignment(Pos.CENTER_LEFT);
        itemBox.setPadding(new Insets(10)); // Added padding
        itemBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 8;");
        itemBox.setPrefWidth(Region.USE_COMPUTED_SIZE); // Use full width
        itemBox.setMaxWidth(Double.MAX_VALUE);

        // Product image - make much bigger
        ImageView productImage = new ImageView();
        productImage.setFitWidth(80); // Increased from 50 to 80
        productImage.setFitHeight(80); // Increased from 50 to 80
        productImage.setPreserveRatio(true);
        productImage.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8;");

        String imageUrl = null;
        if (item.getProduct() != null) {
            try {
                imageUrl = item.getProduct().getImagePath();
            } catch (Exception e) {
                imageUrl = null;
            }
        }

        if (imageUrl != null && !imageUrl.isEmpty()) {
            try {
                Image img = new Image(imageUrl, true);
                productImage.setImage(img);
            } catch (Exception e) {
                // fallback: no image - add a placeholder
                productImage.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 8;");
            }
        } else {
            // Add placeholder styling when no image
            productImage.setStyle("-fx-background-color: #ecf0f1; -fx-background-radius: 8;");
        }

        // Product info - make bigger and add more details
        VBox productInfo = new VBox(5);
        productInfo.setAlignment(Pos.CENTER_LEFT);

        Label productNameLabel = new Label(item.getProduct().getName());
        productNameLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;"); // Much larger font
        productNameLabel.setWrapText(true);

        Label quantityLabel = new Label("Quantity: " + item.getQuantity());
        quantityLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;"); // Larger font

        // Add price if available (assuming OrderItem has getPrice() method)
        try {
            double price = item.getProduct().getPrice();
            if (price > 0) {
                Label priceLabel = new Label("Price: $" + String.format("%.2f", price));
                priceLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
                productInfo.getChildren().add(priceLabel);
            }
        } catch (Exception e) {
            // Price not available or method doesn't exist
        }

        productInfo.getChildren().addAll(productNameLabel, quantityLabel);

        // Make product info take remaining space
        HBox.setHgrow(productInfo, Priority.ALWAYS);

        itemBox.getChildren().addAll(productImage, productInfo);

        return itemBox;
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
    // Mask card: show last 4 digits only (assumes digits only)
    private String maskCard(String cardNumber) {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****";
        }
        String last4 = cardNumber.substring(cardNumber.length() - 4);
        return "**** **** **** " + last4;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
