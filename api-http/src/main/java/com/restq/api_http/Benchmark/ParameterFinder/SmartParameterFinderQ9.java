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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmartParameterFinderQ9 {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinderQ9.class);
    
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
        String outputFile = "q9_parameter_combinations.json";
        
        logger.info("Starting COMPREHENSIVE search for ALL Q9 combinations with non-empty results...");
        
        try {
            findAllNonEmptyQ9Combinations(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findAllNonEmptyQ9Combinations(String outputFile) throws SQLException, IOException, InterruptedException {
        logger.info("Step 1: Extracting all possible color values from part names...");
        
        List<String> colorCandidates = extractColorCandidates();
        logger.info("Found {} color candidates from part names", colorCandidates.size());
        
        logger.info("Step 2: Testing color candidates concurrently...");
        
        testQ9ColorsConcurrently(colorCandidates, outputFile);
        
        logger.info("Step 3: Writing final results...");
        
        writeQ9Results(colorCandidates, outputFile);
        
        logger.info("Q9 COMPREHENSIVE search completed! Found {} valid color combinations out of {} total", 
            foundCombinations.get(), colorCandidates.size());
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static List<String> extractColorCandidates() throws SQLException {
        Set<String> colorSet = new HashSet<>();
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Extract all words from part names to find potential colors
            String sql = """
                SELECT DISTINCT p.p_name
                FROM part p
                WHERE p.p_name IS NOT NULL
                AND LENGTH(p.p_name) > 0
                ORDER BY p.p_name
                """;
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String partName = rs.getString("p_name");
                    if (partName != null && !partName.trim().isEmpty()) {
                        // Extract individual words from part names
                        String[] words = partName.toLowerCase().split("\\s+");
                        for (String word : words) {
                            // Clean the word and add potential colors
                            word = word.replaceAll("[^a-z]", "");
                            if (word.length() >= 3 && word.length() <= 15) {
                                colorSet.add(word);
                            }
                        }
                    }
                }
            }
        }
        
        List<String> colorCandidates = new ArrayList<>(colorSet);
        
        // Add some common color names that might not be extracted
        String[] commonColors = {
            "red", "blue", "green", "yellow", "black", "white", "brown", "orange", 
            "purple", "pink", "grey", "gray", "silver", "gold", "copper", "bronze",
            "violet", "indigo", "cyan", "magenta", "lime", "navy", "maroon", "olive",
            "cream", "beige", "tan", "khaki", "coral", "salmon", "turquoise"
        };
        
        for (String color : commonColors) {
            if (!colorCandidates.contains(color)) {
                colorCandidates.add(color);
            }
        }
        
        logger.info("Total color candidates (including common colors): {}", colorCandidates.size());
        return colorCandidates;
    }
    
    private static void testQ9ColorsConcurrently(List<String> colorCandidates, String outputFile) 
            throws InterruptedException, IOException {
        
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        CompletionService<Q9ColorResult> completionService = new ExecutorCompletionService<>(executor);
        
        logger.info("Submitted {} color candidates for testing", colorCandidates.size());
        
        // Submit all color candidates for testing
        for (String color : colorCandidates) {
            completionService.submit(new Q9ColorTester(color));
        }
        
        // Process results as they complete
        for (int i = 0; i < colorCandidates.size(); i++) {
            try {
                Future<Q9ColorResult> future = completionService.take();
                Q9ColorResult result = future.get();
                
                processedCombinations.incrementAndGet();
                
                if (result != null && result.isValid()) {
                    synchronized (results) {
                        ObjectNode resultNode = mapper.createObjectNode();
                        resultNode.put("color", result.getColor());
                        resultNode.put("recordCount", result.getRecordCount());
                        resultNode.put("totalProfit", result.getTotalProfit().doubleValue());
                        resultNode.put("nationCount", result.getNationCount());
                        resultNode.put("yearCount", result.getYearCount());
                        resultNode.put("apiCall", String.format("/product-type-profit?color=%s", result.getColor()));
                        
                        results.add(resultNode);
                        foundCombinations.incrementAndGet();
                    }
                    
                    logger.info("Found valid color: {}, records={}, nations={}, years={}, profit={}", 
                        result.getColor(), result.getRecordCount(), result.getNationCount(), 
                        result.getYearCount(), result.getTotalProfit());
                }
                
                // Write intermediate results every BATCH_SIZE
                if (processedCombinations.get() % BATCH_SIZE == 0) {
                    writeQ9IntermediateResults(outputFile, colorCandidates.size());
                    logger.info("Progress: {}/{} processed, {} valid colors found", 
                        processedCombinations.get(), colorCandidates.size(), foundCombinations.get());
                }
                
            } catch (ExecutionException e) {
                logger.error("Error processing color: {}", e.getMessage());
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
    }
    
    private static void writeQ9IntermediateResults(String filename, int totalCandidates) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q9 - Product Type Profit");
        output.put("method", "comprehensive_color_extraction_and_testing");
        output.put("processed", processedCombinations.get());
        output.put("total", totalCandidates);
        output.put("found", foundCombinations.get());
        output.put("status", "in_progress");
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    private static void writeQ9Results(List<String> colorCandidates, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("query", "Q9 - Product Type Profit");
        output.put("method", "comprehensive_color_extraction_and_testing");
        output.put("totalCombinations", foundCombinations.get());
        output.put("processed", processedCombinations.get());
        output.put("totalCandidates", colorCandidates.size());
        output.put("status", "completed");
        
        // Parameter info
        ObjectNode paramsNode = output.putObject("parameterValues");
        paramsNode.put("totalColorCandidates", colorCandidates.size());
        paramsNode.put("approach", "Extract words from part names + common colors");
        
        ArrayNode colorsArray = paramsNode.putArray("colorCandidates");
        colorCandidates.forEach(colorsArray::add);
        
        // Valid combinations
        output.set("validCombinations", results);
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class Q9ColorTester implements Callable<Q9ColorResult> {
        private final String color;
        
        public Q9ColorTester(String color) {
            this.color = color;
        }
        
        @Override
        public Q9ColorResult call() throws Exception {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Use the exact same query structure as in the repository but with additional metrics
                String sql = """
                    SELECT 
                        COUNT(*) as record_count,
                        COUNT(DISTINCT subquery.nation) as nation_count,
                        COUNT(DISTINCT subquery.o_year) as year_count,
                        COALESCE(SUM(subquery.amount), 0) as total_profit
                    FROM (
                        SELECT
                            n.n_name as nation,
                            EXTRACT(YEAR FROM o.o_orderdate) as o_year,
                            l.l_extendedprice * (1 - l.l_discount) - ps.ps_supplycost * l.l_quantity as amount
                        FROM
                            part p,
                            supplier s,
                            lineitem l,
                            partsupp ps,
                            orders o,
                            nation n
                        WHERE
                            s.s_suppkey = l.l_suppkey
                            AND ps.ps_suppkey = l.l_suppkey
                            AND ps.ps_partkey = l.l_partkey
                            AND p.p_partkey = l.l_partkey
                            AND o.o_orderkey = l.l_orderkey
                            AND s.s_nationkey = n.n_nationkey
                            AND p.p_name LIKE ?
                    ) as subquery
                    """;
                
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, "%" + color + "%");
                    
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            int recordCount = rs.getInt("record_count");
                            int nationCount = rs.getInt("nation_count");
                            int yearCount = rs.getInt("year_count");
                            BigDecimal totalProfit = rs.getBigDecimal("total_profit");
                            
                            if (recordCount > 0) {
                                return new Q9ColorResult(color, recordCount, nationCount, yearCount, totalProfit, true);
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                logger.error("Database error testing Q9 color={}: {}", color, e.getMessage());
            }
            
            return new Q9ColorResult(color, 0, 0, 0, BigDecimal.ZERO, false);
        }
    }
    
    static class Q9ColorResult {
        private final String color;
        private final int recordCount;
        private final int nationCount;
        private final int yearCount;
        private final BigDecimal totalProfit;
        private final boolean valid;
        
        public Q9ColorResult(String color, int recordCount, int nationCount, int yearCount,
                            BigDecimal totalProfit, boolean valid) {
            this.color = color;
            this.recordCount = recordCount;
            this.nationCount = nationCount;
            this.yearCount = yearCount;
            this.totalProfit = totalProfit;
            this.valid = valid;
        }
        
        // Getters
        public String getColor() { return color; }
        public int getRecordCount() { return recordCount; }
        public int getNationCount() { return nationCount; }
        public int getYearCount() { return yearCount; }
        public BigDecimal getTotalProfit() { return totalProfit; }
        public boolean isValid() { return valid; }
    }
}
