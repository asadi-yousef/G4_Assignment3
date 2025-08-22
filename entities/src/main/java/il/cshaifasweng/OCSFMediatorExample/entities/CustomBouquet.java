package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name = "Bouquets")
public class CustomBouquet implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID")
    private Long id;

    @Column(name="title")
    private String title; // e.g. "Custom Bouquet"

    @OneToMany(mappedBy = "bouquet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BouquetItem> items = new ArrayList<>();

    @Column(name="subtotal") private double subtotal;
    @Column(name="discount") private double discount;
    @Column(name="total")    private double total;

    protected CustomBouquet() {}

    // Convenience constructor for creating a new bouquet
    public CustomBouquet(String title) {
        this.title = title;
        this.subtotal = 0.0;
        this.discount = 0.0;
        this.total = 0.0;
    }

    public void addItem(Product flower, int qty) {
        if (!"FLOWER".equalsIgnoreCase(flower.getType()))
            throw new IllegalArgumentException("Only FLOWER products allowed");
        items.add(new BouquetItem(this, flower, qty));
    }

    // getters/setters...
    public double getSubtotal() { return subtotal; }
    public double getDiscount() { return discount; }
    public double getTotal() { return total; }
    public List<BouquetItem> getItems() { return items; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }
    public void setDiscount(double discount) { this.discount = discount; }
    public void setTotal(double total) { this.total = total; }
    public void setItems(List<BouquetItem> items) { this.items = items; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

}
