package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.sql.Date;

public class LargeVolumeCustomerReport {
    @JsonProperty("customer_name")
    private String customerName;
    
    @JsonProperty("customer_key")
    private Long customerKey;
    
    @JsonProperty("order_key")
    private Long orderKey;
    
    @JsonProperty("order_date")
    private Date orderDate;
    
    @JsonProperty("total_price")
    private BigDecimal totalPrice;
    
    @JsonProperty("quantity")
    private BigDecimal quantity;

    public LargeVolumeCustomerReport(String customerName, Long customerKey, 
                                   Long orderKey, Date orderDate, 
                                   BigDecimal totalPrice, BigDecimal quantity) {
        this.customerName = customerName;
        this.customerKey = customerKey;
        this.orderKey = orderKey;
        this.orderDate = orderDate;
        this.totalPrice = totalPrice;
        this.quantity = quantity;
    }
} 