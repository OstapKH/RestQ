package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class GlobalSalesOpportunityReport {
    @JsonProperty("country_code")
    private String countryCode;
    
    @JsonProperty("customer_count")
    private Long customerCount;
    
    @JsonProperty("total_balance")
    private BigDecimal totalBalance;

    public GlobalSalesOpportunityReport(String countryCode, Long customerCount, 
                                      BigDecimal totalBalance) {
        this.countryCode = countryCode;
        this.customerCount = customerCount;
        this.totalBalance = totalBalance;
    }
} 