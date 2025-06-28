package com.restq.api_http.Services.tpcc;

import com.restq.core.Models.tpcc.Customer.Customer;
import com.restq.core.Models.tpcc.District.District;
import com.restq.core.Models.tpcc.Order.Order;
import com.restq.core.Models.tpcc.OrderLine.OrderLine;
import com.restq.core.Models.tpcc.Warehouse.Warehouse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

// Request classes
public class ServiceModels {

    public static class NewOrderRequest {
        private Integer warehouseId;
        private Integer districtId;
        private Integer customerId;
        private List<OrderLineRequest> orderLines;

        // Constructors
        public NewOrderRequest() {}
        
        public NewOrderRequest(Integer warehouseId, Integer districtId, Integer customerId, List<OrderLineRequest> orderLines) {
            this.warehouseId = warehouseId;
            this.districtId = districtId;
            this.customerId = customerId;
            this.orderLines = orderLines;
        }

        // Getters and setters
        public Integer getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Integer warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDistrictId() { return districtId; }
        public void setDistrictId(Integer districtId) { this.districtId = districtId; }
        public Integer getCustomerId() { return customerId; }
        public void setCustomerId(Integer customerId) { this.customerId = customerId; }
        public List<OrderLineRequest> getOrderLines() { return orderLines; }
        public void setOrderLines(List<OrderLineRequest> orderLines) { this.orderLines = orderLines; }
    }

    public static class OrderLineRequest {
        private Integer itemId;
        private Integer supplierWarehouseId;
        private Integer quantity;

        // Constructors
        public OrderLineRequest() {}
        
        public OrderLineRequest(Integer itemId, Integer supplierWarehouseId, Integer quantity) {
            this.itemId = itemId;
            this.supplierWarehouseId = supplierWarehouseId;
            this.quantity = quantity;
        }

        // Getters and setters
        public Integer getItemId() { return itemId; }
        public void setItemId(Integer itemId) { this.itemId = itemId; }
        public Integer getSupplierWarehouseId() { return supplierWarehouseId; }
        public void setSupplierWarehouseId(Integer supplierWarehouseId) { this.supplierWarehouseId = supplierWarehouseId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }

    public static class PaymentRequest {
        private Integer warehouseId;
        private Integer districtId;
        private Integer customerId;
        private String customerLastName;
        private BigDecimal paymentAmount;

        // Constructors
        public PaymentRequest() {}

        public PaymentRequest(Integer warehouseId, Integer districtId, Integer customerId, 
                            String customerLastName, BigDecimal paymentAmount) {
            this.warehouseId = warehouseId;
            this.districtId = districtId;
            this.customerId = customerId;
            this.customerLastName = customerLastName;
            this.paymentAmount = paymentAmount;
        }

        // Getters and setters
        public Integer getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Integer warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDistrictId() { return districtId; }
        public void setDistrictId(Integer districtId) { this.districtId = districtId; }
        public Integer getCustomerId() { return customerId; }
        public void setCustomerId(Integer customerId) { this.customerId = customerId; }
        public String getCustomerLastName() { return customerLastName; }
        public void setCustomerLastName(String customerLastName) { this.customerLastName = customerLastName; }
        public BigDecimal getPaymentAmount() { return paymentAmount; }
        public void setPaymentAmount(BigDecimal paymentAmount) { this.paymentAmount = paymentAmount; }
    }

    public static class OrderStatusRequest {
        private Integer warehouseId;
        private Integer districtId;
        private Integer customerId;
        private String customerLastName;

        // Constructors
        public OrderStatusRequest() {}

        // Getters and setters
        public Integer getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Integer warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDistrictId() { return districtId; }
        public void setDistrictId(Integer districtId) { this.districtId = districtId; }
        public Integer getCustomerId() { return customerId; }
        public void setCustomerId(Integer customerId) { this.customerId = customerId; }
        public String getCustomerLastName() { return customerLastName; }
        public void setCustomerLastName(String customerLastName) { this.customerLastName = customerLastName; }
    }

    public static class DeliveryRequest {
        private Integer warehouseId;
        private Integer carrierId;

        // Constructors
        public DeliveryRequest() {}

        // Getters and setters
        public Integer getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Integer warehouseId) { this.warehouseId = warehouseId; }
        public Integer getCarrierId() { return carrierId; }
        public void setCarrierId(Integer carrierId) { this.carrierId = carrierId; }
    }

    public static class StockLevelRequest {
        private Integer warehouseId;
        private Integer districtId;
        private Integer threshold;

        // Constructors
        public StockLevelRequest() {}

