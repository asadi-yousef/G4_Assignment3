package il.cshaifasweng.OCSFMediatorExample.entities;


import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
public class Customer extends User {
    private static final long serialVersionUID = 1L;
    @Column
    private boolean isSigned;
    @Column
    private boolean isSubbed;
    @Column
    private String creditNumber;
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
    @JoinColumn(name = "cart_id")
    private Cart cart;


    public Customer(String name, String username, String password, boolean isSigned, boolean isSubbed, String creditNumber, String email, String phone, String address, String city, String country) {
        super(name, username, password);
        this.isSigned = isSigned;
        this.isSubbed = isSubbed;
        this.creditNumber = creditNumber;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.city = city;
        this.country = country;
    }

    public Customer() {
        super();
    }

    public boolean isSigned() {
        return isSigned;
    }
    public void setSigned(boolean signed) {
        isSigned = signed;
    }
    public boolean isSubbed() {
        return isSubbed;
    }
    public void setSubbed(boolean subbed) {
        isSubbed = subbed;
    }
    public String getCreditNumber() {
        return creditNumber;
    }
    public void setCreditNumber(String creditNumber) {
        this.creditNumber = creditNumber;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }
    public String getAddress() {
        return address;
    }
    public void setAddress(String address) {
        this.address = address;
    }
    public String getCity() {
        return city;
    }
    public void setCity(String city) {
        this.city = city;
    }
    public String getCountry() {
        return country;
    }
    public void setCountry(String country) {
        this.country = country;
    }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) {
        this.cart = cart;
        if (cart != null) cart.setCustomer(this);
    }

}

