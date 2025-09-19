package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Cart implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private List<CartItem> items = new ArrayList<>();

    public Cart() {}

    public Cart(Customer customer) {
        setCustomer(customer);
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
    public Long getId() {
        return id;
    }

    @Transient
    public double getTotalWithDiscount() {
        double total = getBaseTotal(); // sum of item prices * quantities
        if (customer != null && customer.hasValidSubscription() && total > 50) {
            return total * 0.9; // apply 10% discount
        }
        return total;
    }
    @Transient
    public double getBaseTotal() {
        return items.stream()
                .mapToDouble(CartItem::getSubtotal)
                .sum();
    }


    public List<CartItem> getItems() { return items; }

    public void addItem(Product product, int quantity) {
        for (CartItem item : items) {
            if (item.getProduct().getId().equals(product.getId())) {
                item.setQuantity(item.getQuantity() + quantity);
                return;
            }
        }
        CartItem newItem = new CartItem(product, this, quantity);
        items.add(newItem);
    }
}




