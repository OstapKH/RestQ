package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class CustomerDistributionReport {
    @JsonProperty("order_count")
    private Long orderCount;
    
    @JsonProperty("customer_count")
    private Long customerCount;

    public CustomerDistributionReport(Long orderCount, Long customerCount) {
        this.orderCount = orderCount;
        this.customerCount = customerCount;
    }
} 