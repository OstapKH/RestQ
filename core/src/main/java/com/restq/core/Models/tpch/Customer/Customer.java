package com.restq.core.Models.tpch.Customer;

import com.restq.core.Models.tpch.Nation.Nation;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

import java.math.BigDecimal;

@Entity
@Table(name = "CUSTOMER")
public class Customer {

    @Id
    @Column(name = "C_CUSTKEY", nullable = false)
    private Integer customerKey;

    @Column(name = "C_NAME", length = 25, nullable = false)
    private String name;

    @Column(name = "C_ADDRESS", length = 40, nullable = false)
    private String address;

    @ManyToOne
    @JoinColumn(name = "C_NATIONKEY", referencedColumnName = "N_NATIONKEY", nullable = false)
    private Nation nation;

    @Column(name = "C_PHONE", length = 15, nullable = false, columnDefinition = "bpchar")
    private String phone;

    @Column(name = "C_ACCTBAL", nullable = false, columnDefinition = "numeric(15, 2)")
    private BigDecimal accountBalance;

    @Column(name = "C_MKTSEGMENT", length = 10, nullable = false, columnDefinition = "bpchar")
    private String marketSegment;

    @Column(name = "C_COMMENT", length = 117, nullable = false)
    private String comment;

    public Customer() {
    }

    public Integer getCustomerKey() {
        return customerKey;
    }

    public void setCustomerKey(Integer customerKey) {
        this.customerKey = customerKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Nation getNation() {
        return nation;
    }

    public void setNation(Nation nation) {
        this.nation = nation;
    }

    public String getMarketSegment() {
        return marketSegment;
    }

    public void setMarketSegment(String marketSegment) {
        this.marketSegment = marketSegment;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public BigDecimal getAccountBalance() {
        return accountBalance;
    }

    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance;
    }
} 
