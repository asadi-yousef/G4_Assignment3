package il.cshaifasweng.OCSFMediatorExample.entities;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class OrdersReport extends Report implements Serializable {
    private static final long serialVersionUID = 1L;
    private int totalOrders;
    private int cancelledOrders;
    private int netOrders;
    private Map<String,Integer> typeHistogram;
    private String slot;
    private String requestId;


    public OrdersReport(int store_id, String branch_name, long branch_id, LocalDateTime startDate, LocalDateTime endDate,int totalOrders,int cancelledOrders,int netOrders,Map<String,Integer> typeHistogram) {
        super(store_id, branch_name, branch_id, startDate, endDate);
        this.totalOrders = totalOrders;
        this.cancelledOrders = cancelledOrders;
        this.netOrders = netOrders;
        this.typeHistogram = typeHistogram;
    }
    @Override
    public void generate() {
        // Mock data
        this.totalOrders = 500;
        this.cancelledOrders = 30;
        this.netOrders = totalOrders - cancelledOrders;

        this.typeHistogram = new HashMap<>();
        typeHistogram.put("Roses", 200);
        typeHistogram.put("Tulips", 150);
        typeHistogram.put("Lilies", 120);
        typeHistogram.put("Orchids", 30);
    }
    public int getCancelledOrders() {
        return cancelledOrders;
    }
    public int getNetOrders() {
        return netOrders;
    }
    public int getTotalOrders() {
        return totalOrders;
    }
    public Map<String, Integer> getTypeHistogram() {
        return typeHistogram;
    }
    public String getSlot(){return slot;} public void setSlot(String s){this.slot=s;}
    public String getRequestId(){return requestId;} public void setRequestId(String r){this.requestId=r;}
    public void setCancelledOrders(int cancelledOrders) {
        this.cancelledOrders = cancelledOrders;
    }
    public void setNetOrders(int netOrders) {
        this.netOrders = netOrders;
    }
    public void setTotalOrders(int totalOrders) {
        this.totalOrders = totalOrders;
    }
    public void setTypeHistogram(Map<String, Integer> typeHistogram) {
        this.typeHistogram = typeHistogram;
    }

}
