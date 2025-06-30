package com.restq.core.Models.tpch.PartSupp;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import jakarta.persistence.Column;

@Embeddable
public class PartSuppId implements Serializable {
    @Column(name = "PS_PARTKEY")
    private Integer partKey;
    
    @Column(name = "PS_SUPPKEY")
    private Integer supplierKey;

    public PartSuppId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PartSuppId)) return false;
        PartSuppId that = (PartSuppId) o;
        return Objects.equals(partKey, that.partKey) && Objects.equals(supplierKey, that.supplierKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(partKey, supplierKey);
    }

    public Integer getPartKey() {
        return partKey;
    }

    public void setPartKey(Integer partKey) {
        this.partKey = partKey;
    }

    public Integer getSupplierKey() {
        return supplierKey;
    }

    public void setSupplierKey(Integer supplierKey) {
        this.supplierKey = supplierKey;
    }
} 
