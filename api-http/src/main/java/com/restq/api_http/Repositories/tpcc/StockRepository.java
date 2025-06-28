package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.Stock.Stock;
import com.restq.core.Models.tpcc.Stock.StockId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface StockRepository extends JpaRepository<Stock, StockId> {

    // For New Order transaction - get stock with lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.itemId = :itemId AND s.warehouseId = :warehouseId")
    Stock findByItemIdAndWarehouseIdForUpdate(@Param("itemId") Integer itemId, @Param("warehouseId") Integer warehouseId);

    // For New Order transaction - get stock quantity and distribution data
    @Query("""
        SELECT s.quantity, s.data, s.dist01, s.dist02, s.dist03, s.dist04, s.dist05,
               s.dist06, s.dist07, s.dist08, s.dist09, s.dist10
        FROM Stock s WHERE s.itemId = :itemId AND s.warehouseId = :warehouseId
    """)
    Object[] getStockQuantityAndDistData(@Param("itemId") Integer itemId, @Param("warehouseId") Integer warehouseId);

    // For New Order transaction - update stock
    @Modifying
    @Query("""
        UPDATE Stock s SET 
        s.quantity = :quantity,
        s.yearToDate = s.yearToDate + :ytdIncrease,
        s.orderCount = s.orderCount + 1,
        s.remoteCount = s.remoteCount + :remoteIncrease
        WHERE s.itemId = :itemId AND s.warehouseId = :warehouseId
    """)
    void updateStock(@Param("itemId") Integer itemId, @Param("warehouseId") Integer warehouseId, 
                    @Param("quantity") Integer quantity, @Param("ytdIncrease") Integer ytdIncrease, 
                    @Param("remoteIncrease") Integer remoteIncrease);

    // For Stock Level transaction - count distinct items below threshold
    @Query("""
        SELECT COUNT(DISTINCT s.itemId)
        FROM Stock s
        WHERE s.warehouseId = :warehouseId
        AND s.quantity < :threshold
        AND s.itemId IN (
            SELECT DISTINCT ol.itemId
            FROM OrderLine ol
            WHERE ol.warehouseId = :warehouseId
            AND ol.districtId = :districtId
            AND ol.orderId >= :minOrderId
            AND ol.orderId < :maxOrderId
        )
    """)
    Long countLowStockItems(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId,
                           @Param("minOrderId") Integer minOrderId, @Param("maxOrderId") Integer maxOrderId,
                           @Param("threshold") Integer threshold);
} 
