package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;

public class PriceUpdate implements Serializable {
    private static final long serialVersionUID = 123456789L;

    private int flowerId;
    private double newPrice;

    public PriceUpdate(Flower flower) {
        this.flowerId = flower.getId(); // Ensure Flower has a getId() method
        this.newPrice = flower.getPrice();
    }

    public int getFlowerId() {
        return flowerId;
    }

    public double getNewPrice() {
        return newPrice;
    }
}
