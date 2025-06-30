package com.restq.api_http.DTO.tpcc;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {
    @JsonProperty("warehouse_id")
    private Integer warehouseId;
    
    @JsonProperty("warehouse_name")
    private String warehouseName;
    
    @JsonProperty("warehouse_address")
    private String warehouseAddress;
    
    @JsonProperty("district_id")
    private Integer districtId;
    
    @JsonProperty("district_name")
    private String districtName;
    
    @JsonProperty("district_address")
    private String districtAddress;
    
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
    
    @JsonProperty("customer_credit")
    private String customerCredit;
    
    @JsonProperty("payment_amount")
    private BigDecimal paymentAmount;
    
    @JsonProperty("payment_date")
    private LocalDateTime paymentDate;

    public PaymentResponse(Integer warehouseId, String warehouseName, String warehouseAddress,
                           Integer districtId, String districtName, String districtAddress,
                           Integer customerId, String customerFirstName, String customerMiddleName,
                           String customerLastName, BigDecimal customerBalance, String customerCredit,
                           BigDecimal paymentAmount, LocalDateTime paymentDate) {
        this.warehouseId = warehouseId;
        this.warehouseName = warehouseName;
        this.warehouseAddress = warehouseAddress;
        this.districtId = districtId;
        this.districtName = districtName;
        this.districtAddress = districtAddress;
        this.customerId = customerId;
        this.customerFirstName = customerFirstName;
        this.customerMiddleName = customerMiddleName;
        this.customerLastName = customerLastName;
        this.customerBalance = customerBalance;
        this.customerCredit = customerCredit;
        this.paymentAmount = paymentAmount;
        this.paymentDate = paymentDate;
    }

    // Getters
    public Integer getWarehouseId() { return warehouseId; }
    public String getWarehouseName() { return warehouseName; }
    public String getWarehouseAddress() { return warehouseAddress; }
    public Integer getDistrictId() { return districtId; }
    public String getDistrictName() { return districtName; }
    public String getDistrictAddress() { return districtAddress; }
    public Integer getCustomerId() { return customerId; }
    public String getCustomerFirstName() { return customerFirstName; }
    public String getCustomerMiddleName() { return customerMiddleName; }
    public String getCustomerLastName() { return customerLastName; }
    public BigDecimal getCustomerBalance() { return customerBalance; }
    public String getCustomerCredit() { return customerCredit; }
    public BigDecimal getPaymentAmount() { return paymentAmount; }
    public LocalDateTime getPaymentDate() { return paymentDate; }
} 
