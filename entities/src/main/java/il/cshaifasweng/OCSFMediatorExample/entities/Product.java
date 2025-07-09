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
    @Column(name = "price")
    private double price;

    @Column(name = "ImagePath")
    private String image_path;

    public Product() { }

    public Product(String name, String type, double price, String image_path) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.image_path = image_path;
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

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Product product = (Product) obj;
        return Objects.equals(name, product.name) &&
                Objects.equals(type, product.type) &&
                Double.compare(product.price, price) == 0 &&
                Objects.equals(image_path, product.image_path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, price,image_path);
    }

}