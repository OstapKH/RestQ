package com.restq.api_http.Repositories.tpcc;

import com.restq.core.Models.tpcc.Customer.Customer;
import com.restq.core.Models.tpcc.Customer.CustomerId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface TpccCustomerRepository extends JpaRepository<Customer, CustomerId> {

    // For New Order transaction - get customer discount, last name, credit
    @Query("SELECT c.discount, c.lastName, c.credit FROM Customer c WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId")
    Object[] getCustomerDiscountLastCredit(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("customerId") Integer customerId);

    // For Payment transaction - get customer by ID
    @Query("SELECT c FROM Customer c WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId")
    Customer findByWarehouseIdAndDistrictIdAndCustomerId(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("customerId") Integer customerId);

    // For Payment transaction - get customers by last name (sorted by first name)
    @Query("SELECT c FROM Customer c WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.lastName = :lastName ORDER BY c.firstName")
    List<Customer> findByWarehouseIdAndDistrictIdAndLastNameOrderByFirstName(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("lastName") String lastName);

    // For Payment transaction - get customer data
    @Query("SELECT c.data FROM Customer c WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId")
    String getCustomerData(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, @Param("customerId") Integer customerId);

    // For Payment transaction - update customer balance (good credit)
    @Modifying
    @Query("""
        UPDATE Customer c SET 
        c.balance = :balance,
        c.yearToDatePayment = :ytdPayment,
        c.paymentCount = :paymentCount
        WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId
    """)
    void updateCustomerBalance(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, 
                              @Param("customerId") Integer customerId, @Param("balance") BigDecimal balance, 
                              @Param("ytdPayment") Float ytdPayment, @Param("paymentCount") Integer paymentCount);

    // For Payment transaction - update customer balance and data (bad credit)
    @Modifying
    @Query("""
        UPDATE Customer c SET 
        c.balance = :balance,
        c.yearToDatePayment = :ytdPayment,
        c.paymentCount = :paymentCount,
        c.data = :data
        WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId
    """)
    void updateCustomerBalanceAndData(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, 
                                     @Param("customerId") Integer customerId, @Param("balance") BigDecimal balance, 
                                     @Param("ytdPayment") Float ytdPayment, @Param("paymentCount") Integer paymentCount, 
                                     @Param("data") String data);

    // For Delivery transaction - update customer balance and delivery count
    @Modifying
    @Query("""
        UPDATE Customer c SET 
        c.balance = c.balance + :amount,
        c.deliveryCount = c.deliveryCount + 1
        WHERE c.warehouseId = :warehouseId AND c.districtId = :districtId AND c.customerId = :customerId
    """)
    void updateCustomerDelivery(@Param("warehouseId") Integer warehouseId, @Param("districtId") Integer districtId, 
                               @Param("customerId") Integer customerId, @Param("amount") BigDecimal amount);
} 
