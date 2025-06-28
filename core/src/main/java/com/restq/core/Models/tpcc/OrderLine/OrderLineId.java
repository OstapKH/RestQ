package com.restq.core.Models.tpcc.OrderLine;

import java.io.Serializable;
import java.util.Objects;

public class OrderLineId implements Serializable {

    private Integer warehouseId;
    private Integer districtId;
    private Integer orderId;
    private Integer lineNumber;

    public OrderLineId() {
    }

    public OrderLineId(Integer warehouseId, Integer districtId, Integer orderId, Integer lineNumber) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
        this.orderId = orderId;
        this.lineNumber = lineNumber;
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

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderLineId that = (OrderLineId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(districtId, that.districtId) &&
               Objects.equals(orderId, that.orderId) &&
               Objects.equals(lineNumber, that.lineNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, districtId, orderId, lineNumber);
    }
} 
