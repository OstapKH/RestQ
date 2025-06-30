package com.restq.core.Models.tpcc.OrderLine;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ORDER_LINE")
@IdClass(OrderLineId.class)
public class OrderLine {

    @Id
    @Column(name = "OL_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "OL_D_ID")
    private Integer districtId;

    @Id
    @Column(name = "OL_O_ID")
    private Integer orderId;

    @Id
    @Column(name = "OL_NUMBER")
    private Integer lineNumber;

    @Column(name = "OL_I_ID", nullable = false)
    private Integer itemId;

    @Column(name = "OL_SUPPLY_W_ID", nullable = false)
    private Integer supplyWarehouseId;

    @Column(name = "OL_DELIVERY_D")
    private LocalDateTime deliveryDate;

    @Column(name = "OL_QUANTITY", nullable = false, precision = 6, scale = 2)
    private BigDecimal quantity;

    @Column(name = "OL_AMOUNT", nullable = false, precision = 6, scale = 2)
    private BigDecimal amount;

    @Column(name = "OL_DIST_INFO", length = 24, nullable = false)
    private String distInfo;

    public OrderLine() {
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

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getSupplyWarehouseId() {
        return supplyWarehouseId;
    }

    public void setSupplyWarehouseId(Integer supplyWarehouseId) {
        this.supplyWarehouseId = supplyWarehouseId;
    }

    public LocalDateTime getDeliveryDate() {
        return deliveryDate;
    }

    public void setDeliveryDate(LocalDateTime deliveryDate) {
        this.deliveryDate = deliveryDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDistInfo() {
        return distInfo;
    }

    public void setDistInfo(String distInfo) {
        this.distInfo = distInfo;
    }
} 
