package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
    public BigDecimal getTotalWithDiscount() {
        BigDecimal total = getBaseTotal();

        if (customer != null
                && customer.hasValidSubscription()
                && total.compareTo(BigDecimal.valueOf(50)) > 0) {
            // Apply 10% discount: multiply by 0.9
            total = total.multiply(BigDecimal.valueOf(0.9));
        }

        // Optionally enforce 2 decimal places (e.g., for currency)
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    @Transient
    public BigDecimal getBaseTotal() {
        return items.stream()
                .map(CartItem::getSubtotal)   // make sure getSubtotal() returns BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP); // ensures 2 decimal precision
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




