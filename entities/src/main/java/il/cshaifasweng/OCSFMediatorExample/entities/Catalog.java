package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.Entity;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;

public class Catalog implements Serializable {
    private List<Product> products = new ArrayList<>();


    public Catalog(List<Product> products) {
        this.products = products;
    }

    public List<Product> getFlowers() {
        return products;
    }

    public void setFlowers(List<Product> products) {
        this.products = products;
    }
}
