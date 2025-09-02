package il.cshaifasweng.OCSFMediatorExample.entities;

import il.cshaifasweng.OCSFMediatorExample.entities.UserAdminDTO;

import java.io.Serializable;
import java.util.List;

public class AdminUsersPage implements Serializable {
    private static final long serialVersionUID = 1L;
    private List<UserAdminDTO> rows;
    private int total;

    public AdminUsersPage() {}
    public AdminUsersPage(List<UserAdminDTO> rows, int total) {
        this.rows = rows;
        this.total = total;
    }
    public List<UserAdminDTO> getRows() {
        return rows;
    }
    public void setRows(List<UserAdminDTO> rows) {
        this.rows = rows;
    }
    public int getTotal() {
        return total;
    }
    public void setTotal(int total) {
        this.total = total;
    }
}
