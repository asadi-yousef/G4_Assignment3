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
import java.util.stream.Collectors;
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
    private String pendingType;
    private String pendingBranch;
    private LocalDate pendingStart, pendingEnd;
    private boolean waitingForOrders = false;

    private ToggleGroup reportTypeGroup;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Role gate (same logic as before)
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

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
        System.out.println("DEBUG: onGenerate clicked");
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

        // Clear UI and show a fetching message
        clearChart();
        reportOutput.setText("Fetching orders for " + branch + " (" + start + " → " + end + ")...");

        // Build criteria for server
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("branch", "All Branches".equalsIgnoreCase(branch) ? null : branch);
        criteria.put("from", start.atStartOfDay());
        criteria.put("to",   end.plusDays(1).atStartOfDay()); // exclusive end

        // Include current user in payload if your server expects it (mirrors cart pattern)
        List<Object> payload = new ArrayList<>();
        payload.add(SessionManager.getInstance().getCurrentUser());

        try {
            if (!SimpleClient.getClient().isConnected()) {
                SimpleClient.getClient().openConnection();
            }
            SimpleClient.getClient().sendToServer(new Message("request_orders", criteria, payload));
        } catch (IOException e) {
            showInfo("Connection Error", "Failed to request orders from server.");
        }
    }

    // ---------- RENDERERS ----------
    @Subscribe
    public void onMessageFromServer(Message msg) {
        if (msg == null || msg.getMessage() == null) return;
        String kind = msg.getMessage();
        if (!"orders_data_report".equals(kind) && !"orders_data".equals(kind)) return;

        if (!"orders_data_report".equals(msg.getMessage())) return;

        // Always switch to FX thread
        Platform.runLater(() -> {
            // Defensive cast
            System.out.println("DEBUG: "+kind+ "received");
            List<Order> orders = new ArrayList<>();
            Object obj = msg.getObject();
            if (obj instanceof List<?>) {
                for (Object o : (List<?>) obj) {
                    if (o instanceof Order) orders.add((Order) o);
                }
            }

            // Client-side safety filter (in case server didn't filter):
            String branch = Optional.ofNullable(branchComboBox.getValue()).orElse("All Branches");
            LocalDate start = startDatePicker.getValue();
            LocalDate end   = endDatePicker.getValue();
            LocalDateTime from = start != null ? start.atStartOfDay() : null;
            LocalDateTime to   = end != null ? end.plusDays(1).atStartOfDay() : null;

            orders = orders.stream()
                    .filter(o -> {
                        LocalDateTime od = o.getOrderDate();
                        boolean inTime = (od == null) ||
                                ((from == null || !od.isBefore(from)) && (to == null || od.isBefore(to)));
                        boolean inBranch = "All Branches".equalsIgnoreCase(branch)
                                || Objects.equals(branch, safe(o.getStoreLocation()));
                        return inTime && inBranch;
                    })
                    .collect(Collectors.toList());

            if (orders.isEmpty()) {
                clearChart();
                reportOutput.setText("No orders found for the selected filters.");
                return;
            }
            if (incomeReportBtn.isSelected()) {
                renderIncomeFromOrders(orders, branch, from, to);
            } else if (ordersReportBtn.isSelected()) {
                renderOrdersFromOrders(orders, branch, from, to);
            } else if (complaintsReportBtn.isSelected()) {
                reportOutput.setText("Complaints report is not wired to server data yet.");
            }
        });
    }


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
    private String safe(String s) { return s == null ? "" : s; }

    private void renderIncomeFromOrders(List<Order> orders, String branch, LocalDateTime from, LocalDateTime to) {
        BigDecimal revenue = BigDecimal.ZERO;

        for (Order o : orders) {
            double orderTotal = 0.0;

            // Prefer Order.getTotal() if you added it
            try {
                orderTotal = o.getTotal(); // <- now that you set it on the server
            } catch (Throwable ignored) { /* fallback below */ }

            if (orderTotal <= 0.0 && o.getItems() != null) {
                for (OrderItem it : o.getItems()) {
                    double price = 0.0;
                    int qty = 0;
                    try { price = it.getProduct().getPrice(); } catch (Throwable ignored) {}
                    try { qty   = it.getQuantity(); }          catch (Throwable ignored) {}
                    orderTotal += price * qty;
                }
            }
            revenue = revenue.add(BigDecimal.valueOf(orderTotal));
        }

        BigDecimal expenses = BigDecimal.ZERO; // replace when you track COGS/expenses
        BigDecimal net = revenue.subtract(expenses);

        // days in [from, to) — avoid divide-by-zero
        long days = 1;
        if (from != null && to != null) {
            long d = java.time.temporal.ChronoUnit.DAYS.between(from.toLocalDate(), to.toLocalDate());
            if (d > 0) days = d;
        }
        BigDecimal avgDaily = net.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);

        int transactions = orders.size();

        IncomeReport r = new IncomeReport(
                0, branch, 0L,
                (from != null ? from : LocalDate.now().atStartOfDay()),
                (to   != null ? to   : LocalDate.now().plusDays(1).atStartOfDay()),
                revenue,
                expenses,
                net,
                avgDaily,
                transactions
        );

        clearChart();
        renderIncomeReport(r);
    }

    private void renderOrdersFromOrders(List<Order> orders, String branch, LocalDateTime from, LocalDateTime to) {
        int totalOrders = orders.size();

        // If you have cancellation status, compute it here
        int cancelled = 0; // e.g., (int) orders.stream().filter(o -> "CANCELLED".equalsIgnoreCase(o.getStatus())).count();
        int netOrders = totalOrders - cancelled;

        Map<String,Integer> hist = new LinkedHashMap<>();
        for (Order o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItem it : o.getItems()) {
                String key = (it.getProduct() != null && it.getProduct().getName() != null)
                        ? it.getProduct().getName()
                        : "Unknown";

                int qty = 0;
                try { qty = it.getQuantity(); } catch (Throwable ignored) {}
                hist.merge(key, qty, Integer::sum);
            }
        }


        // Build a DTO and display with your existing renderer
        OrdersReport r = new OrdersReport(
                0, branch, 0L,
                from != null ? from : LocalDate.now().atStartOfDay(),
                to   != null ? to   : LocalDate.now().plusDays(1).atStartOfDay(),
                totalOrders,
                cancelled,
                netOrders,
                hist
        );

        clearChart();
        renderOrdersReport(r);
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
