package com.restq.core.Models.tpcc.Warehouse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "WAREHOUSE")
public class Warehouse {

    @Id
    @Column(name = "W_ID")
    private Integer warehouseId;

    @Column(name = "W_NAME", length = 10, nullable = false)
    private String name;

    @Column(name = "W_STREET_1", length = 20, nullable = false)
    private String street1;

    @Column(name = "W_STREET_2", length = 20, nullable = false)
    private String street2;

    @Column(name = "W_CITY", length = 20, nullable = false)
    private String city;

    @Column(name = "W_STATE", length = 2, nullable = false)
    private String state;

    @Column(name = "W_ZIP", length = 9, nullable = false)
    private String zip;

    @Column(name = "W_TAX", nullable = false, precision = 4, scale = 4)
    private BigDecimal tax;

    @Column(name = "W_YTD", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearToDateBalance;

    public Warehouse() {
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
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
} 
