package com.restq.core.Models.tpch.Part;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "PART")
public class Part {

    @Id
    @Column(name = "P_PARTKEY")
    private Integer partKey;

    @Column(name = "P_NAME", length = 55, nullable = false, columnDefinition = "bpchar")
    private String name;

    @Column(name = "P_MFGR", length = 25, nullable = false, columnDefinition = "bpchar")
    private String manufacturer;

    @Column(name = "P_BRAND", length = 10, nullable = false, columnDefinition = "bpchar")
    private String brand;

    @Column(name = "P_TYPE", length = 25, nullable = false, columnDefinition = "bpchar")
    private String type;

    @Column(name = "P_SIZE", nullable = false)
    private Integer size;

    @Column(name = "P_CONTAINER", length = 10, nullable = false, columnDefinition = "bpchar")
    private String container;

    @Column(name = "P_RETAILPRICE", nullable = false)
    private BigDecimal retailPrice;

    @Column(name = "P_COMMENT", length = 23, columnDefinition = "bpchar")
    private String comment;

    public Part() {
    }

    public Integer getPartKey() {
        return partKey;
    }

    public void setPartKey(Integer partKey) {
        this.partKey = partKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public BigDecimal getRetailPrice() {
        return retailPrice;
    }

    public void setRetailPrice(BigDecimal retailPrice) {
        this.retailPrice = retailPrice;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }
} 
