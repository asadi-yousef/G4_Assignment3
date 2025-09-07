package il.cshaifasweng.OCSFMediatorExample.entities;
import java.io.Serializable;
import java.math.BigDecimal;

public class ScheduleItemDTO implements Serializable {
    private String name;
    private int quantity;
    private BigDecimal unitPrice;
    private String imagePath;

    public ScheduleItemDTO() {}
    public ScheduleItemDTO(String name, int quantity, BigDecimal unitPrice, String imagePath) {
        this.name = name; this.quantity = quantity; this.unitPrice = unitPrice; this.imagePath = imagePath;
    }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getImagePath() { return imagePath; }
}
