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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartParameterFinderQ7 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ7.class);
    
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
        String outputFile = "q7_parameter_combinations.json";
        
        logger.info("Starting EFFICIENT search for ALL Q7 combinations with non-empty results...");
        
        try {
            findAllNonEmptyQ7CombinationsEfficient(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findAllNonEmptyQ7CombinationsEfficient(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting ALL viable combinations in a single optimized query...");
        
        List<Q7SmartCombination> allCombinations = getAllQ7CombinationsDirectly();
        logger.info("Found {} viable combinations directly from database", allCombinations.size());
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testQ7SmartCombinations(allCombinations, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ7EfficientResults(allCombinations, outputFile);
        
        logger.info("Q7 EFFICIENT search completed! Found {} valid combinations with non-empty results", foundCombinations.get());
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static List<Q7SmartCombination> getAllQ7CombinationsDirectly() throws SQLException {
        List<Q7SmartCombination> combinations = new ArrayList<>();
        Set<String> seenCombinations = new HashSet<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Single optimized query to get ALL meaningful combinations
            String sql = """
                WITH nation_pairs AS (
                    SELECT DISTINCT
                        CASE WHEN n1.n_name < n2.n_name THEN n1.n_name ELSE n2.n_name END as nation1,
                        CASE WHEN n1.n_name < n2.n_name THEN n2.n_name ELSE n1.n_name END as nation2
                    FROM supplier s, lineitem l, orders o, customer c, nation n1, nation n2
                    WHERE s.s_suppkey = l.l_suppkey
                    AND o.o_orderkey = l.l_orderkey
                    AND c.c_custkey = o.o_custkey
                    AND s.s_nationkey = n1.n_nationkey
                    AND c.c_nationkey = n2.n_nationkey
                    AND n1.n_name != n2.n_name
                    AND l.l_shipdate IS NOT NULL
                ),
                date_ranges AS (
                    SELECT DISTINCT
                        DATE_TRUNC('year', l.l_shipdate)::date as start_date,
                        (DATE_TRUNC('year', l.l_shipdate) + INTERVAL '1 year' - INTERVAL '1 day')::date as end_date
                    FROM lineitem l
                    WHERE l.l_shipdate IS NOT NULL
                    
                    UNION
                    
                    SELECT DISTINCT
                        DATE_TRUNC('quarter', l.l_shipdate)::date as start_date,
                        (DATE_TRUNC('quarter', l.l_shipdate) + INTERVAL '3 months' - INTERVAL '1 day')::date as end_date
                    FROM lineitem l
                    WHERE l.l_shipdate IS NOT NULL
                )
                SELECT 
                    np.nation1,
                    np.nation2,
                    dr.start_date,
                    dr.end_date,
                    100 as estimated_count
                FROM nation_pairs np
                CROSS JOIN date_ranges dr
                ORDER BY np.nation1, np.nation2, dr.start_date
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String nation1 = rs.getString("nation1");
                    String nation2 = rs.getString("nation2");
                    LocalDate startDate = rs.getDate("start_date").toLocalDate();
                    LocalDate endDate = rs.getDate("end_date").toLocalDate();
                    int estimatedCount = rs.getInt("estimated_count");
                    
                    // Create unique key to avoid duplicates
                    String combinationKey = nation1 + "|" + nation2 + "|" + startDate + "|" + endDate;
                    
                    if (!seenCombinations.contains(combinationKey)) {
                        seenCombinations.add(combinationKey);
                        combinations.add(new Q7SmartCombination(nation1, nation2, startDate, endDate, estimatedCount));
                    }
                }
            }
            
            logger.info("Generated {} unique combinations from cross join", combinations.size());
        }
        
        return combinations;
    }
    
    private static void testQ7SmartCombinations(List<Q7SmartCombination> combinations, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q7CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        logger.info("Submitted {} combinations for testing", combinations.size());
        
        // Submit all combinations for testing
        for (Q7SmartCombination combination : combinations) {
            completionService.submit(new Q7SmartCombinationTester(combination));
        }
        
        // Process results as they complete
        for (int i = 0; i < combinations.size(); i++) {
            try {
                Future<Q7CombinationResult> future = completionService.take();
                Q7CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("nation1", result.getNation1());
                        resultNode.put("nation2", result.getNation2());
                        resultNode.put("startDate", result.getStartDate().toString());
                        resultNode.put("endDate", result.getEndDate().toString());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("totalVolume", result.getTotalVolume().doubleValue());
                        resultNode.put("apiCall", String.format("/nations-volume-shipping?nation1=%s&nation2=%s&startDate=%s&endDate=%s", 
                            result.getNation1(), result.getNation2(), result.getStartDate().toString(), result.getEndDate().toString()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    if (foundCombinations.get() % 10 == 0) {
                        logger.info("Found {} valid combinations so far...", foundCombinations.get());
                    }
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ7IntermediateResults(outputFile, combinations.size());
                    logger.info("Progress: {}/{} processed, {} valid combinations found", 
                        processedCombinations.get(), combinations.size(), foundCombinations.get());
                }
                
            } catch (ExecutionException e) {
                logger.error("Error processing combination: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
    
    private static void writeQ7IntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q7 - Nations Volume Shipping");
        output.put("method", "efficient_cross_join_approach");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ7EfficientResults(List<Q7SmartCombination> combinations, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q7 - Nations Volume Shipping");
        output.put("method", "efficient_cross_join_approach");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("totalGenerated", combinations.size());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalGeneratedCombinations", combinations.size());
        paramsNode.put("approach", "Single optimized query with cross join");
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q7SmartCombinationTester implements Callable<Q7CombinationResult> {
        private final Q7SmartCombination combination;
        
        public Q7SmartCombinationTester(Q7SmartCombination combination) {
            this.combination = combination;
        }
        
        @Override
        public Q7CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository but with counts and total volume
                String sql = """
                    SELECT 
                        COUNT(*) as record_count,
                        COALESCE(SUM(l.l_extendedprice * (1 - l.l_discount)), 0) as total_volume
                    FROM
                        supplier s,
                        lineitem l,
                        orders o,
                        customer c,
                        nation n1,
                        nation n2
                    WHERE
                        s.s_suppkey = l.l_suppkey
                        AND o.o_orderkey = l.l_orderkey
                        AND c.c_custkey = o.o_custkey
                        AND s.s_nationkey = n1.n_nationkey
                        AND c.c_nationkey = n2.n_nationkey
                        AND (
                            (n1.n_name = ? AND n2.n_name = ?)
                            OR
                            (n1.n_name = ? AND n2.n_name = ?)
                        )
                        AND l.l_shipdate BETWEEN ? AND ?
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, combination.nation1);
                    stmt.setString(2, combination.nation2);
                    stmt.setString(3, combination.nation2);
                    stmt.setString(4, combination.nation1);
                    stmt.setDate(5, Date.valueOf(combination.startDate));
                    stmt.setDate(6, Date.valueOf(combination.endDate));
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            BigDecimal totalVolume = rs.getBigDecimal("total_volume");
                            
                            if (recordCount > 0 && totalVolume != null && totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                                return new Q7CombinationResult(combination.nation1, combination.nation2, 
                                    combination.startDate, combination.endDate, recordCount, totalVolume, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q7 combination nation1={}, nation2={}, dateRange={}-{}: {}", 
                    combination.nation1, combination.nation2, combination.startDate, combination.endDate, e.getMessage());
            }
            
            return new Q7CombinationResult(combination.nation1, combination.nation2, 
                combination.startDate, combination.endDate, 0, BigDecimal.ZERO, false);
        }
    }
    
    static class Q7SmartCombination {
        final String nation1;
        final String nation2;
        final LocalDate startDate;
        final LocalDate endDate;
        final int expectedCount;
        
        Q7SmartCombination(String nation1, String nation2, LocalDate startDate, LocalDate endDate, int expectedCount) {
            this.nation1 = nation1;
            this.nation2 = nation2;
            this.startDate = startDate;
            this.endDate = endDate;
            this.expectedCount = expectedCount;
        }
    }
    
    static class Q7CombinationResult {
        private final String nation1;
        private final String nation2;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final int recordCount;
        private final BigDecimal totalVolume;
        private final boolean valid;
        
        public Q7CombinationResult(String nation1, String nation2, LocalDate startDate, LocalDate endDate,
                                   int recordCount, BigDecimal totalVolume, boolean valid) {
            this.nation1 = nation1;
            this.nation2 = nation2;
            this.startDate = startDate;
            this.endDate = endDate;
            this.recordCount = recordCount;
            this.totalVolume = totalVolume;
            this.valid = valid;
        }
        
        // Getters
        public String getNation1() { return nation1; }
        public String getNation2() { return nation2; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }
        public int getRecordCount() { return recordCount; }
        public BigDecimal getTotalVolume() { return totalVolume; }
        public boolean isValid() { return valid; }
    }
}
