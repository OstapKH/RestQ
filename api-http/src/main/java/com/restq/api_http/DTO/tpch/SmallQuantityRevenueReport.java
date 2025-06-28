package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class SmallQuantityRevenueReport {
    @JsonProperty("avg_yearly")
    private BigDecimal avgYearly;

    public SmallQuantityRevenueReport(BigDecimal avgYearly) {
        this.avgYearly = avgYearly;
    }
} 