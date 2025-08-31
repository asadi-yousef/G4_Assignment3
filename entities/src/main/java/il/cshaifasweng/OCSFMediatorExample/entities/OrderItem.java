package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Optional for bouquet-only lines
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    // Custom bouquet owned by this order line
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "custom_bouquet_id")
    private CustomBouquet customBouquet;

    private int quantity;

    /* --------- SNAPSHOT COLUMNS (persisted) --------- */
    @Column(name = "name_snapshot", length = 255, nullable = false)
    private String nameSnapshot;

    @Column(name = "unit_price_snapshot", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPriceSnapshot = BigDecimal.ZERO;

    @Column(name = "image_path_snapshot", length = 500)
    private String imagePathSnapshot;

    public OrderItem() { }

    // Product line
    public OrderItem(Order order, Product product, int quantity) {
        this.order = order;
        this.product = product;
        this.quantity = Math.max(1, quantity);
        snapshotFromProduct(product);
    }

    // Bouquet line
    public OrderItem(Order order, CustomBouquet customBouquet, int quantity) {
        this.order = order;
        this.customBouquet = customBouquet;
        this.quantity = Math.max(1, quantity);
        snapshotFromBouquet(customBouquet);
    }

    /* --------- Snapshot helpers --------- */

    public void snapshotFromProduct(Product p) {
        if (p == null) return;
        this.nameSnapshot = p.getName();
        this.unitPriceSnapshot = BigDecimal.valueOf(p.getSalePrice());
        this.imagePathSnapshot = p.getImagePath();
    }

    public void snapshotFromBouquet(CustomBouquet bq) {
        this.nameSnapshot = "Custom Bouquet";
        this.unitPriceSnapshot = (bq != null && bq.getTotalPrice() != null)
                ? bq.getTotalPrice()
                : BigDecimal.ZERO;
        // Optional icon kept for history (your UI uses a classpath image anyway)
        this.imagePathSnapshot = "/il/cshaifasweng/OCSFMediatorExample/client/images/custombouquet.png";
    }

    /* --------- Getters/Setters --------- */

    public Long getId() { return id; }

    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public CustomBouquet getCustomBouquet() { return customBouquet; }
    public void setCustomBouquet(CustomBouquet customBouquet) { this.customBouquet = customBouquet; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = Math.max(1, quantity); }

    public String getNameSnapshot() { return nameSnapshot; }
    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public String getImagePathSnapshot() { return imagePathSnapshot; }

    /* ---------- UI-friendly helpers now prefer snapshots ---------- */

    @Transient
    public boolean isBouquet() { return customBouquet != null; }

    @Transient
    public String getDisplayName() {
        return (nameSnapshot != null && !nameSnapshot.isEmpty())
                ? nameSnapshot
                : (isBouquet() ? "Custom Bouquet" : (product != null ? product.getName() : ""));
    }

    @Transient
    public double getDisplayUnitPrice() {
        if (unitPriceSnapshot != null) return unitPriceSnapshot.doubleValue();
        if (isBouquet()) return customBouquet != null && customBouquet.getTotalPrice() != null
                ? customBouquet.getTotalPrice().doubleValue() : 0.0;
        return product != null ? product.getPrice() : 0.0;
    }

    @Transient
    public String getDisplayImagePath() {
        if (imagePathSnapshot != null && !imagePathSnapshot.isEmpty()) return imagePathSnapshot;
        if (isBouquet()) {
            final String R = "/il/cshaifasweng/OCSFMediatorExample/client/images/custombouquet.png";
            java.net.URL url = OrderItem.class.getResource(R);
            return (url != null) ? url.toExternalForm() : R;
        }
        return (product != null) ? product.getImagePath() : null;
    }
}
