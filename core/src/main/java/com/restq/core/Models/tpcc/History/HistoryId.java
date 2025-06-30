package com.restq.core.Models.tpcc.History;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

public class HistoryId implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer customerId;
    private Integer customerDistrictId;
    private Integer customerWarehouseId;
    private Integer districtId;
    private Integer warehouseId;
    private LocalDateTime date;

    public HistoryId() {
    }

    public HistoryId(Integer customerId, Integer customerDistrictId, Integer customerWarehouseId,
                     Integer districtId, Integer warehouseId, LocalDateTime date) {
        this.customerId = customerId;
        this.customerDistrictId = customerDistrictId;
        this.customerWarehouseId = customerWarehouseId;
        this.districtId = districtId;
        this.warehouseId = warehouseId;
        this.date = date;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public Integer getCustomerDistrictId() {
        return customerDistrictId;
    }

    public void setCustomerDistrictId(Integer customerDistrictId) {
        this.customerDistrictId = customerDistrictId;
    }

    public Integer getCustomerWarehouseId() {
        return customerWarehouseId;
    }

    public void setCustomerWarehouseId(Integer customerWarehouseId) {
        this.customerWarehouseId = customerWarehouseId;
    }

    public Integer getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Integer districtId) {
        this.districtId = districtId;
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public void setDate(LocalDateTime date) {
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HistoryId historyId = (HistoryId) o;
        return Objects.equals(customerId, historyId.customerId) &&
               Objects.equals(customerDistrictId, historyId.customerDistrictId) &&
               Objects.equals(customerWarehouseId, historyId.customerWarehouseId) &&
               Objects.equals(districtId, historyId.districtId) &&
               Objects.equals(warehouseId, historyId.warehouseId) &&
               Objects.equals(date, historyId.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, customerDistrictId, customerWarehouseId, 
                          districtId, warehouseId, date);
    }
} 
