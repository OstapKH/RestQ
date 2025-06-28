package com.restq.api_http.DTO.tpcc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class NewOrderResponse {
    @JsonProperty("warehouse_id")
    private Integer warehouseId;
    
    @JsonProperty("district_id")
    private Integer districtId;
    
    @JsonProperty("customer_id")
    private Integer customerId;
    
    @JsonProperty("order_id")
    private Integer orderId;
    
    @JsonProperty("order_date")
    private LocalDateTime orderDate;
    
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    
    @JsonProperty("customer_last_name")
    private String customerLastName;
    
    @JsonProperty("customer_credit")
    private String customerCredit;
    
    @JsonProperty("customer_discount")
    private BigDecimal customerDiscount;
    
    @JsonProperty("warehouse_tax")
    private BigDecimal warehouseTax;
    
    @JsonProperty("district_tax")
    private BigDecimal districtTax;
    
    @JsonProperty("order_lines")
    private List<OrderLineInfo> orderLines;
    
    public NewOrderResponse(Integer warehouseId, Integer districtId, Integer customerId,
                            Integer orderId, LocalDateTime orderDate, BigDecimal totalAmount,
                            String customerLastName, String customerCredit,
                            BigDecimal customerDiscount, BigDecimal warehouseTax,
                            BigDecimal districtTax, List<OrderLineInfo> orderLines) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
        this.customerId = customerId;
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
        this.customerLastName = customerLastName;
        this.customerCredit = customerCredit;
        this.customerDiscount = customerDiscount;
        this.warehouseTax = warehouseTax;
        this.districtTax = districtTax;
        this.orderLines = orderLines;
    }

    // Getters
    public Integer getWarehouseId() { return warehouseId; }
    public Integer getDistrictId() { return districtId; }
    public Integer getCustomerId() { return customerId; }
    public Integer getOrderId() { return orderId; }
    public LocalDateTime getOrderDate() { return orderDate; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCustomerLastName() { return customerLastName; }
    public String getCustomerCredit() { return customerCredit; }
    public BigDecimal getCustomerDiscount() { return customerDiscount; }
    public BigDecimal getWarehouseTax() { return warehouseTax; }
    public BigDecimal getDistrictTax() { return districtTax; }
    public List<OrderLineInfo> getOrderLines() { return orderLines; }
    
    public static class OrderLineInfo {
        @JsonProperty("item_id")
        private Integer itemId;
        
        @JsonProperty("item_name")
        private String itemName;
        
        @JsonProperty("supplier_warehouse_id")
        private Integer supplierWarehouseId;
        
        @JsonProperty("quantity")
        private BigDecimal quantity;
        
        @JsonProperty("item_price")
        private BigDecimal itemPrice;
        
        @JsonProperty("line_amount")
        private BigDecimal lineAmount;
        
        @JsonProperty("stock_quantity")
        private Integer stockQuantity;
        
        public OrderLineInfo(Integer itemId, String itemName, Integer supplierWarehouseId,
                           BigDecimal quantity, BigDecimal itemPrice, BigDecimal lineAmount,
                           Integer stockQuantity) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.supplierWarehouseId = supplierWarehouseId;
            this.quantity = quantity;
            this.itemPrice = itemPrice;
            this.lineAmount = lineAmount;
            this.stockQuantity = stockQuantity;
        }

        // Getters
        public Integer getItemId() { return itemId; }
        public String getItemName() { return itemName; }
        public Integer getSupplierWarehouseId() { return supplierWarehouseId; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getItemPrice() { return itemPrice; }
        public BigDecimal getLineAmount() { return lineAmount; }
        public Integer getStockQuantity() { return stockQuantity; }
    }
} 
