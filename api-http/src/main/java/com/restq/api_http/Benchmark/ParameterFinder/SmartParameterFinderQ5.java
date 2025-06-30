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

public class SmartParameterFinderQ5 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ5.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Concurrency settings
    private static final int THREAD_POOL_SIZE = 8;
    private static final int BATCH_SIZE = 10;
    
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ArrayNode results = mapper.createArrayNode();
    private static AtomicInteger foundCombinations = new AtomicInteger(0);
    private static AtomicInteger processedCombinations = new AtomicInteger(0);
    
    public static void main(String[] args) {
        String outputFile = "q5_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search for Q5 (local-supplier-volume)...");
        
        try {
            findValidCombinationsQ5(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsQ5(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible parameter values from database...");
        
        Q5ParameterValues paramValues = getQ5ParameterValues();
        
        logger.info("Found {} regions, {} start dates", 
            paramValues.regions.size(), paramValues.startDates.size());
        
        int totalCombinations = paramValues.regions.size() * paramValues.startDates.size();
        logger.info("Total combinations to test: {}", totalCombinations);
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testQ5CombinationsConcurrently(paramValues, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ5Results(paramValues, outputFile);
        
        logger.info("Q5 search completed! Found {} valid combinations out of {} total", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static Q5ParameterValues getQ5ParameterValues() throws SQLException {
        List<String> regions = new ArrayList<>();
        List<LocalDate> startDates = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get all distinct regions
            String regionsSQL = "SELECT DISTINCT r_name FROM region ORDER BY r_name";
            try (PreparedStatement stmt = conn.prepareStatement(regionsSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    regions.add(rs.getString("r_name"));
                }
            }
            
            // Get candidate start dates (monthly sampling from order dates)
            // The query uses: o.orderDate >= :startDate AND o.orderDate < :endDate (where endDate = startDate + 1 year)
            String datesSQL = """
                SELECT DISTINCT DATE_TRUNC('month', o_orderdate)::date as start_date
                FROM orders 
                WHERE o_orderdate >= '1992-01-01' AND o_orderdate <= '1997-12-01'
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
            
            // Sample dates if too many (keep it reasonable)
            if (startDates.size() > 50) {
                List<LocalDate> sampledDates = new ArrayList<>();
                int step = Math.max(1, startDates.size() / 50);
                for (int i = 0; i < startDates.size(); i += step) {
                    sampledDates.add(startDates.get(i));
                }
                startDates = sampledDates;
                logger.info("Sampled {} dates from {} total for efficiency", sampledDates.size(), startDates.size());
            }
        }
        
        return new Q5ParameterValues(regions, startDates);
    }
    
    private static void testQ5CombinationsConcurrently(Q5ParameterValues paramValues, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q5CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        int totalCombinations = 0;
        
        // Submit all combinations for testing
        for (String region : paramValues.regions) {
            for (LocalDate startDate : paramValues.startDates) {
                completionService.submit(new Q5CombinationTester(region, startDate));
                totalCombinations++;
            }
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<Q5CombinationResult> future = completionService.take();
                Q5CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("region", result.getRegion());
                        resultNode.put("startDate", result.getStartDate().toString());
                        resultNode.put("endDate", result.getStartDate().plusYears(1).toString());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("totalVolume", result.getTotalVolume().doubleValue());
                        resultNode.put("apiCall", String.format("/local-supplier-volume?region=%s&startDate=%s", 
                            result.getRegion(), result.getStartDate().toString()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: region={}, startDate={}, records={}, volume={}", 
                        result.getRegion(), result.getStartDate(), result.getRecordCount(), result.getTotalVolume());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ5IntermediateResults(outputFile, totalCombinations);
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
    
    private static void writeQ5IntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q5 - Local Supplier Volume");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ5Results(Q5ParameterValues paramValues, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q5 - Local Supplier Volume");
        output.put("method", "smart_concurrent_testing");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalRegions", paramValues.regions.size());
        paramsNode.put("totalStartDates", paramValues.startDates.size());
        
        ArrayNode regionsArray = paramsNode.putArray("regions");
        paramValues.regions.forEach(regionsArray::add);
        
        ArrayNode datesArray = paramsNode.putArray("startDates");
        paramValues.startDates.forEach(date -> datesArray.add(date.toString()));
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q5CombinationTester implements Callable<Q5CombinationResult> {
        private final String region;
        private final LocalDate startDate;
        
        public Q5CombinationTester(String region, LocalDate startDate) {
            this.region = region;
            this.startDate = startDate;
        }
        
        @Override
        public Q5CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository but with counts and total volume
                LocalDate endDate = startDate.plusYears(1);
                
                String sql = """
                    SELECT 
                        COUNT(*) as record_count,
                        COALESCE(SUM(li.l_extendedprice * (1 - li.l_discount)), 0) as total_volume
                    FROM customer c, orders o, lineitem li, supplier s, nation n, region r
                    WHERE c.c_custkey = o.o_custkey
                    AND li.l_orderkey = o.o_orderkey
                    AND li.l_suppkey = s.s_suppkey
                    AND c.c_nationkey = s.s_nationkey
                    AND s.s_nationkey = n.n_nationkey
                    AND n.n_regionkey = r.r_regionkey
                    AND r.r_name = ?
                    AND o.o_orderdate >= ?
                    AND o.o_orderdate < ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, region);
                    stmt.setDate(2, Date.valueOf(startDate));
                    stmt.setDate(3, Date.valueOf(endDate));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            BigDecimal totalVolume = rs.getBigDecimal("total_volume");
                            
                            if (recordCount > 0 && totalVolume != null && totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                                return new Q5CombinationResult(region, startDate, recordCount, totalVolume, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q5 combination region={}, startDate={}: {}", 
                    region, startDate, e.getMessage());
            }
            
            return new Q5CombinationResult(region, startDate, 0, BigDecimal.ZERO, false);
        }
    }
    
    static class Q5ParameterValues {
        final List<String> regions;
        final List<LocalDate> startDates;
        
        Q5ParameterValues(List<String> regions, List<LocalDate> startDates) {
            this.regions = regions;
            this.startDates = startDates;
        }
    }
    
    static class Q5CombinationResult {
        private final String region;
        private final LocalDate startDate;
        private final int recordCount;
        private final BigDecimal totalVolume;
        private final boolean valid;
        
        public Q5CombinationResult(String region, LocalDate startDate, int recordCount, 
                                   BigDecimal totalVolume, boolean valid) {
            this.region = region;
            this.startDate = startDate;
            this.recordCount = recordCount;
            this.totalVolume = totalVolume;
            this.valid = valid;
        }
        
        // Getters
        public String getRegion() { return region; }
        public LocalDate getStartDate() { return startDate; }
        public int getRecordCount() { return recordCount; }
        public BigDecimal getTotalVolume() { return totalVolume; }
        public boolean isValid() { return valid; }
    }
}
