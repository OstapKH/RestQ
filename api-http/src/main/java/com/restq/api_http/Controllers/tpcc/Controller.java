package com.restq.api_http.Controllers.tpcc;

import com.restq.api_http.DTO.tpcc.*;
import com.restq.api_http.Services.tpcc.TransactionService;
import com.restq.api_http.Services.tpcc.ServiceModels.*;
import com.restq.api_http.utils.TpccUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tpcc")
@CrossOrigin(origins = "*")
@ConditionalOnProperty(name = "benchmark.type", havingValue = "TPCC", matchIfMissing = false)
public class Controller {

    @Autowired
    private TransactionService tpccService;

    private final Random random = new Random();

    /**
     * TPC-C New Order Transaction
     * Creates a new order with 5-15 order lines
     */
    @PostMapping("/new-order")
    public ResponseEntity<NewOrderResponse> newOrder(@Valid @RequestBody NewOrderRequest request) {
        try {
            // Convert DTO to service request
            NewOrderRequest serviceRequest = new NewOrderRequest();
            serviceRequest.setWarehouseId(request.getWarehouseId());
            serviceRequest.setDistrictId(request.getDistrictId());
            serviceRequest.setCustomerId(request.getCustomerId());

            List<OrderLineRequest> orderLines = request.getOrderLines().stream()
                .map(ol -> new OrderLineRequest(ol.getItemId(), ol.getSupplierWarehouseId(), ol.getQuantity()))
                .collect(Collectors.toList());
            serviceRequest.setOrderLines(orderLines);

            // Execute transaction
            NewOrderResult result = tpccService.newOrderTransaction(serviceRequest);

            // Convert result to DTO
            List<NewOrderResponse.OrderLineInfo> orderLineInfos = result.getOrderLines().stream()
                .map(ol -> new NewOrderResponse.OrderLineInfo(
                    ol.getItemId(), ol.getItemName(), ol.getSupplierWarehouseId(),
                    ol.getQuantity(), ol.getItemPrice(), ol.getLineAmount(), ol.getStockQuantity()))
                .collect(Collectors.toList());

            NewOrderResponse response = new NewOrderResponse(
                result.getWarehouseId(), result.getDistrictId(), result.getCustomerId(),
                result.getOrderId(), result.getOrderDate(), result.getTotalAmount(),
                result.getCustomerLastName(), result.getCustomerCredit(),
                result.getCustomerDiscount(), result.getWarehouseTax(),
                result.getDistrictTax(), orderLineInfos);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * TPC-C Payment Transaction
     * Processes customer payment to warehouse/district
     */
    @PostMapping("/payment")
    public ResponseEntity<PaymentResponse> payment(@Valid @RequestBody PaymentRequest request) {
        try {
            // Convert DTO to service request
            PaymentRequest serviceRequest = new PaymentRequest();
            serviceRequest.setWarehouseId(request.getWarehouseId());
            serviceRequest.setDistrictId(request.getDistrictId());
            serviceRequest.setCustomerId(request.getCustomerId());
            serviceRequest.setCustomerLastName(request.getCustomerLastName());
            serviceRequest.setPaymentAmount(request.getPaymentAmount());

            // Execute transaction
            PaymentResult result = tpccService.paymentTransaction(serviceRequest);

            // Convert result to DTO
            PaymentResponse response = new PaymentResponse(
                result.getWarehouse().getWarehouseId(),
                result.getWarehouse().getName(),
                formatWarehouseAddress(result.getWarehouse()),
                result.getDistrict().getDistrictId(),
                result.getDistrict().getName(),
                formatDistrictAddress(result.getDistrict()),
                result.getCustomer().getCustomerId(),
                result.getCustomer().getFirstName(),
                result.getCustomer().getMiddleName(),
                result.getCustomer().getLastName(),
                result.getCustomer().getBalance(),
                result.getCustomer().getCredit(),
                result.getPaymentAmount(),
                result.getPaymentDate());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * TPC-C Order Status Transaction
     * Queries customer order status and order line details
     */
    @GetMapping("/order-status")
    public ResponseEntity<OrderStatusResponse> orderStatus(
            @RequestParam Integer warehouseId,
            @RequestParam Integer districtId,
            @RequestParam(required = false) Integer customerId,
            @RequestParam(required = false) String customerLastName) {
        try {
            // Validate parameters
            if (customerId == null && customerLastName == null) {
                return ResponseEntity.badRequest().build();
            }

            // Convert to service request
            OrderStatusRequest serviceRequest = new OrderStatusRequest();
            serviceRequest.setWarehouseId(warehouseId);
            serviceRequest.setDistrictId(districtId);
            serviceRequest.setCustomerId(customerId);
            serviceRequest.setCustomerLastName(customerLastName);

            // Execute transaction
            OrderStatusResult result = tpccService.orderStatusTransaction(serviceRequest);

            // Convert result to DTO
            List<OrderStatusResponse.OrderLineInfo> orderLineInfos = result.getOrderLines().stream()
                .map(ol -> new OrderStatusResponse.OrderLineInfo(
                    ol.getLineNumber(), ol.getItemId(), ol.getSupplyWarehouseId(),
                    ol.getQuantity(), ol.getAmount(), ol.getDeliveryDate()))
                .collect(Collectors.toList());

            OrderStatusResponse response = new OrderStatusResponse(
                result.getCustomer().getCustomerId(),
                result.getCustomer().getFirstName(),
                result.getCustomer().getMiddleName(),
                result.getCustomer().getLastName(),
                result.getCustomer().getBalance(),
                result.getOrder().getOrderId(),
                result.getOrder().getEntryDate(),
                result.getOrder().getCarrierId(),
                orderLineInfos);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * TPC-C Delivery Transaction
     * Batch delivery of oldest new orders per district
     */
    @PostMapping("/delivery")
    public ResponseEntity<DeliveryResponse> delivery(@Valid @RequestBody DeliveryRequest request) {
        try {
            // Convert DTO to service request
            DeliveryRequest serviceRequest = new DeliveryRequest();
            serviceRequest.setWarehouseId(request.getWarehouseId());
            serviceRequest.setCarrierId(request.getCarrierId());

            // Execute transaction
            DeliveryResult result = tpccService.deliveryTransaction(serviceRequest);

            // Convert result to DTO
            List<DeliveryResponse.DeliveredOrder> deliveredOrders = result.getDeliveredOrders().stream()
                .map(order -> new DeliveryResponse.DeliveredOrder(
                    order.getDistrictId(), order.getOrderId(), order.getCustomerId(), order.getTotalAmount()))
                .collect(Collectors.toList());

            DeliveryResponse response = new DeliveryResponse(
                result.getWarehouseId(),
                result.getCarrierId(),
                result.getDeliveryDate(),
                deliveredOrders,
                result.getSkippedDistricts());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * TPC-C Stock Level Transaction
     * Counts low stock items in recent orders
     */
    @GetMapping("/stock-level")
    public ResponseEntity<StockLevelResponse> stockLevel(
            @RequestParam Integer warehouseId,
            @RequestParam Integer districtId,
            @RequestParam Integer threshold) {
        try {
            // Convert to service request
            StockLevelRequest serviceRequest = new StockLevelRequest();
            serviceRequest.setWarehouseId(warehouseId);
            serviceRequest.setDistrictId(districtId);
            serviceRequest.setThreshold(threshold);

            // Execute transaction
            StockLevelResult result = tpccService.stockLevelTransaction(serviceRequest);

            // Convert result to DTO
            StockLevelResponse response = new StockLevelResponse(
                result.getWarehouseId(),
                result.getDistrictId(),
                result.getThreshold(),
                result.getLowStockCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate random TPC-C New Order transaction parameters
     * Useful for benchmarking
     */
    @GetMapping("/generate/new-order")
    public ResponseEntity<NewOrderRequest> generateNewOrder(
            @RequestParam(defaultValue = "1") Integer warehouseId) {
        try {
            Integer districtId = TpccUtil.randomNumber(1, 10, random);
            Integer customerId = TpccUtil.getCustomerID(random);

            // Generate 5-15 order lines
            int lineCount = TpccUtil.randomNumber(5, 15, random);
            List<OrderLineRequest> orderLines = new java.util.ArrayList<>();

            for (int i = 0; i < lineCount; i++) {
                Integer itemId = TpccUtil.getItemID(random);
                Integer supplierWarehouseId = warehouseId; // Usually local warehouse
                Integer quantity = TpccUtil.randomNumber(1, 10, random);

                orderLines.add(new OrderLineRequest(itemId, supplierWarehouseId, quantity));
            }

            NewOrderRequest request = new NewOrderRequest(warehouseId, districtId, customerId, orderLines);
            return ResponseEntity.ok(request);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Generate random TPC-C Payment transaction parameters
     */
    @GetMapping("/generate/payment")
    public ResponseEntity<PaymentRequest> generatePayment(
            @RequestParam(defaultValue = "1") Integer warehouseId) {
        try {
            Integer districtId = TpccUtil.randomNumber(1, 10, random);
            Integer customerId = TpccUtil.getCustomerID(random);
            BigDecimal paymentAmount = BigDecimal.valueOf(TpccUtil.getPaymentAmount(random));

            PaymentRequest request = new PaymentRequest(warehouseId, districtId, customerId, null, paymentAmount);
            return ResponseEntity.ok(request);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Helper methods
    private String formatWarehouseAddress(com.restq.core.Models.tpcc.Warehouse.Warehouse warehouse) {
        return String.format("%s, %s, %s %s", 
            warehouse.getStreet1(), warehouse.getStreet2(), 
            warehouse.getCity(), warehouse.getZip());
    }

    private String formatDistrictAddress(com.restq.core.Models.tpcc.District.District district) {
        return String.format("%s, %s, %s %s", 
            district.getStreet1(), district.getStreet2(), 
            district.getCity(), district.getZip());
    }
} 
