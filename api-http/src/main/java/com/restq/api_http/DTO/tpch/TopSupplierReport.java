package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class TopSupplierReport {
    @JsonProperty("supplier_key")
    private Long supplierKey;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("address")
    private String address;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("total_revenue")
    private BigDecimal totalRevenue;

    public TopSupplierReport(Long supplierKey, String name, String address, 
                           String phone, BigDecimal totalRevenue) {
        this.supplierKey = supplierKey;
        this.name = name;
        this.address = address;
        this.phone = phone;
        this.totalRevenue = totalRevenue;
    }
} 