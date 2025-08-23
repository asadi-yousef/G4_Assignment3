package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "custom_bouquets")
public class CustomBouquet implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Optional: who created it (helps cleanup/audit). Keep optional to allow system-created bouquets too.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "customer_id")
    private Customer createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Optional free-text instructions (wrapping color, card text, etc.)
    @Column(name = "instructions", length = 500)
    private String instructions;

    @OneToMany(
            mappedBy = "bouquet",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CustomBouquetItem> items = new ArrayList<>();

    @Column(name = "total_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    public CustomBouquet() {}

    public CustomBouquet(Customer createdBy, String instructions) {
        this.createdBy = createdBy;
        this.instructions = instructions;
    }

    /* ------------ Domain helpers ------------ */

    public void addItem(CustomBouquetItem item) {
        Objects.requireNonNull(item, "item");
        item.setBouquet(this);
        items.add(item);
        recomputeTotalPrice();
    }

    public void removeItem(CustomBouquetItem item) {
        if (item == null) return;
        if (items.remove(item)) {
            item.setBouquet(null);
            recomputeTotalPrice();
        }
    }

    /** Recalculate from item snapshots (unitPriceSnapshot * quantity). */
    public void recomputeTotalPrice() {
        BigDecimal sum = BigDecimal.ZERO;
        for (CustomBouquetItem it : items) {
            BigDecimal unit = it.getUnitPriceSnapshot() != null ? it.getUnitPriceSnapshot() : BigDecimal.ZERO;
            BigDecimal qty  = BigDecimal.valueOf(Math.max(it.getQuantity(), 0));
            sum = sum.add(unit.multiply(qty));
        }
        this.totalPrice = sum;
    }

    @PrePersist @PreUpdate
    private void onPersistOrUpdate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        // Make sure price is always consistent with items
        recomputeTotalPrice();
    }

    /* ------------ Getters/Setters ------------ */

    public Long getId() { return id; }

    public Customer getCreatedBy() { return createdBy; }
    public void setCreatedBy(Customer createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }

    public List<CustomBouquetItem> getItems() { return items; }
    public void setItems(List<CustomBouquetItem> items) {
        this.items.clear();
        if (items != null) {
            for (CustomBouquetItem it : items) addItem(it);
        }
    }

    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) {
        // You can allow manual override if you add a service-level flag/discounts.
        this.totalPrice = totalPrice != null ? totalPrice : BigDecimal.ZERO;
    }

    /* ------------ equals/hashCode/toString ------------ */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomBouquet)) return false;
        CustomBouquet that = (CustomBouquet) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() { return 31; }

    @Override
    public String toString() {
        return "CustomBouquet{id=" + id + ", items=" + (items != null ? items.size() : 0) +
                ", totalPrice=" + totalPrice + "}";
    }
}
