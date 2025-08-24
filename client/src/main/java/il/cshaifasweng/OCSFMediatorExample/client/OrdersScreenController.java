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
import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

public class OrdersScreenController implements Initializable {

    @FXML
    private ListView<VBox> ordersListView;

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

                // most recent first
                for (int i = orders.size() - 1; i >= 0; i--) {
                    Order order = orders.get(i);
                    VBox orderBox = createOrderBox(order);
                    ordersListView.getItems().add(orderBox);
                }
            }
        });
    }

    private VBox createOrderBox(Order order) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(15));
        container.setStyle("-fx-background-color: white; -fx-background-radius: 12; -fx-border-color: #e1e8ed; -fx-border-width: 1; -fx-border-radius: 12; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.1), 5, 0, 0, 2);");
        container.setMaxWidth(Double.MAX_VALUE);
        container.setPrefWidth(Region.USE_COMPUTED_SIZE);

        Label orderIdLabel = new Label("Order #" + order.getId());
        orderIdLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 20px; -fx-text-fill: #2c3e50;");

        Label orderDateLabel = new Label("Order Date: " + order.getOrderDate().format(dtFormatter));
        orderDateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;");

        String typeText = order.getDelivery() ? "Delivery" : "Self Pickup";
        Label deliveryTypeLabel = new Label("Type: " + typeText);
        deliveryTypeLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");

        String deliveryDateStr = order.getDeliveryDateTime() != null ? order.getDeliveryDateTime().format(dtFormatter) : "N/A";
        Label deliveryDateLabel = new Label((order.getDelivery() ? "Delivery" : "Pickup") + " Date: " + deliveryDateStr);
        deliveryDateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;");

        String paymentMethod = order.getPaymentMethod() == null ? "Unknown" : order.getPaymentMethod();
        String paymentDetails = order.getPaymentDetails() == null ? "" : order.getPaymentDetails();

        String paymentText;
        if ("SavedCard".equalsIgnoreCase(paymentMethod) || "NewCard".equalsIgnoreCase(paymentMethod)) {
            paymentText = "Payment: Card ending with " + maskCard(paymentDetails);
        } else {
            paymentText = "Payment: " + paymentMethod;
        }
        Label paymentLabel = new Label(paymentText);
        paymentLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8e44ad; -fx-font-weight: bold;");

        String recipientPhone = order.getRecipientPhone();
        String customerPhone = order.getCustomer() != null ? order.getCustomer().getPhone() : "";
        Label recipientPhoneLabel = null;
        if (recipientPhone != null && !recipientPhone.isEmpty() && !recipientPhone.equals(customerPhone)) {
            recipientPhoneLabel = new Label("Recipient Phone: " + recipientPhone);
            recipientPhoneLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;");
        }

        Label noteLabel = null;
        if (order.getNote() != null && !order.getNote().trim().isEmpty()) {
            noteLabel = new Label("Note: " + order.getNote());
            noteLabel.setStyle("-fx-font-style: italic; -fx-font-size: 16px; -fx-text-fill: #7f8c8d;");
            noteLabel.setWrapText(true);
        }

        VBox itemsContainer = new VBox(8);
        itemsContainer.setPadding(new Insets(10, 0, 0, 0));
        itemsContainer.setStyle("-fx-background-color: #f8f9fa; -fx-background-radius: 8; -fx-padding: 10;");

        Label itemsHeaderLabel = new Label("Order Items:");
        itemsHeaderLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 18px; -fx-text-fill: #2c3e50;");
        itemsContainer.getChildren().add(itemsHeaderLabel);

        for (OrderItem item : order.getItems()) {
            HBox itemBox = createOrderItemBox(item);
            itemsContainer.getChildren().add(itemBox);
        }

        container.getChildren().addAll(orderIdLabel, orderDateLabel, deliveryTypeLabel, deliveryDateLabel, paymentLabel);
        if (recipientPhoneLabel != null) container.getChildren().add(recipientPhoneLabel);
        if (noteLabel != null) container.getChildren().add(noteLabel);
        container.getChildren().add(itemsContainer);

        // Optional: complaint button if delivered within last 14 days
        if (order.getDeliveryDateTime() != null) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime deliveryTime = order.getDeliveryDateTime();
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
            SessionManager.getInstance().setSelectedOrder(order);
            App.setRoot("complaints");
        } catch (IOException e) {
            showAlert("Error", "Failed to open complaints page.");
        }
    }

    private HBox createOrderItemBox(OrderItem item) {
        HBox itemBox = new HBox(15);
        itemBox.setAlignment(Pos.CENTER_LEFT);
        itemBox.setPadding(new Insets(10));
        itemBox.setStyle("-fx-background-color: white; -fx-background-radius: 8; -fx-border-color: #dee2e6; -fx-border-width: 1; -fx-border-radius: 8;");
        itemBox.setPrefWidth(Region.USE_COMPUTED_SIZE);
        itemBox.setMaxWidth(Double.MAX_VALUE);

        ImageView productImage = new ImageView();
        productImage.setFitWidth(80);
        productImage.setFitHeight(80);
        productImage.setPreserveRatio(true);

        VBox info = new VBox(5);
        info.setAlignment(Pos.CENTER_LEFT);

        // *** Snapshot-first rendering ***
        String name = item.getDisplayName();
        BigDecimal unitPrice = BigDecimal.valueOf(item.getDisplayUnitPrice());

        String imagePath = item.getDisplayImagePath();
        if (imagePath != null && !imagePath.isEmpty()) {
            try { productImage.setImage(new Image(imagePath, true)); } catch (Exception ignored) {}
        }

        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        Label qtyLbl = new Label("Quantity: " + item.getQuantity());
        qtyLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;");

        if (unitPrice != null && unitPrice.compareTo(BigDecimal.ZERO) > 0) {
            Label priceLbl = new Label("Price: $" + String.format("%.2f", unitPrice));
            priceLbl.setStyle("-fx-font-size: 16px; -fx-text-fill: #27ae60; -fx-font-weight: bold;");
            info.getChildren().addAll(nameLbl, qtyLbl, priceLbl);
        } else {
            info.getChildren().addAll(nameLbl, qtyLbl);
        }

        HBox.setHgrow(info, Priority.ALWAYS);
        itemBox.getChildren().addAll(productImage, info);
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
