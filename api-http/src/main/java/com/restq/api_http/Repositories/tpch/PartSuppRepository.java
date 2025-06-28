package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.ImportantStockReport;
import com.restq.api_http.DTO.tpch.PartSupplierReport;
import com.restq.core.Models.tpch.PartSupp.PartSupp;
import com.restq.core.Models.tpch.PartSupp.PartSuppId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface PartSuppRepository extends JpaRepository<PartSupp, PartSuppId> {
     @Query("""
         SELECT new com.restq.api_http.DTO.tpch.ImportantStockReport(
             ps.part.partKey,
             SUM(ps.supplyCost * ps.availableQuantity)
         )
         FROM PartSupp ps, Supplier s, Nation n
         WHERE ps.supplier.supplierKey = s.supplierKey
         AND s.nation.nationKey = n.nationKey
         AND n.name = :nation
         GROUP BY ps.part.partKey
         HAVING SUM(ps.supplyCost * ps.availableQuantity) > (
             SELECT SUM(ps2.supplyCost * ps2.availableQuantity) * CAST(:fraction AS double)
             FROM PartSupp ps2, Supplier s2, Nation n2
             WHERE ps2.supplier.supplierKey = s2.supplierKey
             AND s2.nation.nationKey = n2.nationKey
             AND n2.name = :nation
         )
         ORDER BY SUM(ps.supplyCost * ps.availableQuantity) DESC
     """)
     // Q11
     List<ImportantStockReport> findImportantStock(
         @Param("nation") String nation,
         @Param("fraction") BigDecimal fraction);

    @Query("""
        SELECT new com.restq.api_http.DTO.tpch.PartSupplierReport(
            p.brand,
            p.type,
            p.size,
            COUNT(DISTINCT ps.supplier.supplierKey)
        )
        FROM PartSupp ps
        JOIN Part p ON p.partKey = ps.part.partKey
        WHERE p.brand <> :brand
        AND p.type NOT LIKE CONCAT(:type, '%')
        AND p.size IN :sizes
        AND ps.supplier.supplierKey NOT IN (
            SELECT s.supplierKey
            FROM Supplier s
            WHERE s.comment LIKE '%Customer%Complaints%'
        )
        GROUP BY p.brand, p.type, p.size
        ORDER BY COUNT(DISTINCT ps.supplier.supplierKey) DESC, p.brand ASC, p.type ASC, p.size ASC
    """)
    // Q16
    List<PartSupplierReport> findPartSupplierRelationships(
        @Param("brand") String brand,
        @Param("type") String type,
        @Param("sizes") List<Integer> sizes);
} 