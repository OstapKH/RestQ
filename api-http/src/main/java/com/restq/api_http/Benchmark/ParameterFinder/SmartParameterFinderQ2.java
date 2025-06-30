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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartParameterFinderQ2 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ2.class);
    
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
        String outputFile = "q2_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search for Q2 (supplier-part-info)...");
        
        try {
            findValidCombinationsQ2(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsQ2(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible parameter values from database...");
        
        ParameterValues paramValues = getParameterValues();
        
        logger.info("Found {} sizes, {} types, {} regions", 
            paramValues.sizes.size(), paramValues.types.size(), paramValues.regions.size());
        
        int totalCombinations = paramValues.sizes.size() * paramValues.types.size() * paramValues.regions.size();
        logger.info("Total combinations to test: {}", totalCombinations);
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testCombinationsConcurrently(paramValues, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeResults(paramValues, outputFile);
        
        logger.info("Q2 search completed! Found {} valid combinations out of {} total", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static ParameterValues getParameterValues() throws SQLException {
        List<Integer> sizes = new ArrayList<>();
        List<String> types = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get all distinct sizes
            String sizesSQL = "SELECT DISTINCT p_size FROM part ORDER BY p_size";
            try (PreparedStatement stmt = conn.prepareStatement(sizesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sizes.add(rs.getInt("p_size"));
                }
            }
            
            // Get all distinct types (for LIKE matching, we'll get full types)
            String typesSQL = "SELECT DISTINCT p_type FROM part ORDER BY p_type";
            try (PreparedStatement stmt = conn.prepareStatement(typesSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    types.add(rs.getString("p_type"));
                }
            }
            
            // Get all distinct regions
            String regionsSQL = "SELECT DISTINCT r_name FROM region ORDER BY r_name";
            try (PreparedStatement stmt = conn.prepareStatement(regionsSQL);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    regions.add(rs.getString("r_name"));
                }
            }
        }
        
        return new ParameterValues(sizes, types, regions);
    }
    
    private static void testCombinationsConcurrently(ParameterValues paramValues, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q2CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        int totalCombinations = 0;
        
        // Submit all combinations for testing
        for (Integer size : paramValues.sizes) {
            for (String type : paramValues.types) {
                for (String region : paramValues.regions) {
                    completionService.submit(new Q2CombinationTester(size, type, region));
                    totalCombinations++;
                }
            }
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<Q2CombinationResult> future = completionService.take();
                Q2CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("size", result.getSize());
                        resultNode.put("type", result.getType());
                        resultNode.put("region", result.getRegion());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("apiCall", String.format("/supplier-part-info?size=%d&type=%s&region=%s", 
                            result.getSize(), result.getType(), result.getRegion()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: size={}, type={}, region={}, records={}", 
                        result.getSize(), result.getType(), result.getRegion(), result.getRecordCount());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeIntermediateResults(outputFile, totalCombinations);
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
    
    private static void writeIntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q2 - Supplier Part Info");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeResults(ParameterValues paramValues, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q2 - Supplier Part Info");
        output.put("method", "smart_concurrent_testing");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalSizes", paramValues.sizes.size());
        paramsNode.put("totalTypes", paramValues.types.size());
        paramsNode.put("totalRegions", paramValues.regions.size());
        
        ArrayNode sizesArray = paramsNode.putArray("sizes");
        paramValues.sizes.forEach(sizesArray::add);
        
        ArrayNode typesArray = paramsNode.putArray("types");
        paramValues.types.forEach(typesArray::add);
        
        ArrayNode regionsArray = paramsNode.putArray("regions");
        paramValues.regions.forEach(regionsArray::add);
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q2CombinationTester implements Callable<Q2CombinationResult> {
        private final Integer size;
        private final String type;
        private final String region;
        
        public Q2CombinationTester(Integer size, String type, String region) {
            this.size = size;
            this.type = type;
            this.region = region;
        }
        
        @Override
        public Q2CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query as in the repository but just count results
                String sql = """
                    SELECT COUNT(*) as record_count
                    FROM part p, supplier s, partsupp ps, nation n, region r
                    WHERE p.p_partkey = ps.ps_partkey
                    AND ps.ps_suppkey = s.s_suppkey
                    AND p.p_size = ?
                    AND p.p_type LIKE ?
                    AND s.s_nationkey = n.n_nationkey
                    AND n.n_regionkey = r.r_regionkey
                    AND r.r_name = ?
                    AND ps.ps_supplycost = (
                        SELECT MIN(ps2.ps_supplycost)
                        FROM partsupp ps2, supplier s2, nation n2, region r2
                        WHERE ps2.ps_partkey = p.p_partkey
                        AND ps2.ps_suppkey = s2.s_suppkey
                        AND s2.s_nationkey = n2.n_nationkey
                        AND n2.n_regionkey = r2.r_regionkey
                        AND r2.r_name = ?
                    )
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setInt(1, size);
                    stmt.setString(2, "%" + type + "%"); // LIKE pattern
                    stmt.setString(3, region);
                    stmt.setString(4, region);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            if (recordCount > 0) {
                                return new Q2CombinationResult(size, type, region, recordCount, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q2 combination size={}, type={}, region={}: {}", 
                    size, type, region, e.getMessage());
            }
            
            return new Q2CombinationResult(size, type, region, 0, false);
        }
    }
    
    static class ParameterValues {
        final List<Integer> sizes;
        final List<String> types;
        final List<String> regions;
        
        ParameterValues(List<Integer> sizes, List<String> types, List<String> regions) {
            this.sizes = sizes;
            this.types = types;
            this.regions = regions;
        }
    }
    
    static class Q2CombinationResult {
        private final Integer size;
        private final String type;
        private final String region;
        private final int recordCount;
        private final boolean valid;
        
        public Q2CombinationResult(Integer size, String type, String region, int recordCount, boolean valid) {
            this.size = size;
            this.type = type;
            this.region = region;
            this.recordCount = recordCount;
            this.valid = valid;
        }
        
        // Getters
        public Integer getSize() { return size; }
        public String getType() { return type; }
        public String getRegion() { return region; }
        public int getRecordCount() { return recordCount; }
        public boolean isValid() { return valid; }
    }
} 
