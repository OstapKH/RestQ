package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.SupplierPartInfo;
import com.restq.core.Models.tpch.Part.Part;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartRepository extends JpaRepository<Part, Long> {
    @Query("""
            SELECT new com.restq.api_http.DTO.tpch.SupplierPartInfo(\
            s.accountBalance, s.name, n.name, p.partKey, p.manufacturer, s.address, s.phone, s.comment) \
            FROM Part p, Supplier s, PartSupp ps, Nation n, Region r \
            WHERE p.partKey = ps.part.partKey \
            AND ps.supplier.supplierKey = s.supplierKey \
            AND p.size = :size \
            AND p.type LIKE %:type \
            AND s.nation.nationKey = n.nationKey \
            AND n.region.regionKey = r.regionKey \
            AND r.name = :region \
            AND ps.supplyCost = ( \
                SELECT MIN(ps2.supplyCost) \
                FROM PartSupp ps2, Supplier s2, Nation n2, Region r2 \
                WHERE ps2.part.partKey = p.partKey \
                AND ps2.supplier.supplierKey = s2.supplierKey \
                AND s2.nation.nationKey = n2.nationKey \
                AND n2.region.regionKey = r2.regionKey \
                AND r2.name = :region \
            ) \
            ORDER BY s.accountBalance DESC, n.name, s.name, p.partKey
            LIMIT 100""")
    // Q2
    List<SupplierPartInfo> findSupplierPartInfo(@Param("size") Integer size, @Param("type") String type, @Param("region") String region);
}


// package com.restq.api_http.Repositories;

// import com.restq.api_http.DTO.tpch.SupplierPartInfo;
// import com.restq.core.Models.tpch.Part.Part;
// import org.springframework.data.jpa.repository.JpaRepository;
// import org.springframework.data.jpa.repository.Query;
// import org.springframework.data.repository.query.Param;
// import org.springframework.stereotype.Repository;

// import java.util.List;

// @Repository
// public interface PartRepository extends JpaRepository<Part, Long> {
//     @Query(value = """
//             SELECT s.S_ACCTBAL as accountBalance, s.S_NAME as name, n.N_NAME as nationName, 
//                    p.P_PARTKEY as partKey, p.P_MFGR as manufacturer, s.S_ADDRESS as address, 
//                    s.S_PHONE as phone, s.S_COMMENT as comment
//             FROM PART p, PARTSUPP ps, SUPPLIER s, NATION n, REGION r
//             WHERE p.P_PARTKEY = ps.PS_PARTKEY
//             AND ps.PS_SUPPKEY = s.S_SUPPKEY
//             AND s.S_NATIONKEY = n.N_NATIONKEY
//             AND n.N_REGIONKEY = r.R_REGIONKEY
//             AND p.P_SIZE = :size
//             AND p.P_TYPE LIKE CONCAT('%', :type, '%')
//             AND r.R_NAME = :region
//             AND ps.PS_SUPPLYCOST = (
//                 SELECT MIN(ps2.PS_SUPPLYCOST)
//                 FROM PARTSUPP ps2, SUPPLIER s2, NATION n2, REGION r2
//                 WHERE ps2.PS_PARTKEY = p.P_PARTKEY
//                 AND ps2.PS_SUPPKEY = s2.S_SUPPKEY
//                 AND s2.S_NATIONKEY = n2.N_NATIONKEY
//                 AND n2.N_REGIONKEY = r2.R_REGIONKEY
//                 AND r2.R_NAME = :region
//             )
//             ORDER BY s.S_ACCTBAL DESC, n.N_NAME, s.S_NAME, p.P_PARTKEY
//             """, nativeQuery = true)
//     // Q2
//     List<SupplierPartInfo> findSupplierPartInfo(@Param("size") Integer size, @Param("type") String type, @Param("region") String region);
// }