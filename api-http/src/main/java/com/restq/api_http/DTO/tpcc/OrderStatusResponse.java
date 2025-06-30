package com.restq.api_http.DTO.tpcc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderStatusResponse {
    @JsonProperty("customer_id")
    private Integer customerId;
    
    @JsonProperty("customer_first_name")
    private String customerFirstName;
    
    @JsonProperty("customer_middle_name")
    private String customerMiddleName;
    
    @JsonProperty("customer_last_name")
    private String customerLastName;
    
    @JsonProperty("customer_balance")
    private BigDecimal customerBalance;
    
    @JsonProperty("order_id")
    private Integer orderId;
    
    @JsonProperty("order_entry_date")
    private LocalDateTime orderEntryDate;
    
    @JsonProperty("carrier_id")
    private Integer carrierId;
    
    @JsonProperty("order_lines")
    private List<OrderLineInfo> orderLines;

    public OrderStatusResponse(Integer customerId, String customerFirstName, String customerMiddleName,
                               String customerLastName, BigDecimal customerBalance, Integer orderId,
                               LocalDateTime orderEntryDate, Integer carrierId, List<OrderLineInfo> orderLines) {
        this.customerId = customerId;
        this.customerFirstName = customerFirstName;
        this.customerMiddleName = customerMiddleName;
        this.customerLastName = customerLastName;
        this.customerBalance = customerBalance;
        this.orderId = orderId;
        this.orderEntryDate = orderEntryDate;
        this.carrierId = carrierId;
        this.orderLines = orderLines;
    }

    // Getters
    public Integer getCustomerId() { return customerId; }
    public String getCustomerFirstName() { return customerFirstName; }
    public String getCustomerMiddleName() { return customerMiddleName; }
    public String getCustomerLastName() { return customerLastName; }
    public BigDecimal getCustomerBalance() { return customerBalance; }
    public Integer getOrderId() { return orderId; }
    public LocalDateTime getOrderEntryDate() { return orderEntryDate; }
    public Integer getCarrierId() { return carrierId; }
    public List<OrderLineInfo> getOrderLines() { return orderLines; }

    public static class OrderLineInfo {
        @JsonProperty("line_number")
        private Integer lineNumber;
        
        @JsonProperty("item_id")
        private Integer itemId;
        
        @JsonProperty("supply_warehouse_id")
        private Integer supplyWarehouseId;
        
        @JsonProperty("quantity")
        private BigDecimal quantity;
        
        @JsonProperty("amount")
        private BigDecimal amount;
        
        @JsonProperty("delivery_date")
        private LocalDateTime deliveryDate;

        public OrderLineInfo(Integer lineNumber, Integer itemId, Integer supplyWarehouseId,
                           BigDecimal quantity, BigDecimal amount, LocalDateTime deliveryDate) {
            this.lineNumber = lineNumber;
            this.itemId = itemId;
            this.supplyWarehouseId = supplyWarehouseId;
            this.quantity = quantity;
            this.amount = amount;
            this.deliveryDate = deliveryDate;
        }

        // Getters
        public Integer getLineNumber() { return lineNumber; }
        public Integer getItemId() { return itemId; }
        public Integer getSupplyWarehouseId() { return supplyWarehouseId; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getAmount() { return amount; }
        public LocalDateTime getDeliveryDate() { return deliveryDate; }
    }
} 
