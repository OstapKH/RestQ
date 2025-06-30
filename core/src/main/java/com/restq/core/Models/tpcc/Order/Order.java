package com.restq.core.Models.tpcc.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "OORDER")
@IdClass(OrderId.class)
public class Order {

    @Id
    @Column(name = "O_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "O_D_ID")
    private Integer districtId;

    @Id
    @Column(name = "O_ID")
    private Integer orderId;

    @Column(name = "O_C_ID", nullable = false)
    private Integer customerId;

    @Column(name = "O_ENTRY_D", nullable = false)
    private LocalDateTime entryDate;

    @Column(name = "O_CARRIER_ID")
    private Integer carrierId;

    @Column(name = "O_OL_CNT", nullable = false)
    private Integer orderLineCount;

    @Column(name = "O_ALL_LOCAL", nullable = false)
    private Integer allLocal;

    public Order() {
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

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public LocalDateTime getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDateTime entryDate) {
        this.entryDate = entryDate;
    }

    public Integer getCarrierId() {
        return carrierId;
    }

    public void setCarrierId(Integer carrierId) {
        this.carrierId = carrierId;
    }

    public Integer getOrderLineCount() {
        return orderLineCount;
    }

    public void setOrderLineCount(Integer orderLineCount) {
        this.orderLineCount = orderLineCount;
    }

    public Integer getAllLocal() {
        return allLocal;
    }

    public void setAllLocal(Integer allLocal) {
        this.allLocal = allLocal;
    }
} 
