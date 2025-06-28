package com.restq.api_http.Benchmark.ParameterFinder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ParameterCombinationFinder {
    
    private static final Logger logger = LoggerFactory.getLogger(ParameterCombinationFinder.class);
    
    // Database configuration - adjust these for your setup
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Parameters
    private static final int MIN_DELTA = 60;
    private static final int MAX_DELTA = 120;
    private static final LocalDate START_DATE = LocalDate.of(1992, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(1998, 9, 1); // Leave room for delta
    
    // Concurrency settings
    private static final int THREAD_POOL_SIZE = 8;
    private static final int BATCH_SIZE = 100; // Write to file every 100 results
    
    // JSON handling
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ArrayNode results = mapper.createArrayNode();
    private static AtomicInteger foundCombinations = new AtomicInteger(0);
    private static AtomicInteger processedCombinations = new AtomicInteger(0);
    
    public static void main(String[] args) {
        String outputFile = "valid_parameter_combinations.json";
        
        logger.info("Starting parameter combination search...");
        logger.info("Date range: {} to {}", START_DATE, END_DATE);
        logger.info("Delta range: {} to {}", MIN_DELTA, MAX_DELTA);
        logger.info("Thread pool size: {}", THREAD_POOL_SIZE);
        
        try {
            findValidCombinations(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinations(String outputFile) throws InterruptedException, IOException {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<ParameterResult> completionService = new ExecutorCompletionService<>(executor);
        
        // Generate all combinations and submit tasks
        int totalCombinations = 0;
        LocalDate currentDate = START_DATE;
        
        while (!currentDate.isAfter(END_DATE)) {
            for (int delta = MIN_DELTA; delta <= MAX_DELTA; delta++) {
                LocalDate endDate = currentDate.plusDays(delta);
                if (!endDate.isAfter(LocalDate.of(1998, 12, 1))) {
                    completionService.submit(new CombinationTester(currentDate, delta));
                    totalCombinations++;
                }
            }
            currentDate = currentDate.plusMonths(1); // Test monthly increments
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<ParameterResult> future = completionService.take();
                ParameterResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("shipDate", result.getShipDate().toString());
                        resultNode.put("delta", result.getDelta());
                        resultNode.put("endDate", result.getEndDate().toString());
                        resultNode.put("totalRecords", result.getTotalRecords());
                        resultNode.put("distinctGroups", result.getDistinctGroups());
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: shipDate={}, delta={}, records={}, groups={}", 
                        result.getShipDate(), result.getDelta(), result.getTotalRecords(), result.getDistinctGroups());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeIntermediateResults(outputFile);
                    logger.info("Progress: {}/{} processed, {} valid combinations found", 
                        processedCombinations.get(), totalCombinations, foundCombinations.get());
                }
                
            } catch (ExecutionException e) {
                logger.error("Error processing combination: {}", e.getMessage());
            }
        }
        
        // Final write
        writeResults(outputFile);
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        
        logger.info("Search completed! Found {} valid combinations out of {} tested", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static void writeIntermediateResults(String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("processed", processedCombinations.get());
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeResults(String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("processed", processedCombinations.get());
        output.put("found", foundCombinations.get());
        output.put("status", "completed");
        output.put("dateRange", START_DATE + " to " + END_DATE);
        output.put("deltaRange", MIN_DELTA + " to " + MAX_DELTA);
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class CombinationTester implements Callable<ParameterResult> {
        private final LocalDate shipDate;
        private final int delta;
        
        public CombinationTester(LocalDate shipDate, int delta) {
            this.shipDate = shipDate;
            this.delta = delta;
        }
        
        @Override
        public ParameterResult call() throws Exception {
            LocalDate endDate = shipDate.plusDays(delta);
            
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String sql = """
                    SELECT 
                        COUNT(DISTINCT l_returnflag || '|' || l_linestatus) as distinct_groups,
                        COUNT(*) as total_records
                    FROM lineitem 
                    WHERE l_shipdate <= ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setDate(1, Date.valueOf(endDate));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int distinctGroups = rs.getInt("distinct_groups");
                            long totalRecords = rs.getLong("total_records");
                            
                            if (totalRecords > 0) {
                                return new ParameterResult(shipDate, delta, endDate, totalRecords, distinctGroups, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing combination shipDate={}, delta={}: {}", 
                    shipDate, delta, e.getMessage());
            }
            
            return new ParameterResult(shipDate, delta, endDate, 0, 0, false);
        }
    }
    
    static class ParameterResult {
        private final LocalDate shipDate;
        private final int delta;
        private final LocalDate endDate;
        private final long totalRecords;
        private final int distinctGroups;
        private final boolean valid;
        
        public ParameterResult(LocalDate shipDate, int delta, LocalDate endDate, 
                              long totalRecords, int distinctGroups, boolean valid) {
            this.shipDate = shipDate;
            this.delta = delta;
            this.endDate = endDate;
            this.totalRecords = totalRecords;
            this.distinctGroups = distinctGroups;
            this.valid = valid;
        }
        
        // Getters
        public LocalDate getShipDate() { return shipDate; }
        public int getDelta() { return delta; }
        public LocalDate getEndDate() { return endDate; }
        public long getTotalRecords() { return totalRecords; }
        public int getDistinctGroups() { return distinctGroups; }
        public boolean isValid() { return valid; }
    }
} 
