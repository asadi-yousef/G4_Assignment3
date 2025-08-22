package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity @Table(name = "BouquetItems")
public class BouquetItem implements Serializable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID")
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name="bouquet_id")
    private CustomBouquet bouquet;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name="product_id")
    private Product product; // must be type=FLOWER

    @Column(name="quantity", nullable = false)
    private int quantity;

    protected BouquetItem() {}
    public BouquetItem(CustomBouquet b, Product p, int q) { this.bouquet = b; this.product = p; this.quantity = q; }

    // getters/setters...
    public CustomBouquet getBouquet() { return bouquet; }
    public Product getProduct() { return product; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int q) { quantity = q; }
    public void setBouquet(CustomBouquet b) { bouquet = b; }
    public void setProduct(Product p) { product = p; }

}
