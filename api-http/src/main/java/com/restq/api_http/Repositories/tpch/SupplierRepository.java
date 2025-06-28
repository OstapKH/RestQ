package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.LocalSupplierVolume;
import com.restq.api_http.DTO.tpch.SupplierWaitingReport;
import com.restq.core.Models.tpch.Supplier.Supplier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    @Query("""
        SELECT new com.restq.api_http.DTO.tpch.LocalSupplierVolume(
            n.name, 
            SUM(li.extendedPrice * (1 - li.discount))
        )
        FROM Customer c, Order o, LineItem li, Supplier s, Nation n, Region r
        WHERE c.customerKey = o.customer.customerKey
        AND li.orderKey = o.orderKey
        AND li.suppKey = s.supplierKey
        AND c.nation.nationKey = s.nation.nationKey
        AND s.nation.nationKey = n.nationKey
        AND n.region.regionKey = r.regionKey
        AND r.name = :region
        AND o.orderDate >= :startDate
        AND o.orderDate < :endDate
        GROUP BY n.name
        ORDER BY SUM(li.extendedPrice * (1 - li.discount)) DESC
        """)
    // Q5
    List<LocalSupplierVolume> findLocalSupplierVolume(
            @Param("region") String region,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

//     @Query("""
//             SELECT new com.restq.api_http.DTO.tpch.TopSupplierReport(
//                 s.supplierKey,
//                 s.name,
//                 s.address,
//                 s.phone,
//                 SUM(l.extendedPrice * (1 - l.discount))
//             )
//             FROM Supplier s
//             JOIN LineItem l ON s.supplierKey = l.suppKey
//             WHERE l.shipDate >= :startDate
//             AND l.shipDate < :endDate
//             GROUP BY s.supplierKey, s.name, s.address, s.phone
//             HAVING SUM(l.extendedPrice * (1 - l.discount)) = (
//                 SELECT SUM(l2.extendedPrice * (1 - l2.discount))
//                 FROM LineItem l2
//                 WHERE l2.shipDate >= :startDate
//                 AND l2.shipDate < :endDate
//                 GROUP BY l2.suppKey
//                 ORDER BY SUM(l2.extendedPrice * (1 - l2.discount)) DESC
//                 LIMIT 1
//             )
//             ORDER BY s.supplierKey
//             """)
//    // Q15         
//     List<TopSupplierReport> findTopSuppliers(
//             @Param("startDate") Date startDate,
//             @Param("endDate") Date endDate);

//     @Query("""
//     SELECT new com.restq.api_http.DTO.tpch.PotentialPromotionReport(
//         s.name,
//         s.address
//     )
//     FROM Supplier s, Nation n
//     WHERE s.supplierKey IN (
//         SELECT ps.supplier.supplierKey
//         FROM PartSupp ps
//         WHERE ps.part.partKey IN (
//             SELECT p.partKey
//             FROM Part p
//             WHERE p.name LIKE CONCAT(:color, '%')
//         )
//         AND ps.availQty > (
//             SELECT 0.5 * SUM(l.quantity)
//             FROM LineItem l
//             WHERE l.partKey = ps.part.partKey
//             AND l.suppKey = ps.supplier.supplierKey
//             AND l.shipDate >= :startDate
//             AND l.shipDate < :endDate
//         )
//     )
//     AND s.nation.nationKey = n.nationKey
//     AND n.name = :nation
//     ORDER BY s.name
// """)
// // Q20
// List<PotentialPromotionReport> findPotentialPromotionSuppliers(
//     @Param("color") String color,
//     @Param("startDate") Date startDate,
//     @Param("endDate") Date endDate,
//     @Param("nation") String nation);

    @Query("""
    SELECT new com.restq.api_http.DTO.tpch.SupplierWaitingReport(
        s.name,
        COUNT(*) as numwait
    )
    FROM Supplier s, LineItem l1, Order o, Nation n
    WHERE s.supplierKey = l1.suppKey
    AND o.orderKey = l1.orderKey
    AND o.orderStatus = 'F'
    AND l1.receiptDate > l1.commitDate
    AND EXISTS (
        SELECT 1 FROM LineItem l2
        WHERE l2.orderKey = l1.orderKey
        AND l2.suppKey <> l1.suppKey
    )
    AND NOT EXISTS (
        SELECT 1 FROM LineItem l3
        WHERE l3.orderKey = l1.orderKey
        AND l3.suppKey <> l1.suppKey
        AND l3.receiptDate > l3.commitDate
    )
    AND s.nation.nationKey = n.nationKey
    AND n.name = :nation
    GROUP BY s.name
    ORDER BY COUNT(*) DESC, s.name ASC
""")
// Q21
List<SupplierWaitingReport> findSuppliersWhoKeptWaiting(
    @Param("nation") String nation,
    Pageable pageable);
}
