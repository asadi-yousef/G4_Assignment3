package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Customer;
import il.cshaifasweng.OCSFMediatorExample.entities.Employee;
import il.cshaifasweng.OCSFMediatorExample.entities.Product;
import il.cshaifasweng.OCSFMediatorExample.entities.User;
import java.util.List;
import java.util.ArrayList;

public class SessionManager {
    private static SessionManager instance;
    private User currentUser;
    private List<Product> cart;  // Add this line

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

    public void addToCart(Product product) {
        cart.add(product);
    }

    public void removeFromCart(Product product) {
        cart.remove(product);
    }

    public void clearCart() {
        cart.clear();
    }
}
