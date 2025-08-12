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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Role gate (same logic as before)
        if (!(isSystemManager()||isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
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
            // System Manager: all branches + any dates
            loadBranchesForSystemManager(); // below
            startDatePicker.setValue(today.minusMonths(1));
            endDatePicker.setValue(today);
            // allow full range
        } else if (isBranchManager()) {
            // Branch Manager: only their branch + this month only
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
            // Others: no access
            showInfo("Permission Denied", "You do not have permission to view reports.");
        }
        reportOutput.setText("Pick a type, branch and dates, then click Generate.");
        clearChart();
    }

    private void loadBranchesForSystemManager() {
        // TODO replace with server fetch if you have it
        branchComboBox.getItems().setAll("All Branches", "Haifa", "Tel Aviv", "Jerusalem");
        branchComboBox.getSelectionModel().selectFirst();
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
        // Allow roles: SYSTEM_MANAGER or BRANCH_MANAGER
        if (!(isSystemManager() || isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
        }

        String branch = Optional.ofNullable(branchComboBox.getValue()).orElse("All Branches");
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

        clearChart();
        reportOutput.clear();

        if (incomeReportBtn.isSelected()) {
            IncomeReport r = new IncomeReport(
                    0, branch, 0L,
                    start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0
            );
            r.generate();
            renderIncomeReport(r);

        } else if (ordersReportBtn.isSelected()) {
            OrdersReport r = new OrdersReport(
                    0, branch, 0L,
                    start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                    0, 0, 0, new HashMap<>()
            );
            r.generate();
            renderOrdersReport(r);

        } else if (complaintsReportBtn.isSelected()) {
            ComplaintsReport r = new ComplaintsReport(
                    0, branch, 0L,
                    start.atStartOfDay(), end.plusDays(1).atStartOfDay(),
                    new HashMap<>(), 0, 0, 0, null
            );
            r.generate();
            renderComplaintsReport(r);
        }
    }

    // ---------- RENDERERS ----------

    private void renderIncomeReport(IncomeReport r) {
        // Chart: Revenue vs Expenses vs Net Profit
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Income Summary");
        s.getData().add(new XYChart.Data<>("Revenue", r.getTotalRevenue().doubleValue()));
        s.getData().add(new XYChart.Data<>("Expenses", r.getTotalExpenses().doubleValue()));
        s.getData().add(new XYChart.Data<>("Net Profit", r.getNetProfit().doubleValue()));
        barChart.getData().add(s);

        String text =
                "Income Report — " + r.getBranch_name() + "\n" +
                        "Period: " + fmtDate(r.getStartDate()) + " → " + fmtDate(r.getEndDate()) + "\n\n" +
                        "Total Revenue:  " + money(r.getTotalRevenue()) + "\n" +
                        "Total Expenses: " + money(r.getTotalExpenses()) + "\n" +
                        "Net Profit:     " + money(r.getNetProfit()) + "\n" +
                        "Avg Daily Net:  " + money(r.getAvgDailyNetProfit()) + "\n" +
                        "Transactions:   " + r.getTotalTransactions() + "\n";
        reportOutput.setText(text);
    }

    private void renderOrdersReport(OrdersReport r) {
        // Chart: histogram by type
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Orders by Type");
        if (r.getTypeHistogram() != null) {
            r.getTypeHistogram().forEach((type, count) -> s.getData().add(new XYChart.Data<>(type, count)));
        }
        barChart.getData().add(s);

        String text =
                "Orders Report — " + r.getBranch_name() + "\n" +
                        "Period: " + fmtDate(r.getStartDate()) + " → " + fmtDate(r.getEndDate()) + "\n\n" +
                        "Total Orders:    " + r.getTotalOrders() + "\n" +
                        "Cancelled:       " + r.getCancelledOrders() + "\n" +
                        "Net Orders:      " + r.getNetOrders() + "\n";
        reportOutput.setText(text);
    }

    private void renderComplaintsReport(ComplaintsReport r) {
        // Chart: complaints per day (format LocalDateTime → yyyy-MM-dd)
        DateTimeFormatter df = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        XYChart.Series<String, Number> s = new XYChart.Series<>();
        s.setName("Complaints per Day");
        if (r.getComplaintsHistogram() != null) {
            r.getComplaintsHistogram().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(e -> s.getData().add(new XYChart.Data<>(df.format(e.getKey()), e.getValue())));
        }
        barChart.getData().add(s);

        String maxDate = (r.getMaxComplaintsDate() != null) ? df.format(r.getMaxComplaintsDate()) : "-";
        String text =
                "Complaints Report — " + r.getBranch_name() + "\n" +
                        "Period: " + fmtDate(r.getStartDate()) + " → " + fmtDate(r.getEndDate()) + "\n\n" +
                        "Total Complaints:    " + r.getTotalComplaints() + "\n" +
                        "Resolved:            " + r.getResolvedComplaints() + "\n" +
                        "Unresolved:          " + r.getUnresolvedComplaints() + "\n" +
                        "Peak Complaints Day: " + maxDate + "\n";
        reportOutput.setText(text);
    }

    // ---------- HELPERS ----------

    private void clearChart() {
        if (barChart != null) barChart.getData().clear();
    }

    private String fmtDate(LocalDateTime dt) {
        if (dt == null) return "-";
        return dt.toLocalDate().toString();
    }

    private String money(BigDecimal v) {
        return (v == null) ? "-" : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /*private boolean isManager() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return false;
        try {
            Object role = u.getClass().getMethod("getRole").invoke(u);
            return role != null && "MANAGER".equalsIgnoreCase(role.toString());
        } catch (Exception ignored) { }
        return false;
    }*/

    private void showInfo(String title, String msg) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
        });
    }

    private String userRole() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return "";
        try { Object r = u.getClass().getMethod("getRole").invoke(u);
            return r == null ? "" : r.toString(); } catch (Exception e) { return ""; }
    }

    private boolean isSystemManager() {
        return "system_manager".equalsIgnoreCase(userRole());
    }

    private boolean isBranchManager() {
        return "manager".equalsIgnoreCase(userRole()) ||
                "MANAGER".equalsIgnoreCase(userRole()); // if your older role name was MANAGER
    }

    // Try common method names to discover the manager's branch id/name
    private Long currentUserBranchId() {
        User u = SessionManager.getInstance().getCurrentUser();
        if (u == null) return null;
        for (String m : List.of("getBranchId","getStoreId","getBranch_id")) {
            try { Object v = u.getClass().getMethod(m).invoke(u);
                if (v instanceof Number) return ((Number)v).longValue();
            } catch (Exception ignored) {}
        }
        return null;
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
            App.setRoot("primary"); // or whatever your main screen id is
            System.out.println("Navigated back to primary");
        } catch (IOException e) {
            e.printStackTrace();
            showInfo("Navigation Error", "Could not return to catalog:\n" + e.getMessage());
        }
    }
}
