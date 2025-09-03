package il.cshaifasweng.OCSFMediatorExample.entities;
import java.io.Serializable;

public class AddProductRequest implements Serializable {
    public Product productMeta;   // name, type, price, discount, color...
    public byte[] imageBytes;     // may be null
    public String imageName;      // e.g., "rose.png" (to keep extension)
}