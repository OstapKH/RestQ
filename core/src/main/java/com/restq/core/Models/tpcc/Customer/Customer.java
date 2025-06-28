package com.restq.core.Models.tpcc.Customer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CUSTOMER")
@IdClass(CustomerId.class)
public class Customer {

    @Id
    @Column(name = "C_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "C_D_ID")
    private Integer districtId;

    @Id
    @Column(name = "C_ID")
    private Integer customerId;

    @Column(name = "C_FIRST", length = 16, nullable = false)
    private String firstName;

    @Column(name = "C_MIDDLE", length = 2, nullable = false)
    private String middleName;

    @Column(name = "C_LAST", length = 16, nullable = false)
    private String lastName;

    @Column(name = "C_STREET_1", length = 20, nullable = false)
    private String street1;

    @Column(name = "C_STREET_2", length = 20, nullable = false)
    private String street2;

    @Column(name = "C_CITY", length = 20, nullable = false)
    private String city;

    @Column(name = "C_STATE", length = 2, nullable = false)
    private String state;

    @Column(name = "C_ZIP", length = 9, nullable = false)
    private String zip;

    @Column(name = "C_PHONE", length = 16, nullable = false)
    private String phone;

    @Column(name = "C_SINCE", nullable = false)
    private LocalDateTime since;

    @Column(name = "C_CREDIT", length = 2, nullable = false)
    private String credit;

    @Column(name = "C_CREDIT_LIM", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "C_DISCOUNT", nullable = false, precision = 4, scale = 4)
    private BigDecimal discount;

    @Column(name = "C_BALANCE", nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    @Column(name = "C_YTD_PAYMENT", nullable = false, precision = 12, scale = 2)
    private BigDecimal yearToDatePayment;

    @Column(name = "C_PAYMENT_CNT", nullable = false)
    private Integer paymentCount;

    @Column(name = "C_DELIVERY_CNT", nullable = false)
    private Integer deliveryCount;

    @Column(name = "C_DATA", length = 500, nullable = false)
    private String data;

    public Customer() {
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getDistrictId() {
        return districtId;
    }

    public void setDistrictId(Integer districtId) {
        this.districtId = districtId;
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Integer customerId) {
        this.customerId = customerId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getStreet1() {
        return street1;
    }

    public void setStreet1(String street1) {
        this.street1 = street1;
    }

    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getSince() {
        return since;
    }

    public void setSince(LocalDateTime since) {
        this.since = since;
    }

    public String getCredit() {
        return credit;
    }

    public void setCredit(String credit) {
        this.credit = credit;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getYearToDatePayment() {
        return yearToDatePayment;
    }

    public void setYearToDatePayment(BigDecimal yearToDatePayment) {
        this.yearToDatePayment = yearToDatePayment;
    }

    public Integer getPaymentCount() {
        return paymentCount;
    }

    public void setPaymentCount(Integer paymentCount) {
        this.paymentCount = paymentCount;
    }

    public Integer getDeliveryCount() {
        return deliveryCount;
    }

    public void setDeliveryCount(Integer deliveryCount) {
        this.deliveryCount = deliveryCount;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
} 
