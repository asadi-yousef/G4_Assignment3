package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;

public class OrderRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long customerId;

    public OrderRequest() {}

    public OrderRequest(Long customerId) {
        this.customerId = customerId;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }
}
