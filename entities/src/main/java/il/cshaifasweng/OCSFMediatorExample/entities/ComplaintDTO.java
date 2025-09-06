package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ComplaintDTO implements Serializable {
    private int id;
    private String customerName;
    private Long orderId;
    private String text;
    private LocalDateTime submittedAt;
    private LocalDateTime deadline;
    private boolean resolved;
    private String responseText;
    private BigDecimal compensationAmount;

    public ComplaintDTO() {}

    public ComplaintDTO(Complaint c) {
        this.id = c.getId();
        this.customerName = (c.getCustomer() != null ? c.getCustomer().getFirstName() : "—");
        this.orderId = (c.getOrder() != null ? c.getOrder().getId() : null);
        this.text = c.getText();
        this.submittedAt = c.getSubmittedAt();
        this.deadline = c.getDeadline();
        this.resolved = c.isResolved();
        this.responseText = c.getResponseText();
        this.compensationAmount = c.getCompensationAmount();
    }

    // getters and setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }

    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal compensationAmount) { this.compensationAmount = compensationAmount; }
}

