package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.VolumeShippingOfNations;
import com.restq.api_http.DTO.tpch.MarketShareReport;
import com.restq.api_http.DTO.tpch.ProductProfitReport;
import com.restq.core.Models.tpch.Nation.Nation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface NationRepository extends JpaRepository<Nation, Long> {

    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.VolumeShippingOfNations(
                n1.name,
                n2.name,
                EXTRACT(YEAR FROM o.orderDate),
                SUM(l.extendedPrice * (1 - l.discount))
            )
            FROM
                Supplier s,
                LineItem l,
                Order o,
                Customer c,
                Nation n1,
                Nation n2
            WHERE
                s.supplierKey = l.suppKey
                AND o.orderKey = l.orderKey
                AND c.customerKey = o.customer.customerKey
                AND s.nation.nationKey = n1.nationKey
                AND c.nation.nationKey = n2.nationKey
                AND (
                    (n1.name = :nation1 AND n2.name = :nation2)
                    OR
                    (n1.name = :nation2 AND n2.name = :nation1)
                )
                AND l.shipDate BETWEEN :startDate AND :endDate
            GROUP BY
                n1.name,
                n2.name,
                EXTRACT(YEAR FROM o.orderDate)
            ORDER BY
                n1.name,
                n2.name,
                EXTRACT(YEAR FROM o.orderDate)
            """)
    // Q7
    List<VolumeShippingOfNations> getNationsVolumeShipping(
            @Param("nation1") String nation1,
            @Param("nation2") String nation2,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
                SELECT new com.restq.api_http.DTO.tpch.MarketShareReport(
                    all_nations.o_year,
                    SUM(CASE WHEN all_nations.nation = :nation THEN all_nations.volume ELSE 0 END) / SUM(all_nations.volume)
                )
                FROM (
                    SELECT
                        EXTRACT(YEAR FROM o.orderDate) as o_year,
                        l.extendedPrice * (1 - l.discount) as volume,
                        n2.name as nation
                    FROM
                        Part p, Supplier s, LineItem l, Order o, Customer c, Nation n1, Nation n2, Region r
                    WHERE
                        p.partKey = l.partKey
                        AND s.supplierKey = l.suppKey
                        AND l.orderKey = o.orderKey
                        AND o.customer.customerKey = c.customerKey
                        AND c.nation.nationKey = n1.nationKey
                        AND n1.region.regionKey = r.regionKey
                        AND r.name = :region
                        AND s.nation.nationKey = n2.nationKey
                        AND o.orderDate BETWEEN :startDate AND :endDate
                        AND p.type = :type
                ) as all_nations
                GROUP BY all_nations.o_year
                ORDER BY all_nations.o_year
            """)
    // Q8
    List<MarketShareReport> getMarketShare(
            @Param("nation") String nation,
            @Param("region") String region,
            @Param("type") String type,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.ProductProfitReport(
                profit.nation,
                profit.o_year,
                SUM(profit.amount) as sum_profit
            )
            FROM (
                SELECT
                    n.name as nation,
                    EXTRACT(YEAR FROM o.orderDate) as o_year,
                    l.extendedPrice * (1 - l.discount) - ps.supplyCost * l.quantity as amount
                FROM
                    Part p,
                    Supplier s,
                    LineItem l,
                    PartSupp ps,
                    Order o,
                    Nation n
                WHERE
                    s.supplierKey = l.suppKey
                    AND ps.supplier.supplierKey = l.suppKey
                    AND ps.part.partKey = l.partKey
                    AND p.partKey = l.partKey
                    AND o.orderKey = l.orderKey
                    AND s.nation.nationKey = n.nationKey
                    AND p.name LIKE CONCAT('%', :color, '%')
            ) as profit
            GROUP BY
                profit.nation,
                profit.o_year
            ORDER BY
                profit.nation,
                profit.o_year DESC
            """)
    // Q9
    List<ProductProfitReport> getProductTypeProfit(@Param("color") String color);

}
