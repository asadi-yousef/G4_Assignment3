package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Customer extends User implements Serializable {
    private static final long serialVersionUID = 1L;

    @Column
    private boolean isNetworkAccount;

    @Column
    private boolean isSubscribed;

    @Column
    private String email;
    @Column
    private String phone;
    @Column
    private String address;
    @Column
    private String city;
    @Column
    private String country;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreditCard creditCard;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Subscription subscription;

    // Cart logic from first file
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    public Customer() {
        super();
    }

    public Customer(String name, String username, String password, boolean isNetworkAccount,
                    boolean isSubscribed, LocalDate subStartDate, LocalDate subExpDate, String email, String phone,
                    String address, String city, String country, String cardNumber,
                    int expirationMonth, int expirationYear, String cvv, Branch branch) {
        super(name, username, password);
        this.isNetworkAccount = isNetworkAccount;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.country = country;
        this.creditCard = new CreditCard(cardNumber, expirationMonth, expirationYear, cvv, this);
        this.subscription = new Subscription(subStartDate, subExpDate, true, this);
        this.branch = branch;
    }

    public boolean isNetworkAccount() { return isNetworkAccount; }
    public void setNetworkAccount(boolean networkAccount) { isNetworkAccount = networkAccount; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public CreditCard getCreditCard() { return creditCard; }
    public void setCreditCard(CreditCard creditCard) {
        this.creditCard = creditCard;
        creditCard.setCustomer(this); // keep both sides in sync
    }

    public Subscription getSubscription() { return subscription; }
    public void setSubscription(Subscription subscription) {
        this.subscription = subscription;
        subscription.setCustomer(this); // keep both sides in sync
    }

    // Cart methods from first file
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) {
        this.cart = cart;
        if (cart != null) cart.setCustomer(this);
    }

    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) {
        this.branch = branch;
    }
}
