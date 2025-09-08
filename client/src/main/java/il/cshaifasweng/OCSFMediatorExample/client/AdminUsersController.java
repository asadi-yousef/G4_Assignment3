package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.Branch;
import il.cshaifasweng.OCSFMediatorExample.entities.Message;
import il.cshaifasweng.OCSFMediatorExample.entities.UserAdminDTO;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.StringConverter;
import javafx.util.converter.BigDecimalStringConverter;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

public class AdminUsersController {

    @FXML private TableView<UserRow> table;

    @FXML private TableColumn<UserRow,String> colType;
    @FXML private TableColumn<UserRow,String> colIdNumber;
    @FXML private TableColumn<UserRow,String> colUsername;
    @FXML private TableColumn<UserRow,String> colFirstName;
    @FXML private TableColumn<UserRow,String> colLastName;

    @FXML private TableColumn<UserRow,Branch>  colBranch;
    @FXML private TableColumn<UserRow,Boolean> colNetwork;
    @FXML private TableColumn<UserRow,Boolean> colLogged;
    @FXML private TableColumn<UserRow,String>  colRole;
    @FXML private TableColumn<UserRow,BigDecimal> colBudget;
    @FXML private TableColumn<UserRow,Boolean> colFrozen;

    @FXML private TextField searchField;
    @FXML private Label statusLabel;

    // De-dupe using a string key to avoid null-ID collisions.
    private final Set<String> seenKeys = new HashSet<>();


    private final ObservableList<UserRow> data = FXCollections.observableArrayList();
    private final ObservableList<Branch> branches = FXCollections.observableArrayList();

    private int offset = 0;
    private String currentSearch = "";
    private static final int PAGE = 50;

    // Allowed roles (lowercase)
    private static final ObservableList<String> ROLE_OPTIONS = FXCollections.observableArrayList(
            "netemployee", "netmanager", "branchmanager", "systemadmin", "customerservice"
    );

    private SimpleClient client;

