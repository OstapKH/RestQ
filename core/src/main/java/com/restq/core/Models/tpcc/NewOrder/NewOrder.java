package com.restq.core.Models.tpcc.NewOrder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "NEW_ORDER")
@IdClass(NewOrderId.class)
public class NewOrder {

    @Id
    @Column(name = "NO_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "NO_D_ID")
    private Integer districtId;

    @Id
    @Column(name = "NO_O_ID")
    private Integer orderId;

    public NewOrder() {
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
} 
