package com.restq.core.Models.tpch.Supplier;

import java.math.BigDecimal;

import com.restq.core.Models.tpch.Nation.Nation;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

@Entity
@Table(name = "SUPPLIER")
public class Supplier {

    @Id
    @Column(name = "S_SUPPKEY")
    private Integer supplierKey;

    @Column(name = "S_NAME", length = 25, nullable = false, columnDefinition = "bpchar")
    private String name;

    @Column(name = "S_ADDRESS", length = 40, nullable = false, columnDefinition = "bpchar")
    private String address;

    @ManyToOne
    @JoinColumn(name = "S_NATIONKEY", referencedColumnName = "N_NATIONKEY")
    private Nation nation;

    @Column(name = "S_PHONE", length = 15, nullable = false, columnDefinition = "bpchar")
    private String phone;

    @Column(name = "S_ACCTBAL", nullable = false)
    private BigDecimal accountBalance;

    @Column(name = "S_COMMENT", length = 101, columnDefinition = "bpchar")
    private String comment;

    public Supplier() {
    }

    public Integer getSupplierKey() {
        return supplierKey;
    }

    public void setSupplierKey(Integer supplierKey) {
        this.supplierKey = supplierKey;
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

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
} 
