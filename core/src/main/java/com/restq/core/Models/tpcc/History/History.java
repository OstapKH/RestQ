package com.restq.core.Models.tpcc.History;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "HISTORY")
@IdClass(HistoryId.class)
public class History {

    // Composite key using customer, district, warehouse, and date to make records unique

    @Id
    @Column(name = "H_C_ID", nullable = false)
    private Integer customerId;

    @Id
    @Column(name = "H_C_D_ID", nullable = false)
    private Integer customerDistrictId;

    @Id
    @Column(name = "H_C_W_ID", nullable = false)
    private Integer customerWarehouseId;

    @Id
    @Column(name = "H_D_ID", nullable = false)
    private Integer districtId;

    @Id
    @Column(name = "H_W_ID", nullable = false)
    private Integer warehouseId;

    @Id
    @Column(name = "H_DATE", nullable = false)
    private LocalDateTime date;

    @Column(name = "H_AMOUNT", nullable = false, precision = 6, scale = 2)
    private BigDecimal amount;

    @Column(name = "H_DATA", length = 24, nullable = false)
    private String data;

    public History() {
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
} 
