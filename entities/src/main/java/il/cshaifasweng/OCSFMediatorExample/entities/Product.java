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
    @Column(name = "price")
    private BigDecimal price;
    @Column(name = "discountPercentage")
    private BigDecimal discountPercentage;
    @Column(name = "isDisabled")
    private boolean isDisabled;
    @Column(name = "ImagePath")
    private String image_path;

    public Product() { }

    public Product(String name, String type, BigDecimal price,String color, String image_path) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.image_path = image_path;
        this.color = color;
        this.discountPercentage = BigDecimal.ZERO;
        this.isDisabled = false;
    }

    public void setDisabled(boolean isDisabled) {
        this.isDisabled = isDisabled;
    }
    public boolean isDisabled() {
        return isDisabled;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }
    public Long getId() {
        return id;
    }
    public BigDecimal getPrice() {
        return price;
    }
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    public String getImagePath() {
        return this.image_path;
    }
    public void setImagePath(String image_path) {
        this.image_path = image_path;
    }
    public BigDecimal getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(BigDecimal discountPercentage) {
        this.discountPercentage = discountPercentage;
    }
    public String getColor() {
        return color;
    }
    public void setColor(String color) {
        this.color = color;
    }

    public BigDecimal getSalePrice() {
        if (this.discountPercentage.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal discountAmount = this.getPrice()
                    .multiply(this.discountPercentage)
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

            return this.getPrice().subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);
        } else {
            return this.getPrice().setScale(2, RoundingMode.HALF_UP);
        }
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