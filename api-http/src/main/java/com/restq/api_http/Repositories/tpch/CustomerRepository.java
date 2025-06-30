package com.restq.api_http.Repositories.tpch;

import com.restq.api_http.DTO.tpch.ReturnedItemReport;
import com.restq.api_http.DTO.tpch.CustomerDistributionReport;
import com.restq.api_http.DTO.tpch.GlobalSalesOpportunityReport;
import com.restq.core.Models.tpch.Customer.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

     @Query("""
         SELECT new com.restq.api_http.DTO.tpch.ReturnedItemReport(
             c.customerKey,
             c.name,
             SUM(l.extendedPrice * (1 - l.discount)),
             c.accountBalance,
             n.name,
             c.address,
             c.phone,
             c.comment
         )
         FROM Customer c, Order o, LineItem l, Nation n
         WHERE c.customerKey = o.customer.customerKey
         AND l.orderKey = o.orderKey
         AND o.orderDate >= :startDate
         AND o.orderDate < :endDate
         AND l.returnFlag = 'R'
         AND c.nation.nationKey = n.nationKey
         GROUP BY c.customerKey, c.name, c.accountBalance, c.phone, n.name, c.address, c.comment
         ORDER BY SUM(l.extendedPrice * (1 - l.discount)) DESC
         LIMIT 20
     """)
     // Q10
     List<ReturnedItemReport> findTopReturnedItems(
         @Param("startDate") LocalDate startDate,
         @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT new com.restq.api_http.DTO.tpch.CustomerDistributionReport(
            COUNT(o.orderKey) as orderCount,
            COUNT(DISTINCT c.customerKey) as customerCount
        )
        FROM Customer c
        LEFT OUTER JOIN Order o ON c.customerKey = o.customer.customerKey
            AND o.comment NOT LIKE CONCAT('%', :word1, '%', :word2, '%')
        GROUP BY c.customerKey
        ORDER BY COUNT(o.orderKey) DESC, c.customerKey DESC
    """)
    // Q13
    List<CustomerDistributionReport> findCustomerDistribution(
        @Param("word1") String word1,
        @Param("word2") String word2);

    // @Query("""
    //     SELECT new com.restq.api_http.DTO.tpch.LargeVolumeCustomerReport(
    //         c.name,
    //         c.customerKey,
    //         o.orderKey,
    //         o.orderDate,
    //         o.totalPrice,
    //         SUM(l.quantity)
    //     )
    //     FROM Customer c
    //     JOIN Order o ON c.customerKey = o.customer.customerKey
    //     JOIN LineItem l ON o.orderKey = l.orderKey
    //     WHERE o.orderKey IN (
    //         SELECT l2.orderKey
    //         FROM LineItem l2
    //         GROUP BY l2.orderKey
    //         HAVING SUM(l2.quantity) > :quantity
    //     )
    //     GROUP BY c.name, c.customerKey, o.orderKey, o.orderDate, o.totalPrice
    //     ORDER BY o.totalPrice DESC, o.orderDate ASC
    // """)
    // // Q18
    // List<LargeVolumeCustomerReport> findLargeVolumeCustomers(
    //     @Param("quantity") BigDecimal quantity,
    //     Pageable pageable);

    @Query("""
        SELECT new com.restq.api_http.DTO.tpch.GlobalSalesOpportunityReport(
            SUBSTRING(c.phone, 1, 2),
            COUNT(*),
            SUM(c.accountBalance)
        )
        FROM Customer c
        WHERE SUBSTRING(c.phone, 1, 2) IN :countryCodes
        AND c.accountBalance > (
            SELECT AVG(c2.accountBalance)
            FROM Customer c2
            WHERE c2.accountBalance > 0.00
            AND SUBSTRING(c2.phone, 1, 2) IN :countryCodes
        )
        AND NOT EXISTS (
            SELECT 1
            FROM Order o
            WHERE o.customer.customerKey = c.customerKey
        )
        GROUP BY SUBSTRING(c.phone, 1, 2)
        ORDER BY SUBSTRING(c.phone, 1, 2)
    """)
    // Q22
    List<GlobalSalesOpportunityReport> findGlobalSalesOpportunities(
        @Param("countryCodes") List<String> countryCodes);
} 