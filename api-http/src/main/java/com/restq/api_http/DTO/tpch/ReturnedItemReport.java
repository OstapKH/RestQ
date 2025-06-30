package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class ReturnedItemReport {
    @JsonProperty("customer_key")
    private Integer customerKey;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("revenue")
    private BigDecimal revenue;
    
    @JsonProperty("account_balance")
    private BigDecimal accountBalance;
    
    @JsonProperty("nation")
    private String nation;
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("comment")
    private String comment;

    public ReturnedItemReport(Integer customerKey, String name, BigDecimal revenue, 
                            BigDecimal accountBalance, String nation, String address, 
                            String phone, String comment) {
        this.customerKey = customerKey;
        this.name = name;
        this.revenue = revenue;
        this.accountBalance = accountBalance;
        this.nation = nation;
        this.address = address;
        this.phone = phone;
        this.comment = comment;
    }
} 