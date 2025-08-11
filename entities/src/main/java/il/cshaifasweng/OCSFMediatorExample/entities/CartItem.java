package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
public class CartItem implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    private int quantity;

    public CartItem() {}

    public CartItem(Product product, Cart cart, int quantity) {
        this.product = product;
        this.cart = cart;
        this.quantity = quantity;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() { return product; }
    public void setProduct(Product product) {
        this.product = product;
    }


    public void setCart(Cart cart) {
        this.cart = cart;
    }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}


