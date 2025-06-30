package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ShippingModeReport {
    @JsonProperty("ship_mode")
    private String shipMode;
    
    @JsonProperty("high_line_count")
    private Long highLineCount;
    
    @JsonProperty("low_line_count")
    private Long lowLineCount;

    public ShippingModeReport(String shipMode, Long highLineCount, Long lowLineCount) {
        this.shipMode = shipMode;
        this.highLineCount = highLineCount;
        this.lowLineCount = lowLineCount;
    }
} 