package com.restq.core.Models.tpcc.District;

import java.io.Serializable;
import java.util.Objects;

public class DistrictId implements Serializable {

    private Integer warehouseId;
    private Integer districtId;

    public DistrictId() {
    }

    public DistrictId(Integer warehouseId, Integer districtId) {
        this.warehouseId = warehouseId;
        this.districtId = districtId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DistrictId that = (DistrictId) o;
        return Objects.equals(warehouseId, that.warehouseId) &&
               Objects.equals(districtId, that.districtId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(warehouseId, districtId);
    }
} 