        // Getters and setters
        public Integer getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Integer warehouseId) { this.warehouseId = warehouseId; }
        public Integer getDistrictId() { return districtId; }
        public void setDistrictId(Integer districtId) { this.districtId = districtId; }
        public Integer getThreshold() { return threshold; }
        public void setThreshold(Integer threshold) { this.threshold = threshold; }
    }

    // Result classes
    public static class NewOrderResult {
        private final int warehouseId;
        private final int districtId;
        private final int customerId;
        private final int orderId;
        private final LocalDateTime orderDate;
        private final BigDecimal totalAmount;
        private final String customerLastName;
        private final String customerCredit;
        private final BigDecimal customerDiscount;
        private final BigDecimal warehouseTax;
        private final BigDecimal districtTax;
        private final List<OrderLineResult> orderLines;

        public NewOrderResult(int warehouseId, int districtId, int customerId, int orderId, 
                             LocalDateTime orderDate, BigDecimal totalAmount, String customerLastName, 
                             String customerCredit, BigDecimal customerDiscount, BigDecimal warehouseTax, 
                             BigDecimal districtTax, List<OrderLineResult> orderLines) {
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
        public int getWarehouseId() { return warehouseId; }
        public int getDistrictId() { return districtId; }
        public int getCustomerId() { return customerId; }
        public int getOrderId() { return orderId; }
        public LocalDateTime getOrderDate() { return orderDate; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public String getCustomerLastName() { return customerLastName; }
        public String getCustomerCredit() { return customerCredit; }
        public BigDecimal getCustomerDiscount() { return customerDiscount; }
        public BigDecimal getWarehouseTax() { return warehouseTax; }
        public BigDecimal getDistrictTax() { return districtTax; }
        public List<OrderLineResult> getOrderLines() { return orderLines; }
    }

    public static class OrderLineResult {
        private final Integer itemId;
        private final String itemName;
        private final Integer supplierWarehouseId;
        private final BigDecimal quantity;
        private final BigDecimal itemPrice;
        private final BigDecimal lineAmount;
        private final Integer stockQuantity;

        public OrderLineResult(Integer itemId, String itemName, Integer supplierWarehouseId, 
                              BigDecimal quantity, BigDecimal itemPrice, BigDecimal lineAmount, Integer stockQuantity) {
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

    public static class PaymentResult {
        private final Warehouse warehouse;
        private final District district;
        private final Customer customer;
        private final BigDecimal paymentAmount;
        private final LocalDateTime paymentDate;

        public PaymentResult(Warehouse warehouse, District district, Customer customer, 
                           BigDecimal paymentAmount, LocalDateTime paymentDate) {
            this.warehouse = warehouse;
            this.district = district;
            this.customer = customer;
            this.paymentAmount = paymentAmount;
            this.paymentDate = paymentDate;
        }

        // Getters
        public Warehouse getWarehouse() { return warehouse; }
        public District getDistrict() { return district; }
        public Customer getCustomer() { return customer; }
        public BigDecimal getPaymentAmount() { return paymentAmount; }
        public LocalDateTime getPaymentDate() { return paymentDate; }
    }

    public static class OrderStatusResult {
        private final Customer customer;
        private final Order order;
        private final List<OrderLine> orderLines;

        public OrderStatusResult(Customer customer, Order order, List<OrderLine> orderLines) {
            this.customer = customer;
            this.order = order;
            this.orderLines = orderLines;
        }

        // Getters
        public Customer getCustomer() { return customer; }
        public Order getOrder() { return order; }
        public List<OrderLine> getOrderLines() { return orderLines; }
    }

    public static class DeliveryResult {
        private final int warehouseId;
        private final int carrierId;
        private final LocalDateTime deliveryDate;
        private final List<DeliveredOrderInfo> deliveredOrders;
        private final List<Integer> skippedDistricts;

        public DeliveryResult(int warehouseId, int carrierId, LocalDateTime deliveryDate, 
                             List<DeliveredOrderInfo> deliveredOrders, List<Integer> skippedDistricts) {
            this.warehouseId = warehouseId;
            this.carrierId = carrierId;
            this.deliveryDate = deliveryDate;
            this.deliveredOrders = deliveredOrders;
            this.skippedDistricts = skippedDistricts;
        }

        // Getters
        public int getWarehouseId() { return warehouseId; }
        public int getCarrierId() { return carrierId; }
        public LocalDateTime getDeliveryDate() { return deliveryDate; }
        public List<DeliveredOrderInfo> getDeliveredOrders() { return deliveredOrders; }
        public List<Integer> getSkippedDistricts() { return skippedDistricts; }
    }

    public static class DeliveredOrderInfo {
        private final int districtId;
        private final int orderId;
        private final int customerId;
        private final BigDecimal totalAmount;

        public DeliveredOrderInfo(int districtId, int orderId, int customerId, BigDecimal totalAmount) {
            this.districtId = districtId;
            this.orderId = orderId;
            this.customerId = customerId;
            this.totalAmount = totalAmount;
        }

        // Getters
        public int getDistrictId() { return districtId; }
        public int getOrderId() { return orderId; }
        public int getCustomerId() { return customerId; }
        public BigDecimal getTotalAmount() { return totalAmount; }
    }

    public static class StockLevelResult {
        private final int warehouseId;
        private final int districtId;
        private final int threshold;
        private final Long lowStockCount;

        public StockLevelResult(int warehouseId, int districtId, int threshold, Long lowStockCount) {
            this.warehouseId = warehouseId;
            this.districtId = districtId;
            this.threshold = threshold;
            this.lowStockCount = lowStockCount;
        }

        // Getters
        public int getWarehouseId() { return warehouseId; }
        public int getDistrictId() { return districtId; }
        public int getThreshold() { return threshold; }
        public Long getLowStockCount() { return lowStockCount; }
    }
} 
