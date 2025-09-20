package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;

public class AuthUserDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long   id;
    private String userType;     // "CUSTOMER" | "EMPLOYEE"
    private String username;
    private String firstName;
    private String lastName;

    // optional/convenience (safe for client)
    private Boolean networkAccount;
    private Long   branchId;
    private String branchName;
    private String role;         // only for EMPLOYEE
    private Boolean frozen;      // only for CUSTOMER
    private double budget;
    private boolean isSubbed;

    public AuthUserDTO() {}

    public AuthUserDTO(Long id, String userType, String username, String firstName, String lastName,
                       Boolean networkAccount, Long branchId, String branchName, String role, Boolean frozen) {
        this.id = id;
        this.userType = userType;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.networkAccount = networkAccount;
        this.branchId = branchId;
        this.branchName = branchName;
        this.role = role;
        this.frozen = frozen;
    }

    public Long getId() { return id; }
    public String getUserType() { return userType; }
    public String getUsername() { return username; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Boolean getNetworkAccount() { return networkAccount; }
    public Long getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public String getRole() { return role; }
    public Boolean getFrozen() { return frozen; }

    public void setId(Long id) { this.id = id; }
    public void setUserType(String userType) { this.userType = userType; }
    public void setUsername(String username) { this.username = username; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setNetworkAccount(Boolean networkAccount) { this.networkAccount = networkAccount; }
    public void setBranchId(Long branchId) { this.branchId = branchId; }
    public void setBranchName(String branchName) { this.branchName = branchName; }
    public void setRole(String role) { this.role = role; }
    public void setFrozen(Boolean frozen) { this.frozen = frozen; }
    public void setBudget(double budget) { this.budget = budget; }
    public double getBudget() { return budget; }

    public boolean isSubscribed() {
        return isSubbed;
    }
}
