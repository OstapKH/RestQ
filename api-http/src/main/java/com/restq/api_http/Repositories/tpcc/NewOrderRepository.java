package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.NewOrder.NewOrder;
import com.restq.core.Models.tpcc.NewOrder.NewOrderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NewOrderRepository extends JpaRepository<NewOrder, NewOrderId> {

    // For Delivery transaction - find oldest new order for district
    @Query("""
        SELECT no FROM NewOrder no 
        WHERE no.warehouseId = :warehouseId 
        AND no.districtId = :districtId 
        ORDER BY no.orderId ASC
    """)
    List<NewOrder> findOldestNewOrder(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId);

    // For Delivery transaction - delete new order after delivery
    @Modifying
    @Query("DELETE FROM NewOrder no WHERE no.warehouseId = :warehouseId AND no.districtId = :districtId AND no.orderId = :orderId")
    void deleteByWarehouseIdAndDistrictIdAndOrderId(@Param("warehouseId") Integer warehouseId, 
                                                   @Param("districtId") Integer districtId, 
                                                   @Param("orderId") Integer orderId);
} 
