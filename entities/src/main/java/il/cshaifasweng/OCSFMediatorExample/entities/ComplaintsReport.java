package il.cshaifasweng.OCSFMediatorExample.entities;
import java.time.LocalDateTime;
import java.util.Map;

public class ComplaintsReport extends Report{
    private Map<LocalDateTime,Integer> ComplaintsHistogram;
    private int totalComplaints;
    private int resolvedComplaints;
    private int unresolvedComplaints;
    private LocalDateTime maxComplaintsDate;

    ComplaintsReport(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate,Map<LocalDateTime,Integer> complaintsHistogram, int totalComplaints, int resolvedComplaints, int unresolvedComplaints, LocalDateTime maxComplaintsDate) {
        super(store_id, branch_name, branch_id, startDate, endDate);
        this.ComplaintsHistogram = complaintsHistogram;
        this.totalComplaints = totalComplaints;
        this.resolvedComplaints = resolvedComplaints;
        this.unresolvedComplaints = unresolvedComplaints;
        this.maxComplaintsDate = maxComplaintsDate;
    }
    public Map<LocalDateTime, Integer> getComplaintsHistogram() {
        return ComplaintsHistogram;
    }
    public int getTotalComplaints() {
        return totalComplaints;
    }
    public int getResolvedComplaints() {
        return resolvedComplaints;
    }
    public int getUnresolvedComplaints() {
        return unresolvedComplaints;
    }
    public LocalDateTime getMaxComplaintsDate() {
        return maxComplaintsDate;
    }
    public void setMaxComplaintsDate(LocalDateTime maxComplaintsDate) {
        this.maxComplaintsDate = maxComplaintsDate;
    }
    public void setTotalComplaints(int totalComplaints) {
        this.totalComplaints = totalComplaints;
    }
    public void setResolvedComplaints(int resolvedComplaints) {
        this.resolvedComplaints = resolvedComplaints;
    }
    public void setUnresolvedComplaints(int unresolvedComplaints) {
        this.unresolvedComplaints = unresolvedComplaints;
    }
    public void setComplaintsHistogram(Map<LocalDateTime, Integer> complaintsHistogram) {
        ComplaintsHistogram = complaintsHistogram;
    }
}
