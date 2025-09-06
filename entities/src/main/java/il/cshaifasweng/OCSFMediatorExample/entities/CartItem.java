package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "cart_items")
@Check(constraints = "( (product_id IS NOT NULL) <> (custom_bouquet_id IS NOT NULL) )") // XOR at DB level
public class CartItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optional: for bouquet-only items this is null
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

    @Column(nullable = false)
    private int quantity;

    public CartItem() {} // for JPA

    // Product ctor
    public CartItem(Product product, Cart cart, int quantity) {
        this.product = Objects.requireNonNull(product, "product must not be null");
        this.customBouquet = null;
        this.cart = Objects.requireNonNull(cart, "cart must not be null");
        this.quantity = positive(quantity);
    }

    // Bouquet ctor
    public CartItem(CustomBouquet customBouquet, Cart cart, int quantity) {
        this.product = null;
        this.customBouquet = Objects.requireNonNull(customBouquet, "customBouquet must not be null");
        this.cart = Objects.requireNonNull(cart, "cart must not be null");
        this.quantity = positive(quantity);
    }

    private static int positive(int q) {
        if (q <= 0) throw new IllegalArgumentException("quantity must be > 0");
        return q;
    }

    /* ---------- Lifecycle validation (enforce XOR, non-null cart) ---------- */
    @PrePersist @PreUpdate
    private void validateXor() {
        boolean hasProduct = (product != null);
        boolean hasBouquet = (customBouquet != null);
        if (hasProduct == hasBouquet) {
            throw new IllegalStateException("CartItem must have exactly one of {product, customBouquet}");
        }
        if (cart == null) {
            throw new IllegalStateException("CartItem.cart must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalStateException("CartItem.quantity must be > 0");
        }
    }

    /* ---------- Accessors ---------- */
    public Long getId() { return id; }

    public Product getProduct() { return product; }
    /** Setting a product clears the bouquet to keep XOR invariant. */
    public void setProduct(Product product) {
        this.product = product;
        if (product != null) this.customBouquet = null;
    }

    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }

    public CustomBouquet getCustomBouquet() { return customBouquet; }
    /** Setting a bouquet clears the product to keep XOR invariant. */
    public void setCustomBouquet(CustomBouquet customBouquet) {
        this.customBouquet = customBouquet;
        if (customBouquet != null) this.product = null;
    }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = positive(quantity); }

    /* ---------- UI-friendly, non-persistent helpers ---------- */
    @Transient
    public boolean isBouquet() { return customBouquet != null; }

    @Transient
    public boolean isProductItem() { return product != null; }

    @Transient
    public String getDisplayName() {
        if (isBouquet()) return "Custom Bouquet";
        return (product != null) ? product.getName() : "";
    }

    @Transient
    public double getDisplayUnitPrice() {
        if (isBouquet()) {
            // totalPrice is BigDecimal (per your model). Use double for UI display.
            return (customBouquet != null && customBouquet.getTotalPrice() != null)
                    ? customBouquet.getTotalPrice().doubleValue()
                    : 0.0;
        }
        return (product != null) ? product.getPrice() : 0.0;
    }

    @Transient
    public String getDisplayImagePath() {
        if (isBouquet()) {
            final String R = "/il/cshaifasweng/OCSFMediatorExample/client/images/custombouquet.png";
            java.net.URL url = CartItem.class.getResource(R);
            return (url != null) ? url.toExternalForm() : R;
        }
        return (product != null) ? product.getImagePath() : null;
    }

    @Transient
    public double getSubtotal() {
        // FIX: respect XOR (bouquet OR product)
        if (isBouquet()) {
            return (customBouquet != null && customBouquet.getTotalPrice() != null)
                    ? customBouquet.getTotalPrice().doubleValue() * quantity
                    : 0.0;
        }
        return (product != null) ? product.getPrice() * quantity : 0.0;
    }
}
