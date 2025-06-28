package com.restq.core.benchmark;

import com.restq.core.DBDataLoad.DatabaseInitializationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class TPCHBenchmarkStrategy implements BenchmarkStrategy {
    
    private final DatabaseInitializationService databaseInitializationService;
    
    @Override
    public BenchmarkType getBenchmarkType() {
        return BenchmarkType.TPCH;
    }
    
    @Override
    public void initializeDatabase(String dbUrl, String username, String password, 
                                 double scaleFactor, int batchSize, int terminals) throws Exception {
        log.info("Initializing TPC-H database with strategy pattern");
        databaseInitializationService.initializeTPCHDatabase(dbUrl, username, password, scaleFactor, batchSize, terminals);
    }
    
    @Override
    public boolean isDatabasePopulated(String dbUrl, String username, String password) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CUSTOMER");
            rs.next();
            boolean populated = rs.getInt(1) > 0;
            
            if (populated) {
                log.info("TPC-H database is already populated with {} customers", rs.getInt(1));
            }
            
            return populated;
        } catch (Exception e) {
            log.debug("TPC-H database check failed (likely not populated): {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public double getDefaultScaleFactor() {
        return 1.0; // SF 1
    }
    
    @Override
    public int getDefaultBatchSize() {
        return 1000;
    }
    
    @Override
    public BenchmarkConfig getDefaultConfig() {
        return BenchmarkConfig.createDefault(BenchmarkType.TPCH);
    }
    
    @Override
    public void validateParameters(double scaleFactor, int terminals, int batchSize) throws IllegalArgumentException {
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("TPC-H scale factor must be positive");
        }
        
        // TPC-H scale factors are typically 1, 10, 100, 1000, etc.
        double[] validScaleFactors = {0.01, 0.1, 1.0, 10.0, 100.0, 1000.0, 3000.0, 10000.0, 30000.0, 100000.0};
        boolean validSF = false;
        for (double validSF_value : validScaleFactors) {
            if (Math.abs(scaleFactor - validSF_value) < 0.001) {
                validSF = true;
                break;
            }
        }
        
        if (!validSF) {
            log.warn("TPC-H scale factor {} is not a standard value. Standard values: 0.01, 0.1, 1, 10, 100, 1000, 3000, 10000, 30000, 100000", scaleFactor);
        }
        
        if (terminals <= 0) {
            throw new IllegalArgumentException("TPC-H terminals must be positive");
        }
        
        if (batchSize <= 0) {
            throw new IllegalArgumentException("TPC-H batch size must be positive");
        }
        
        // TPC-H doesn't have strict terminal limits like TPC-C, but warn for very high values
        if (terminals > 100) {
            log.warn("TPC-H terminals ({}) is very high and may impact performance", terminals);
        }
        
        log.info("TPC-H parameters validated: scale factor {}, {} terminals, batch size {}", 
                scaleFactor, terminals, batchSize);
    }
} 
