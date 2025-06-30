package com.restq.core.Models.tpch.LineItem;

import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "LINEITEM")
public class LineItem {

    @Id
    @Column(name = "L_ORDERKEY", nullable = false)
    private Integer orderKey;

    @Column(name = "L_PARTKEY", nullable = false)
    private Integer partKey;

    @Column(name = "L_SUPPKEY", nullable = false)
    private Integer suppKey;

    @Column(name = "L_LINENUMBER", nullable = false)
    private Integer lineNumber;

    @Column(name = "L_QUANTITY", nullable = false, columnDefinition = "numeric(15, 2)")
    private BigDecimal quantity;

    @Column(name = "L_EXTENDEDPRICE", nullable = false, columnDefinition = "numeric(15, 2)")
    private BigDecimal extendedPrice;

    @Column(name = "L_DISCOUNT", nullable = false, columnDefinition = "numeric(15, 2)")
    private BigDecimal discount;

    @Column(name = "L_TAX", nullable = false, columnDefinition = "numeric(15, 2)")
    private BigDecimal tax;

    @Column(name = "L_RETURNFLAG", nullable = false, columnDefinition = "bpchar")
    private String returnFlag;

    @Column(name = "L_LINESTATUS", nullable = false, columnDefinition = "bpchar")
    private String lineStatus;

    @Column(name = "L_SHIPDATE", nullable = false)
    private LocalDate shipDate;

    @Column(name = "L_COMMITDATE", nullable = false)
    private LocalDate commitDate;

    @Column(name = "L_RECEIPTDATE", nullable = false)
    private LocalDate receiptDate;

    @Column(name = "L_SHIPINSTRUCT", length = 25, nullable = false, columnDefinition = "bpchar")
    private String shipInstruct;

    @Column(name = "L_SHIPMODE", length = 10, nullable = false, columnDefinition = "bpchar")
    private String shipMode;

    @Column(name = "L_COMMENT", length = 44, nullable = false)
    private String comment;

    public LineItem() {
    }

    public LocalDate getCommitDate() {
        return commitDate;
    }

    public void setCommitDate(LocalDate commitDate) {
        this.commitDate = commitDate;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getShipMode() {
        return shipMode;
    }

    public void setShipMode(String shipMode) {
        this.shipMode = shipMode;
    }

    public String getShipInstruct() {
        return shipInstruct;
    }

    public void setShipInstruct(String shipInstruct) {
        this.shipInstruct = shipInstruct;
    }

    public LocalDate getReceiptDate() {
        return receiptDate;
    }

    public void setReceiptDate(LocalDate receiptDate) {
        this.receiptDate = receiptDate;
    }

    public LocalDate getShipDate() {
        return shipDate;
    }

    public void setShipDate(LocalDate shipDate) {
        this.shipDate = shipDate;
    }

    public String getLineStatus() {
        return lineStatus;
    }

    public void setLineStatus(String lineStatus) {
        this.lineStatus = lineStatus;
    }

    public String getReturnFlag() {
        return returnFlag;
    }

    public void setReturnFlag(String returnFlag) {
        this.returnFlag = returnFlag;
    }

    public BigDecimal getTax() {
        return tax;
    }

    public void setTax(BigDecimal tax) {
        this.tax = tax;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public Integer getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(Integer lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Integer getSuppKey() {
        return suppKey;
    }

    public void setSuppKey(Integer suppKey) {
        this.suppKey = suppKey;
    }

    public Integer getPartKey() {
        return partKey;
    }

    public void setPartKey(Integer partKey) {
        this.partKey = partKey;
    }

    public Integer getOrderKey() {
        return orderKey;
    }

    public void setOrderKey(Integer orderKey) {
        this.orderKey = orderKey;
    }

    public BigDecimal getExtendedPrice() {
        return extendedPrice;
    }

    public void setExtendedPrice(BigDecimal extendedPrice) {
        this.extendedPrice = extendedPrice;
    }
} 
