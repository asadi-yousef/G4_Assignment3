package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name ="Products")

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
    private double price;
    @Column(name = "discountPercentage")
    private double discountPercentage;

    @Column(name = "ImagePath")
    private String image_path;

    public Product() { }

    public Product(String name, String type, double price,String color, String image_path) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.image_path = image_path;
        this.color = color;
        this.discountPercentage = 0;
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
    public double getPrice() {
        return price;
    }
    public void setPrice(double price) {
        this.price = price;
    }
    public String getImagePath() {
        return this.image_path;
    }
    public void setImagePath(String image_path) {
        this.image_path = image_path;
    }
    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage = discountPercentage;
    }
    public String getColor() {
        return color;
    }
    public void setColor(String color) {
        this.color = color;
    }

    public double getSalePrice() {
        if (this.discountPercentage > 0) {
            double discountAmount = this.getPrice() * (this.discountPercentage / 100.0);
            return this.getPrice() - discountAmount;
        } else {
            return this.price;
        }
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