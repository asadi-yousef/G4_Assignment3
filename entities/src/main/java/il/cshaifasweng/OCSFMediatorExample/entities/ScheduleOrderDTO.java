package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

public class ScheduleOrderDTO implements Serializable {
    private Long id;
    private String customerName;
    private boolean delivery;
    private LocalDateTime scheduledAt;
    private String whereText;
    private String status;

    private java.util.List<ScheduleItemDTO> items;
    public java.util.List<ScheduleItemDTO> getItems() { return items; }
    public void setItems(java.util.List<ScheduleItemDTO> items) { this.items = items; }

    public ScheduleOrderDTO() {}

    public ScheduleOrderDTO(Long id, String customerName, boolean delivery,
                            LocalDateTime scheduledAt, String whereText, String status) {
        this.id = id;
        this.customerName = customerName;
        this.delivery = delivery;
        this.scheduledAt = scheduledAt;
        this.whereText = whereText;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getCustomerName() { return customerName; }
    public boolean isDelivery() { return delivery; }
    public LocalDateTime getScheduledAt() { return scheduledAt; }
    public String getWhereText() { return whereText; }
    public String getStatus() { return status; }

    public void setId(Long id) { this.id = id; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setDelivery(boolean delivery) { this.delivery = delivery; }
    public void setScheduledAt(LocalDateTime scheduledAt) { this.scheduledAt = scheduledAt; }
    public void setWhereText(String whereText) { this.whereText = whereText; }
    public void setStatus(String status) { this.status = status; }
}

