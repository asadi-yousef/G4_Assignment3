package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SoftDelete;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Entity
@Table(name ="Products")
@SoftDelete(columnName = "deleted")
public class Product implements Serializable {
    private static final long serialVersionUID = -5912738471623457890L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "type")
    private String type;

    @Column(name = "color")
    private String color;

    /** Keep price as BigDecimal for accurate money calculations */
    @Column(name = "price")
    private BigDecimal price;

    /** Change discountPercentage to a primitive double */
    @Column(name = "discountPercentage")
    private double discountPercentage;

    @Column(name = "isDisabled")
    private boolean isDisabled;

    @Column(name = "ImagePath")
    private String imagePath;

    public Product() { }

    public Product(String name, String type, BigDecimal price, String color, String imagePath) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.imagePath = imagePath;
        this.color = color;
        this.discountPercentage = 0.0;
        this.isDisabled = false;
    }

    // --- Getters / Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    /** now returns primitive double */
    public double getDiscountPercentage() { return discountPercentage; }

    /** accept primitive double, clamp to [0,100] */
    public void setDiscountPercentage(double discountPercentage) {
        if (discountPercentage < 0) discountPercentage = 0;
        if (discountPercentage > 100) discountPercentage = 100;
        this.discountPercentage = discountPercentage;
    }

    public boolean isDisabled() { return isDisabled; }
    public void setDisabled(boolean disabled) { isDisabled = disabled; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    /** Compute sale price using BigDecimal math for currency, but percentage as double */
    public BigDecimal getSalePrice() {
        if (discountPercentage > 0.0) {
            BigDecimal discountAmount = price
                    .multiply(BigDecimal.valueOf(discountPercentage))
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            return price.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }

    public void deleteProduct() {
        this.isDisabled = true;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Product product = (Product) obj;
        return Objects.equals(id, product.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
