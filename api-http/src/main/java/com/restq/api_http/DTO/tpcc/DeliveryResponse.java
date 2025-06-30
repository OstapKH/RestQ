package com.restq.api_http.DTO.tpcc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class DeliveryResponse {
    @JsonProperty("warehouse_id")
    private Integer warehouseId;
    
    @JsonProperty("carrier_id")
    private Integer carrierId;
    
    @JsonProperty("delivery_date")
    private LocalDateTime deliveryDate;
    
    @JsonProperty("delivered_orders")
    private List<DeliveredOrder> deliveredOrders;
    
    @JsonProperty("skipped_districts")
    private List<Integer> skippedDistricts;

    public DeliveryResponse(Integer warehouseId, Integer carrierId, LocalDateTime deliveryDate,
                               List<DeliveredOrder> deliveredOrders, List<Integer> skippedDistricts) {
        this.warehouseId = warehouseId;
        this.carrierId = carrierId;
        this.deliveryDate = deliveryDate;
        this.deliveredOrders = deliveredOrders;
        this.skippedDistricts = skippedDistricts;
    }

    // Getters
    public Integer getWarehouseId() { return warehouseId; }
    public Integer getCarrierId() { return carrierId; }
    public LocalDateTime getDeliveryDate() { return deliveryDate; }
    public List<DeliveredOrder> getDeliveredOrders() { return deliveredOrders; }
    public List<Integer> getSkippedDistricts() { return skippedDistricts; }

    public static class DeliveredOrder {
        @JsonProperty("district_id")
        private Integer districtId;
        
        @JsonProperty("order_id")
        private Integer orderId;
        
        @JsonProperty("customer_id")
        private Integer customerId;
        
        @JsonProperty("total_amount")
        private BigDecimal totalAmount;

        public DeliveredOrder(Integer districtId, Integer orderId, Integer customerId, BigDecimal totalAmount) {
            this.districtId = districtId;
            this.orderId = orderId;
            this.customerId = customerId;
            this.totalAmount = totalAmount;
        }

        // Getters
        public Integer getDistrictId() { return districtId; }
        public Integer getOrderId() { return orderId; }
        public Integer getCustomerId() { return customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }
} 
