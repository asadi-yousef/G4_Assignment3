package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.time.LocalDateTime;

public class InboxItemDTO implements Serializable {
    private long id;
    private String title;
    private String body;
    private LocalDateTime createdAt;
    private boolean read;
    private boolean broadcast;

    public InboxItemDTO() {}

    public InboxItemDTO(long id, String title, String body,
                        LocalDateTime createdAt, boolean read, boolean broadcast) {
        this.id = id;
        this.title = title;
        this.body = body;
        this.createdAt = createdAt;
        this.read = read;
        this.broadcast = broadcast;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isRead() { return read; }
    public boolean isBroadcast() { return broadcast; }

    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setBody(String body) { this.body = body; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setRead(boolean read) { this.read = read; }
    public void setBroadcast(boolean broadcast) { this.broadcast = broadcast; }
}
