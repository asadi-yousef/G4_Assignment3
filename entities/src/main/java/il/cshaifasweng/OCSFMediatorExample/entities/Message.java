package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.util.List;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    private String message;
    private Object object;
    private List<Object> objectList;

    public Message(String message, Object object, List<Object> objectList) {
        this.message = message;
        this.object = object;
        this.objectList = objectList;
    }
    public String getMessage() {
        return message;
    }
    public void setMessage(String message) {
        this.message = message;
    }
    public Object getObject() {
        return object;
    }
    public void setObject(Object object) {
        this.object = object;
    }
    public List<Object> getObjectList() {
        return objectList;
    }
    public void setObjectList(List<Object> objectList) {
        this.objectList = objectList;
    }
}
