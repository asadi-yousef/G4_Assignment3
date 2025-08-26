package il.cshaifasweng.OCSFMediatorExample.entities;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class IncomeReport extends Report implements Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal TotalRevenue;
    private BigDecimal TotalExpenses;
    private BigDecimal NetProfit;
    private BigDecimal AvgDailyNetProfit;
    private int TotalTransactions;

    public IncomeReport(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate, BigDecimal totalRevenue,BigDecimal totalExpenses, BigDecimal netProfit, BigDecimal avgDailyNetProfit, int totalTransactions) {
        super(store_id, branch_name, branch_id, startDate, endDate);
        TotalRevenue = totalRevenue;
        TotalExpenses = totalRevenue;
        NetProfit = totalRevenue;
        AvgDailyNetProfit = netProfit;
        TotalTransactions = totalTransactions;
    }
    @Override
    public void generate() {
        // Mock data (replace with DB calls later)
        this.TotalRevenue = new BigDecimal("12000.50");
        this.TotalExpenses = new BigDecimal("4500.25");
        this.TotalTransactions = 300;
        computeDerivedFields();
    }
    private void computeDerivedFields() {
        this.NetProfit = this.TotalRevenue.subtract(this.TotalExpenses);
        long days = Math.max(1,
                ChronoUnit.DAYS.between(getStartDate().toLocalDate(), getEndDate().toLocalDate()));
        this.AvgDailyNetProfit = this.NetProfit.divide(BigDecimal.valueOf(days), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getTotalRevenue() {
        return TotalRevenue;
    }
    public BigDecimal getTotalExpenses() {
        return TotalExpenses;
    }
    public BigDecimal getNetProfit() {
        return NetProfit;
    }
    public BigDecimal getAvgDailyNetProfit() {
        return AvgDailyNetProfit;
    }
    public int getTotalTransactions() {
        return TotalTransactions;
    }
    public void setTotalRevenue(BigDecimal TotalRevenue) {
        this.TotalRevenue = TotalRevenue;
    }
    public void setTotalExpenses(BigDecimal TotalExpenses) {
        this.TotalExpenses = TotalExpenses;
    }
    public void setNetProfit(BigDecimal NetProfit) {
        this.NetProfit = NetProfit;
    }
    public void setAvgDailyNetProfit(BigDecimal AvgDailyNetProfit) {
        this.AvgDailyNetProfit = AvgDailyNetProfit;
    }
    public void setTotalTransactions(int TotalTransactions) {
        this.TotalTransactions = TotalTransactions;
    }
}
