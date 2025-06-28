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
public class TPCCBenchmarkStrategy implements BenchmarkStrategy {
    
    private final DatabaseInitializationService databaseInitializationService;
    
    @Override
    public BenchmarkType getBenchmarkType() {
        return BenchmarkType.TPCC;
    }
    
    @Override
    public void initializeDatabase(String dbUrl, String username, String password, 
                                 double scaleFactor, int batchSize, int terminals) throws Exception {
        log.info("Initializing TPC-C database with strategy pattern");
        databaseInitializationService.initializeTPCCDatabase(dbUrl, username, password, scaleFactor, batchSize, terminals);
    }
    
    @Override
    public boolean isDatabasePopulated(String dbUrl, String username, String password) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl, username, password);
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM WAREHOUSE");
            rs.next();
            boolean populated = rs.getInt(1) > 0;
            
            if (populated) {
                log.info("TPC-C database is already populated with {} warehouses", rs.getInt(1));
            }
            
            return populated;
        } catch (Exception e) {
            log.debug("TPC-C database check failed (likely not populated): {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public double getDefaultScaleFactor() {
        return 1.0; // 1 warehouse
    }
    
    @Override
    public int getDefaultBatchSize() {
        return 500;
    }
    
    @Override
    public BenchmarkConfig getDefaultConfig() {
        return BenchmarkConfig.createDefault(BenchmarkType.TPCC);
    }
    
    @Override
    public void validateParameters(double scaleFactor, int terminals, int batchSize) throws IllegalArgumentException {
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("TPC-C scale factor (warehouses) must be positive");
        }
        
        if (scaleFactor != Math.floor(scaleFactor)) {
            throw new IllegalArgumentException("TPC-C scale factor must be a whole number (number of warehouses)");
        }
        
        if (terminals <= 0) {
            throw new IllegalArgumentException("TPC-C terminals must be positive");
        }
        
        if (batchSize <= 0) {
            throw new IllegalArgumentException("TPC-C batch size must be positive");
        }
        
        // TPC-C specific validation: terminals should not exceed 10 * warehouses
        if (terminals > scaleFactor * 10) {
            log.warn("TPC-C terminals ({}) exceeds recommended maximum of 10 per warehouse ({})", 
                    terminals, (int)scaleFactor * 10);
        }
        
        log.info("TPC-C parameters validated: {} warehouses, {} terminals, batch size {}", 
                (int)scaleFactor, terminals, batchSize);
    }
} 
