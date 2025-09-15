package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "order_table")
public class Order implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // primary key

    @ManyToOne
    private Customer customer;

    private String branchName;
    private boolean delivery;

    private LocalDateTime orderDate;
    private LocalDateTime deliveryDateTime; // combines date & hour

    private String recipientPhone;
    private String deliveryAddress; // new
    private String note; // ברכה
    private String paymentMethod; // "SavedCard", "NewCard", "CashOnDelivery"
    private String paymentDetails;
    private String cardExpiryDate;
    private String cardCVV;
    private double totalPrice;
    private String status; // "PLACED","IN_PREP","READY_FOR_PICKUP","OUT_FOR_DELIVERY","DELIVERED","CANCELLED"
    public String getStatus(){ return status; }
    public void setStatus(String s){ this.status = s; }

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {}

    public Order(Customer customer, String branchName, boolean delivery,
                 LocalDateTime orderDate, LocalDateTime deliveryDateTime,
                 String recipientPhone, String deliveryAddress, String note,
                 String paymentMethod, String paymentDetails, double totalPrice) {
        this.customer = customer;
        this.branchName = branchName;
        this.delivery = delivery;
        this.orderDate = orderDate;
        this.deliveryDateTime = deliveryDateTime;
        this.recipientPhone = recipientPhone;
        this.deliveryAddress = deliveryAddress;
        this.note = note;
        this.paymentMethod = paymentMethod;
        this.paymentDetails = paymentDetails;
        this.cardExpiryDate = cardExpiryDate;
        this.cardCVV = cardCVV;
        this.totalPrice = totalPrice;
    }

    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getStoreLocation() {
        return branchName;
    }
    public void setStoreLocation(String branchName) {
        this.branchName = branchName;
    }

    public boolean getDelivery() {
        return delivery;
    }
    public void setDelivery(boolean delivery) {
        this.delivery = delivery;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }
    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public LocalDateTime getDeliveryDateTime() {
        return deliveryDateTime;
    }
    public void setDeliveryDateTime(LocalDateTime deliveryDateTime) {
        this.deliveryDateTime = deliveryDateTime;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }
    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }
    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getNote() {
        return note;
    }
    public void setNote(String note) {
        this.note = note;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentDetails() {
        return paymentDetails;
    }
    public void setPaymentDetails(String paymentDetails) {
        this.paymentDetails = paymentDetails;
    }

    public String getCardExpiryDate() {
        return cardExpiryDate;
    }
    public void setCardExpiryDate(String cardExpiryDate) { this.cardExpiryDate = cardExpiryDate; }
    public String getCardCVV() { return cardCVV; }
    public void setCardCVV(String cardCVV) { this.cardCVV = cardCVV; }

    public double getTotalPrice() {
        return totalPrice;
    }
    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }

    private java.time.LocalDateTime pickupDateTime; // nullable

    public LocalDateTime getPickupDateTime() { return pickupDateTime; }
    public void setPickupDateTime(LocalDateTime t) { this.pickupDateTime = t; }

    public List<OrderItem> getItems() {
        return items;
    }
    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    @jakarta.persistence.Transient
    public LocalDateTime getScheduledAt() {
        // delivery: deliveryDateTime; pickup: pickupDateTime if set; else fallback to orderDate
        if (Boolean.TRUE.equals(getDelivery())) {
            return getDeliveryDateTime();
        }
        return (getPickupDateTime() != null) ? getPickupDateTime()
                : getOrderDate(); // fallback
    }
    @Transient
    public double calculateFinalTotal() {
        double total = items.stream()
                .mapToDouble(item -> item.getDisplayUnitPrice() * item.getQuantity())
                .sum();

        // Network-account discount (10%) for orders >= 50
        boolean network = (customer != null) && customer.isNetworkAccount();
        if (network && total >= 50.0) {
            total *= 0.9;
        }
        return total;
    }

}
