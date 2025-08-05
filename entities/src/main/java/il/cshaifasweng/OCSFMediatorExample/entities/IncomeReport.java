package il.cshaifasweng.OCSFMediatorExample.entities;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class IncomeReport extends Report{
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
