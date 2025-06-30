package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;

public class SupplierWaitingReport {
    @JsonProperty("supplier_name")
    private String supplierName;
    
    @JsonProperty("wait_count")
    private Long waitCount;

    public SupplierWaitingReport(String supplierName, Long waitCount) {
        this.supplierName = supplierName;
        this.waitCount = waitCount;
    }
} 