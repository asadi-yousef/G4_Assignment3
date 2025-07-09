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
    @Column(name="ID")
    private Long id;
    @Column
    private String name;
    @Column
    private Catalog catalog;
    @OneToMany(mappedBy = "branch", cascade = CascadeType.ALL)
    private List<Employee> employees;

    //getters & setters
    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name = name;
    }
    public Catalog getCatalog(){
        return catalog;
    }
    public void setCatalog(Catalog catalog){
        this.catalog = catalog;
    }
    public List<Employee> getEmployees(){
        return employees;
    }
    public void setEmployees(List<Employee> employees){
        this.employees = employees;
    }


}
