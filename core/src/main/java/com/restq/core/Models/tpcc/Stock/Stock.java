package com.restq.core.Models.tpcc.Stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "STOCK")
@IdClass(StockId.class)
public class Stock {

    @Id
    @Column(name = "S_W_ID")
    private Integer warehouseId;

    @Id
    @Column(name = "S_I_ID")
    private Integer itemId;

    @Column(name = "S_QUANTITY", nullable = false)
    private Integer quantity;

    @Column(name = "S_YTD", nullable = false, precision = 8, scale = 2)
    private BigDecimal yearToDate;

    @Column(name = "S_ORDER_CNT", nullable = false)
    private Integer orderCount;

    @Column(name = "S_REMOTE_CNT", nullable = false)
    private Integer remoteCount;

    @Column(name = "S_DATA", length = 50, nullable = false)
    private String data;

    @Column(name = "S_DIST_01", length = 24, nullable = false)
    private String dist01;

    @Column(name = "S_DIST_02", length = 24, nullable = false)
    private String dist02;

    @Column(name = "S_DIST_03", length = 24, nullable = false)
    private String dist03;

    @Column(name = "S_DIST_04", length = 24, nullable = false)
    private String dist04;

    @Column(name = "S_DIST_05", length = 24, nullable = false)
    private String dist05;

    @Column(name = "S_DIST_06", length = 24, nullable = false)
    private String dist06;

    @Column(name = "S_DIST_07", length = 24, nullable = false)
    private String dist07;

    @Column(name = "S_DIST_08", length = 24, nullable = false)
    private String dist08;

    @Column(name = "S_DIST_09", length = 24, nullable = false)
    private String dist09;

    @Column(name = "S_DIST_10", length = 24, nullable = false)
    private String dist10;

    public Stock() {
    }

    public Integer getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Integer warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getYearToDate() {
        return yearToDate;
    }

    public void setYearToDate(BigDecimal yearToDate) {
        this.yearToDate = yearToDate;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public Integer getRemoteCount() {
        return remoteCount;
    }

    public void setRemoteCount(Integer remoteCount) {
        this.remoteCount = remoteCount;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getDist01() {
        return dist01;
    }

    public void setDist01(String dist01) {
        this.dist01 = dist01;
    }

    public String getDist02() {
        return dist02;
    }

    public void setDist02(String dist02) {
        this.dist02 = dist02;
    }

    public String getDist03() {
        return dist03;
    }

    public void setDist03(String dist03) {
        this.dist03 = dist03;
    }

    public String getDist04() {
        return dist04;
    }

    public void setDist04(String dist04) {
        this.dist04 = dist04;
    }

    public String getDist05() {
        return dist05;
    }

    public void setDist05(String dist05) {
        this.dist05 = dist05;
    }

    public String getDist06() {
        return dist06;
    }

    public void setDist06(String dist06) {
        this.dist06 = dist06;
    }

    public String getDist07() {
        return dist07;
    }

    public void setDist07(String dist07) {
        this.dist07 = dist07;
    }

    public String getDist08() {
        return dist08;
    }

    public void setDist08(String dist08) {
        this.dist08 = dist08;
    }

    public String getDist09() {
        return dist09;
    }

    public void setDist09(String dist09) {
        this.dist09 = dist09;
    }

    public String getDist10() {
        return dist10;
    }

    public void setDist10(String dist10) {
        this.dist10 = dist10;
    }
} 
