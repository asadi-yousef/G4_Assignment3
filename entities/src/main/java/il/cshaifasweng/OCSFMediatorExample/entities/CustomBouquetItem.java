package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * One line inside a CustomBouquet = a flower + quantity.
 * - Holds a (nullable) reference to the catalog Product used.
 * - Always stores snapshot name & unit price for history.
 */
@Entity
@Table(name = "custom_bouquet_items")
public class CustomBouquetItem implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bouquet_id", nullable = false)
    private CustomBouquet bouquet;

    // Reference to the base flower product; optional so history survives if product is disabled/deleted
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "flower_product_id")
    private Product flower;

    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    // Snapshots to preserve history even if the Product changes later
    @Column(name = "flower_name_snapshot", length = 255)
    private String flowerNameSnapshot;

    @Column(name = "unit_price_snapshot", precision = 10, scale = 2)
    private BigDecimal unitPriceSnapshot;

    public CustomBouquetItem() {}

    /** Convenience: build line from a Product, copying snapshots now. */
    public CustomBouquetItem(Product flower, int quantity) {
        this.flower = flower;
        this.quantity = Math.max(quantity, 0);
        if (flower != null) {
            this.flowerNameSnapshot = flower.getName();
            // If your Product.price is double, convert to BigDecimal; adjust if you have BigDecimal already.
            this.unitPriceSnapshot = BigDecimal.valueOf(flower.getSalePrice());
        }
    }

    /* ------------ Derived ------------ */

    /** Nullable subtotal; returns null if unit price is missing. */
    @Transient
    public BigDecimal getLineSubtotal() {
        if (unitPriceSnapshot == null) return null;
        return unitPriceSnapshot.multiply(BigDecimal.valueOf(Math.max(quantity, 0)));
    }

    /* ------------ Getters/Setters ------------ */

    public Long getId() { return id; }

    public CustomBouquet getBouquet() { return bouquet; }
    public void setBouquet(CustomBouquet bouquet) { this.bouquet = bouquet; }

    public Product getFlower() { return flower; }
    public void setFlower(Product flower) { this.flower = flower; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = Math.max(quantity, 0);
    }

    public String getFlowerNameSnapshot() { return flowerNameSnapshot; }
    public void setFlowerNameSnapshot(String flowerNameSnapshot) {
        this.flowerNameSnapshot = flowerNameSnapshot;
    }

    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot; }
    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot;
    }

    /* ------------ equals/hashCode/toString ------------ */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomBouquetItem)) return false;
        CustomBouquetItem that = (CustomBouquetItem) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() { return 31; }

    @Override
    public String toString() {
        return "CustomBouquetItem{id=" + id +
                ", flowerName='" + flowerNameSnapshot + '\'' +
                ", qty=" + quantity +
                ", unit=" + unitPriceSnapshot + "}";
    }
}
