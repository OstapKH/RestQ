package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.OrderLine.OrderLine;
import com.restq.core.Models.tpcc.OrderLine.OrderLineId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, OrderLineId> {

    // For Order Status transaction - get order lines for specific order
    @Query("""
        SELECT ol FROM OrderLine ol 
        WHERE ol.warehouseId = :warehouseId 
        AND ol.districtId = :districtId 
        AND ol.orderId = :orderId 
        ORDER BY ol.lineNumber
    """)
    List<OrderLine> findByOrderIdOrderByLineNumber(@Param("warehouseId") Integer warehouseId, 
                                                   @Param("districtId") Integer districtId, 
                                                   @Param("orderId") Integer orderId);

    // For Delivery transaction - update order line delivery date
    @Modifying
    @Query("UPDATE OrderLine ol SET ol.deliveryDate = :deliveryDate WHERE ol.warehouseId = :warehouseId AND ol.districtId = :districtId AND ol.orderId = :orderId")
    void updateDeliveryDate(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, 
                           @Param("orderId") Integer orderId, @Param("deliveryDate") LocalDateTime deliveryDate);

    // For Delivery transaction - get total amount for order
    @Query("SELECT SUM(ol.amount) FROM OrderLine ol WHERE ol.warehouseId = :warehouseId AND ol.districtId = :districtId AND ol.orderId = :orderId")
    BigDecimal getTotalAmountForOrder(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("orderId") Integer orderId);

    // For Stock Level transaction - get distinct item IDs in recent orders
    @Query("""
        SELECT DISTINCT ol.itemId FROM OrderLine ol 
        WHERE ol.warehouseId = :warehouseId 
        AND ol.districtId = :districtId 
        AND ol.orderId >= :minOrderId 
        AND ol.orderId < :maxOrderId
    """)
    List<Integer> findDistinctItemIdsByOrderRange(@Param("warehouseId") Integer warehouseId, 
                                                 @Param("districtId") Integer districtId, 
                                                 @Param("minOrderId") Integer minOrderId, 
                                                 @Param("maxOrderId") Integer maxOrderId);
} 
