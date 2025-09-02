package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.UserAdminDTO;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.converter.BigDecimalStringConverter;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class AdminUsersController {

    @FXML private TableView<UserRow> table;
    @FXML private TableColumn<UserRow,String> colType, colUsername, colName, colBranch, colRole;
    @FXML private TableColumn<UserRow,Boolean> colNetwork, colLogged, colFrozen;
    @FXML private TableColumn<UserRow,BigDecimal> colBudget;
    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    private final ObservableList<UserRow> data = FXCollections.observableArrayList();
    private int offset = 0;
    private String currentSearch = "";
    private static final int PAGE = 50;

    // Allowed roles (lowercase)
    private static final ObservableList<String> ROLE_OPTIONS =
            FXCollections.observableArrayList("employee","manager","systemadmin","customerservice");

    private SimpleClient client;

    @FXML
    private void initialize() {
        client = SimpleClient.getClient();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
        table.setItems(data);
        table.setEditable(true);

        colType.setCellValueFactory(c -> c.getValue().userType);
        colUsername.setCellValueFactory(c -> c.getValue().username);
        colName.setCellValueFactory(c -> c.getValue().name);
        colBranch.setCellValueFactory(c -> c.getValue().branchName);
        colNetwork.setCellValueFactory(c -> c.getValue().network.asObject());
        colLogged.setCellValueFactory(c -> c.getValue().loggedIn.asObject());
        colRole.setCellValueFactory(c -> c.getValue().role);
        colBudget.setCellValueFactory(c -> c.getValue().budget);
        colFrozen.setCellValueFactory(c -> c.getValue().frozen.asObject());

        // Editable columns
        makeEditable(colName);
        makeEditable(colUsername);
        // REMOVE free-text editing for Role; replace with strict ComboBox:
        makeRoleCombo(colRole);
        makeEditableBudget(colBudget);

        // initial load
        refresh(true);
    }

    private void makeEditable(TableColumn<UserRow, String> col) {
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setOnEditCommit(ev -> {
            UserRow row = ev.getRowValue();
            if (col == colName) row.name.set(ev.getNewValue());
            else if (col == colUsername) row.username.set(ev.getNewValue());
            row.dirty.set(true);
        });
    }

    private void makeEditableBudget(TableColumn<UserRow, BigDecimal> col) {
        col.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        col.setOnEditCommit(ev -> {
            UserRow row = ev.getRowValue();
            row.budget.set(ev.getNewValue());
            row.dirty.set(true);
        });
    }

    /** Strict ComboBox editor for the Role column, employees only. */
    private void makeRoleCombo(TableColumn<UserRow, String> col) {
        col.setEditable(true);
        col.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(ROLE_OPTIONS);
            {
                combo.setEditable(false); // no typing
                combo.valueProperty().addListener((obs, old, val) -> {
                    UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;
                    if (!"EMPLOYEE".equalsIgnoreCase(row.userType.get())) return;
                    // Normalize to lowercase; server expects lowercase too
                    String newRole = val == null ? null : val.toLowerCase();
                    if (!Objects.equals(row.role.get(), newRole)) {
                        row.role.set(newRole);
                        row.dirty.set(true);
                    }
                });
            }

            @Override
            protected void updateItem(String roleValue, boolean empty) {
                super.updateItem(roleValue, empty);
                if (empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                if ("EMPLOYEE".equalsIgnoreCase(row.userType.get())) {
                    // Ensure combo shows current role if it's one of the allowed values
                    String normalized = roleValue == null ? null : roleValue.toLowerCase();
                    if (!ROLE_OPTIONS.contains(normalized)) {
                        // Unknown role in DB -> show empty; user must choose a valid one
                        combo.setValue(null);
                    } else {
                        combo.setValue(normalized);
                    }
                    setGraphic(combo);
                    setText(null);
                } else {
                    // Not an employee -> no editor
                    setGraphic(null);
                    setText(""); // keep cell blank for customers
                }
            }
        });
        // Editing commit is handled by the listener; no need to setOnEditCommit
    }

    private void refresh(boolean reset) {
        if (reset) {
            offset = 0;
            data.clear();
        }
        Map<String,Object> q = new HashMap<>();
        q.put("search", currentSearch);
        q.put("offset", offset);
        q.put("limit", PAGE);

        try {
            client.sendToServer(new Message("admin_list_users", q, null));
            statusLabel.setText("Loading...");
        } catch (IOException e) {
            statusLabel.setText("Load failed: " + e.getMessage());
        }
    }

    @Subscribe
    public void onServerMessage(Message msg) {
        Platform.runLater(() -> {
            switch (msg.getMessage()) {
                case "admin_users_page" -> {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> page = (Map<String,Object>) msg.getObject();
                    if (page == null) { statusLabel.setText("No results"); return; }

                    @SuppressWarnings("unchecked")
                    List<UserAdminDTO> rows = (List<UserAdminDTO>) page.get("rows");
                    Number total = (Number) page.get("total");
                    if (rows != null) {
                        for (var dto : rows) data.add(fromDTO(dto));
                        offset += rows.size();
                        statusLabel.setText("Loaded " + data.size() + " / " + (total == null ? "?" : total));
                    } else {
                        statusLabel.setText("No results");
                    }
                }
                case "admin_update_ok" -> {
                    UserAdminDTO dto = (UserAdminDTO) msg.getObject();
                    if (dto != null) {
                        for (int i=0; i<data.size(); i++) {
                            if (Objects.equals(data.get(i).id.get(), dto.getId())) {
                                data.set(i, fromDTO(dto));
                                break;
                            }
                        }
                        statusLabel.setText("Saved.");
                    }
                }
                case "admin_freeze_ok" -> {
                    UserAdminDTO dto = (UserAdminDTO) msg.getObject();
                    if (dto != null) {
                        for (int i=0; i<data.size(); i++) {
                            if (Objects.equals(data.get(i).id.get(), dto.getId())) {
                                data.set(i, fromDTO(dto));
                                break;
                            }
                        }
                        statusLabel.setText("Updated frozen state.");
                    }
                }
                case "admin_users_error", "admin_update_error", "admin_freeze_error" -> {
                    statusLabel.setText("Error: " + msg.getObject());
                }
            }
        });
    }

    private UserRow fromDTO(UserAdminDTO dto) {
        var r = new UserRow();
        r.id.set(dto.getId() != null ? dto.getId() : -1);
        r.userType.set(dto.getUserType());
        r.username.set(dto.getUsername());
        r.name.set(dto.getName());
        r.branchName.set(dto.getBranchName());
        r.network.set(Boolean.TRUE.equals(dto.getNetworkAccount()));
        r.loggedIn.set(Boolean.TRUE.equals(dto.getLoggedIn()));
        r.role.set(dto.getRole() != null ? dto.getRole().toLowerCase() : "");
        r.budget.set(dto.getBudget());
        r.frozen.set(Boolean.TRUE.equals(dto.getFrozen()));
        r.dirty.set(false);
        return r;
    }

    @FXML
    private void onSearch() {
        currentSearch = searchField.getText() == null ? "" : searchField.getText().trim();
        refresh(true);
    }

    @FXML
    private void onLoadMore() { refresh(false); }

    @FXML
    private void onSaveSelected() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a row first."); return; }
        if (!sel.dirty.get()) { statusLabel.setText("No changes to save."); return; }

        // Validate role for employees
        if ("EMPLOYEE".equalsIgnoreCase(sel.userType.get())) {
            String r = sel.role.get() == null ? null : sel.role.get().toLowerCase();
            if (r == null || !ROLE_OPTIONS.contains(r)) {
                statusLabel.setText("Pick a valid role: " + ROLE_OPTIONS);
                return;
            }
        }

        // Build a UserAdminDTO to send
        var dto = new UserAdminDTO();
        dto.setId(sel.id.get());
        dto.setUserType(sel.userType.get());
        dto.setName(sel.name.get());
        dto.setUsername(sel.username.get());
        dto.setBranchName(sel.branchName.get());
        dto.setNetworkAccount(sel.network.get());
        if ("EMPLOYEE".equalsIgnoreCase(dto.getUserType())) {
            dto.setRole(sel.role.get() == null ? null : sel.role.get().toLowerCase());
        } else if ("CUSTOMER".equalsIgnoreCase(dto.getUserType())) {
            dto.setBudget(sel.budget.get());
        }

        try {
            client.sendToServer(new Message("admin_update_user", dto, null));
            statusLabel.setText("Saving...");
        } catch (IOException e) {
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void onToggleFreeze() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a row first."); return; }
        if (!"CUSTOMER".equalsIgnoreCase(sel.userType.get())) {
            statusLabel.setText("Freeze applies to customers only.");
            return;
        }
        Map<String,Object> req = new HashMap<>();
        req.put("customerId", sel.id.get());
        req.put("frozen", !sel.frozen.get());

        try {
            client.sendToServer(new Message("admin_freeze_customer", req, null));
            statusLabel.setText((!sel.frozen.get() ? "Freezing..." : "Unfreezing..."));
        } catch (IOException e) {
            statusLabel.setText("Action failed: " + e.getMessage());
        }
    }

    @FXML
    private void onBack() {
        EventBus.getDefault().unregister(this);
        try { App.setRoot("primary"); } catch (Exception e) { e.printStackTrace(); }
    }
}
