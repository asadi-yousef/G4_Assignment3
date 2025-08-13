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
    @Column
    private boolean isNetworkEmployee;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id")
    private Branch branch;

    public Employee(String name, String username, String password, String role, Branch branch, boolean isNetworkEmployee) {
        super(name, username, password);
        this.role = role;
        this.branch = branch;
        this.isNetworkEmployee = isNetworkEmployee;
    }

    public Employee() {
        super();
    }

    public String getRole() {return role;}
    public void setRole(String role) {this.role = role;}
    public Branch getBranch() {
        return branch;
    }
    public void setBranch(Branch branch) {
        this.branch = branch;
    }
    public boolean isNetworkEmployee() {
        return isNetworkEmployee;
    }
    public void setNetworkEmployee(boolean isNetworkEmployee) {
        this.isNetworkEmployee = isNetworkEmployee;
    }
}
