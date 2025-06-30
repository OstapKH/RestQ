package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class DiscountedRevenueReport {
    @JsonProperty("revenue")
    private BigDecimal revenue;

    public DiscountedRevenueReport(BigDecimal revenue) {
        this.revenue = revenue;
    }
} 