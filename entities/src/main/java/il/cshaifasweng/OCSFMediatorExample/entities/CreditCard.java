package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
public class CreditCard implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String cardNumber;

    @Column(nullable = false)
    private int expirationMonth;

    @Column(nullable = false)
    private int expirationYear;

    @Column(nullable = false)
    private String cvv;

    // Bidirectional mapping: each CreditCard belongs to one Customer
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    public CreditCard() {}

    public CreditCard(String cardNumber, int expirationMonth, int expirationYear, String cvv, Customer customer) {
        this.cardNumber = cardNumber;
        this.expirationMonth = expirationMonth;
        this.expirationYear = expirationYear;
        this.cvv = cvv;
        this.customer = customer;
    }

    // Getters and setters
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }

    public int getExpirationMonth() { return expirationMonth; }
    public void setExpirationMonth(int expirationMonth) { this.expirationMonth = expirationMonth; }
    public int getExpirationYear() { return expirationYear; }
    public void setExpirationYear(int expirationYear) { this.expirationYear = expirationYear; }

    public String getCvv() { return cvv; }
    public void setCvv(String cvv) { this.cvv = cvv; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
}
