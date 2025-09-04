package il.cshaifasweng.OCSFMediatorExample.entities;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ComplaintsReport extends Report{
    private Map<LocalDateTime,Integer> complaintsHistogram;
    private int totalComplaints;
    private int resolvedComplaints;
    private int unresolvedComplaints;
    private LocalDateTime maxComplaintsDate;
    private String slot, requestId;

    @Override
    public void generate() {
        // Mock data
        this.complaintsHistogram = new HashMap<>();
        LocalDateTime today = LocalDateTime.now();
        complaintsHistogram.put(today.minusDays(3), 5);
        complaintsHistogram.put(today.minusDays(2), 12);
        complaintsHistogram.put(today.minusDays(1), 8);

        this.totalComplaints = complaintsHistogram.values().stream().mapToInt(Integer::intValue).sum();
        this.resolvedComplaints = 20;
        this.unresolvedComplaints = totalComplaints - resolvedComplaints;
        this.maxComplaintsDate = complaintsHistogram.entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
    public ComplaintsReport(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate,Map<LocalDateTime,Integer> complaintsHistogram, int totalComplaints, int resolvedComplaints, int unresolvedComplaints, LocalDateTime maxComplaintsDate) {
        super(store_id, branch_name, branch_id, startDate, endDate);
        this.complaintsHistogram = complaintsHistogram;
        this.totalComplaints = totalComplaints;
        this.resolvedComplaints = resolvedComplaints;
        this.unresolvedComplaints = unresolvedComplaints;
        this.maxComplaintsDate = maxComplaintsDate;
    }
    public Map<LocalDateTime, Integer> getComplaintsHistogram() {
        return complaintsHistogram;
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
        this.complaintsHistogram = complaintsHistogram;
    }
    private Map<String, Number> histogram = new LinkedHashMap<>();
    public Map<String, Number> getHistogram(){ return histogram; }
    public void setHistogram(Map<String, Number> h){ this.histogram = h; }
}
