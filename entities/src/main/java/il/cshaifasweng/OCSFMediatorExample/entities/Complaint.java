package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "complaints")
public class Complaint implements Serializable {
    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.EAGER)
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER)
    private Order order;

    @Column(length = 100, nullable = false)
    private String text;

    private LocalDateTime submittedAt;

    private boolean resolved = false;

    private LocalDateTime deadline; // must be answered within 24 hours

    public Complaint() {}

    public Complaint(Customer customer, Order order, String text) {
        this.customer = customer;
        this.order = order;
        this.text = text;
        this.submittedAt = LocalDateTime.now();
        this.deadline = this.submittedAt.plusHours(24);
    }

    // Getters & Setters
    public int getId() { return id; }

    public Customer getCustomer() { return customer; }

    public void setCustomer(Customer customer) { this.customer = customer; }

    public Order getOrder() { return order; }

    public void setOrder(Order order) { this.order = order; }

    public String getText() { return text; }

    public void setText(String text) { this.text = text; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }

    public boolean isResolved() { return resolved; }

    public void setResolved(boolean resolved) { this.resolved = resolved; }

    public LocalDateTime getDeadline() { return deadline; }

    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
}

