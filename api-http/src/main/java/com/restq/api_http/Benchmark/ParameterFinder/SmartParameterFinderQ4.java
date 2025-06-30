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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartParameterFinderQ4 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ4.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Concurrency settings
    private static final int THREAD_POOL_SIZE = 8;
    private static final int BATCH_SIZE = 50;
    
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ArrayNode results = mapper.createArrayNode();
    private static AtomicInteger foundCombinations = new AtomicInteger(0);
    private static AtomicInteger processedCombinations = new AtomicInteger(0);
    
    public static void main(String[] args) {
        String outputFile = "q4_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search for Q4 (order-priority-count)...");
        
        try {
            findValidCombinationsQ4(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsQ4(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible date values from database...");
        
        List<LocalDate> candidateDates = getQ4CandidateDates();
        
        logger.info("Found {} candidate dates to test", candidateDates.size());
        
        logger.info("Step 2: Testing date parameters concurrently...");
        
        testQ4DatesConcurrently(candidateDates, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ4Results(candidateDates, outputFile);
        
        logger.info("Q4 search completed! Found {} valid dates out of {} total", 
            foundCombinations.get(), candidateDates.size());
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static List<LocalDate> getQ4CandidateDates() throws SQLException {
        List<LocalDate> candidateDates = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get candidate dates from order dates
            // The query uses date range: o.orderDate >= :date AND o.orderDate < :datePlus (where datePlus = date + 3 months)
            // So we need dates where there are orders in a 3-month window starting from that date
            String datesSQL = """
                SELECT DISTINCT o_orderdate as candidate_date 
                FROM orders 
                WHERE o_orderdate >= '1992-01-01' AND o_orderdate <= '1998-09-01'
                ORDER BY o_orderdate
                """;
                
            try (PreparedStatement stmt = conn.prepareStatement(datesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate candidateDate = rs.getDate("candidate_date").toLocalDate();
                    // Only include dates that leave room for 3-month window
                    if (!candidateDate.isAfter(LocalDate.of(1998, 9, 1))) {
                        candidateDates.add(candidateDate);
                    }
                }
            }
            
            // Sample dates if there are too many (to keep testing reasonable)
            if (candidateDates.size() > 500) {
                List<LocalDate> sampledDates = new ArrayList<>();
                int step = candidateDates.size() / 500;
                for (int i = 0; i < candidateDates.size(); i += step) {
                    sampledDates.add(candidateDates.get(i));
                }
                candidateDates = sampledDates;
                logger.info("Sampled {} dates from {} total for efficiency", sampledDates.size(), candidateDates.size());
            }
        }
        
        return candidateDates;
    }
    
    private static void testQ4DatesConcurrently(List<LocalDate> candidateDates, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q4DateResult> completionService = new ExecutorCompletionService<>(executor);
        
        // Submit all dates for testing
        for (LocalDate date : candidateDates) {
            completionService.submit(new Q4DateTester(date));
        }
        
        logger.info("Submitted {} dates for testing", candidateDates.size());
        
        // Process results as they complete
        for (int i = 0; i < candidateDates.size(); i++) {
            try {
                Future<Q4DateResult> future = completionService.take();
                Q4DateResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("date", result.getDate().toString());
                        resultNode.put("datePlus", result.getDate().plusMonths(3).toString());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("apiCall", String.format("/order-priority-count?date=%s", 
                            result.getDate().toString()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid date: date={}, records={}", 
                        result.getDate(), result.getRecordCount());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ4IntermediateResults(outputFile, candidateDates.size());
                    logger.info("Progress: {}/{} processed, {} valid dates found", 
                        processedCombinations.get(), candidateDates.size(), foundCombinations.get());
                }
                
            } catch (ExecutionException e) {
                logger.error("Error processing date: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
    
    private static void writeQ4IntermediateResults(String filename, int totalDates) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q4 - Order Priority Count");
        output.put("processed", processedCombinations.get());
        output.put("total", totalDates);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validDates", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ4Results(List<LocalDate> candidateDates, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q4 - Order Priority Count");
        output.put("method", "smart_concurrent_testing");
        output.put("totalValidDates", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterInfo");
        paramsNode.put("parameterName", "date");
        paramsNode.put("parameterType", "LocalDate");
        paramsNode.put("totalCandidateDates", candidateDates.size());
        paramsNode.put("dateRange", "3-month window (date to date+3months)");
        
        ArrayNode datesArray = paramsNode.putArray("candidateDates");
        candidateDates.forEach(date -> datesArray.add(date.toString()));
        
        // Valid dates
        output.set("validDates", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q4DateTester implements Callable<Q4DateResult> {
        private final LocalDate date;
        
        public Q4DateTester(LocalDate date) {
            this.date = date;
        }
        
        @Override
        public Q4DateResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository but just count results
                LocalDate datePlus = date.plusMonths(3);
                
                String sql = """
                    SELECT COUNT(*) as record_count
                    FROM orders o
                    WHERE o.o_orderdate >= ?
                    AND o.o_orderdate < ?
                    AND EXISTS(
                        SELECT 1 FROM lineitem l
                        WHERE l.l_orderkey = o.o_orderkey
                        AND l.l_commitdate < l.l_receiptdate
                    )
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setDate(1, Date.valueOf(date));
                    stmt.setDate(2, Date.valueOf(datePlus));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            if (recordCount > 0) {
                                return new Q4DateResult(date, recordCount, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q4 date={}: {}", date, e.getMessage());
            }
            
            return new Q4DateResult(date, 0, false);
        }
    }
    
    static class Q4DateResult {
        private final LocalDate date;
        private final int recordCount;
        private final boolean valid;
        
        public Q4DateResult(LocalDate date, int recordCount, boolean valid) {
            this.date = date;
            this.recordCount = recordCount;
            this.valid = valid;
        }
        
        // Getters
        public LocalDate getDate() { return date; }
        public int getRecordCount() { return recordCount; }
        public boolean isValid() { return valid; }
    }
} 
