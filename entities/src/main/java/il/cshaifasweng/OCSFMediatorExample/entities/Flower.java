package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.util.Arrays;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name ="flowers")

public class Flower implements Serializable {
    private static final long serialVersionUID = -5912738471623457890L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID")
    private int id;
    @Column(name = "name")
    private String name;
    @Column(name = "type")
    private String type;
    @Column(name = "price")
    private double price;

    @Lob
    private String image_path;

    public Flower() { }

    public Flower(String name, String type, double price,String image_path) {
        this.name = name;
        this.type = type;
        this.price = price;
        this.image_path = image_path;
    }
    public void setId(int id) {
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
    public int getId() {
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
        Flower flower = (Flower) obj;
        return Objects.equals(name, flower.name) &&
                Objects.equals(type, flower.type) &&
                Double.compare(flower.price, price) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, price);
    }

}