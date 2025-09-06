package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name ="Users")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="ID")
    protected Long id;
    @Column(unique = true, nullable = false)
    protected String idNumber;
    @Column
    protected String firstName;
    @Column
    protected String lastName;
    @Column(unique = true, nullable = false)
    protected String username;
    @Column
    protected String password;
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "branch_id",nullable = true)
    protected Branch branch;
    @Column
    protected boolean isNetworkAccount;
    protected boolean isLoggedIn;

    public User() {

    }

    public User(String idNumber, String firstName,String lastName, String username, String password,Branch branch, boolean isNetworkAccount) {
        this.idNumber = idNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.username = username;
        this.password = password;
        this.branch = branch;
        this.isNetworkAccount = isNetworkAccount;
        this.isLoggedIn = false;
    }

    public Long getId() {return id;}
    public void setId(Long id) {this.id = id;}
    public String getIdNumber() {return idNumber;}
    public void setIdNumber(String idNumber) {this.idNumber = idNumber;}
    public String getFirstName() {return firstName;}
    public void setFirstName(String firstName) {this.firstName = firstName;}
    public String getLastName() {return lastName;}
    public void setLastName(String lastName) {this.lastName = lastName;}
    public String getUsername() {return username;}
    public void setUsername(String username) {this.username = username;}
    public String getPassword() {return password;}
    public void setPassword(String password) {this.password = password;}
    public Branch getBranch() {return branch;}
    public void setBranch(Branch branch) {this.branch = branch;}
    public boolean isNetworkAccount() {return isNetworkAccount;}
    public void setNetworkAccount(boolean isNetworkAccount) {this.isNetworkAccount = isNetworkAccount;}
    public boolean isLoggedIn() {return isLoggedIn;}
    public void setLoggedIn(boolean isLoggedIn) {this.isLoggedIn = isLoggedIn;}
}
