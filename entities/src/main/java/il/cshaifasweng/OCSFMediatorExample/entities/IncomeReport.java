package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public class IncomeReport extends Report implements Serializable {
    private static final long serialVersionUID = 1L;

    // existing
    private BigDecimal TotalRevenue;
    private BigDecimal TotalExpenses;
    private BigDecimal NetProfit;
    private BigDecimal AvgDailyNetProfit;
    private int TotalTransactions;

    // NEW: what the chart needs
    private Map<String, Number> histogram = new LinkedHashMap<>();

    // NEW: compare-mode routing
    private String slot;       // "A" or "B"
    private String requestId;  // UUID echoed back

    public IncomeReport(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate,
                        BigDecimal totalRevenue, BigDecimal totalExpenses, BigDecimal netProfit,
                        BigDecimal avgDailyNetProfit, int totalTransactions) {
        super(store_id, branch_name, branch_id, startDate, endDate);
        this.TotalRevenue = totalRevenue;
        this.TotalExpenses = totalExpenses;
        this.NetProfit = netProfit;
        this.AvgDailyNetProfit = avgDailyNetProfit;
        this.TotalTransactions = totalTransactions;
    }

    @Override
    public void generate() {
        // (kept for old mock flows)
        this.TotalRevenue = new BigDecimal("12000.50");
        this.TotalExpenses = new BigDecimal("4500.25");
        this.TotalTransactions = 300;
        computeDerivedFields();
    }

    private void computeDerivedFields() {
        this.NetProfit = this.TotalRevenue.subtract(this.TotalExpenses);
        long days = Math.max(1, ChronoUnit.DAYS.between(getStartDate().toLocalDate(), getEndDate().toLocalDate()));
        this.AvgDailyNetProfit = this.NetProfit.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    // getters/setters
    public BigDecimal getTotalRevenue() { return TotalRevenue; }
    public BigDecimal getTotalExpenses() { return TotalExpenses; }
    public BigDecimal getNetProfit() { return NetProfit; }
    public BigDecimal getAvgDailyNetProfit() { return AvgDailyNetProfit; }
    public int getTotalTransactions() { return TotalTransactions; }
    public void setTotalRevenue(BigDecimal v) { this.TotalRevenue = v; }
    public void setTotalExpenses(BigDecimal v) { this.TotalExpenses = v; }
    public void setNetProfit(BigDecimal v) { this.NetProfit = v; }
    public void setAvgDailyNetProfit(BigDecimal v) { this.AvgDailyNetProfit = v; }
    public void setTotalTransactions(int v) { this.TotalTransactions = v; }

    public Map<String, Number> getHistogram() { return histogram; }
    public void setHistogram(Map<String, Number> histogram) { this.histogram = histogram; }
    public String getSlot() { return slot; }
    public void setSlot(String slot) { this.slot = slot; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
