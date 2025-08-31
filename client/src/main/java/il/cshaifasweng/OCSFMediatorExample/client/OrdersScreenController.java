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

    // Tracks complaint status per order id
    private final java.util.Map<Long, ComplaintDTO> complaintByOrder = new java.util.concurrent.ConcurrentHashMap<>();

    // the HBox that holds the controls for each order card, so we can update it in-place
    private final java.util.Map<Long, HBox> complaintControlsByOrder = new java.util.concurrent.ConcurrentHashMap<>();


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
        switch (msg.getMessage()) {
            case "orders_data":
                orders = (List<Order>) msg.getObject();
                SessionManager.getInstance().setOrders(orders);
                renderOrders();

                // Request complaint status once per order after initial render
                if (orders != null) {
                    for (Order o : orders) requestOrderComplaintStatus(o);
                }
                break;

            case "order_complaint_status":
                Platform.runLater(() -> {
                    Object obj = msg.getObject();
                    if (obj instanceof ComplaintDTO dto && dto.getOrderId() != null) {
                        complaintByOrder.put(dto.getOrderId(), dto);
                        // update only that card's controls in-place
                        applyComplaintUIForOrder(dto.getOrderId());
                    }
                    // If server returns null, we don't know which order — ignore silently
                });
                break;

            case "complaints_refresh":
                // employees resolved / a customer submitted — re-request statuses (no re-render)
                Platform.runLater(() -> {
                    if (orders != null) {
                        for (Order o : orders) requestOrderComplaintStatus(o);
                    }
                });
                break;
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

        String deliveryDateStr = (order.getDeliveryDateTime() != null)
                ? order.getDeliveryDateTime().format(dtFormatter)
                : (order.getDelivery() ? "N/A" : order.getOrderDate().format(dtFormatter));
        Label deliveryDateLabel = new Label((order.getDelivery() ? "Delivery" : "Pickup") + " Date: " + deliveryDateStr);
        deliveryDateLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #34495e;");

        String paymentMethod = (order.getPaymentMethod() == null) ? "Unknown" : order.getPaymentMethod();
        String paymentDetails = (order.getPaymentDetails() == null) ? "" : order.getPaymentDetails();

        String paymentText;
        if ("SavedCard".equalsIgnoreCase(paymentMethod) || "NewCard".equalsIgnoreCase(paymentMethod)) {
            paymentText = "Payment: Card ending with " + maskCard(paymentDetails);
        } else {
            paymentText = "Payment: " + paymentMethod;
        }
        Label paymentLabel = new Label(paymentText);
        paymentLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #8e44ad; -fx-font-weight: bold;");

        String recipientPhone = order.getRecipientPhone();
        String customerPhone = (order.getCustomer() != null) ? order.getCustomer().getPhone() : "";
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

        // Complaint controls (make/view/pending) based on cached server status
        HBox complaintControls = buildComplaintControls(order);
        container.getChildren().add(complaintControls);

        // remember the controls so we can update them later without re-rendering the whole list
        complaintControlsByOrder.put(order.getId(), complaintControls);

        return container;
    }


    private void requestOrderComplaintStatus(Order order) {
        try {
            User u = SessionManager.getInstance().getCurrentUser();
            if (!(u instanceof Customer) || order == null || order.getId() == null) return;

            java.util.Map<String,Object> p = new java.util.HashMap<>();
            p.put("customerId", ((Customer) u).getId());
            p.put("orderId", order.getId());

            SimpleClient.getClient().sendToServer(
                    new Message("get_order_complaint_status", null,
                            new java.util.ArrayList<>(java.util.List.of(p)))
            );
        } catch (IOException ignored) {}
    }

    private void applyComplaintUIForOrder(Long orderId) {
        javafx.scene.layout.HBox box = complaintControlsByOrder.get(orderId);
        if (box == null) return; // card not visible yet

        // find the order object to wire navigation correctly
        Order order = null;
        if (orders != null) {
            for (Order o : orders) {
                if (o.getId().equals(orderId)) { order = o; break; }
            }
        }
        if (order == null) return;

        // Rebuild the small control row
        javafx.scene.control.Button makeBtn = new javafx.scene.control.Button("Make Complaint");
        makeBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 16;");
        makeBtn.setCursor(javafx.scene.Cursor.HAND);
        Order finalOrder = order;
        makeBtn.setOnAction(e -> openComplaintPage(finalOrder));

        javafx.scene.control.Button viewBtn = new javafx.scene.control.Button("View Response");
        viewBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 16;");
        viewBtn.setOnAction(e -> {
            ComplaintDTO dto = complaintByOrder.get(orderId);
            String comp = (dto != null && dto.getCompensationAmount() != null)
                    ? dto.getCompensationAmount().toPlainString() : "—";
            String resp = (dto != null && dto.getResponseText() != null)
                    ? dto.getResponseText() : "—";
            javafx.scene.control.Alert a = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION,
                    "Response: " + resp + "\nCompensation: " + comp,
                    javafx.scene.control.ButtonType.OK
            );
            a.setHeaderText("Complaint Response for Order #" + orderId);
            a.setTitle("Complaint Response");
            a.showAndWait();
        });

        javafx.scene.control.Label pendingLbl = new javafx.scene.control.Label("Response pending");
        pendingLbl.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        ComplaintDTO dto = complaintByOrder.get(orderId);
        if (dto == null) {
            makeBtn.setDisable(false);
            box.getChildren().setAll(makeBtn);
        } else if (!dto.isResolved()) {
            makeBtn.setDisable(true);
            box.getChildren().setAll(makeBtn, pendingLbl);
        } else {
            makeBtn.setDisable(true);
            box.getChildren().setAll(makeBtn, viewBtn);
        }
    }


    private void openComplaintPage(Order order) {
        try {
            SessionManager.getInstance().setSelectedOrder(order);
            App.setRoot("complaintsScreen"); // << was "complaints"
        } catch (IOException e) {
            showAlert("Error", "Failed to open complaints page.");
        }
    }

    private boolean canComplain(Order order) {
        if (order == null) return false;

        // For delivery: use deliveryDateTime
        // For pickup: fall back to orderDate (or add a dedicated pickup time if you have one)
        java.time.LocalDateTime refTime = order.getDelivery()
                ? order.getDeliveryDateTime()
                : order.getOrderDate();

        if (refTime == null) return false;                  // nothing to compare against
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if (!refTime.isBefore(now)) return false;           // only after delivery/pickup time

        long days = java.time.temporal.ChronoUnit.DAYS.between(refTime, now);
        return days <= 14;                                  // show up to (and including) 14 days
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

    private HBox buildComplaintControls(Order order) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);

        Button makeBtn = new Button("Make Complaint");
        makeBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 8 16;");
        makeBtn.setCursor(javafx.scene.Cursor.HAND);
        makeBtn.setOnAction(e -> openComplaintPage(order));

        Button viewBtn = new Button("View Response");
        viewBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 8; -fx-padding: 8 16;");
        viewBtn.setOnAction(e -> {
            ComplaintDTO dto = complaintByOrder.get(order.getId());
            String comp = (dto != null && dto.getCompensationAmount() != null)
                    ? dto.getCompensationAmount().toPlainString() : "—";
            String resp = (dto != null && dto.getResponseText() != null)
                    ? dto.getResponseText() : "—";
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "Response: " + resp + "\nCompensation: " + comp, ButtonType.OK);
            a.setHeaderText("Complaint Response for Order #" + order.getId());
            a.setTitle("Complaint Response");
            a.showAndWait();
        });

        Label pendingLbl = new Label("Response pending");
        pendingLbl.setStyle("-fx-text-fill: #7f8c8d; -fx-font-style: italic;");

        // decide UI from cached status
        ComplaintDTO dto = complaintByOrder.get(order.getId());
        if (dto == null) {
            makeBtn.setDisable(false);
            box.getChildren().setAll(makeBtn);
        } else if (!dto.isResolved()) {
            makeBtn.setDisable(true);
            box.getChildren().setAll(makeBtn, pendingLbl);
        } else {
            makeBtn.setDisable(true);
            box.getChildren().setAll(makeBtn, viewBtn);
        }

        return box;
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
