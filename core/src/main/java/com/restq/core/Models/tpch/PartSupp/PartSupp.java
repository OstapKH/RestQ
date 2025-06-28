package com.restq.core.Models.tpch.PartSupp;

import com.restq.core.Models.tpch.Supplier.Supplier;
import com.restq.core.Models.tpch.Part.Part;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;

import java.math.BigDecimal;

@Entity
@Table(name = "PARTSUPP")
public class PartSupp {

    @EmbeddedId
    private PartSuppId id;

    @ManyToOne
    @MapsId("partKey")
    @JoinColumn(name = "PS_PARTKEY", referencedColumnName = "P_PARTKEY")
    private Part part;

    @ManyToOne
    @MapsId("supplierKey")
    @JoinColumn(name = "PS_SUPPKEY", referencedColumnName = "S_SUPPKEY")
    private Supplier supplier;

    @Column(name = "PS_AVAILQTY", nullable = false)
    private Integer availableQuantity;

    @Column(name = "PS_SUPPLYCOST", nullable = false)
    private BigDecimal supplyCost;

    @Column(name = "PS_COMMENT", length = 199)
    private String comment;

    public PartSupp() {
    }

    public PartSuppId getId() {
        return id;
    }

    public void setId(PartSuppId id) {
        this.id = id;
    }

    public Part getPart() {
        return part;
    }

    public void setPart(Part part) {
        this.part = part;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }

    public BigDecimal getSupplyCost() {
        return supplyCost;
    }

    public void setSupplyCost(BigDecimal supplyCost) {
        this.supplyCost = supplyCost;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Integer getAvailableQuantity() {
        return availableQuantity;
    }

    public void setAvailableQuantity(Integer availableQuantity) {
        this.availableQuantity = availableQuantity;
    }
} 
