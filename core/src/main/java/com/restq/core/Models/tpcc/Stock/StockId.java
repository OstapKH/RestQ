package com.restq.core.Models.tpcc.Stock;

import java.io.Serializable;
import java.util.Objects;

public class StockId implements Serializable {

    private Integer warehouseId;
    private Integer itemId;

    public StockId() {
    }

    public StockId(Integer warehouseId, Integer itemId) {
        this.warehouseId = warehouseId;
        this.itemId = itemId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StockId that = (StockId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, itemId);
    }
} 
