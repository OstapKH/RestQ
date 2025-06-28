package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class ProductProfitReport {
    @JsonProperty("nation")
    private String nation;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("sum_profit")
    private BigDecimal sumProfit;

    public ProductProfitReport(String nation, Integer year, BigDecimal sumProfit) {
        this.nation = nation;
        this.year = year;
        this.sumProfit = sumProfit;
    }
} 