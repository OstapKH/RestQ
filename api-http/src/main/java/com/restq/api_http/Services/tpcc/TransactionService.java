package com.restq.api_http.Services.tpcc;

import com.restq.api_http.Repositories.tpcc.*;
import com.restq.api_http.Services.tpcc.ServiceModels.*;
import com.restq.core.Models.tpcc.Customer.Customer;
import com.restq.core.Models.tpcc.District.District;
import com.restq.core.Models.tpcc.History.History;
import com.restq.core.Models.tpcc.NewOrder.NewOrder;
import com.restq.core.Models.tpcc.Order.Order;
import com.restq.core.Models.tpcc.OrderLine.OrderLine;
import com.restq.core.Models.tpcc.Warehouse.Warehouse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionService {

    @Autowired
    private WarehouseRepository warehouseRepository;
    
    @Autowired
    private DistrictRepository districtRepository;
    
    @Autowired
    private CustomerRepository customerRepository;
    
    @Autowired
    private ItemRepository itemRepository;
    
    @Autowired
    private StockRepository stockRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private OrderLineRepository orderLineRepository;
    
    @Autowired
    private NewOrderRepository newOrderRepository;
    
    @Autowired
    private HistoryRepository historyRepository;

    // New Order Transaction
    @Transactional
    public NewOrderResult newOrderTransaction(NewOrderRequest request) {
        
        // Get customer info
        Object[] customerInfo = customerRepository.getCustomerDiscountLastCredit(
            request.getWarehouseId(), request.getDistrictId(), request.getCustomerId());
        BigDecimal customerDiscount = (BigDecimal) customerInfo[0];
        String customerLastName = (String) customerInfo[1];
        String customerCredit = (String) customerInfo[2];

        // Get warehouse tax
        BigDecimal warehouseTax = warehouseRepository.getWarehouseTax(request.getWarehouseId());

        // Get district and increment next order ID
        District district = districtRepository.findByWarehouseIdAndDistrictIdForUpdate(
            request.getWarehouseId(), request.getDistrictId());
        int nextOrderId = district.getNextOrderId();
        BigDecimal districtTax = district.getTax();
        districtRepository.incrementNextOrderId(request.getWarehouseId(), request.getDistrictId());

        // Create order
        Order order = new Order();
        order.setWarehouseId(request.getWarehouseId());
        order.setDistrictId(request.getDistrictId());
        order.setOrderId(nextOrderId);
        order.setCustomerId(request.getCustomerId());
        order.setEntryDate(LocalDateTime.now());
        order.setOrderLineCount(request.getOrderLines().size());
        order.setAllLocal(request.getOrderLines().stream()
            .allMatch(ol -> ol.getSupplierWarehouseId().equals(request.getWarehouseId())) ? 1 : 0);
        orderRepository.save(order);

        // Create new order
        NewOrder newOrder = new NewOrder();
        newOrder.setWarehouseId(request.getWarehouseId());
        newOrder.setDistrictId(request.getDistrictId());
        newOrder.setOrderId(nextOrderId);
        newOrderRepository.save(newOrder);

        // Process order lines
        List<OrderLineResult> orderLineResults = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (int i = 0; i < request.getOrderLines().size(); i++) {
            OrderLineRequest olRequest = request.getOrderLines().get(i);
            
            // Get item info
            Object[] itemInfo = itemRepository.getItemPriceNameData(olRequest.getItemId());
            BigDecimal itemPrice = (BigDecimal) itemInfo[0];
            String itemName = (String) itemInfo[1];

            // Get and update stock
            Object[] stockInfo = stockRepository.getStockQuantityAndDistData(
                olRequest.getItemId(), olRequest.getSupplierWarehouseId());
            Integer stockQuantity = (Integer) stockInfo[0];
            String distInfo = (String) stockInfo[request.getDistrictId() + 1];

            // Calculate new stock quantity
            int newQuantity = stockQuantity >= olRequest.getQuantity() ? 
                stockQuantity - olRequest.getQuantity() : 
                stockQuantity + 91 - olRequest.getQuantity();

            // Update stock
            stockRepository.updateStock(olRequest.getItemId(), olRequest.getSupplierWarehouseId(), 
                newQuantity, olRequest.getQuantity(), 
                olRequest.getSupplierWarehouseId().equals(request.getWarehouseId()) ? 0 : 1);

            // Calculate line amount
            BigDecimal lineAmount = itemPrice
                .multiply(BigDecimal.valueOf(olRequest.getQuantity()))
                .multiply(BigDecimal.ONE.subtract(customerDiscount))
                .multiply(BigDecimal.ONE.add(warehouseTax).add(districtTax));

            totalAmount = totalAmount.add(lineAmount);

            // Create order line
            OrderLine orderLine = new OrderLine();
            orderLine.setWarehouseId(request.getWarehouseId());
            orderLine.setDistrictId(request.getDistrictId());
            orderLine.setOrderId(nextOrderId);
            orderLine.setLineNumber(i + 1);
            orderLine.setItemId(olRequest.getItemId());
            orderLine.setSupplyWarehouseId(olRequest.getSupplierWarehouseId());
            orderLine.setQuantity(BigDecimal.valueOf(olRequest.getQuantity()));
            orderLine.setAmount(lineAmount);
            orderLine.setDistInfo(distInfo);
            orderLineRepository.save(orderLine);

            orderLineResults.add(new OrderLineResult(olRequest.getItemId(), itemName, 
                olRequest.getSupplierWarehouseId(), BigDecimal.valueOf(olRequest.getQuantity()), 
                itemPrice, lineAmount, newQuantity));
        }

        return new NewOrderResult(request.getWarehouseId(), request.getDistrictId(), 
            request.getCustomerId(), nextOrderId, LocalDateTime.now(), totalAmount, 
            customerLastName, customerCredit, customerDiscount, warehouseTax, 
            districtTax, orderLineResults);
    }

    // Payment Transaction
    @Transactional
    public PaymentResult paymentTransaction(PaymentRequest request) {
        
        // Update warehouse
        warehouseRepository.updateYearToDateBalance(request.getWarehouseId(), request.getPaymentAmount());
        Warehouse warehouse = warehouseRepository.findByWarehouseId(request.getWarehouseId());

        // Update district
        districtRepository.updateYearToDateBalance(request.getWarehouseId(), request.getDistrictId(), request.getPaymentAmount());
        District district = districtRepository.findByWarehouseIdAndDistrictId(request.getWarehouseId(), request.getDistrictId());

        // Get customer
        Customer customer;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findByWarehouseIdAndDistrictIdAndCustomerId(
                request.getWarehouseId(), request.getDistrictId(), request.getCustomerId());
        } else {
            List<Customer> customers = customerRepository.findByWarehouseIdAndDistrictIdAndLastNameOrderByFirstName(
                request.getWarehouseId(), request.getDistrictId(), request.getCustomerLastName());
            if (customers.isEmpty()) {
                throw new RuntimeException("Customer not found");
            }
            customer = customers.get(customers.size() / 2);
        }

        // Update customer
        BigDecimal newBalance = customer.getBalance().subtract(request.getPaymentAmount());
        Float newYtdPayment = customer.getYearToDatePayment().floatValue() + request.getPaymentAmount().floatValue();
        Integer newPaymentCount = customer.getPaymentCount() + 1;

        if ("BC".equals(customer.getCredit())) {
            String oldData = customerRepository.getCustomerData(
                customer.getWarehouseId(), customer.getDistrictId(), customer.getCustomerId());
            String newData = String.format("%d %d %d %d %d %.2f | %s", 
                customer.getCustomerId(), customer.getDistrictId(), customer.getWarehouseId(),
                request.getDistrictId(), request.getWarehouseId(), request.getPaymentAmount().doubleValue(), 
                oldData.length() > 450 ? oldData.substring(0, 450) : oldData);
            
            customerRepository.updateCustomerBalanceAndData(customer.getWarehouseId(), 
                customer.getDistrictId(), customer.getCustomerId(), newBalance, newYtdPayment, 
                newPaymentCount, newData);
        } else {
            customerRepository.updateCustomerBalance(customer.getWarehouseId(), customer.getDistrictId(), 
                customer.getCustomerId(), newBalance, newYtdPayment, newPaymentCount);
        }

        // Create history
        History history = new History();
        history.setCustomerWarehouseId(customer.getWarehouseId());
        history.setCustomerDistrictId(customer.getDistrictId());
        history.setCustomerId(customer.getCustomerId());
        history.setWarehouseId(request.getWarehouseId());
        history.setDistrictId(request.getDistrictId());
        history.setDate(LocalDateTime.now());
        history.setAmount(request.getPaymentAmount());
        history.setData(warehouse.getName() + "    " + district.getName());
        historyRepository.save(history);

        return new PaymentResult(warehouse, district, customer, request.getPaymentAmount(), LocalDateTime.now());
    }

    // Order Status Transaction
    @Transactional(readOnly = true)
    public OrderStatusResult orderStatusTransaction(OrderStatusRequest request) {
        
        // Get customer
        Customer customer;
        if (request.getCustomerId() != null) {
            customer = customerRepository.findByWarehouseIdAndDistrictIdAndCustomerId(
                request.getWarehouseId(), request.getDistrictId(), request.getCustomerId());
        } else {
            List<Customer> customers = customerRepository.findByWarehouseIdAndDistrictIdAndLastNameOrderByFirstName(
                request.getWarehouseId(), request.getDistrictId(), request.getCustomerLastName());
            if (customers.isEmpty()) {
                throw new RuntimeException("Customer not found");
            }
            customer = customers.get(customers.size() / 2);
        }

        // Get latest order
        List<Order> orders = orderRepository.findLatestOrderByCustomer(
            customer.getWarehouseId(), customer.getDistrictId(), customer.getCustomerId());
        if (orders.isEmpty()) {
            throw new RuntimeException("No orders found");
        }
        Order latestOrder = orders.get(0);

        // Get order lines
        List<OrderLine> orderLines = orderLineRepository.findByOrderIdOrderByLineNumber(
            latestOrder.getWarehouseId(), latestOrder.getDistrictId(), latestOrder.getOrderId());

        return new OrderStatusResult(customer, latestOrder, orderLines);
    }

    // Delivery Transaction
    @Transactional
    public DeliveryResult deliveryTransaction(DeliveryRequest request) {
        
        List<DeliveredOrderInfo> deliveredOrders = new ArrayList<>();
        List<Integer> skippedDistricts = new ArrayList<>();
        LocalDateTime deliveryDate = LocalDateTime.now();

        for (int districtId = 1; districtId <= 10; districtId++) {
            List<NewOrder> newOrders = newOrderRepository.findOldestNewOrder(request.getWarehouseId(), districtId);
            
            if (newOrders.isEmpty()) {
                skippedDistricts.add(districtId);
                continue;
            }

            NewOrder oldestNewOrder = newOrders.get(0);
            int orderId = oldestNewOrder.getOrderId();

            newOrderRepository.deleteByWarehouseIdAndDistrictIdAndOrderId(request.getWarehouseId(), districtId, orderId);
            orderRepository.updateCarrierId(request.getWarehouseId(), districtId, orderId, request.getCarrierId());
            orderLineRepository.updateDeliveryDate(request.getWarehouseId(), districtId, orderId, deliveryDate);

            BigDecimal totalAmount = orderLineRepository.getTotalAmountForOrder(request.getWarehouseId(), districtId, orderId);
            
            List<Order> orders = orderRepository.findLatestOrderByCustomer(request.getWarehouseId(), districtId, orderId);
            if (!orders.isEmpty()) {
                Order order = orders.get(0);
                customerRepository.updateCustomerDelivery(request.getWarehouseId(), districtId, order.getCustomerId(), totalAmount);
                deliveredOrders.add(new DeliveredOrderInfo(districtId, orderId, order.getCustomerId(), totalAmount));
            }
        }

        return new DeliveryResult(request.getWarehouseId(), request.getCarrierId(), deliveryDate, deliveredOrders, skippedDistricts);
    }

    // Stock Level Transaction
    @Transactional(readOnly = true)
    public StockLevelResult stockLevelTransaction(StockLevelRequest request) {
        
        District district = districtRepository.findByWarehouseIdAndDistrictId(request.getWarehouseId(), request.getDistrictId());
        int nextOrderId = district.getNextOrderId();
        int minOrderId = Math.max(1, nextOrderId - 20);

        Long lowStockCount = stockRepository.countLowStockItems(request.getWarehouseId(), request.getDistrictId(), minOrderId, nextOrderId, request.getThreshold());

        return new StockLevelResult(request.getWarehouseId(), request.getDistrictId(), request.getThreshold(), lowStockCount);
    }
} 
