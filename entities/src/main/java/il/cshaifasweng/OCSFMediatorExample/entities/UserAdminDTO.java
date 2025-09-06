package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.math.BigDecimal;

public class UserAdminDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String userType;         // "CUSTOMER" | "EMPLOYEE"
    private String idNumber;
    private String firstName;
    private String lastName;
    private String username;
    private String password;

    private Long   branchId;         // nullable
    private String branchName;       // optional convenience
    private Boolean networkAccount;  // true = network (no branch)
    private Boolean loggedIn;

    // extras
    private String role;             // employees
    private BigDecimal budget;       // customers
    private Boolean frozen;          // customers

    public UserAdminDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserType() { return userType; }
    public void setUserType(String userType) { this.userType = userType; }

    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Long getBranchId() { return branchId; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }

    public String getBranchName() { return branchName; }
    public void setBranchName(String branchName) { this.branchName = branchName; }

    public Boolean getNetworkAccount() { return networkAccount; }
    public void setNetworkAccount(Boolean networkAccount) { this.networkAccount = networkAccount; }

    public Boolean getLoggedIn() { return loggedIn; }
    public void setLoggedIn(Boolean loggedIn) { this.loggedIn = loggedIn; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public BigDecimal getBudget() { return budget; }
    public void setBudget(BigDecimal budget) { this.budget = budget; }

    public Boolean getFrozen() { return frozen; }
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
}
