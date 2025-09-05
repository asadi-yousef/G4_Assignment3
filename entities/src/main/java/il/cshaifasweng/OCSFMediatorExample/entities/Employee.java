package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Inheritance(strategy = InheritanceType.JOINED)
@Table(name = "employees")
public class Employee extends User implements Serializable {
    private static final long serialVersionUID = 1L;
    @Column
    private String role;


    public Employee(String idNumber,String firstName,String lastName, String username, String password, String role, Branch branch, boolean isNetworkEmployee) {
        super(idNumber, firstName,lastName,username, password,branch,isNetworkEmployee);
        this.role = role;
    }

    public Employee() {
        super();
    }

    public String getRole() {return role;}
    public void setRole(String role) {this.role = role;}
}
