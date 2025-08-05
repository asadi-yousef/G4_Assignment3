package il.cshaifasweng.OCSFMediatorExample.entities;
import java.time.LocalDateTime;

public abstract class Report{
    protected int Store_id;
    protected String Branch_name;
    protected long Branch_id;
    protected LocalDateTime StartDate;
    protected LocalDateTime EndDate;

    public Report(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate) {
        this.Store_id = store_id;
        this.Branch_name = branch_name;
        this.Branch_id = branch_id;
        this.StartDate = startDate;
        this.EndDate = endDate;
    }

    public int getStore_id() {
        return Store_id;
    }
    public String getBranch_name() {
        return Branch_name;
    }
    public long getBranch_id() {
        return Branch_id;
    }

    public LocalDateTime getStartDate() {
        return StartDate;
    }

    public LocalDateTime getEndDate() {
        return EndDate;
    }

    public void setStore_id(int store_id) {
        Store_id = store_id;
    }

    public void setBranch_name(String branch_name) {
        Branch_name = branch_name;
    }

    public void setBranch_id(long branch_id) {
        Branch_id = branch_id;
    }

    public void setStartDate(LocalDateTime startDate) {
        StartDate = startDate;
    }

    public void setEndDate(LocalDateTime endDate) {
        EndDate = endDate;
    }
}
