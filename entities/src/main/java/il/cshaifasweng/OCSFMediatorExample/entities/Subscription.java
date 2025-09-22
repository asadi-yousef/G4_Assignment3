package il.cshaifasweng.OCSFMediatorExample.entities;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDate;

@Entity
public class Subscription implements Serializable {
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(nullable = false)
    private boolean active;

    // Each subscription belongs to a customer
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false,unique = true)
    private Customer customer;

    public Subscription() {}

    public Subscription(LocalDate startDate, LocalDate endDate, boolean active, Customer customer) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.active = active;
        this.customer = customer;
    }

    // Getters and setters
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer c) {
        if (this.customer == c) return;
        this.customer = c;
        if (c != null && c.getSubscription() != this) c.setSubscription(this);
    }
    public boolean isCurrentlyActive() {
        LocalDate today = LocalDate.now();
        // start inclusive, end exclusive  →  [startDate, endDate)
        return active
                && startDate != null
                && endDate != null
                && (!today.isBefore(startDate) && today.isBefore(endDate));
    }


    //subscription renwal methods
    //Anchor date to extend from: current end when active, else today.
    public LocalDate renewalAnchor() {
        if (isCurrentlyActive() && getEndDate() != null) {
            return getEndDate();
        }
        return LocalDate.now();
    }

    // --- Renewal helpers (keep endDate exclusive) ---
    public void renewOneYear() {
        if (isCurrentlyActive() && endDate != null) {
            // extend exactly one more year from current exclusive end
            setEndDate(endDate.plusYears(1));
        } else {
            LocalDate start = LocalDate.now();
            setStartDate(start);
            setEndDate(start.plusYears(1));
        }
        setActive(true);
    }

    public void renewYears(int years) {
        if (years <= 0) return;
        if (isCurrentlyActive() && endDate != null) {
            setEndDate(endDate.plusYears(years));
        } else {
            LocalDate start = LocalDate.now();
            setStartDate(start);
            setEndDate(start.plusYears(years));
        }
        setActive(true);
    }

}
