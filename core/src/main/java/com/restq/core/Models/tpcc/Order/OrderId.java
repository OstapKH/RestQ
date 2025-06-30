package com.restq.core.Models.tpcc.Order;

import java.io.Serializable;
import java.util.Objects;

public class OrderId implements Serializable {

    private Integer warehouseId;
    private Integer districtId;
    private Integer orderId;

    public OrderId() {
    }

    public OrderId(Integer warehouseId, Integer districtId, Integer orderId) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
        this.orderId = orderId;
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

    public Integer getOrderId() {
        return orderId;
    }

    public void setOrderId(Integer orderId) {
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderId that = (OrderId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(districtId, that.districtId) &&
               Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, districtId, orderId);
    }
} 
