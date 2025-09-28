package il.cshaifasweng.OCSFMediatorExample.client;

import il.cshaifasweng.OCSFMediatorExample.entities.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
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
    @FXML private Button compareButton;

    // Custom legend under the chart
    @FXML private VBox legendBox;
    @FXML private HBox legendRowA, legendRowB;
    @FXML private Label legendALabel, legendBLabel;
    @FXML private javafx.scene.shape.Rectangle legendASwatch, legendBSwatch;

    private ToggleGroup reportTypeGroup;
    private List<Branch> branches;
    private volatile boolean overlayMode = false;

    // Colors must match your custom legend
    private static final boolean USE_CUSTOM_COLORS = true;
    private static final String COLOR_A = "#2E86DE"; // blue
    private static final String COLOR_B = "#E67E22"; // orange

    // Padding categories for sparse charts
    private static final String PAD_L = "";       // figure-space
    private static final String PAD_R = "\u2007"; // two figure-spaces

    private String labelA = "A";
    private String labelB = "B";

    private final Map<String, Long> branchNameToId = new HashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }

        if (!(isNetManager() || isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
        }

        // Request branches
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

        // Dates defaults
        LocalDate today = LocalDate.now();
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate lastOfMonth  = today.withDayOfMonth(today.lengthOfMonth());

        if (isNetManager()) {
            startDatePicker.setValue(today.minusMonths(1));
            endDatePicker.setValue(today);
        } else if (isBranchManager()) {
            User current = SessionManager.getInstance().getCurrentUser();
            Branch myBranch = current.getBranch();
            if(myBranch != null) {
                branchComboBox.getItems().setAll(myBranch.getName());
                branchComboBox.getSelectionModel().selectFirst();
                branchComboBox.setDisable(true);
            }
         //   String myBranchName = Optional.ofNullable(currentUserBranchName()).orElse("My Branch");
         //   branchComboBox.getItems().setAll(myBranchName);
         //   branchComboBox.getSelectionModel().selectFirst();
         //   branchComboBox.setDisable(true);

            startDatePicker.setValue(firstOfMonth);
            endDatePicker.setValue(today.isBefore(lastOfMonth) ? today : lastOfMonth);
            restrictDatePickerToMonth(startDatePicker, firstOfMonth, lastOfMonth);
            restrictDatePickerToMonth(endDatePicker, firstOfMonth, lastOfMonth);
        }

        // Use our custom legend (built-in OFF)
        barChart.setLegendVisible(false);
        barChart.setPadding(new Insets(8, 12, 24, 12)); // default; will be overridden for sparse data in tuneChart(...)
        barChart.setMinHeight(360);

        // Hide custom legend rows until we have data
        showLegendRow(false, false);

        reportOutput.setText("Pick a type, branch and dates, then click Generate.");
        clearChart();
    }

    private void loadBranchesForSystemManager() {
        branchComboBox.getItems().clear();
        branchNameToId.clear();

        if (isNetManager()) {
            branchComboBox.getItems().add("All Branches");
            branchNameToId.put("All Branches", null);
        }

        if (branches != null) {
            for (Branch branch : branches) {
                if (branch == null) continue;
                String name = branch.getName();
                Long id = null;
                try {
                    Object rawId = branch.getId();
                    if (rawId instanceof Number) id = ((Number) rawId).longValue();
                    else if (rawId != null) id = Long.valueOf(rawId.toString());
                } catch (Exception ignored) {}
                if (name != null) {
                    branchComboBox.getItems().add(name);
                    branchNameToId.put(name, id);
                }
            }
        }

        if (isBranchManager()) {
            String myBranchName = Optional.ofNullable(currentUserBranchName()).orElse(null);
            if (myBranchName != null) {
                for (String name : new ArrayList<>(branchNameToId.keySet())) {
                    if (name.equalsIgnoreCase(myBranchName)) { myBranchName = name; break; }
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
        overlayMode = false; // single run
        if (!(isNetManager() || isBranchManager())) {
            showInfo("Permission Denied", "You do not have permission to view reports.");
            return;
        }

        String branch = Optional.ofNullable(branchComboBox.getValue()).orElse("All Branches");
        Long branchId = branchNameToId.get(branch);
        LocalDate start = startDatePicker.getValue();
        LocalDate end   = endDatePicker.getValue();

        if (start == null || end == null) { showInfo("Validation","Please pick start and end dates."); return; }
        if (end.isBefore(start)) { showInfo("Validation","End date cannot be before start date."); return; }

        // Branch manager restrictions
        if (isBranchManager()) {
            String myBranch = Optional.ofNullable(currentUserBranchName()).orElse(branch);
            if (!Objects.equals(branch, myBranch)) {
                showInfo("Permission Denied", "Branch managers can only view their own branch."); return;
            }
            LocalDate first = LocalDate.now().withDayOfMonth(1);
            LocalDate last  = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            if (start.isBefore(first) || end.isAfter(last)) {
                showInfo("Permission Denied", "Branch managers can only view reports for this month."); return;
            }
        }

        // Legend: A only
        labelA = "A: " + branch + " (" + start + " → " + end + ")";
        setLegendA(labelA);
        showLegendRow(true, false);

        clearChart();
        reportOutput.setText("Fetching " + (incomeReportBtn.isSelected() ? "income" : ordersReportBtn.isSelected() ? "orders" : "complaints")
                + " for " + branch + " (" + start + " → " + end + ")...");

        Map<String,Object> criteria = buildCriteria(branch, branchId, start, end, "A");
        String key = incomeReportBtn.isSelected() ? "request_report_income"
                : ordersReportBtn.isSelected() ? "request_report_orders"
                : "request_report_complaints";

        try {
            if (!SimpleClient.getClient().isConnected()) SimpleClient.getClient().openConnection();
            SimpleClient.getClient().sendToServer(new Message(key, criteria, Collections.emptyList()));
        } catch (IOException e) {
            showInfo("Connection Error", "Failed to reach server.");
        }
    }

    @FXML
    private void onCompare() {
        if (!isNetManager()) {
            showInfo("Permission Denied", "Compare is available to system managers.");
            return;
        }

        String branchA = Optional.ofNullable(branchComboBox.getValue()).orElse("All Branches");
        Long branchIdA = branchNameToId.get(branchA);
        LocalDate startA = startDatePicker.getValue();
        LocalDate endA   = endDatePicker.getValue();
        if (startA == null || endA == null) { showInfo("Validation","Pick start and end dates (left)."); return; }
        if (endA.isBefore(startA)) { showInfo("Validation","Left end date cannot be before start date."); return; }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Compare Reports (Report B)");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        ComboBox<String> branchB = new ComboBox<>();
        branchB.getItems().setAll(branchComboBox.getItems());
        branchB.getSelectionModel().selectFirst();

        DatePicker startB = new DatePicker(Optional.ofNullable(startDatePicker.getValue()).orElse(LocalDate.now().minusWeeks(1)));
        DatePicker endB   = new DatePicker(Optional.ofNullable(endDatePicker.getValue()).orElse(LocalDate.now()));

        GridPane gp = new GridPane();
        gp.setHgap(10); gp.setVgap(8);
        gp.addRow(0, new Label("Branch B:"), branchB);
        gp.addRow(1, new Label("From B:"),   startB);
        gp.addRow(2, new Label("To B:"),     endB);
        dlg.getDialogPane().setContent(gp);

        Optional<ButtonType> res = dlg.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return;
        if (startB.getValue() == null || endB.getValue() == null) { showInfo("Validation","Pick dates for Report B."); return; }
        if (endB.getValue().isBefore(startB.getValue())) { showInfo("Validation","Right end date cannot be before start date."); return; }

        labelA = "A: " + branchA + " (" + startA + " → " + endA + ")";
        String branchBVal = Optional.ofNullable(branchB.getValue()).orElse("All Branches");
        labelB = "B: " + branchBVal + " (" + startB.getValue() + " → " + endB.getValue() + ")";

        overlayMode = true;
        clearChart();
        reportOutput.setText("Comparing:\n" + labelA + "\n" + labelB);

        setLegendA(labelA);
        setLegendB(labelB);
        showLegendRow(true, true);

        Long branchIdB = branchNameToId.get(branchBVal);
        String key = incomeReportBtn.isSelected() ? "request_report_income"
                : ordersReportBtn.isSelected() ? "request_report_orders"
                : "request_report_complaints";

        Map<String,Object> critA = buildCriteria(branchA, branchIdA, startA, endA, "A");
        Map<String,Object> critB = buildCriteria(branchBVal, branchIdB, startB.getValue(), endB.getValue(), "B");

        try {
            if (!SimpleClient.getClient().isConnected()) SimpleClient.getClient().openConnection();
            SimpleClient.getClient().sendToServer(new Message(key, critA, Collections.emptyList()));
            SimpleClient.getClient().sendToServer(new Message(key, critB, Collections.emptyList()));
        } catch (IOException e) {
            showInfo("Connection Error","Failed to reach server.");
        }
    }

    // ===== Subscribers

    @Subscribe
    public void onIncomeReport(Message msg) {
        if (msg == null || !"report_income_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            String slot = Objects.toString(data.getOrDefault("slot","A"));

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number totalRevenue  = (Number) data.getOrDefault("totalRevenue", 0d);
            Number totalExpenses = (Number) data.getOrDefault("totalExpenses", 0d);
            Number netProfit     = (Number) data.getOrDefault("netProfit", 0d);
            Number avgDailyNet   = (Number) data.getOrDefault("avgDailyNet", 0d);
            Number transactions  = (Number) data.getOrDefault("transactions", 0);

            if (hist.isEmpty()) { if (!overlayMode || "A".equalsIgnoreCase(slot)) noData(); return; }

            if (!overlayMode || "A".equalsIgnoreCase(slot)) clearChart();

            String seriesTitle = (slot.equalsIgnoreCase("B") ? labelB : labelA) + " — Daily Revenue (₪)";
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(seriesTitle);
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            padIfSparse(s, hist.size());
            barChart.getData().add(s);

            if (USE_CUSTOM_COLORS) colorSeries(s, slot.equalsIgnoreCase("B") ? COLOR_B : COLOR_A);

            tuneChart(barChart, hist.size());
            installTooltips(barChart);

            String block =
                    (slot.equalsIgnoreCase("B") ? "B" : "A") + " — Income Report\n" +
                            "Total Revenue:  " + money(numToBD(totalRevenue)) + "\n" +
                            "Total Expenses: " + money(numToBD(totalExpenses)) + "\n" +
                            "Net Profit:     " + money(numToBD(netProfit)) + "\n" +
                            "Avg Daily Net:  " + money(numToBD(avgDailyNet)) + "\n" +
                            "Transactions:   " + transactions + "\n";

            if (!overlayMode || "A".equalsIgnoreCase(slot)) reportOutput.setText(block);
            else reportOutput.appendText("\n" + block);
        });
    }

    @Subscribe
    public void onOrdersReport(Message msg) {
        if (msg == null || !"report_orders_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            String slot = Objects.toString(data.getOrDefault("slot","A"));

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number total     = (Number) data.getOrDefault("totalOrders", 0);
            Number cancelled = (Number) data.getOrDefault("cancelled", 0);
            Number net       = (Number) data.getOrDefault("netOrders", 0);

            if (hist.isEmpty()) { if (!overlayMode || "A".equalsIgnoreCase(slot)) noData(); return; }

            if (!overlayMode || "A".equalsIgnoreCase(slot)) clearChart();

            String seriesTitle = (slot.equalsIgnoreCase("B") ? labelB : labelA) + " — Orders by Item";
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(seriesTitle);
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            padIfSparse(s, hist.size());
            barChart.getData().add(s);

            if (USE_CUSTOM_COLORS) colorSeries(s, slot.equalsIgnoreCase("B") ? COLOR_B : COLOR_A);

            tuneChart(barChart, hist.size());
            installTooltips(barChart);

            String block =
                    (slot.equalsIgnoreCase("B") ? "B" : "A") + " — Orders Report\n" +
                            "Total Orders: " + total + "\n" +
                            "Cancelled:    " + cancelled + "\n" +
                            "Net Orders:   " + net + "\n";

            if (!overlayMode || "A".equalsIgnoreCase(slot)) reportOutput.setText(block);
            else reportOutput.appendText("\n" + block);
        });
    }

    @Subscribe
    public void onComplaintsReport(Message msg) {
        if (msg == null || !"report_complaints_data".equals(msg.getMessage())) return;
        Platform.runLater(() -> {
            @SuppressWarnings("unchecked")
            Map<String,Object> data = (Map<String,Object>) msg.getObject();
            if (data == null) { noData(); return; }

            String slot = Objects.toString(data.getOrDefault("slot","A"));

            @SuppressWarnings("unchecked")
            Map<String, Number> hist = (Map<String, Number>) data.getOrDefault("histogram", Collections.emptyMap());
            Number total      = (Number) data.getOrDefault("total", 0);
            Number resolved   = (Number) data.getOrDefault("resolved", 0);
            Number unresolved = (Number) data.getOrDefault("unresolved", 0);
            String peakDay    = Objects.toString(data.getOrDefault("peakDay","-"));

            if (hist.isEmpty()) { if (!overlayMode || "A".equalsIgnoreCase(slot)) noData(); return; }

            if (!overlayMode || "A".equalsIgnoreCase(slot)) clearChart();

            String seriesTitle = (slot.equalsIgnoreCase("B") ? labelB : labelA) + " — Complaints per Day";
            XYChart.Series<String, Number> s = new XYChart.Series<>();
            s.setName(seriesTitle);
            for (Map.Entry<String, Number> e : hist.entrySet()) {
                s.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
            }
            padIfSparse(s, hist.size());
            barChart.getData().add(s);

            if (USE_CUSTOM_COLORS) colorSeries(s, slot.equalsIgnoreCase("B") ? COLOR_B : COLOR_A);

            tuneChart(barChart, hist.size());
            installTooltips(barChart);

            String block =
                    (slot.equalsIgnoreCase("B") ? "B" : "A") + " — Complaints Report\n" +
                            "Total:      " + total + "\n" +
                            "Resolved:   " + resolved + "\n" +
                            "Unresolved: " + unresolved + "\n" +
                            "Peak Day:   " + peakDay + "\n";

            if (!overlayMode || "A".equalsIgnoreCase(slot)) reportOutput.setText(block);
            else reportOutput.appendText("\n" + block);
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
        showLegendRow(false, false);
    }

    // ===== Helpers

    private Map<String, Object> buildCriteria(String branch, Long branchId, LocalDate start, LocalDate end, String slot) {
        Map<String, Object> criteria = new HashMap<>();
        criteria.put("branchId", branchId);
        criteria.put("branch", (branchId == null) ? null : branch);
        criteria.put("from", start.atStartOfDay());
        criteria.put("to",   end.plusDays(1).atStartOfDay());
        criteria.put("slot", slot);
        return criteria;
    }

    /** Set spacing + keep x-labels horizontal (0°). Also give extra gap for sparse data. */
    private void tuneChart(BarChart<String, Number> chart, int categoryCount) {
        if (chart == null) return;
        chart.setBarGap(5);
        chart.setAnimated(false);

        if (categoryCount <= 2) {
            chart.setCategoryGap(70); // slimmer bars
        } else if (categoryCount <= 5) {
            chart.setCategoryGap(25);
        } else {
            chart.setCategoryGap(12);
        }

        if (categoryCount <= 2) {
            chart.setPadding(new Insets(8, 8, 24, 4)); // << smaller LEFT padding -> bars start closer to the left axis
        } else {
            chart.setPadding(new Insets(8, 12, 24, 12)); // default
        }


        if (chart.getXAxis() instanceof CategoryAxis) {
            CategoryAxis x = (CategoryAxis) chart.getXAxis();
            x.setTickLabelRotation(0); // << straight text
            x.setTickLabelGap(6);
        }
    }

    /** Add invisible side categories so a 1-bar chart doesn't fill the plot. */
    private void padIfSparse(XYChart.Series<String, Number> s, int realCount) {
        if (realCount >= 3) return;
        XYChart.Data<String, Number> right = new XYChart.Data<>(PAD_R, 0);
        s.getData().add(right);
        Platform.runLater(() -> {
            if (right.getNode() != null) {
                right.getNode().setStyle("-fx-bar-fill: transparent;");
                right.getNode().setMouseTransparent(true);
            }
        });
    }

    /** Tooltips only for real categories (not pads). */
    private void installTooltips(BarChart<String, Number> chart) {
        if (chart == null) return;
        Platform.runLater(() -> {
            for (XYChart.Series<String, Number> s : chart.getData()) {
                for (XYChart.Data<String, Number> d : s.getData()) {
                    String x = d.getXValue();
                    String normalized = x == null ? "" : x.replace("\u2007", "").trim();
                    if (normalized.isEmpty()) continue; // skip pad bars
                    if (d.getNode() != null) {
                        Tooltip.install(d.getNode(), new Tooltip(x + ": " + d.getYValue()));
                    } else {
                        d.nodeProperty().addListener((o, oldN, newN) -> {
                            if (newN != null) Tooltip.install(newN, new Tooltip(x + ": " + d.getYValue()));
                        });
                    }
                }
            }
        });
    }

    /** Color bars to match our custom legend. */
    private void colorSeries(XYChart.Series<String, Number> s, String colorHex) {
        for (XYChart.Data<String, Number> d : s.getData()) {
            if (d.getNode() != null) {
                d.getNode().setStyle("-fx-bar-fill: " + colorHex + ";");
            } else {
                d.nodeProperty().addListener((obs, oldN, newN) -> {
                    if (newN != null) newN.setStyle("-fx-bar-fill: " + colorHex + ";");
                });
            }
        }
    }

    private void setLegendA(String text) {
        if (legendALabel != null) legendALabel.setText(text);
        if (legendASwatch != null) legendASwatch.setStyle("-fx-fill: " + COLOR_A + ";");
    }

    private void setLegendB(String text) {
        if (legendBLabel != null) legendBLabel.setText(text);
        if (legendBSwatch != null) legendBSwatch.setStyle("-fx-fill: " + COLOR_B + ";");
    }

    private void showLegendRow(boolean showA, boolean showB) {
        if (legendRowA != null) { legendRowA.setVisible(showA); legendRowA.setManaged(showA); }
        if (legendRowB != null) { legendRowB.setVisible(showB); legendRowB.setManaged(showB); }
    }

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

    private boolean isNetManager() {
        return "netmanager".equalsIgnoreCase(userRole());
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
