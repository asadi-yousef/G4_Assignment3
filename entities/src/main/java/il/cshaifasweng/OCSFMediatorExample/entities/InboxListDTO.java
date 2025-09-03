package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.util.List;

public class InboxListDTO implements Serializable {
    private List<InboxItemDTO> personal;
    private List<InboxItemDTO> broadcast;

    public InboxListDTO() {}

    public InboxListDTO(List<InboxItemDTO> personal, List<InboxItemDTO> broadcast) {
        this.personal = personal;
        this.broadcast = broadcast;
    }

    public List<InboxItemDTO> getPersonal() { return personal; }
    public List<InboxItemDTO> getBroadcast() { return broadcast; }

    public void setPersonal(List<InboxItemDTO> personal) { this.personal = personal; }
    public void setBroadcast(List<InboxItemDTO> broadcast) { this.broadcast = broadcast; }
}
