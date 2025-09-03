package il.cshaifasweng.OCSFMediatorExample.client;

import javafx.beans.property.*;
import java.math.BigDecimal;

public class UserRow {
    public final LongProperty id = new SimpleLongProperty();
    public final StringProperty userType = new SimpleStringProperty();
    public final StringProperty username = new SimpleStringProperty();
    public final StringProperty name = new SimpleStringProperty();
    public final StringProperty branchName = new SimpleStringProperty();
    public final BooleanProperty network = new SimpleBooleanProperty();
    public final BooleanProperty loggedIn = new SimpleBooleanProperty();
    public final StringProperty role = new SimpleStringProperty();
    public final ObjectProperty<BigDecimal> budget = new SimpleObjectProperty<>();
    public final BooleanProperty frozen = new SimpleBooleanProperty();
    public final BooleanProperty dirty = new SimpleBooleanProperty(false);

    public long getId() { return id.get(); }
    public void setId(long v) { id.set(v); }
    public LongProperty idProperty() { return id; }

    public String getUserType() { return userType.get(); }
    public void setUserType(String v) { userType.set(v); }
    public StringProperty userTypeProperty() { return userType; }

    public String getUsername() { return username.get(); }
    public void setUsername(String v) { username.set(v); }
    public StringProperty usernameProperty() { return username; }

    public String getName() { return name.get(); }
    public void setName(String v) { name.set(v); }
    public StringProperty nameProperty() { return name; }

    public String getBranchName() { return branchName.get(); }
    public void setBranchName(String v) { branchName.set(v); }
    public StringProperty branchNameProperty() { return branchName; }

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
