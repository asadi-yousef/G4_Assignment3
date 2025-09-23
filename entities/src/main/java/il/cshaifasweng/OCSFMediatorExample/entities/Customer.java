package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Customer extends User implements Serializable {
    private static final long serialVersionUID = 1L;


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

    @Column
    private boolean frozen = false;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private CreditCard creditCard;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Subscription subscription;

    // Cart logic from first file
    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private Cart cart;

    @OneToOne(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Budget budget;

    public Customer() {
        super();
    }

    public Customer(String idNumber, String firstName,String lastName, String username, String password, boolean isNetworkAccount,
                    boolean isSubscribed, LocalDate subStartDate, LocalDate subExpDate, String email, String phone,
                    String address, String city, String country, String cardNumber,
                    int expirationMonth, int expirationYear, String cvv, Branch branch, Budget budget) {
        super(idNumber,firstName,lastName, username, password,branch,isNetworkAccount);
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.country = country;
        this.creditCard = new CreditCard(cardNumber, expirationMonth, expirationYear, cvv, this);
        this.isSubscribed = isSubscribed; // set the flag first
        if (isSubscribed) {
            // Expect end-exclusive semantics:
            LocalDate start = (subStartDate != null) ? subStartDate : LocalDate.now();
            LocalDate end   = (subExpDate   != null) ? subExpDate   : start.plusYears(1);
            this.subscription = new Subscription(start, end, true, this);
        } else {
            this.subscription = null; // branch/network without yearly sub → no Subscription row
        }

        this.budget = budget;

    }


    public void setIdNumber(String idNumber) {
        this.idNumber = idNumber;
    }
    public String getIdNumber() {
        return idNumber;
    }
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
    public void setSubscription(Subscription s) {
        if (this.subscription == s) return;
        if (this.subscription != null) this.subscription.setCustomer(null);
        this.subscription = s;
        if (s != null && s.getCustomer() != this) s.setCustomer(this);
    }

    public boolean hasValidSubscription() {
        return isSubscribed && subscription != null && subscription.isCurrentlyActive();
    }


    public Cart getCart() { return cart; }
    public void setCart(Cart cart) {
        this.cart = cart;
        if (cart != null) cart.setCustomer(this);
    }


    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean frozen) { this.frozen = frozen; }
    public boolean isSubscribed() {
        return isSubscribed;
    }
    public void setSubscribed(boolean isSubscribed) {
        this.isSubscribed = isSubscribed;
        if(!isSubscribed){
            if(this.subscription != null) this.subscription.setActive(false);
        }
    }
    public Budget getBudget() {
        return budget;
    }
    public void setBudget(Budget budget) {
        this.budget = budget;
        if(budget != null) budget.setCustomer(this);
    }

    //renewal subscription
    public void renewSubscriptionOneYear() {
        LocalDate today = LocalDate.now();

        if (this.getSubscription() == null) {
            // Buying when none exists: create fresh sub
            this.setSubscription(new Subscription(today, today.plusYears(1), true, this));
        } else {
            this.getSubscription().renewOneYear(); // extend/reactivate
        }

        // Keep flags consistent
        this.setSubscribed(this.getSubscription().isCurrentlyActive());
        this.setNetworkAccount(true); // yearly subscription implies network account
    }

}
