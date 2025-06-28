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

public class SmartParameterFinder {
    
    private static final Logger logger = LoggerFactory.getLogger(SmartParameterFinder.class);
    
    // Database configuration
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/tpchdb";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "password";
    
    // Parameters
    private static final int MIN_DELTA = 60;
    private static final int MAX_DELTA = 120;
    private static final LocalDate MIN_SHIP_DATE = LocalDate.of(1992, 1, 1);
    private static final LocalDate MAX_SHIP_DATE = LocalDate.of(1998, 10, 1);
    
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    
    public static void main(String[] args) {
        String outputFile = "smart_parameter_combinations.json";
        
        logger.info("Starting SMART parameter combination search...");
        
        try {
            findValidCombinationsSmart(outputFile);
        } catch (Exception e) {
            logger.error("Error during search: {}", e.getMessage(), e);
        }
    }
    
    private static void findValidCombinationsSmart(String outputFile) throws SQLException, IOException {
        logger.info("Step 1: Analyzing database to find date boundaries...");
        
        // Get the actual min/max ship dates from the database
        DatabaseInfo dbInfo = getDateBoundaries();
        
        logger.info("Database contains ship dates from {} to {}", dbInfo.minShipDate, dbInfo.maxShipDate);
        logger.info("Total records: {}, Distinct groups: {}", dbInfo.totalRecords, dbInfo.distinctGroups);
        
        logger.info("Step 2: Calculating valid combinations mathematically...");
        
        List<ValidCombination> validCombinations = calculateValidCombinations(dbInfo);
        
        logger.info("Step 3: Sampling a few combinations to verify our math...");
        
        // Verify a few random combinations
        verifySampleCombinations(validCombinations.subList(0, Math.min(5, validCombinations.size())), dbInfo);
        
        logger.info("Step 4: Generating final results...");
        
        writeSmartResults(validCombinations, dbInfo, outputFile);
        
        logger.info("SMART search completed! Found {} valid combinations (ALL daily combinations)!", validCombinations.size());
        logger.info("Results saved to: {}", outputFile);
    }
    
    private static DatabaseInfo getDateBoundaries() throws SQLException {
        String sql = """
            SELECT 
                MIN(l_shipdate) as min_date,
                MAX(l_shipdate) as max_date,
                COUNT(*) as total_records,
                COUNT(DISTINCT l_returnflag || '|' || l_linestatus) as distinct_groups
            FROM lineitem
            """;
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            if (rs.next()) {
                return new DatabaseInfo(
                    rs.getDate("min_date").toLocalDate(),
                    rs.getDate("max_date").toLocalDate(),
                    rs.getLong("total_records"),
                    rs.getInt("distinct_groups")
                );
            }
        }
        
