package com.restq.core.Models.tpcc.District;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "DISTRICT")
@IdClass(DistrictId.class)
public class District {

    @Id
    @Column(name = "D_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "D_ID")
    private Integer districtId;

    @Column(name = "D_NAME", length = 10, nullable = false)
    private String name;

    @Column(name = "D_STREET_1", length = 20, nullable = false)
    private String street1;

    @Column(name = "D_STREET_2", length = 20, nullable = false)
    private String street2;

    @Column(name = "D_CITY", length = 20, nullable = false)
    private String city;

    @Column(name = "D_STATE", length = 2, nullable = false)
    private String state;

    @Column(name = "D_ZIP", length = 9, nullable = false)
    private String zip;

    @Column(name = "D_TAX", nullable = false, precision = 4, scale = 4)
    private BigDecimal tax;

    @Column(name = "D_YTD", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearToDateBalance;

    @Column(name = "D_NEXT_O_ID", nullable = false)
    private Integer nextOrderId;

    public District() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getYearToDateBalance() {
        return yearToDateBalance;
    }

    public void setYearToDateBalance(BigDecimal yearToDateBalance) {
        this.yearToDateBalance = yearToDateBalance;
    }

    public Integer getNextOrderId() {
        return nextOrderId;
    }

    public void setNextOrderId(Integer nextOrderId) {
        this.nextOrderId = nextOrderId;
    }
} 