    @FXML
    public void initialize() {
        client = SimpleClient.getClient();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        table.setItems(data);
        table.setEditable(true);

        // --- value factories
        colType.setCellValueFactory(c -> c.getValue().userTypeProperty());
        colIdNumber.setCellValueFactory(c -> c.getValue().idNumberProperty());
        colUsername.setCellValueFactory(c -> c.getValue().usernameProperty());
        colFirstName.setCellValueFactory(c -> c.getValue().firstNameProperty());
        colLastName.setCellValueFactory(c -> c.getValue().lastNameProperty());

        // Branch as ComboBox (disabled if logged in OR network account OR non-employee/non-customer rules)
        colBranch.setCellValueFactory(c -> c.getValue().branchProperty());
        makeBranchCombo(colBranch);

        // NETWORK: editable only for CUSTOMERS and only if NOT logged in
        colNetwork.setCellValueFactory(c -> c.getValue().networkProperty());
        colNetwork.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(e -> {
                    UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    // block if logged in
                    if (row.isLoggedIn()) {
                        check.setSelected(row.isNetwork());
                        statusLabel.setText("User is logged in — only Freeze/Unfreeze is allowed.");
                        return;
                    }

                    // employees: never editable here (role controls it)
                    if ("EMPLOYEE".equalsIgnoreCase(row.getUserType())) {
                        check.setSelected(row.isNetwork());
                        return;
                    }

                    // customers: apply rule
                    boolean newVal = check.isSelected();
                    if (newVal) { // ON => no branch
                        if (row.getBranch() != null) row.setBranch(null);
                        row.setNetwork(true);
                        row.setDirty(true);
                    } else { // OFF => must have a branch
                        if (row.getBranch() == null) {
                            Branch chosen = promptSelectBranch("Select a branch for this customer");
                            if (chosen == null) { check.setSelected(true); return; }
                            row.setBranch(chosen);
                        }
                        row.setNetwork(false);
                        row.setDirty(true);
                    }
                    table.refresh();
                });
            }
            @Override
            public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); setText(null); return; }

                check.setSelected(item != null && item);
                boolean editable = "CUSTOMER".equalsIgnoreCase(row.getUserType()) && !row.isLoggedIn();
                check.setDisable(!editable);
                setGraphic(check);
                setText(null);
            }
        });

        // LOGGED: read-only
        colLogged.setCellValueFactory(c -> c.getValue().loggedInProperty());
        colLogged.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            @Override public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                check.setSelected(item != null && item);
                check.setDisable(true);
                setGraphic(check); setText(null);
            }
        });

        // Role combobox (employees only) — auto-syncs Network; blocked if logged in
        colRole.setCellValueFactory(c -> c.getValue().roleProperty());
        makeRoleCombo(colRole);

        // BUDGET (customers) — editable if not logged in
        colBudget.setCellValueFactory(c -> c.getValue().budgetProperty());
        makeEditableBudget(colBudget);

        // FROZEN: read-only (button controls it)
        colFrozen.setCellValueFactory(c -> c.getValue().frozenProperty());
        colFrozen.setCellFactory(tc -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            @Override public void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                check.setSelected(item != null && item);
                check.setDisable(true);
                setGraphic(check); setText(null);
            }
        });

        // Text columns — editable unless logged in
        makeEditable(colIdNumber);
        makeEditable(colUsername);
        makeEditable(colFirstName);
        makeEditable(colLastName);

        requestBranches();
        refresh(true);
    }



    private void makeEditable(TableColumn<UserRow, String> col) {
        col.setCellFactory(TextFieldTableCell.forTableColumn());
        col.setOnEditCommit(ev -> {
            UserRow row = ev.getRowValue();
            if (row == null) return;

            final String newVal = safe(ev.getNewValue());
            final String oldVal = safe(ev.getOldValue());

            // Per-column validation
            if (col == colIdNumber) {
                if (!newVal.matches("\\d{9}")) {
                    statusLabel.setText("ID number must be exactly 9 digits.");
                    // Revert visually if invalid
                    if (!Objects.equals(oldVal, row.getIdNumber())) {
                        row.setIdNumber(oldVal);
                    }
                    table.refresh();
                    return;
                }
                row.setIdNumber(newVal);
            } else if (col == colUsername) {
                if (newVal.isEmpty()) {
                    statusLabel.setText("Username cannot be empty.");
                    if (!Objects.equals(oldVal, row.getUsername())) {
                        row.setUsername(oldVal);
                    }
                    table.refresh();
                    return;
                }
                row.setUsername(newVal);
            } else if (col == colFirstName) {
                row.setFirstName(newVal);
            } else if (col == colLastName) {
                row.setLastName(newVal);
            }

            row.setDirty(true);
            // No table.refresh() here — allow the edited value to remain visible
        });
    }



    private void makeEditableBudget(TableColumn<UserRow, BigDecimal> col) {
        col.setCellFactory(TextFieldTableCell.forTableColumn(new BigDecimalStringConverter()));
        col.setOnEditCommit(ev -> {
            UserRow row = ev.getRowValue();
            if (row == null) return;

            BigDecimal v = ev.getNewValue();
            if (v != null && v.signum() >= 0) {
                row.setBudget(v);
                row.setDirty(true);
            } else {
                statusLabel.setText("Budget must be non-negative.");
                table.refresh();
            }
        });
    }


    private void makeRoleCombo(TableColumn<UserRow, String> col) {
        col.setEditable(true);
        col.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<String> combo = new ComboBox<>(ROLE_OPTIONS);
            private boolean suppress; // reentrancy guard

            {
                combo.setEditable(false);
                combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (suppress) return; // prevent recursive loops
                    UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;
                    if (!"EMPLOYEE".equalsIgnoreCase(row.getUserType())) return;

                    if (row.isLoggedIn()) {
                        // revert immediately if logged in
                        try {
                            suppress = true;
                            combo.setValue(row.getRole());
                        } finally {
                            suppress = false;
                        }
                        statusLabel.setText("User is logged in — edits are disabled.");
                        return;
                    }

                    String newRole = newVal == null ? null : newVal.toLowerCase();
                    String oldRole = row.getRole() == null ? null : row.getRole().toLowerCase();
                    if (Objects.equals(newRole, oldRole)) return;

                    boolean oldNet = isNetRole(oldRole);
                    boolean newNet = isNetRole(newRole);

                    // net → branch requires choosing a branch
                    if (oldNet && !newNet) {
                        // guard: if branches not loaded yet, request and revert selection
                        if (branches.isEmpty()) {
                            statusLabel.setText("Loading branches... try again in a moment.");
                            requestBranches();
                            try {
                                suppress = true;
                                combo.setValue(oldRole);
                            } finally {
                                suppress = false;
                            }
                            return;
                        }

                        Branch chosen = promptSelectBranch("Select a branch for this employee");
                        if (chosen == null) {
                            // user canceled: revert selection
                            try {
                                suppress = true;
                                combo.setValue(oldRole);
                            } finally {
                                suppress = false;
                            }
                            return;
                        }
                        row.setBranch(chosen);
                    }

                    // branch → net clears branch
                    if (!oldNet && newNet) {
                        row.setBranch(null);
                    }

                    // auto-sync network flag for employees
                    row.setNetwork(newNet);
                    row.setRole(newRole);
                    row.setDirty(true);

                    // optional: refresh row visuals
                    getTableView().refresh();
                });
            }

            @Override
            protected void updateItem(String roleValue, boolean empty) {
                super.updateItem(roleValue, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); setText(null); return; }

                if ("EMPLOYEE".equalsIgnoreCase(row.getUserType())) {
                    String normalized = roleValue == null ? null : roleValue.toLowerCase();
                    try {
                        suppress = true; // avoid firing listener while painting cell
                        combo.setValue(ROLE_OPTIONS.contains(normalized) ? normalized : null);
                        combo.setDisable(row.isLoggedIn());
                        // If you prefer to block role changes until branches are loaded, you can use:
                        // combo.setDisable(row.isLoggedIn() || branches.isEmpty());
                    } finally {
                        suppress = false;
                    }
                    setGraphic(combo);
                    setText(null);
                } else {
                    setGraphic(null);
                    setText("");
                }
            }
        });
    }


    private void makeBranchCombo(TableColumn<UserRow, Branch> col) {
        col.setCellFactory(tc -> new TableCell<>() {
            private final ComboBox<Branch> combo = new ComboBox<>(branches);
            private final javafx.util.StringConverter<Branch> conv = new javafx.util.StringConverter<>() {
                @Override public String toString(Branch b) { return b == null ? "—" : b.getName(); }
                @Override public Branch fromString(String s) { return null; }
            };
            private boolean suppress; // reentrancy guard

            {
                combo.setConverter(conv);
                combo.valueProperty().addListener((obs, oldVal, newVal) -> {
                    if (suppress) return; // prevent recursive loops
                    UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row == null) return;

                    if (row.isLoggedIn()) {
                        // revert if logged in
                        try {
                            suppress = true;
                            combo.setValue(oldVal);
                        } finally {
                            suppress = false;
                        }
                        statusLabel.setText("User is logged in — edits are disabled.");
                        return;
                    }

                    if (row.isNetwork()) {
                        // network accounts cannot have a branch — clear and revert UI
                        statusLabel.setText("Network accounts cannot have a branch.");
                        row.setBranch(null);
                        try {
                            suppress = true;
                            combo.setValue(null);
                        } finally {
                            suppress = false;
                        }
                        return;
                    }

                    if (!Objects.equals(oldVal, newVal)) {
                        row.setBranch(newVal);
                        row.setDirty(true);
                    }
                });
            }

            @Override
            protected void updateItem(Branch item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); setText(null); return; }
                UserRow row = getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); setText(null); return; }

                boolean disabled = row.isLoggedIn() || row.isNetwork();
                try {
                    suppress = true; // avoid firing while painting
                    combo.setDisable(disabled);
                    combo.setValue(item);
                } finally {
                    suppress = false;
                }

                if (disabled) {
                    setGraphic(null);
                    setText(item == null ? "—" : item.getName());
                } else {
                    setGraphic(combo);
                    setText(null);
                }
            }
        });

        // Optional comparator by branch name
        col.setComparator((a, b) -> {
            String an = a == null ? "" : a.getName();
            String bn = b == null ? "" : b.getName();
            return an.compareToIgnoreCase(bn);
        });
    }



    private Branch promptSelectBranch(String header) {
        if (branches.isEmpty()) {
            statusLabel.setText("No branches loaded yet. Requesting...");
            requestBranches();
            return null;
        }
        ChoiceDialog<Branch> dlg = new ChoiceDialog<>(branches.get(0), branches);
        dlg.setTitle("Choose Branch");
        dlg.setHeaderText(header);
        dlg.setContentText("Branch:");
        dlg.setSelectedItem(branches.get(0));
        dlg.getDialogPane().lookupButton(ButtonType.CANCEL).setDisable(false);
        dlg.setResultConverter(btn -> btn == ButtonType.OK ? dlg.getSelectedItem() : null);
        Optional<Branch> res = dlg.showAndWait();
        return res.orElse(null);
    }


    private static boolean isNetRole(String role) {
        if (role == null) return false;
        return role.toLowerCase().startsWith("net") || role.equalsIgnoreCase("systemadmin")||role.equalsIgnoreCase("customerservice");
    }

    private String safe(String s) { return s == null ? "" : s.trim(); }

    private void requestBranches() {
        try {
            client.sendToServer(new Message("request_branches", null, null));
        } catch (IOException e) {
            statusLabel.setText("Failed to request branches: " + e.getMessage());
        }
    }

    private void refresh(boolean reset) {
        if (reset) {
            offset = 0;
            data.clear();
            seenKeys.clear();
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
                // === NEW: populate the branches list ===
                case "Branches" -> { // must match server's message name exactly (case-sensitive)
                    @SuppressWarnings("unchecked")
                    List<Branch> list = (List<Branch>) msg.getObject();
                    branches.setAll(list == null ? List.of() : list);
                    statusLabel.setText("Branches loaded: " + branches.size());
                    table.refresh(); // repaint cells that depend on branches
                }

                case "admin_users_page" -> {
                    @SuppressWarnings("unchecked")
                    Map<String,Object> page = (Map<String,Object>) msg.getObject();
                    if (page == null) { statusLabel.setText("No results"); return; }

                    @SuppressWarnings("unchecked")
                    List<UserAdminDTO> rows = (List<UserAdminDTO>) page.get("rows");
                    Number total = (Number) page.get("total");

                    if (rows != null) {
                        int added = 0, skipped = 0;
                        for (var dto : rows) {
                            // Build a stable de-dupe key: prefer ID; otherwise username (which you enforce unique)
                            String uname = dto.getUsername() == null ? "" : dto.getUsername().trim().toLowerCase();
                            String key = (dto.getId() != null) ? ("id:" + dto.getId()) : ("user:" + uname);

                            if (!seenKeys.add(key)) { skipped++; continue; } // already showing it

                            data.add(fromDTO(dto));
                            added++;
                        }

                        // Advance offset by what the server returned (keeps contract stable)
                        offset += rows.size();

                        statusLabel.setText(
                                "Loaded " + data.size() + " / " + (total == null ? "?" : total)
                                        + (skipped > 0 ? (" (+" + added + ", skipped " + skipped + ")") : "")
                        );

                        // Optional: table.refresh(); // not required when adding to ObservableList
                    } else {
                        statusLabel.setText("No results");
                    }
                }


                case "admin_update_ok" -> {
                    UserAdminDTO dto = (UserAdminDTO) msg.getObject();
                    if (dto != null) {
                        for (int i = 0; i < data.size(); i++) {
                            if (Objects.equals(data.get(i).getId(), dto.getId())) {
                                data.set(i, fromDTO(dto));
                                break;
                            }
                        }
                        statusLabel.setText("Saved.");
                    }
                }

                case "admin_update_error" -> {
                    String err = String.valueOf(msg.getObject());
                    showUpdateError(err);
                    // Optional: force a full reload after an error
                    refresh(true);
                }

                case "admin_freeze_ok" -> {
                    UserAdminDTO dto = (UserAdminDTO) msg.getObject();
                    if (dto != null) {
                        for (int i = 0; i < data.size(); i++) {
                            if (Objects.equals(data.get(i).getId(), dto.getId())) {
                                data.set(i, fromDTO(dto));
                                break;
                            }
                        }
                        statusLabel.setText("Updated frozen state.");
                    }
                }
                case "admin_delete_ok" -> {
                    // Expecting either a userId (Number) or a DTO; handle both.
                    Long id = null;
                    Object obj = msg.getObject();
                    if (obj instanceof Number) {
                        id = ((Number) obj).longValue();
                    } else if (obj instanceof UserAdminDTO dto) {
                        id = dto.getId();
                    }
                    if (id != null) {
                        UserRow removed = null;
                        for (Iterator<UserRow> it = data.iterator(); it.hasNext();) {
                            UserRow r = it.next();
                            if (Objects.equals(r.getId(), id)) {
                                removed = r;
                                it.remove();
                                break;
                            }
                        }
                        // Keep de-dupe set consistent so a future refresh can re-add if needed
                        if (removed != null) {
                            seenKeys.remove("id:" + id);
                            String uname = removed.getUsername() == null ? "" : removed.getUsername().trim().toLowerCase();
                            if (!uname.isEmpty()) seenKeys.remove("user:" + uname);
                        }
                        statusLabel.setText("User deleted.");
                    } else {
                        statusLabel.setText("User deleted.");
                    }
                }

                case "admin_delete_error" -> {
                    statusLabel.setText("Delete failed: " + String.valueOf(msg.getObject()));
                }


                case "admin_users_error", "admin_freeze_error" -> {
                    statusLabel.setText("Error: " + msg.getObject());
                }
            }
        });
    }



    private UserRow fromDTO(UserAdminDTO dto) {
        var r = new UserRow();
        r.setId(dto.getId() == null ? -1 : dto.getId());
        r.setUserType(safe(dto.getUserType()));
        r.setIdNumber(safe(dto.getIdNumber()));
        r.setUsername(safe(dto.getUsername()));
        r.setFirstName(safe(dto.getFirstName()));
        r.setLastName(safe(dto.getLastName()));
        r.setNetwork(Boolean.TRUE.equals(dto.getNetworkAccount()));
        r.setLoggedIn(Boolean.TRUE.equals(dto.getLoggedIn()));
        r.setRole(dto.getRole() == null ? "" : dto.getRole().toLowerCase());
        r.setFrozen(Boolean.TRUE.equals(dto.getFrozen()));
        r.setBudget(dto.getBudget());

        // prefer branchId matching our loaded list; fallback to name-only
        Branch b = null;
        if (dto.getBranchId() != null) {
            b = branches.stream().filter(x -> Objects.equals(x.getId(), dto.getBranchId())).findFirst().orElse(null);
        }
        if (b == null && dto.getBranchName() != null) {
            b = branches.stream().filter(x -> dto.getBranchName().equalsIgnoreCase(x.getName())).findFirst().orElse(null);
        }
        r.setBranch(b);

        r.setDirty(false);
        return r;
    }

    @FXML
    private void onSearch() {
        currentSearch = searchField.getText() == null ? "" : searchField.getText().trim();
        refresh(true);
    }

    @FXML
    private void onLoadMore() {
        refresh(true);
    }

    @FXML
    private void onSaveSelected() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a row first."); return; }

        // NOTE: We no longer hard-block saves based on the (possibly stale) client-side loggedIn flag.
        // If the server still sees the user as logged in, it will reject the update and we handle it via admin_update_error.
        if (sel.isLoggedIn()) {
            statusLabel.setText("User might still be logged in — attempting save; server will verify.");
        }

        if (!sel.isDirty()) { statusLabel.setText("No changes to save."); return; }

        // Validate employee role
        if ("EMPLOYEE".equalsIgnoreCase(sel.getUserType())) {
            String r = sel.getRole() == null ? null : sel.getRole().toLowerCase();
            if (r == null || !ROLE_OPTIONS.contains(r)) {
                statusLabel.setText("Pick a valid role: " + ROLE_OPTIONS);
                return;
            }
            if (!isNetRole(r) && sel.getBranch() == null) {
                statusLabel.setText("Branch role requires a branch.");
                return;
            }
        }

        // Validate network ↔ branch
        if (sel.isNetwork() && sel.getBranch() != null) {
            statusLabel.setText("Network accounts cannot have a branch.");
            return;
        }
        if (!sel.isNetwork() && sel.getBranch() == null) {
            statusLabel.setText("Non-network accounts must have a branch.");
            return;
        }

        // Build and send DTO
        var dto = new UserAdminDTO();
        dto.setId(sel.getId());
        dto.setUserType(sel.getUserType());
        dto.setIdNumber(sel.getIdNumber());
        dto.setUsername(sel.getUsername());
        dto.setFirstName(sel.getFirstName());
        dto.setLastName(sel.getLastName());
        dto.setNetworkAccount(sel.isNetwork());
        dto.setLoggedIn(sel.isLoggedIn()); // informational; server should compute truth
        if (sel.getBranch() != null) {
            dto.setBranchId(sel.getBranch().getId());
            dto.setBranchName(sel.getBranch().getName());
        }
        if ("EMPLOYEE".equalsIgnoreCase(dto.getUserType())) {
            dto.setRole(sel.getRole());
        } else if ("CUSTOMER".equalsIgnoreCase(dto.getUserType())) {
            dto.setBudget(sel.getBudget());
            dto.setFrozen(sel.isFrozen());
        }

        try {
            client.sendToServer(new Message("admin_update_user", dto, null));
            statusLabel.setText("Saving...");
        } catch (IOException e) {
            statusLabel.setText("Save failed: " + e.getMessage());
        }
    }

    @FXML
    private void onDeleteSelected() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a row first."); return; }

        // Optional UX hint if locally marked as logged-in (server will still decide)
        if (sel.isLoggedIn()) {
            statusLabel.setText("User might still be logged in — attempting delete; server will verify.");
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete user");
        confirm.setHeaderText("Permanently delete this user?");
        confirm.setContentText(
                "Type: " + sel.getUserType() + "\n" +
                        "Username: " + sel.getUsername() + "\n" +
                        "ID Number: " + sel.getIdNumber() + "\n\n" +
                        "This cannot be undone."
        );
        Optional<ButtonType> ans = confirm.showAndWait();
        if (ans.isEmpty() || ans.get() != ButtonType.OK) {
            statusLabel.setText("Delete canceled.");
            return;
        }

        Map<String,Object> req = new HashMap<>();
        req.put("userId", sel.getId());

        try {
            // Server message name for hard delete:
            client.sendToServer(new Message("admin_delete_user", req, null));
            statusLabel.setText("Deleting...");
        } catch (IOException e) {
            statusLabel.setText("Delete failed: " + e.getMessage());
        }
    }


    @FXML
    private void onToggleFreeze() {
        var sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) { statusLabel.setText("Select a row first."); return; }
        if (!"CUSTOMER".equalsIgnoreCase(sel.getUserType())) {
            statusLabel.setText("Freeze applies to customers only.");
            return;
        }
        Map<String,Object> req = new HashMap<>();
        req.put("customerId", sel.getId());
        req.put("frozen", !sel.isFrozen());

        try {
            client.sendToServer(new Message("admin_freeze_customer", req, null));
            statusLabel.setText((!sel.isFrozen() ? "Freezing..." : "Unfreezing..."));
        } catch (IOException e) {
            statusLabel.setText("Action failed: " + e.getMessage());
        }
    }

    private void showUpdateError(String details) {
        String text = (details == null || details.isBlank()) ? "Unknown error" : details;
        statusLabel.setText("Save failed: " + text);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Update failed");
        alert.setHeaderText("User update was rejected");
        alert.setContentText(text);
        alert.showAndWait();
    }


    @FXML
    private void onBack() {
        EventBus.getDefault().unregister(this);
        try { App.setRoot("primary"); } catch (Exception e) { e.printStackTrace(); }
    }
}
