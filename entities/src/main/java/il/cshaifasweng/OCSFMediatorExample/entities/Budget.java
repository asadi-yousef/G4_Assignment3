package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
public class Budget implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private Customer customer;

    @Column(nullable = false)
    private double balance = 0.0;

    public Budget() {}

    public Budget(Customer customer) {
        this.customer = customer;
        this.balance = 0.0;
    }

    // --- Business logic methods ---
    public void addFunds(double amount) {
        if (amount > 0) {
            this.balance += amount;
        }
    }

    public boolean useFunds(double amount) {
        if (amount > 0 && balance >= amount) {
            this.balance -= amount;
            return true;
        }
        return false;
    }
    public void subtractFunds(double amount) {
        if(amount > balance) throw new IllegalArgumentException("Not enough budget");
        balance -= amount;
    }

    // --- Getters/Setters ---
    public Long getId() {
        return id;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
