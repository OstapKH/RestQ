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

public class SmartParameterFinderQ8 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ8.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Fixed date range for Q8 as per specification
    private static final LocalDate START_DATE = LocalDate.of(1995, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(1996, 12, 31);
    
    // Concurrency settings
    private static final int THREAD_POOL_SIZE = 8;
    private static final int BATCH_SIZE = 25;
    
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ArrayNode results = mapper.createArrayNode();
    private static AtomicInteger foundCombinations = new AtomicInteger(0);
    private static AtomicInteger processedCombinations = new AtomicInteger(0);
    
    public static void main(String[] args) {
        String outputFile = "q8_parameter_combinations.json";
        
        logger.info("Starting COMPREHENSIVE search for ALL Q8 combinations with non-empty results...");
        logger.info("Using fixed date range: {} to {}", START_DATE, END_DATE);
        
        try {
            findAllNonEmptyQ8Combinations(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findAllNonEmptyQ8Combinations(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Getting all possible parameter values from database...");
        
        Q8ParameterValues paramValues = getQ8ParameterValues();
        
        logger.info("Found {} nations, {} regions, {} part types", 
            paramValues.nations.size(), paramValues.regions.size(), paramValues.partTypes.size());
        
        int totalCombinations = paramValues.nations.size() * paramValues.regions.size() * paramValues.partTypes.size();
        logger.info("Total combinations to test: {}", totalCombinations);
        
        logger.info("Step 2: Testing combinations concurrently...");
        
        testQ8CombinationsConcurrently(paramValues, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ8Results(paramValues, outputFile);
        
        logger.info("Q8 COMPREHENSIVE search completed! Found {} valid combinations out of {} total", 
            foundCombinations.get(), totalCombinations);
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static Q8ParameterValues getQ8ParameterValues() throws SQLException {
        List<String> nations = new ArrayList<>();
        List<String> regions = new ArrayList<>();
        List<String> partTypes = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            
            // Get all distinct nations that appear as suppliers in the date range
            String nationsSQL = """
                SELECT DISTINCT n.n_name
                FROM nation n, supplier s, lineitem l, orders o, part p
                WHERE s.s_nationkey = n.n_nationkey
                AND l.l_suppkey = s.s_suppkey
                AND l.l_orderkey = o.o_orderkey
                AND l.l_partkey = p.p_partkey
                AND o.o_orderdate BETWEEN ? AND ?
                ORDER BY n.n_name
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(nationsSQL)) {
                stmt.setDate(1, Date.valueOf(START_DATE));
                stmt.setDate(2, Date.valueOf(END_DATE));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        nations.add(rs.getString("n_name"));
                    }
                }
            }
            
            // Get all distinct regions that have customers in the date range
            String regionsSQL = """
                SELECT DISTINCT r.r_name
                FROM region r, nation n, customer c, orders o, lineitem l
                WHERE n.n_regionkey = r.r_regionkey
                AND c.c_nationkey = n.n_nationkey
                AND o.o_custkey = c.c_custkey
                AND l.l_orderkey = o.o_orderkey
                AND o.o_orderdate BETWEEN ? AND ?
                ORDER BY r.r_name
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(regionsSQL)) {
                stmt.setDate(1, Date.valueOf(START_DATE));
                stmt.setDate(2, Date.valueOf(END_DATE));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        regions.add(rs.getString("r_name"));
                    }
                }
            }
            
            // Get all distinct part types that were ordered in the date range
            String partTypesSQL = """
                SELECT DISTINCT p.p_type
                FROM part p, lineitem l, orders o
                WHERE p.p_partkey = l.l_partkey
                AND l.l_orderkey = o.o_orderkey
                AND o.o_orderdate BETWEEN ? AND ?
                ORDER BY p.p_type
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(partTypesSQL)) {
                stmt.setDate(1, Date.valueOf(START_DATE));
                stmt.setDate(2, Date.valueOf(END_DATE));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        partTypes.add(rs.getString("p_type"));
                    }
                }
            }
            
            logger.info("Parameter value extraction completed");
        }
        
        return new Q8ParameterValues(nations, regions, partTypes);
    }
    
    private static void testQ8CombinationsConcurrently(Q8ParameterValues paramValues, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q8CombinationResult> completionService = new ExecutorCompletionService<>(executor);
        
        int totalCombinations = 0;
        
        // Submit all combinations for testing
        for (String nation : paramValues.nations) {
            for (String region : paramValues.regions) {
                for (String partType : paramValues.partTypes) {
                    completionService.submit(new Q8CombinationTester(nation, region, partType));
                    totalCombinations++;
                }
            }
        }
        
        logger.info("Submitted {} combinations for testing", totalCombinations);
        
        // Process results as they complete
        for (int i = 0; i < totalCombinations; i++) {
            try {
                Future<Q8CombinationResult> future = completionService.take();
                Q8CombinationResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("nation", result.getNation());
                        resultNode.put("region", result.getRegion());
                        resultNode.put("type", result.getPartType());
                        resultNode.put("startDate", START_DATE.toString());
                        resultNode.put("endDate", END_DATE.toString());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("totalVolume", result.getTotalVolume().doubleValue());
                        resultNode.put("apiCall", String.format("/market-share?nation=%s&region=%s&type=%s", 
                            result.getNation(), result.getRegion(), result.getPartType()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid combination: nation={}, region={}, type={}, records={}, volume={}", 
                        result.getNation(), result.getRegion(), result.getPartType(), 
                        result.getRecordCount(), result.getTotalVolume());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ8IntermediateResults(outputFile, totalCombinations);
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
    
    private static void writeQ8IntermediateResults(String filename, int totalCombinations) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q8 - Market Share");
        output.put("method", "comprehensive_all_non_empty_combinations");
        output.put("fixedDateRange", START_DATE + " to " + END_DATE);
        output.put("processed", processedCombinations.get());
        output.put("total", totalCombinations);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ8Results(Q8ParameterValues paramValues, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q8 - Market Share");
        output.put("method", "comprehensive_all_non_empty_combinations");
        output.put("fixedDateRange", START_DATE + " to " + END_DATE);
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalNations", paramValues.nations.size());
        paramsNode.put("totalRegions", paramValues.regions.size());
        paramsNode.put("totalPartTypes", paramValues.partTypes.size());
        
        ArrayNode nationsArray = paramsNode.putArray("nations");
        paramValues.nations.forEach(nationsArray::add);
        
        ArrayNode regionsArray = paramsNode.putArray("regions");
        paramValues.regions.forEach(regionsArray::add);
        
        ArrayNode typesArray = paramsNode.putArray("partTypes");
        paramValues.partTypes.forEach(typesArray::add);
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q8CombinationTester implements Callable<Q8CombinationResult> {
        private final String nation;
        private final String region;
        private final String partType;
        
        public Q8CombinationTester(String nation, String region, String partType) {
            this.nation = nation;
            this.region = region;
            this.partType = partType;
        }
        
        @Override
        public Q8CombinationResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query structure as in the repository but with counts and total volume
                String sql = """
                    SELECT 
                        COUNT(*) as record_count,
                        COALESCE(SUM(subquery.volume), 0) as total_volume
                    FROM (
                        SELECT
                            EXTRACT(YEAR FROM o.o_orderdate) as o_year,
                            l.l_extendedprice * (1 - l.l_discount) as volume,
                            n2.n_name as nation
                        FROM
                            part p, supplier s, lineitem l, orders o, customer c, nation n1, nation n2, region r
                        WHERE
                            p.p_partkey = l.l_partkey
                            AND s.s_suppkey = l.l_suppkey
                            AND l.l_orderkey = o.o_orderkey
                            AND o.o_custkey = c.c_custkey
                            AND c.c_nationkey = n1.n_nationkey
                            AND n1.n_regionkey = r.r_regionkey
                            AND r.r_name = ?
                            AND s.s_nationkey = n2.n_nationkey
                            AND o.o_orderdate BETWEEN ? AND ?
                            AND p.p_type = ?
                    ) as subquery
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, region);
                    stmt.setDate(2, Date.valueOf(START_DATE));
                    stmt.setDate(3, Date.valueOf(END_DATE));
                    stmt.setString(4, partType);
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            BigDecimal totalVolume = rs.getBigDecimal("total_volume");
                            
                            if (recordCount > 0 && totalVolume != null && totalVolume.compareTo(BigDecimal.ZERO) > 0) {
                                return new Q8CombinationResult(nation, region, partType, recordCount, totalVolume, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q8 combination nation={}, region={}, type={}: {}", 
                    nation, region, partType, e.getMessage());
            }
            
            return new Q8CombinationResult(nation, region, partType, 0, BigDecimal.ZERO, false);
        }
    }
    
    static class Q8ParameterValues {
        final List<String> nations;
        final List<String> regions;
        final List<String> partTypes;
        
        Q8ParameterValues(List<String> nations, List<String> regions, List<String> partTypes) {
            this.nations = nations;
            this.regions = regions;
            this.partTypes = partTypes;
        }
    }
    
    static class Q8CombinationResult {
        private final String nation;
        private final String region;
        private final String partType;
        private final int recordCount;
        private final BigDecimal totalVolume;
        private final boolean valid;
        
        public Q8CombinationResult(String nation, String region, String partType, 
                                   int recordCount, BigDecimal totalVolume, boolean valid) {
            this.nation = nation;
            this.region = region;
            this.partType = partType;
            this.recordCount = recordCount;
            this.totalVolume = totalVolume;
            this.valid = valid;
        }
        
        // Getters
        public String getNation() { return nation; }
        public String getRegion() { return region; }
        public String getPartType() { return partType; }
        public int getRecordCount() { return recordCount; }
        public BigDecimal getTotalVolume() { return totalVolume; }
        public boolean isValid() { return valid; }
    }
}
