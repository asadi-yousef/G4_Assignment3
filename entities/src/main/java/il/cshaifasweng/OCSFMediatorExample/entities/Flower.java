package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;

public class Flower implements Serializable {
    private static final long serialVersionUID = -5912738471623457890L;
    private String name;
    private String type;
    private final int id;
    private double price;
    private byte[] image;

    public Flower(String name, String type, int id, double price, byte[] image) {
        this.name = name;
        this.type = type;
        this.id = id;
        this.price = price;
        this.image = image;
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
    public void setPrice(int price) {
        this.price = price;
    }
    public byte[] getImage() {
        return image;
    }
    public void setImage(byte[] image) {
        this.image = image;
    }
}
