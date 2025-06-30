package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class MarketShareReport {
    @JsonProperty("year")
    private Integer year;

    @JsonProperty("market_share")
    private BigDecimal marketShare;

    public MarketShareReport(Integer year, BigDecimal marketShare) {
        this.year = year;
        this.marketShare = marketShare;
    }
} 