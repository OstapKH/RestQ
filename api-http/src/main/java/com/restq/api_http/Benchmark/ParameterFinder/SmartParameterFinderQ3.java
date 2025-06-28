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

public class SmartParameterFinderQ3 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ3.class);
    
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
        String outputFile = "q3_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search for Q3 (order-revenue-info)...");
        
        try {
            findValidCombinationsQ3(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsQ3(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible parameter values from database...");
        
        Q3ParameterValues paramValues = getQ3ParameterValues();
        
        logger.info("Found {} segments, {} candidate dates", 
            paramValues.segments.size(), paramValues.candidateDates.size());
        
        int totalCombinations = paramValues.segments.size() * paramValues.candidateDates.size();
        logger.info("Total combinations to test: {}", totalCombinations);
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testQ3CombinationsConcurrently(paramValues, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ3Results(paramValues, outputFile);
        
        logger.info("Q3 search completed! Found {} valid combinations out of {} total", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static Q3ParameterValues getQ3ParameterValues() throws SQLException {
        List<String> segments = new ArrayList<>();
        List<LocalDate> candidateDates = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get all distinct market segments
            String segmentsSQL = "SELECT DISTINCT c_mktsegment FROM customer ORDER BY c_mktsegment";
            try (PreparedStatement stmt = conn.prepareStatement(segmentsSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    segments.add(rs.getString("c_mktsegment"));
                }
            }
            
            // Get candidate dates that could potentially return results
            // The query needs: o.orderDate < :date AND l.shipDate > :date
            // So we need dates between some order dates and some ship dates
            String datesSQL = """
                WITH date_candidates AS (
                    SELECT DISTINCT o_orderdate as candidate_date 
                    FROM orders 
                    WHERE o_orderdate >= '1992-01-01' AND o_orderdate <= '1998-10-01'
                    UNION
                    SELECT DISTINCT l_shipdate as candidate_date 
                    FROM lineitem 
                    WHERE l_shipdate >= '1992-01-01' AND l_shipdate <= '1998-10-01'
                )
                SELECT candidate_date FROM date_candidates 
                ORDER BY candidate_date
                """;
                
            try (PreparedStatement stmt = conn.prepareStatement(datesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    candidateDates.add(rs.getDate("candidate_date").toLocalDate());
                }
            }
            
            // Sample dates if there are too many (to keep testing reasonable)
            if (candidateDates.size() > 200) {
                List<LocalDate> sampledDates = new ArrayList<>();
                int step = candidateDates.size() / 200;
                for (int i = 0; i < candidateDates.size(); i += step) {
                    sampledDates.add(candidateDates.get(i));
                }
                candidateDates = sampledDates;
                logger.info("Sampled {} dates from {} total for efficiency", sampledDates.size(), candidateDates.size());
            }
        }
        
        return new Q3ParameterValues(segments, candidateDates);
    }
    
    private static void testQ3CombinationsConcurrently(Q3ParameterValues paramValues, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q3CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        int totalCombinations = 0;
        
        // Submit all combinations for testing
        for (String segment : paramValues.segments) {
            for (LocalDate date : paramValues.candidateDates) {
                completionService.submit(new Q3CombinationTester(segment, date));
                totalCombinations++;
            }
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<Q3CombinationResult> future = completionService.take();
                Q3CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("segment", result.getSegment());
                        resultNode.put("date", result.getDate().toString());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("apiCall", String.format("/order-revenue-info?segment=%s&date=%s", 
                            result.getSegment(), result.getDate().toString()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: segment={}, date={}, records={}", 
                        result.getSegment(), result.getDate(), result.getRecordCount());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ3IntermediateResults(outputFile, totalCombinations);
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
    
    private static void writeQ3IntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q3 - Order Revenue Info");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ3Results(Q3ParameterValues paramValues, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q3 - Order Revenue Info");
        output.put("method", "smart_concurrent_testing");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalSegments", paramValues.segments.size());
        paramsNode.put("totalDates", paramValues.candidateDates.size());
        
        ArrayNode segmentsArray = paramsNode.putArray("segments");
        paramValues.segments.forEach(segmentsArray::add);
        
        ArrayNode datesArray = paramsNode.putArray("candidateDates");
        paramValues.candidateDates.forEach(date -> datesArray.add(date.toString()));
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q3CombinationTester implements Callable<Q3CombinationResult> {
        private final String segment;
        private final LocalDate date;
        
        public Q3CombinationTester(String segment, LocalDate date) {
            this.segment = segment;
            this.date = date;
        }
        
        @Override
        public Q3CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository but just count results
                String sql = """
                    SELECT COUNT(*) as record_count
                    FROM customer c, orders o, lineitem l
                    WHERE c.c_mktsegment = ?
                    AND c.c_custkey = o.o_custkey
                    AND l.l_orderkey = o.o_orderkey
                    AND o.o_orderdate < ?
                    AND l.l_shipdate > ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, segment);
                    stmt.setDate(2, Date.valueOf(date));
                    stmt.setDate(3, Date.valueOf(date));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            if (recordCount > 0) {
                                return new Q3CombinationResult(segment, date, recordCount, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q3 combination segment={}, date={}: {}", 
                    segment, date, e.getMessage());
            }
            
            return new Q3CombinationResult(segment, date, 0, false);
        }
    }
    
    static class Q3ParameterValues {
        final List<String> segments;
        final List<LocalDate> candidateDates;
        
        Q3ParameterValues(List<String> segments, List<LocalDate> candidateDates) {
            this.segments = segments;
            this.candidateDates = candidateDates;
        }
    }
    
    static class Q3CombinationResult {
        private final String segment;
        private final LocalDate date;
        private final int recordCount;
        private final boolean valid;
        
        public Q3CombinationResult(String segment, LocalDate date, int recordCount, boolean valid) {
            this.segment = segment;
            this.date = date;
            this.recordCount = recordCount;
            this.valid = valid;
        }
        
        // Getters
        public String getSegment() { return segment; }
        public LocalDate getDate() { return date; }
        public int getRecordCount() { return recordCount; }
        public boolean isValid() { return valid; }
    }
} 
