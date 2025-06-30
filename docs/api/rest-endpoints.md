# REST API Endpoints

This document provides a detailed overview of the REST API endpoints available in the `api-http` service. The API is divided into two main sections: TPC-C benchmark endpoints, and TPC-H benchmark endpoints. The availability of TPC-C and TPC-H endpoints depends on the `benchmark.type` property in the application configuration.

## TPC-C Benchmark Endpoints

These endpoints are available under the `/api/tpcc` base path and are active only when the `benchmark.type` property is set to `TPCC`. They correspond to the different transaction types in the TPC-C specification.

-   **`POST /api/tpcc/new-order`**
    -   **Description:** Executes a New-Order transaction. It processes a new order for a customer.
    -   **Request Body:** A JSON object representing a `NewOrderRequest`, containing `warehouseId`, `districtId`, `customerId`, and a list of `orderLines`.
    -   **Returns:** A `NewOrderResponse` object with details of the created order.

-   **`POST /api/tpcc/payment`**
    -   **Description:** Executes a Payment transaction. It processes a payment from a customer.
    -   **Request Body:** A JSON object representing a `PaymentRequest`, containing details like `warehouseId`, `districtId`, `customerId` (or `customerLastName`), and `paymentAmount`.
    -   **Returns:** A `PaymentResponse` object with details of the payment and customer data.

-   **`GET /api/tpcc/order-status`**
    -   **Description:** Executes an Order-Status transaction. It retrieves the status of a customer's last order.
    -   **Request Parameters:**
        -   `warehouseId` (integer)
        -   `districtId` (integer)
        -   `customerId` (integer, optional)
        -   `customerLastName` (string, optional)
    -   **Returns:** An `OrderStatusResponse` object with the order status details.

-   **`POST /api/tpcc/delivery`**
    -   **Description:** Executes a Delivery transaction. It processes the delivery of pending orders.
    -   **Request Body:** A `DeliveryRequest` object with `warehouseId` and `carrierId`.
    -   **Returns:** A `DeliveryResponse` object with details of the delivered orders.

-   **`GET /api/tpcc/stock-level`**
    -   **Description:** Executes a Stock-Level transaction. It checks the stock level for recent orders.
    -   **Request Parameters:**
        -   `warehouseId` (integer)
        -   `districtId` (integer)
        -   `threshold` (integer)
    -   **Returns:** A `StockLevelResponse` object with the count of items below the stock threshold.

### TPC-C Data Generation Endpoints

-   **`GET /api/tpcc/generate/new-order`**
    -   **Description:** Generates a random, valid `NewOrderRequest` payload.
    -   **Request Parameters:**
        -   `warehouseId` (integer, default: 1)
    -   **Returns:** A JSON object for a `NewOrderRequest`.

-   **`GET /api/tpcc/generate/payment`**
    -   **Description:** Generates a random, valid `PaymentRequest` payload.
    -   **Request Parameters:**
        -   `warehouseId` (integer, default: 1)
    -   **Returns:** A JSON object for a `PaymentRequest`.

## TPC-H Benchmark Endpoints

These endpoints are available under the `/api/reports` base path and are active only when `benchmark.type` is set to `TPCH`. They correspond to the 22 queries of the TPC-H specification, providing complex, read-only reports.

-   **`GET /api/reports/pricing-summary` (Q1)**
    -   **Description:** Reports the amount of business that was billed, shipped, and returned.
    -   **Request Parameters:** `shipDate` (date), `delta` (integer, optional).

-   **`GET /api/reports/supplier-part-info` (Q2)**
    -   **Description:** Finds which supplier should be selected to supply a specific part in a given region.
    -   **Request Parameters:** `size` (integer), `type` (string), `region` (string).

-   **`GET /api/reports/order-revenue-info` (Q3)**
    -   **Description:** Retrieves the unshipped orders with the highest value.
    -   **Request Parameters:** `segment` (string), `date` (date).

-   **`GET /api/reports/order-priority-count` (Q4)**
    -   **Description:** Determines how many orders have not been delivered in a timely manner.
    -   **Request Parameters:** `date` (date).

-   **`GET /api/reports/local-supplier-volume` (Q5)**
    -   **Description:** Lists the revenue volume done through local suppliers.
    -   **Request Parameters:** `region` (string), `startDate` (date).

-   **`GET /api/reports/revenue-increase` (Q6)**
    -   **Description:** Computes the revenue increase that would have resulted from eliminating certain discounts.
    -   **Request Parameters:** `discount` (double), `quantity` (integer), `startDate` (date).

-   **`GET /api/reports/nations-volume-shipping` (Q7)**
    -   **Description:** Measures the value of commerce between two nations.
    -   **Request Parameters:** `nation1`, `nation2`, `startDate`, `endDate`.

-   **`GET /api/reports/market-share` (Q8)**
    -   **Description:** Measures a nation's market share in a given region.
    -   **Request Parameters:** `nation`, `region`, `type`.

-   **`GET /api/reports/product-type-profit` (Q9)**
    -   **Description:** Determines the profit for all parts of a certain type.
    -   **Request Parameters:** `color` (string).

-   **`GET /api/reports/returned-items` (Q10)**
    -   **Description:** Identifies top customers who have returned items.
    -   **Request Parameters:** `date` (date).

-   **`GET /api/reports/important-stock` (Q11)**
    -   **Description:** Finds the most important stock items in a given nation.
    -   **Request Parameters:** `nation` (string), `fraction` (double, optional).

-   **`GET /api/reports/shipping-modes` (Q12)**
    -   **Description:** Determines whether selecting less-expensive shipping modes is negatively affecting delivery time.
    -   **Request Parameters:** `shipMode1`, `shipMode2`, `date`.

-   **`GET /api/reports/customer-distribution` (Q13)**
    -   **Description:** Finds the distribution of customers by the number of orders they have made.
    -   **Request Parameters:** `word1`, `word2` (optional strings).

-   **`GET /api/reports/promotion-revenue` (Q14)**
    -   **Description:** Monitors the revenue benefit of a promotional campaign.
    -   **Request Parameters:** `date` (date).

-   **`GET /api/reports/part-supplier-relationships` (Q16)**
    -   **Description:** Counts the number of suppliers who can supply parts with certain attributes.
    -   **Request Parameters:** `brand`, `type`, `sizes` (list of integers).

-   **`GET /api/reports/small-quantity-revenue` (Q17)**
    -   **Description:** Determines the average yearly loss in revenue if orders for small-quantity parts were no longer taken.
    -   **Request Parameters:** `brand`, `container`.

-   **`GET /api/reports/discounted-revenue` (Q19)**
    -   **Description:** Determines the gross discounted revenue for all parts shipped in a certain way.
    -   **Request Parameters:** `brand1`, `brand2`, `brand3`, `quantity1`, `quantity2`, `quantity3`.

-   **`GET /api/reports/suppliers-kept-waiting` (Q21)**
    -   **Description:** Identifies suppliers who were not able to ship parts in a timely manner.
    -   **Request Parameters:** `nation` (string).

-   **`GET /api/reports/global-sales-opportunities` (Q22)**
    -   **Description:** Identifies global sales opportunities by finding customers in certain countries with high account balances.
    -   **Request Parameters:** `countryCodes` (list of strings).
