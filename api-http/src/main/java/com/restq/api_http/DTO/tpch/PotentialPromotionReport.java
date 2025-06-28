package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PotentialPromotionReport {
    @JsonProperty("supplier_name")
    private String supplierName;
    
    @JsonProperty("address")
    private String address;

    public PotentialPromotionReport(String supplierName, String address) {
        this.supplierName = supplierName;
        this.address = address;
    }
} 