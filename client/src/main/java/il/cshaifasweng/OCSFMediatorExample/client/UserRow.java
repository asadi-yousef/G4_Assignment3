package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.beans.property.*;
import il.cshaifasweng.OCSFMediatorExample.entities.Branch;

import java.math.BigDecimal;

public class UserRow {
    private final LongProperty id = new SimpleLongProperty();
    private final StringProperty userType = new SimpleStringProperty();
    private final StringProperty idNumber = new SimpleStringProperty();
    private final StringProperty username = new SimpleStringProperty();
    private final StringProperty firstName = new SimpleStringProperty();
    private final StringProperty lastName = new SimpleStringProperty();

    private final ObjectProperty<Branch> branch = new SimpleObjectProperty<>();
    private final BooleanProperty network = new SimpleBooleanProperty();
    private final BooleanProperty loggedIn = new SimpleBooleanProperty();

    private final StringProperty role = new SimpleStringProperty();                 // employees
    private final ObjectProperty<BigDecimal> budget = new SimpleObjectProperty<>(); // customers
    private final BooleanProperty frozen = new SimpleBooleanProperty();             // customers

    private final BooleanProperty dirty = new SimpleBooleanProperty(false);

    // getters/setters + properties
    public long getId() { return id.get(); }
    public void setId(long v) { id.set(v); }
    public LongProperty idProperty() { return id; }

    public String getUserType() { return userType.get(); }
    public void setUserType(String v) { userType.set(v); }
    public StringProperty userTypeProperty() { return userType; }

    public String getIdNumber() { return idNumber.get(); }
    public void setIdNumber(String v) { idNumber.set(v); }
    public StringProperty idNumberProperty() { return idNumber; }

    public String getUsername() { return username.get(); }
    public void setUsername(String v) { username.set(v); }
    public StringProperty usernameProperty() { return username; }

    public String getFirstName() { return firstName.get(); }
    public void setFirstName(String v) { firstName.set(v); }
    public StringProperty firstNameProperty() { return firstName; }

    public String getLastName() { return lastName.get(); }
    public void setLastName(String v) { lastName.set(v); }
    public StringProperty lastNameProperty() { return lastName; }

    public Branch getBranch() { return branch.get(); }
    public void setBranch(Branch b) { branch.set(b); }
    public ObjectProperty<Branch> branchProperty() { return branch; }

    public boolean isNetwork() { return network.get(); }
    public void setNetwork(boolean v) { network.set(v); }
    public BooleanProperty networkProperty() { return network; }

    public boolean isLoggedIn() { return loggedIn.get(); }
    public void setLoggedIn(boolean v) { loggedIn.set(v); }
    public BooleanProperty loggedInProperty() { return loggedIn; }

    public String getRole() { return role.get(); }
    public void setRole(String v) { role.set(v); }
    public StringProperty roleProperty() { return role; }

    public BigDecimal getBudget() { return budget.get(); }
    public void setBudget(BigDecimal v) { budget.set(v); }
    public ObjectProperty<BigDecimal> budgetProperty() { return budget; }

    public boolean isFrozen() { return frozen.get(); }
    public void setFrozen(boolean v) { frozen.set(v); }
    public BooleanProperty frozenProperty() { return frozen; }

    public boolean isDirty() { return dirty.get(); }
    public void setDirty(boolean v) { dirty.set(v); }
    public BooleanProperty dirtyProperty() { return dirty; }
}