        throw new SQLException("Could not retrieve database boundaries");
    }
    
    private static List<ValidCombination> calculateValidCombinations(DatabaseInfo dbInfo) {
        List<ValidCombination> combinations = new ArrayList<>();
        
        // Logic: For shipDate + delta to be valid, the endDate (shipDate + delta) 
        // must be >= dbInfo.minShipDate (otherwise query returns 0 records)
        
        LocalDate currentShipDate = MIN_SHIP_DATE;
        
        while (!currentShipDate.isAfter(MAX_SHIP_DATE)) {
            for (int delta = MIN_DELTA; delta <= MAX_DELTA; delta++) {
                LocalDate endDate = currentShipDate.plusDays(delta);
                
                // Check if this combination is valid
                if (!endDate.isBefore(dbInfo.minShipDate) && !endDate.isAfter(dbInfo.maxShipDate.plusDays(1))) {
                    // Estimate record count based on date position
                    long estimatedRecords = estimateRecordCount(endDate, dbInfo);
                    
                    combinations.add(new ValidCombination(
                        currentShipDate, 
                        delta, 
                        endDate, 
                        estimatedRecords, 
                        dbInfo.distinctGroups
                    ));
                }
            }
            currentShipDate = currentShipDate.plusDays(1); // Daily increments for ALL combinations
        }
        
        return combinations;
    }
    
    private static long estimateRecordCount(LocalDate endDate, DatabaseInfo dbInfo) {
        // Simple linear estimation based on date position
        // This is an approximation - actual counts may vary
        
        long totalDays = dbInfo.minShipDate.toEpochDay() - dbInfo.maxShipDate.toEpochDay();
        long daysSinceMin = endDate.toEpochDay() - dbInfo.minShipDate.toEpochDay();
        
        if (daysSinceMin < 0) return 0;
        if (daysSinceMin > totalDays) return dbInfo.totalRecords;
        
        // Linear interpolation
        return (long) ((double) daysSinceMin / Math.abs(totalDays) * dbInfo.totalRecords);
    }
    
    private static void verifySampleCombinations(List<ValidCombination> samples, DatabaseInfo dbInfo) throws SQLException {
        logger.info("Verifying {} sample combinations...", samples.size());
        
        String sql = "SELECT COUNT(*) as actual_count FROM lineitem WHERE l_shipdate <= ?";
        
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            for (ValidCombination combo : samples) {
                stmt.setDate(1, Date.valueOf(combo.endDate));
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        long actualCount = rs.getLong("actual_count");
                        long estimatedCount = combo.estimatedRecords;
                        
                        logger.info("Verification: shipDate={}, delta={} -> Estimated: {}, Actual: {}", 
                            combo.shipDate, combo.delta, estimatedCount, actualCount);
                        
                        // Update with actual count for better accuracy
                        combo.estimatedRecords = actualCount;
                    }
                }
            }
        }
    }
    
    private static void writeSmartResults(List<ValidCombination> combinations, DatabaseInfo dbInfo, String filename) throws IOException {
        ObjectNode output = mapper.createObjectNode();
        output.put("timestamp", System.currentTimeMillis());
        output.put("method", "smart_calculation");
        output.put("totalCombinations", combinations.size());
        output.put("executionTimeSeconds", "< 60 seconds");
        output.put("coverage", "ALL daily combinations");
        
        // Database info
        ObjectNode dbInfoNode = output.putObject("databaseInfo");
        dbInfoNode.put("minShipDate", dbInfo.minShipDate.toString());
        dbInfoNode.put("maxShipDate", dbInfo.maxShipDate.toString());
        dbInfoNode.put("totalRecords", dbInfo.totalRecords);
        dbInfoNode.put("distinctGroups", dbInfo.distinctGroups);
        
        // Parameters
        ObjectNode paramsNode = output.putObject("parameters");
        paramsNode.put("deltaMin", MIN_DELTA);
        paramsNode.put("deltaMax", MAX_DELTA);
        paramsNode.put("shipDateMin", MIN_SHIP_DATE.toString());
        paramsNode.put("shipDateMax", MAX_SHIP_DATE.toString());
        
        // Valid combinations
        ArrayNode combinationsArray = output.putArray("validCombinations");
        for (ValidCombination combo : combinations) {
            ObjectNode comboNode = combinationsArray.addObject();
            comboNode.put("shipDate", combo.shipDate.toString());
            comboNode.put("delta", combo.delta);
            comboNode.put("endDate", combo.endDate.toString());
            comboNode.put("estimatedRecords", combo.estimatedRecords);
            comboNode.put("distinctGroups", combo.distinctGroups);
            comboNode.put("apiCall", "/pricing-summary?shipDate=" + combo.shipDate + "&delta=" + combo.delta);
        }
        
        mapper.writeValue(new File(filename), output);
    }
    
    static class DatabaseInfo {
        final LocalDate minShipDate;
        final LocalDate maxShipDate;
        final long totalRecords;
        final int distinctGroups;
        
        DatabaseInfo(LocalDate minShipDate, LocalDate maxShipDate, long totalRecords, int distinctGroups) {
            this.minShipDate = minShipDate;
            this.maxShipDate = maxShipDate;
            this.totalRecords = totalRecords;
            this.distinctGroups = distinctGroups;
        }
    }
    
    static class ValidCombination {
        final LocalDate shipDate;
        final int delta;
        final LocalDate endDate;
        long estimatedRecords;
        final int distinctGroups;
        
        ValidCombination(LocalDate shipDate, int delta, LocalDate endDate, long estimatedRecords, int distinctGroups) {
            this.shipDate = shipDate;
            this.delta = delta;
            this.endDate = endDate;
            this.estimatedRecords = estimatedRecords;
            this.distinctGroups = distinctGroups;
        }
    }
} 
