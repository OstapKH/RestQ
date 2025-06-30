package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class ImportantStockReport {
    @JsonProperty("part_key")
    private Integer partKey;
    
    @JsonProperty("value")
    private BigDecimal value;

    public ImportantStockReport(Integer partKey, BigDecimal value) {
        this.partKey = partKey;
        this.value = value;
    }

    // Add default constructor for Jackson deserialization
    public ImportantStockReport() {
    }

    public Integer getPartKey() {
        return partKey;
    }

    public void setPartKey(Integer partKey) {
        this.partKey = partKey;
    }

    public BigDecimal getValue() {
        return value;
    }

    public void setValue(BigDecimal value) {
        this.value = value;
    }
}