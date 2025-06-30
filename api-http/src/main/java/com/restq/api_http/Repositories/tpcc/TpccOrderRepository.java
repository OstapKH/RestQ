package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.Order.Order;
import com.restq.core.Models.tpcc.Order.OrderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TpccOrderRepository extends JpaRepository<Order, OrderId> {

    // For Order Status transaction - get latest order for customer
    @Query("""
        SELECT o FROM Order o 
        WHERE o.warehouseId = :warehouseId 
        AND o.districtId = :districtId 
        AND o.customerId = :customerId 
        ORDER BY o.orderId DESC
    """)
    List<Order> findLatestOrderByCustomer(@Param("warehouseId") Integer warehouseId, 
                                         @Param("districtId") Integer districtId, 
                                         @Param("customerId") Integer customerId);

    // For Delivery transaction - get oldest undelivered order for district
    @Query("""
        SELECT o FROM Order o 
        WHERE o.warehouseId = :warehouseId 
        AND o.districtId = :districtId 
        AND o.carrierId IS NULL 
        ORDER BY o.orderId ASC
    """)
    List<Order> findOldestUndeliveredOrder(@Param("warehouseId") Integer warehouseId, 
                                          @Param("districtId") Integer districtId);

    // For Delivery transaction - update order with carrier ID
    @Modifying
    @Query("UPDATE Order o SET o.carrierId = :carrierId WHERE o.warehouseId = :warehouseId AND o.districtId = :districtId AND o.orderId = :orderId")
    void updateCarrierId(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, 
                        @Param("orderId") Integer orderId, @Param("carrierId") Integer carrierId);
} 
