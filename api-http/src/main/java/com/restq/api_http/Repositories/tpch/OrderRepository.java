package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.OrderPriorityCountInfo;
import com.restq.api_http.DTO.tpch.OrderRevenueInfo;
import com.restq.core.Models.tpch.Orders.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.OrderRevenueInfo(\
                l.orderKey, SUM(l.extendedPrice * (1 - l.discount)), o.orderDate, o.shipPriority)\
            FROM Customer c, Order o, LineItem l \
            WHERE c.marketSegment = :segment \
            AND c.customerKey = o.customer.customerKey \
            AND l.orderKey = o.orderKey \
            AND o.orderDate < :date \
            AND l.shipDate > :date \
            GROUP BY l.orderKey, o.orderDate, o.shipPriority \
            ORDER BY SUM(l.extendedPrice * (1 - l.discount)) DESC, o.orderDate \
            LIMIT 10 \
            """)
    // Q3
    List<OrderRevenueInfo> findTopUnshippedOrders(@Param("segment") String segment, @Param("date") LocalDate date);

    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.OrderPriorityCountInfo(\
                o.orderPriority, COUNT(*)) \
            FROM Order o \
            WHERE o.orderDate >= :date \
            AND o.orderDate < :datePlus \
            AND EXISTS(
                SELECT l FROM LineItem l \
                WHERE l.orderKey = o.orderKey \
                AND l.commitDate < l.receiptDate \
                ) \
            GROUP BY o.orderPriority \
            ORDER BY o.orderPriority
            """)
    // Q4
    List<OrderPriorityCountInfo> findOrderPriorityCount(@Param("date") LocalDate date, @Param("datePlus") LocalDate datePlus);


}
