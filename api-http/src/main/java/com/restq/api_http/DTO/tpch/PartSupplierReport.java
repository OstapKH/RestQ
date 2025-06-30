package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PartSupplierReport {
    @JsonProperty("brand")
    private String brand;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("size")
    private Integer size;
    
    @JsonProperty("supplier_count")
    private Long supplierCount;

    public PartSupplierReport(String brand, String type, Integer size, Long supplierCount) {
        this.brand = brand;
        this.type = type;
        this.size = size;
        this.supplierCount = supplierCount;
    }
} 