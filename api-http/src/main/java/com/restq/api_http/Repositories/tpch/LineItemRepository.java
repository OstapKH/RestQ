package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.PricingSummaryReport;
import com.restq.api_http.DTO.tpch.ShippingModeReport;
import com.restq.api_http.DTO.tpch.PromotionRevenueReport;
import com.restq.api_http.DTO.tpch.SmallQuantityRevenueReport;
import com.restq.api_http.DTO.tpch.DiscountedRevenueReport;
import com.restq.core.Models.tpch.LineItem.LineItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;


@Repository
public interface LineItemRepository extends JpaRepository<LineItem, Long> {

    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.PricingSummaryReport(\
            l.returnFlag, l.lineStatus, \
            SUM(l.quantity), SUM(l.extendedPrice), \
            SUM(l.extendedPrice * (1 - l.discount)), \
            SUM(l.extendedPrice * (1 - l.discount) * (1 + l.tax)), \
            AVG(l.quantity), AVG(l.extendedPrice), \
            AVG(l.discount), COUNT(l) \
            ) FROM LineItem l \
            WHERE l.shipDate <= :shipDate \
            GROUP BY l.returnFlag, l.lineStatus \
            ORDER BY l.returnFlag, l.lineStatus""")
    // Q1
    List<PricingSummaryReport> getPricingSummaryReport(@Param("shipDate") LocalDate shipDate);


    @Query("""
            SELECT SUM(l.extendedPrice * l.discount) AS revenue \
            FROM LineItem l \
            WHERE l.shipDate >= :startDate \
              AND l.shipDate < :endDate \
              AND l.discount BETWEEN :discount - 0.01 AND :discount + 0.01 \
              AND l.quantity < :quantity \
            """)
    // Q6
    BigDecimal calculateRevenueIncrease(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("discount") Double discount,
            @Param("quantity") Integer quantity
    );

    @Query("""
    SELECT new com.restq.api_http.DTO.tpch.ShippingModeReport(
        l.shipMode,
        SUM(CASE WHEN o.orderPriority = '1-URGENT' OR o.orderPriority = '2-HIGH' THEN 1 ELSE 0 END),
        SUM(CASE WHEN o.orderPriority <> '1-URGENT' AND o.orderPriority <> '2-HIGH' THEN 1 ELSE 0 END)
    )
    FROM Order o, LineItem l
    WHERE o.orderKey = l.orderKey
    AND l.shipMode IN :shipModes
    AND l.commitDate < l.receiptDate
    AND l.shipDate < l.commitDate
    AND l.receiptDate >= :startDate
    AND l.receiptDate < :endDate
    GROUP BY l.shipMode
    ORDER BY l.shipMode
""")
    // Q12
    List<ShippingModeReport> findShippingModeStats(
            @Param("shipModes") List<String> shipModes,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
    SELECT new com.restq.api_http.DTO.tpch.PromotionRevenueReport(
        CAST(100.00 * SUM(CASE 
            WHEN p.type LIKE 'PROMO%' 
            THEN l.extendedPrice * (1 - l.discount)
            ELSE 0 
        END) / SUM(l.extendedPrice * (1 - l.discount)) AS java.math.BigDecimal)
    )
    FROM LineItem l
    JOIN Part p ON l.partKey = p.partKey
    WHERE l.shipDate >= :startDate
    AND l.shipDate < :endDate
""")
    // Q14
    PromotionRevenueReport calculatePromotionRevenue(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
    SELECT new com.restq.api_http.DTO.tpch.SmallQuantityRevenueReport(
        CAST(SUM(l.extendedPrice) / 7.0 AS java.math.BigDecimal)
    )
    FROM LineItem l
    JOIN Part p ON p.partKey = l.partKey
    WHERE p.brand = :brand
    AND p.container = :container
    AND l.quantity < (
        SELECT 0.2 * AVG(l2.quantity)
        FROM LineItem l2
        WHERE l2.partKey = p.partKey
    )
""")
    // Q17
    SmallQuantityRevenueReport calculateSmallQuantityRevenue(
            @Param("brand") String brand,
            @Param("container") String container
    );

    @Query("""
    SELECT new com.restq.api_http.DTO.tpch.DiscountedRevenueReport(
        SUM(l.extendedPrice * (1 - l.discount))
    )
    FROM LineItem l, Part p
    WHERE (
        p.partKey = l.partKey
        AND p.brand = :brand1
        AND p.container IN ('SM CASE', 'SM BOX', 'SM PACK', 'SM PKG')
        AND l.quantity >= :quantity1
        AND l.quantity <= :quantity1 + 10
        AND p.size BETWEEN 1 AND 5
        AND l.shipMode IN ('AIR', 'AIR REG')
        AND l.shipInstruct = 'DELIVER IN PERSON'
    ) OR (
        p.partKey = l.partKey
        AND p.brand = :brand2
        AND p.container IN ('MED BAG', 'MED BOX', 'MED PKG', 'MED PACK')
        AND l.quantity >= :quantity2
        AND l.quantity <= :quantity2 + 10
        AND p.size BETWEEN 1 AND 10
        AND l.shipMode IN ('AIR', 'AIR REG')
        AND l.shipInstruct = 'DELIVER IN PERSON'
    ) OR (
        p.partKey = l.partKey
        AND p.brand = :brand3
        AND p.container IN ('LG CASE', 'LG BOX', 'LG PACK', 'LG PKG')
        AND l.quantity >= :quantity3
        AND l.quantity <= :quantity3 + 10
        AND p.size BETWEEN 1 AND 15
        AND l.shipMode IN ('AIR', 'AIR REG')
        AND l.shipInstruct = 'DELIVER IN PERSON'
    )
""")
    // Q19
    DiscountedRevenueReport calculateDiscountedRevenue(
            @Param("brand1") String brand1,
            @Param("brand2") String brand2,
            @Param("brand3") String brand3,
            @Param("quantity1") Integer quantity1,
            @Param("quantity2") Integer quantity2,
            @Param("quantity3") Integer quantity3
    );
}