package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Cart implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "customer_ID")
    private Customer customer;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<CartItem> items = new ArrayList<>();

    public Cart() {}

    public Cart(Customer customer) {
        this.customer = customer;
    }

    public Long getId() { return id; }

    public User getCustomer() { return customer; }

    public void setCustomer(Customer customer) { this.customer = customer; }

    public List<CartItem> getItems() { return items; }

    public void setItems(List<CartItem> items) { this.items = items; }
}
