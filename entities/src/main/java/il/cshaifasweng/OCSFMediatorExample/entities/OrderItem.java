package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Now optional: for bouquet-only order lines this is null
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // Optional: a custom bouquet owned by this order line
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "custom_bouquet_id")
    private CustomBouquet customBouquet;

    private int quantity;

    public OrderItem() {}

    public OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = quantity;
    }

    // Convenience constructor for bouquet orders
    public OrderItem(Order order, CustomBouquet customBouquet, int quantity) {
        this.order = order;
        this.customBouquet = customBouquet;
        this.quantity = quantity;
    }

    // Getters and setters
    public Long getId() { return id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public CustomBouquet getCustomBouquet() { return customBouquet; }
    public void setCustomBouquet(CustomBouquet customBouquet) { this.customBouquet = customBouquet; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    /* ---------- UI-friendly, non-persistent helpers ---------- */

    @Transient
    public boolean isBouquet() { return customBouquet != null; }

    @Transient
    public String getDisplayName() {
        if (isBouquet()) return "Custom Bouquet";
        return product != null ? product.getName() : "";
    }

    @Transient
    public double getDisplayUnitPrice() {
        if (isBouquet()) return customBouquet != null ? customBouquet.getTotalPrice().doubleValue() : 0.0;
        return product != null ? product.getPrice() : 0.0;
    }

    @Transient
    public String getDisplayImagePath() {
        if (isBouquet()) {
            final String R = "/il/cshaifasweng/OCSFMediatorExample/client/images/custombouquet.png";
            java.net.URL url = CartItem.class.getResource(R);   // use OrderItem.class inside OrderItem
            return (url != null) ? url.toExternalForm() : R;     // URL for JavaFX Image; fallback to the path
        }
        return (product != null) ? product.getImagePath() : null;
    }

}
