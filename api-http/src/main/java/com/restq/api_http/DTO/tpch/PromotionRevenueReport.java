package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class PromotionRevenueReport {
    @JsonProperty("promo_revenue")
    private BigDecimal promoRevenue;

    public PromotionRevenueReport(BigDecimal promoRevenue) {
        this.promoRevenue = promoRevenue;
    }
} 