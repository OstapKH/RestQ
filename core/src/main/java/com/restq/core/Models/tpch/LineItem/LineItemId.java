package com.restq.core.Models.tpch.LineItem;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;

@Embeddable
public class LineItemId implements Serializable {
    @Column(name = "L_ORDERKEY")
    private Integer orderKey;
    @Column(name = "L_LINENUMBER")
    private Integer lineNumber;

    public LineItemId() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LineItemId)) return false;
        LineItemId that = (LineItemId) o;
        return Objects.equals(orderKey, that.orderKey) && Objects.equals(lineNumber, that.lineNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderKey, lineNumber);
    }

    public Integer getOrderKey() {
        return orderKey;
    }

    public void setOrderKey(Integer orderKey) {
        this.orderKey = orderKey;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }
} 
