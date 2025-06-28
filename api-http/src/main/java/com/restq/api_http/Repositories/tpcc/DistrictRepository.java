package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.District.District;
import com.restq.core.Models.tpcc.District.DistrictId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;

@Repository
public interface DistrictRepository extends JpaRepository<District, DistrictId> {

    // For New Order transaction - get district info with lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM District d WHERE d.warehouseId = :warehouseId AND d.districtId = :districtId")
    District findByWarehouseIdAndDistrictIdForUpdate(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId);

    // For New Order transaction - get next order ID and tax
    @Query("SELECT d.nextOrderId, d.tax FROM District d WHERE d.warehouseId = :warehouseId AND d.districtId = :districtId")
    Object[] getNextOrderIdAndTax(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId);

    // For New Order transaction - increment next order ID
    @Modifying
    @Query("UPDATE District d SET d.nextOrderId = d.nextOrderId + 1 WHERE d.warehouseId = :warehouseId AND d.districtId = :districtId")
    void incrementNextOrderId(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId);

    // For Payment transaction - get district info
    @Query("SELECT d FROM District d WHERE d.warehouseId = :warehouseId AND d.districtId = :districtId")
    District findByWarehouseIdAndDistrictId(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId);

    // For Payment transaction - update district YTD balance
    @Modifying
    @Query("UPDATE District d SET d.yearToDateBalance = d.yearToDateBalance + :amount WHERE d.warehouseId = :warehouseId AND d.districtId = :districtId")
    void updateYearToDateBalance(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("amount") BigDecimal amount);
} 
