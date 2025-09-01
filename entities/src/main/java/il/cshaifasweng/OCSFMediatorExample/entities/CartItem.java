package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
public class CartItem implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Now optional: for bouquet-only items this is null
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    // Optional: a custom bouquet owned by this cart item
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "custom_bouquet_id")
    private CustomBouquet customBouquet;

    private int quantity;

    public CartItem() {}

    public CartItem(Product product, Cart cart, int quantity) {
        this.product = product;
        this.cart = cart;
        this.quantity = quantity;
    }

    // Convenience constructor for bouquet items
    public CartItem(CustomBouquet customBouquet, Cart cart, int quantity) {
        this.customBouquet = customBouquet;
        this.cart = cart;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
    }

    public Cart getCart() { return cart; }
    public void setCart(Cart cart) {
        this.cart = cart;
    }

    public CustomBouquet getCustomBouquet() { return customBouquet; }
    public void setCustomBouquet(CustomBouquet customBouquet) {
        this.customBouquet = customBouquet;
    }

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
    @Transient
    public double getSubtotal() {
        return product.getPrice() * quantity;
    }

}
