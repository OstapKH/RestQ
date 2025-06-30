package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public class OrderRevenueInfo {

    @JsonProperty("orderKey")
    private Integer orderKey;

    @JsonProperty("revenue")
    private BigDecimal revenue;

    @JsonProperty("orderDate")
    private LocalDate orderDate;

    @JsonProperty("shipPriority")
    private Integer shipPriority;

    public OrderRevenueInfo(Integer orderKey, BigDecimal revenue, LocalDate orderDate, Integer shipPriority) {
        this.orderKey = orderKey;
        this.revenue = revenue;
        this.orderDate = orderDate;
        this.shipPriority = shipPriority;
    }

    public Integer getOrderKey() {
        return orderKey;
    }

    public void setOrderKey(Integer orderKey) {
        this.orderKey = orderKey;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }

    public void setRevenue(BigDecimal revenue) {
        this.revenue = revenue;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public Integer getShipPriority() {
        return shipPriority;
    }

    public void setShipPriority(Integer shipPriority) {
        this.shipPriority = shipPriority;
    }
}