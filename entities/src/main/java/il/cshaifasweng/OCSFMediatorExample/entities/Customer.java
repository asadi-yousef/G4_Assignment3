package il.cshaifasweng.OCSFMediatorExample.entities;


import jakarta.persistence.*;

import java.io.Serializable;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "Customers")
public class Customer extends User implements Serializable {
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


    public Customer(String name, String username, String password,boolean isSigned, boolean isSubbed, String creditNumber, String email, String phone, String address, String city, String country) {
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
}
