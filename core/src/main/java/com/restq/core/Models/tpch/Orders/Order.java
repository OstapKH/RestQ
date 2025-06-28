package com.restq.core.Models.tpch.Orders;

import com.restq.core.Models.tpch.Customer.Customer;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ORDERS")
public class Order {

    @Id
    @Column(name = "O_ORDERKEY")
    private Integer orderKey;

    @ManyToOne
    @JoinColumn(name = "O_CUSTKEY", referencedColumnName = "C_CUSTKEY")
    private Customer customer;

    @Column(name = "O_ORDERSTATUS", length = 1, nullable = false, columnDefinition = "bpchar")
    private String orderStatus;

    @Column(name = "O_TOTALPRICE", nullable = false)
    private BigDecimal totalPrice;

    @Column(name = "O_ORDERDATE", nullable = false)
    private LocalDate orderDate;

    @Column(name = "O_ORDERPRIORITY", length = 15, nullable = false, columnDefinition = "bpchar")
    private String orderPriority;

    @Column(name = "O_CLERK", length = 15, nullable = false, columnDefinition = "bpchar")
    private String clerk;

    @Column(name = "O_SHIPPRIORITY", nullable = false)
    private Integer shipPriority;

    @Column(name = "O_COMMENT", length = 79, columnDefinition = "bpchar")
    private String comment;

    public Order() {
    }

    public Integer getOrderKey() {
        return orderKey;
    }

    public void setOrderKey(Integer orderKey) {
        this.orderKey = orderKey;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getClerk() {
        return clerk;
    }

    public void setClerk(String clerk) {
        this.clerk = clerk;
    }

    public Integer getShipPriority() {
        return shipPriority;
    }

    public void setShipPriority(Integer shipPriority) {
        this.shipPriority = shipPriority;
    }

    public String getOrderPriority() {
        return orderPriority;
    }

    public void setOrderPriority(String orderPriority) {
        this.orderPriority = orderPriority;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
} 
