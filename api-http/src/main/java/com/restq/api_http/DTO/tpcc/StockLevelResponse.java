package com.restq.api_http.DTO.tpcc;

import com.fasterxml.jackson.annotation.JsonProperty;

public class StockLevelResponse {
    @JsonProperty("warehouse_id")
    private Integer warehouseId;
    
    @JsonProperty("district_id")
    private Integer districtId;
    
    @JsonProperty("threshold")
    private Integer threshold;
    
    @JsonProperty("low_stock_count")
    private Long lowStockCount;

    public StockLevelResponse(Integer warehouseId, Integer districtId, Integer threshold, Long lowStockCount) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
        this.threshold = threshold;
        this.lowStockCount = lowStockCount;
    }

    // Getters
    public Integer getWarehouseId() { return warehouseId; }
    public Integer getDistrictId() { return districtId; }
    public Integer getThreshold() { return threshold; }
    public Long getLowStockCount() { return lowStockCount; }
} 
