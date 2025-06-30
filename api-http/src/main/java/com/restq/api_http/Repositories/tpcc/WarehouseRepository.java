package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.Warehouse.Warehouse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCC")
public interface WarehouseRepository extends JpaRepository<Warehouse, Integer> {

    // For Payment transaction - get warehouse tax rate
    @Query("SELECT w.tax FROM Warehouse w WHERE w.warehouseId = :warehouseId")
    BigDecimal getWarehouseTax(@Param("warehouseId") Integer warehouseId);

    // For Payment transaction - get warehouse info
    @Query("SELECT w FROM Warehouse w WHERE w.warehouseId = :warehouseId")
    Warehouse findByWarehouseId(@Param("warehouseId") Integer warehouseId);

    // For Payment transaction - update warehouse YTD balance
    @Modifying
    @Query("UPDATE Warehouse w SET w.yearToDateBalance = w.yearToDateBalance + :amount WHERE w.warehouseId = :warehouseId")
    void updateYearToDateBalance(@Param("warehouseId") Integer warehouseId, @Param("amount") BigDecimal amount);
} 
