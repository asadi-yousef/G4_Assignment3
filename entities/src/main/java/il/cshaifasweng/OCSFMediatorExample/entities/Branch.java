package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "Branches")
public class Branch implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column
    private String name;

    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    private List<Customer> customers;

    @OneToMany(mappedBy = "branch", fetch = FetchType.LAZY)
    private List<Employee> employees;

    public Branch() {}

    public Branch(String name) {
        this.name = name;
    }

    public void addCustomer(Customer customer) {
        customers.add(customer);
    }
    public void addEmployee(Employee employee) {
        employees.add(employee);
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName(){
        return name;
    }

    public void setName(String name){
        this.name = name;
    }

    public List<Customer> getCustomers() {
        return customers;
    }

    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }

    public List<Employee> getEmployees() {
        return employees;
    }

    public void setEmployees(List<Employee> employees) {
        this.employees = employees;
    }
}
