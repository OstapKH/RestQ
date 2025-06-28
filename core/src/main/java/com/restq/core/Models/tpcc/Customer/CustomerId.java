package com.restq.core.Models.tpcc.Customer;

import java.io.Serializable;
import java.util.Objects;

public class CustomerId implements Serializable {

    private Integer warehouseId;
    private Integer districtId;
    private Integer customerId;

    public CustomerId() {
    }

    public CustomerId(Integer warehouseId, Integer districtId, Integer customerId) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
        this.customerId = customerId;
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Integer districtId) {
        this.districtId = districtId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerId that = (CustomerId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(districtId, that.districtId) &&
               Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, districtId, customerId);
    }
} 
