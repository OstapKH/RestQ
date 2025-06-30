package com.restq.api_http.DTO.tpch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public class PricingSummaryReport {

    @JsonProperty("returnflag")
    private String returnflag;

    @JsonProperty("linestatus")
    private String linestatus;

    @JsonProperty("sumQty")
    private BigDecimal sumQty;

    @JsonProperty("sumBasePrice")
    private BigDecimal sumBasePrice;

    @JsonProperty("sumDiscPrice")
    private BigDecimal sumDiscPrice;

    @JsonProperty("sumCharge")
    private BigDecimal sumCharge;

    @JsonProperty("avgQty")
    private Double avgQty;

    @JsonProperty("avgPrice")
    private Double avgPrice;

    @JsonProperty("avgDisc")
    private Double avgDisc;

    @JsonProperty("countOrder")
    private Long countOrder;

    public PricingSummaryReport(String returnflag, String linestatus, BigDecimal sumQty, BigDecimal sumBasePrice, BigDecimal sumDiscPrice, BigDecimal sumCharge, Double avgQty, Double avgPrice, Double avgDisc, Long countOrder) {
        this.returnflag = returnflag;
        this.linestatus = linestatus;
        this.sumQty = sumQty;
        this.sumBasePrice = sumBasePrice;
        this.sumDiscPrice = sumDiscPrice;
        this.sumCharge = sumCharge;
        this.avgQty = avgQty;
        this.avgPrice = avgPrice;
        this.avgDisc = avgDisc;
        this.countOrder = countOrder;
    }

    public String getReturnflag() {
        return returnflag;
    }

    public void setReturnflag(String returnflag) {
        this.returnflag = returnflag;
    }

    public String getLinestatus() {
        return linestatus;
    }

    public void setLinestatus(String linestatus) {
        this.linestatus = linestatus;
    }

    public BigDecimal getSumQty() {
        return sumQty;
    }

    public void setSumQty(BigDecimal sumQty) {
        this.sumQty = sumQty;
    }

    public BigDecimal getSumBasePrice() {
        return sumBasePrice;
    }

    public void setSumBasePrice(BigDecimal sumBasePrice) {
        this.sumBasePrice = sumBasePrice;
    }

    public BigDecimal getSumDiscPrice() {
        return sumDiscPrice;
    }

    public void setSumDiscPrice(BigDecimal sumDiscPrice) {
        this.sumDiscPrice = sumDiscPrice;
    }

    public BigDecimal getSumCharge() {
        return sumCharge;
    }

    public void setSumCharge(BigDecimal sumCharge) {
        this.sumCharge = sumCharge;
    }

    public Double getAvgQty() {
        return avgQty;
    }

    public void setAvgQty(Double avgQty) {
        this.avgQty = avgQty;
    }

    public Double getAvgPrice() {
        return avgPrice;
    }

    public void setAvgPrice(Double avgPrice) {
        this.avgPrice = avgPrice;
    }

    public Double getAvgDisc() {
        return avgDisc;
    }

    public void setAvgDisc(Double avgDisc) {
        this.avgDisc = avgDisc;
    }

    public Long getCountOrder() {
        return countOrder;
    }

    public void setCountOrder(Long countOrder) {
        this.countOrder = countOrder;
    }
}