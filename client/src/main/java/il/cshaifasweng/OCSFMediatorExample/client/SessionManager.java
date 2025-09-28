package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class SessionManager {
    private static SessionManager instance;
    private User currentUser;
    private List<Product> cart;
    private List<Order> orders = new ArrayList<>();
    private Order selectedOrder;
    private double orderTotal;

    private SessionManager() {
        cart = new ArrayList<>();  // Initialize the cart
    }

    public static SessionManager getInstance() {
        if (instance == null) {
            instance = new SessionManager();
        }
        return instance;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isCustomer() {
        return currentUser instanceof Customer;
    }

    public boolean isEmployee() {
        return currentUser instanceof Employee;
    }

    // Cart methods
    public List<Product> getCart() {
        return cart;
    }
    public List<Order> getOrders() {
        return orders;
    }
    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    public void addToCart(Product product) {
        cart.add(product);
    }

    public void removeFromCart(Product product) {
        cart.remove(product);
    }

    public void clearCart() {
        cart.clear();
    }

    public void logout() {
        this.currentUser = null;
    }

    public void setSelectedOrder(Order order) {
        this.selectedOrder = order;
    }
    public Order getSelectedOrder() {
        return selectedOrder;
    }
    public void setOrderTotal(double orderTotal) {
        this.orderTotal = orderTotal;
    }
    public double getOrderTotal() {
        return orderTotal;
    }

}
