package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import org.greenrobot.eventbus.EventBus;
import javafx.scene.control.DateCell;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

public class ReportsViewController implements Initializable {

    // UI
    @FXML private RadioButton incomeReportBtn;
    @FXML private RadioButton ordersReportBtn;
    @FXML private RadioButton complaintsReportBtn;
    @FXML private ComboBox<String> branchComboBox;
    @FXML private DatePicker startDatePicker;
    @FXML private DatePicker endDatePicker;
    @FXML private TextArea reportOutput;
    @FXML private BarChart<String, Number> barChart;
    @FXML private Button backToCatalogButton;

    private ToggleGroup reportTypeGroup;
    private List<Branch> branches;

    // name -> id map (keeps ComboBox<String> simple but lets us send the ID)
    private final Map<String, Long> branchNameToId = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        if (!(isSystemManager() || isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
        }

        // Request branches (open connection first)
        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }
            SimpleClient.getClient().sendToServer(new Message("request_branches", null, null));
        } catch (Exception e) {
            System.err.println("Failed to request branches: " + e.getMessage());
        }

        // Radio group
        reportTypeGroup = new ToggleGroup();
        incomeReportBtn.setToggleGroup(reportTypeGroup);
        ordersReportBtn.setToggleGroup(reportTypeGroup);
        complaintsReportBtn.setToggleGroup(reportTypeGroup);
        incomeReportBtn.setSelected(true);

        // Dates
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth  = today.withDayOfMonth(today.lengthOfMonth());

        if (isSystemManager()) {
            // branches will be populated when the server responds (onBranches -> loadBranchesForSystemManager)
            startDatePicker.setValue(today.minusMonths(1));
            endDatePicker.setValue(today);
        } else if (isBranchManager()) {
            // Pre-fill with manager's branch name; will be hardened when branches arrive
            String myBranchName = Optional.ofNullable(currentUserBranchName()).orElse("My Branch");
            branchComboBox.getItems().setAll(myBranchName);
            branchComboBox.getSelectionModel().selectFirst();
            branchComboBox.setDisable(true);

            // Lock pickers to current month
            startDatePicker.setValue(firstOfMonth);
            endDatePicker.setValue(today.isBefore(lastOfMonth) ? today : lastOfMonth);
            restrictDatePickerToMonth(startDatePicker, firstOfMonth, lastOfMonth);
            restrictDatePickerToMonth(endDatePicker, firstOfMonth, lastOfMonth);
        } else {
            showInfo("Permission Denied", "You do not have permission to view reports.");
        }

        reportOutput.setText("Pick a type, branch and dates, then click Generate.");
        clearChart();
    }

    private void loadBranchesForSystemManager() {
        branchComboBox.getItems().clear();
        branchNameToId.clear();

        if (isSystemManager()) {
            // Offer "All Branches"
            branchComboBox.getItems().add("All Branches");
            branchNameToId.put("All Branches", null);
        }

        if (branches != null) {
            for (Branch branch : branches) {
                if (branch == null) continue;
                String name = branch.getName();
                Long id = null;
                try {
                    Object rawId = branch.getId(); // supports Long/Integer/String
                    if (rawId instanceof Number) id = ((Number) rawId).longValue();
                    else if (rawId != null) id = Long.valueOf(rawId.toString());
                } catch (Exception ignored) {}
                if (name != null) {
                    branchComboBox.getItems().add(name);
                    branchNameToId.put(name, id);
                }
            }
        }

        // If branch manager, lock to their branch (from delivered list) and keep disabled
        if (isBranchManager()) {
            String myBranchName = Optional.ofNullable(currentUserBranchName()).orElse(null);
            if (myBranchName != null) {
                // Ensure the map has an entry for their branch even if server's name capitalization differs
                for (String name : new ArrayList<>(branchNameToId.keySet())) {
                    if (name.equalsIgnoreCase(myBranchName)) {
                        myBranchName = name;
                        break;
                    }
                }
                branchComboBox.getItems().setAll(myBranchName);
            }
            branchComboBox.setDisable(true);
        }

        if (!branchComboBox.getItems().isEmpty()) {
            branchComboBox.getSelectionModel().selectFirst();
        }
    }

    private void restrictDatePickerToMonth(DatePicker dp, LocalDate first, LocalDate last) {
        dp.setDayCellFactory(picker -> new DateCell() {
            @Override public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                if (empty || date == null) return;
                setDisable(date.isBefore(first) || date.isAfter(last));
            }
        });
    }

    @FXML
    private void onGenerate() {
        if (!(isSystemManager() || isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
        }

        String branch = Optional.ofNullable(branchComboBox.getValue()).orElse("All Branches");
        Long branchId = branchNameToId.get(branch); // may be null for "All Branches" or if not loaded yet
        LocalDate start = startDatePicker.getValue();
        LocalDate end   = endDatePicker.getValue();

        if (start == null || end == null) { showInfo("Validation","Please pick start and end dates."); return; }
        if (end.isBefore(start)) { showInfo("Validation","End date cannot be before start date."); return; }

        // Branch manager restrictions
        if (isBranchManager()) {
            String myBranch = Optional.ofNullable(currentUserBranchName()).orElse(branch);
            if (!Objects.equals(branch, myBranch)) {
                showInfo("Permission Denied", "Branch managers can only view their own branch.");
                return;
            }
            LocalDate first = LocalDate.now().withDayOfMonth(1);
            LocalDate last  = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            if (start.isBefore(first) || end.isAfter(last)) {
                showInfo("Permission Denied", "Branch managers can only view reports for this month.");
                return;
            }
        }

        // Build criteria for server
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("branchId", branchId);                               // send the id (null => all branches)
        criteria.put("branch", (branchId == null) ? null : branch);       // optional fallback by name
        criteria.put("from", start.atStartOfDay());
        criteria.put("to",   end.plusDays(1).atStartOfDay()); // exclusive upper bound

        // UI feedback
        clearChart();
        reportOutput.setText("Fetching " + (incomeReportBtn.isSelected() ? "income" : ordersReportBtn.isSelected() ? "orders" : "complaints")
                + " for " + branch + " (" + start + " → " + end + ")...");

        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }
            String key =
                    incomeReportBtn.isSelected() ? "request_report_income" :
                            ordersReportBtn.isSelected() ? "request_report_orders" :
                                    "request_report_complaints";

            // Use Java 8-friendly empty list
            SimpleClient.getClient().sendToServer(new Message(key, criteria, Collections.emptyList()));
            System.out.println("DEBUG(onGenerate): sent " + key + " with " + criteria);
        } catch (IOException e) {
            showInfo("Connection Error", "Failed to reach server.");
        }
    }

    /* ===================== SUBSCRIBERS (NEW) ===================== */

    @Subscribe
    public void onIncomeReport(Message msg) {
        if (msg == null || !"report_income_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number totalRevenue = (Number) data.getOrDefault("totalRevenue", 0d);
            Number totalExpenses = (Number) data.getOrDefault("totalExpenses", 0d);
            Number netProfit = (Number) data.getOrDefault("netProfit", 0d);
            Number avgDailyNet = (Number) data.getOrDefault("avgDailyNet", 0d);
            Number transactions = (Number) data.getOrDefault("transactions", 0);

            if (hist.isEmpty()) { noData(); return; }

            clearChart();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Daily Revenue (₪)");
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            barChart.getData().add(s);

            reportOutput.setText(
                    "Income Report\n\n" +
                            "Total Revenue:  " + money(numToBD(totalRevenue)) + "\n" +
                            "Total Expenses: " + money(numToBD(totalExpenses)) + "\n" +
                            "Net Profit:     " + money(numToBD(netProfit)) + "\n" +
                            "Avg Daily Net:  " + money(numToBD(avgDailyNet)) + "\n" +
                            "Transactions:   " + transactions
            );
        });

    }

    @Subscribe
    public void onOrdersReport(Message msg) {
        if (msg == null || !"report_orders_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number total = (Number) data.getOrDefault("totalOrders", 0);
            Number cancelled = (Number) data.getOrDefault("cancelled", 0);
            Number net = (Number) data.getOrDefault("netOrders", 0);

            if (hist.isEmpty()) { noData(); return; }

            clearChart();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Orders by Item");
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            barChart.getData().add(s);

            reportOutput.setText(
                    "Orders Report\n\n" +
                            "Total Orders: " + total + "\n" +
                            "Cancelled:    " + cancelled + "\n" +
                            "Net Orders:   " + net
            );
        });
    }

    @Subscribe
    public void onComplaintsReport(Message msg) {
        if (msg == null || !"report_complaints_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number total = (Number) data.getOrDefault("total", 0);
            Number resolved = (Number) data.getOrDefault("resolved", 0);
            Number unresolved = (Number) data.getOrDefault("unresolved", 0);
            String peakDay = Objects.toString(data.getOrDefault("peakDay","-"));

            if (hist.isEmpty()) { noData(); return; }

            clearChart();
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName("Complaints per Day");
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            barChart.getData().add(s);

            reportOutput.setText(
                    "Complaints Report\n\n" +
                            "Total:      " + total + "\n" +
                            "Resolved:   " + resolved + "\n" +
                            "Unresolved: " + unresolved + "\n" +
                            "Peak Day:   " + peakDay
            );
        });
    }

    @Subscribe
    public void onBranches(Message msg) {
        if ("Branches".equals(msg.getMessage())) {
            //noinspection unchecked
            branches = (List<Branch>) msg.getObject();
            Platform.runLater(this::loadBranchesForSystemManager);
        }
    }

    private void noData() {
        clearChart();
        reportOutput.setText("No data for the selected filters.");
    }

    /* ===================== HELPERS ===================== */

    private void clearChart() {
        if (barChart != null) barChart.getData().clear();
    }

    private BigDecimal numToBD(Number n) {
        if (n == null) return BigDecimal.ZERO;
        if (n instanceof BigDecimal) return (BigDecimal) n;
        return BigDecimal.valueOf(n.doubleValue());
    }

    private String money(BigDecimal v) {
        return (v == null) ? "-" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void showInfo(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
        });
    }

    private String userRole() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return "";
        try {
            Object r = u.getClass().getMethod("getRole").invoke(u);
            return r == null ? "" : r.toString();
        } catch (Exception e) { return ""; }
    }

    private boolean isSystemManager() {
        return "manager".equalsIgnoreCase(userRole());
    }

    private boolean isBranchManager() {
        return "branchmanager".equalsIgnoreCase(userRole());
    }

    private String currentUserBranchName() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return null;
        for (String m : List.of("getBranchName","getStoreName","getBranch_name")) {
            try { Object v = u.getClass().getMethod(m).invoke(u);
                return v == null ? null : v.toString();
            } catch (Exception ignored) {}
        }
        return null;
    }

    @FXML
    public void handleBackToCatalog(ActionEvent event) {
        try {
            if (EventBus.getDefault().isRegistered(this)) {
                EventBus.getDefault().unregister(this);
            }
            App.setRoot("primary");
            System.out.println("Navigated back to primary");
        } catch (IOException e) {
            e.printStackTrace();
            showInfo("Navigation Error", "Could not return to catalog:\n" + e.getMessage());
        }
    }
}
