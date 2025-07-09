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

    public Employee(String name, String username, String password) {
        super(name, username, password);
    }

    public String getRole() {return role;}
    public void setRole(String role) {this.role = role;}
}
