package com.restq.api_http.Benchmark.ParameterFinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartParameterFinderQ6 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ6.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Concurrency settings
    private static final int THREAD_POOL_SIZE = 8;
    private static final int BATCH_SIZE = 25;
    
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ArrayNode results = mapper.createArrayNode();
    private static AtomicInteger foundCombinations = new AtomicInteger(0);
    private static AtomicInteger processedCombinations = new AtomicInteger(0);
    
    public static void main(String[] args) {
        String outputFile = "q6_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search for Q6 (revenue-increase)...");
        
        try {
            findValidCombinationsQ6(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsQ6(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible parameter values from database...");
        
        Q6ParameterValues paramValues = getQ6ParameterValues();
        
        logger.info("Found {} discounts, {} quantities, {} start dates", 
            paramValues.discounts.size(), paramValues.quantities.size(), paramValues.startDates.size());
        
        int totalCombinations = paramValues.discounts.size() * paramValues.quantities.size() * paramValues.startDates.size();
        logger.info("Total combinations to test: {}", totalCombinations);
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testQ6CombinationsConcurrently(paramValues, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ6Results(paramValues, outputFile);
        
        logger.info("Q6 search completed! Found {} valid combinations out of {} total", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static Q6ParameterValues getQ6ParameterValues() throws SQLException {
        List<Double> discounts = new ArrayList<>();
        List<Integer> quantities = new ArrayList<>();
        List<LocalDate> startDates = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get sample discount values (round to 2 decimal places to match the BETWEEN logic)
            String discountsSQL = """
                SELECT DISTINCT ROUND(CAST(l_discount AS numeric), 2) as discount_value
                FROM lineitem 
                WHERE l_discount > 0
                ORDER BY discount_value
                """;
            try (PreparedStatement stmt = conn.prepareStatement(discountsSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    discounts.add(rs.getDouble("discount_value"));
                }
            }
            
            // Sample discounts if too many
            if (discounts.size() > 20) {
                List<Double> sampledDiscounts = new ArrayList<>();
                int step = Math.max(1, discounts.size() / 20);
                for (int i = 0; i < discounts.size(); i += step) {
                    sampledDiscounts.add(discounts.get(i));
                }
                discounts = sampledDiscounts;
            }
            
            // Get sample quantity values
            String quantitiesSQL = """
                SELECT DISTINCT l_quantity::integer as quantity_value
                FROM lineitem 
                WHERE l_quantity > 0
                ORDER BY quantity_value
                """;
            try (PreparedStatement stmt = conn.prepareStatement(quantitiesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    quantities.add(rs.getInt("quantity_value"));
                }
            }
            
            // Sample quantities if too many
            if (quantities.size() > 25) {
                List<Integer> sampledQuantities = new ArrayList<>();
                int step = Math.max(1, quantities.size() / 25);
                for (int i = 0; i < quantities.size(); i += step) {
                    sampledQuantities.add(quantities.get(i));
                }
                quantities = sampledQuantities;
            }
            
            // Get candidate start dates (monthly sampling)
            String datesSQL = """
                SELECT DISTINCT DATE_TRUNC('month', l_shipdate)::date as start_date
                FROM lineitem 
                WHERE l_shipdate >= '1992-01-01' AND l_shipdate <= '1997-12-01'
                ORDER BY start_date
                """;
                
            try (PreparedStatement stmt = conn.prepareStatement(datesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate startDate = rs.getDate("start_date").toLocalDate();
                    // Only include dates that leave room for 1-year window
                    if (!startDate.isAfter(LocalDate.of(1997, 12, 1))) {
                        startDates.add(startDate);
                    }
                }
            }
            
            // Sample dates if too many
            if (startDates.size() > 30) {
                List<LocalDate> sampledDates = new ArrayList<>();
                int step = Math.max(1, startDates.size() / 30);
                for (int i = 0; i < startDates.size(); i += step) {
                    sampledDates.add(startDates.get(i));
                }
                startDates = sampledDates;
            }
        }
        
        return new Q6ParameterValues(discounts, quantities, startDates);
    }
    
    private static void testQ6CombinationsConcurrently(Q6ParameterValues paramValues, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q6CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        int totalCombinations = 0;
        
        // Submit all combinations for testing
        for (Double discount : paramValues.discounts) {
            for (Integer quantity : paramValues.quantities) {
                for (LocalDate startDate : paramValues.startDates) {
                    completionService.submit(new Q6CombinationTester(discount, quantity, startDate));
                    totalCombinations++;
                }
            }
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<Q6CombinationResult> future = completionService.take();
                Q6CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("discount", result.getDiscount());
                        resultNode.put("quantity", result.getQuantity());
                        resultNode.put("startDate", result.getStartDate().toString());
                        resultNode.put("endDate", result.getStartDate().plusYears(1).toString());
                        resultNode.put("revenue", result.getRevenue().doubleValue());
                        resultNode.put("apiCall", String.format("/revenue-increase?discount=%.2f&quantity=%d&startDate=%s", 
                            result.getDiscount(), result.getQuantity(), result.getStartDate().toString()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: discount={}, quantity={}, startDate={}, revenue={}", 
                        result.getDiscount(), result.getQuantity(), result.getStartDate(), result.getRevenue());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ6IntermediateResults(outputFile, totalCombinations);
                    logger.info("Progress: {}/{} processed, {} valid combinations found", 
                        processedCombinations.get(), totalCombinations, foundCombinations.get());
                }
                
            } catch (ExecutionException e) {
                logger.error("Error processing combination: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
    
    private static void writeQ6IntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q6 - Revenue Increase");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ6Results(Q6ParameterValues paramValues, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q6 - Revenue Increase");
        output.put("method", "smart_concurrent_testing");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalDiscounts", paramValues.discounts.size());
        paramsNode.put("totalQuantities", paramValues.quantities.size());
        paramsNode.put("totalStartDates", paramValues.startDates.size());
        
        ArrayNode discountsArray = paramsNode.putArray("discounts");
        paramValues.discounts.forEach(discountsArray::add);
        
        ArrayNode quantitiesArray = paramsNode.putArray("quantities");
        paramValues.quantities.forEach(quantitiesArray::add);
        
        ArrayNode datesArray = paramsNode.putArray("startDates");
        paramValues.startDates.forEach(date -> datesArray.add(date.toString()));
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q6CombinationTester implements Callable<Q6CombinationResult> {
        private final Double discount;
        private final Integer quantity;
        private final LocalDate startDate;
        
        public Q6CombinationTester(Double discount, Integer quantity, LocalDate startDate) {
            this.discount = discount;
            this.quantity = quantity;
            this.startDate = startDate;
        }
        
        @Override
        public Q6CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository
                LocalDate endDate = startDate.plusYears(1);
                
                String sql = """
                    SELECT SUM(l_extendedprice * l_discount) AS revenue
                    FROM lineitem l
                    WHERE l.l_shipdate >= ?
                    AND l.l_shipdate < ?
                    AND l.l_discount BETWEEN ? AND ?
                    AND l.l_quantity < ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setDate(1, Date.valueOf(startDate));
                    stmt.setDate(2, Date.valueOf(endDate));
                    stmt.setDouble(3, discount - 0.01);
                    stmt.setDouble(4, discount + 0.01);
                    stmt.setInt(5, quantity);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            BigDecimal revenue = rs.getBigDecimal("revenue");
                            if (revenue != null && revenue.compareTo(BigDecimal.ZERO) > 0) {
                                return new Q6CombinationResult(discount, quantity, startDate, revenue, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q6 combination discount={}, quantity={}, startDate={}: {}", 
                    discount, quantity, startDate, e.getMessage());
            }
            
            return new Q6CombinationResult(discount, quantity, startDate, BigDecimal.ZERO, false);
        }
    }
    
    static class Q6ParameterValues {
        final List<Double> discounts;
        final List<Integer> quantities;
        final List<LocalDate> startDates;
        
        Q6ParameterValues(List<Double> discounts, List<Integer> quantities, List<LocalDate> startDates) {
            this.discounts = discounts;
            this.quantities = quantities;
            this.startDates = startDates;
        }
    }
    
    static class Q6CombinationResult {
        private final Double discount;
        private final Integer quantity;
        private final LocalDate startDate;
        private final BigDecimal revenue;
        private final boolean valid;
        
        public Q6CombinationResult(Double discount, Integer quantity, LocalDate startDate, 
                                   BigDecimal revenue, boolean valid) {
            this.discount = discount;
            this.quantity = quantity;
            this.startDate = startDate;
            this.revenue = revenue;
            this.valid = valid;
        }
        
        // Getters
        public Double getDiscount() { return discount; }
        public Integer getQuantity() { return quantity; }
        public LocalDate getStartDate() { return startDate; }
        public BigDecimal getRevenue() { return revenue; }
        public boolean isValid() { return valid; }
    }
} 
