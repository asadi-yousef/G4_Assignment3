package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.Entity;

import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;

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
    public Product getProductById(Long id) {
        for (Product product : products) {
            if (Objects.equals(product.getId(), id)) {
                return product;
            }
        }
        return null; // or throw an exception if not found
    }
    public void addProduct(Product product) {
        products.add(product);
    }
    public void removeProduct(Product product) {
        products.remove(product);
    }
    public void editProduct(Product product) {
        products.set(products.indexOf(product), product);
    }

}
