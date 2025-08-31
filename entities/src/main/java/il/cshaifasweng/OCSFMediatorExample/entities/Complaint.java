package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
public class Complaint implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="order_id")
    private Order order;

    @Column(length = 120, nullable = false) // <= was 100, now 120
    private String text;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name="branch_id", nullable = true)
    private Branch branch;

    private LocalDateTime submittedAt;
    private boolean resolved = false;

    // must be answered within 24 hours
    private LocalDateTime deadline;

    // --- NEW: resolver data ---
    @Column(length = 500)
    private String responseText;               // employee reply to customer

    @Column(precision = 10, scale = 2)
    private BigDecimal compensationAmount;     // optional

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "responder_id")
    private Employee responder;                // who resolved

    private LocalDateTime resolvedAt;          // timestamp of resolve

    public Complaint() {}

    public Complaint(Customer customer, Order order, String text) {
        this.customer = customer;
        this.order = order;
        this.text = text;
        this.submittedAt = LocalDateTime.now();
        this.deadline = this.submittedAt.plusHours(24);
        // We scope to customer's branch (Order stores branch as name only)
        this.branch = (customer != null) ? customer.getBranch() : null;
    }

    public int getId() { return id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Branch getBranch() { return branch; }
    public void setBranch(Branch branch) { this.branch = branch; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public boolean isResolved() { return resolved; }
    public void setResolved(boolean resolved) { this.resolved = resolved; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public String getResponseText() { return responseText; }
    public void setResponseText(String responseText) { this.responseText = responseText; }
    public BigDecimal getCompensationAmount() { return compensationAmount; }
    public void setCompensationAmount(BigDecimal compensationAmount) { this.compensationAmount = compensationAmount; }
    public Employee getResponder() { return responder; }
    public void setResponder(Employee responder) { this.responder = responder; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
