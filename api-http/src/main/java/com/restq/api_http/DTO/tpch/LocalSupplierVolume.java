package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class LocalSupplierVolume {
    @JsonProperty("n_name")
    private String nationName;

    @JsonProperty("revenue")
    private BigDecimal revenue;

    public LocalSupplierVolume(String nationName, BigDecimal revenue) {
        this.nationName = nationName;
        this.revenue = revenue;
    }

    public String getNationName() {
        return nationName;
    }

    public BigDecimal getRevenue() {
        return revenue;
    }
}
