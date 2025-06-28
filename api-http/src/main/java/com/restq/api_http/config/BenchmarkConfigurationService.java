package com.restq.api_http.config;

import com.restq.core.benchmark.BenchmarkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class BenchmarkConfigurationService {
    
    private static final Logger log = LoggerFactory.getLogger(BenchmarkConfigurationService.class);
    
    @Value("${benchmark.type:TPCC}")
    private String benchmarkTypeString;
    
    @Value("${benchmark.scale-factor:1.0}")
    private double scaleFactor;
    
    @Value("${benchmark.terminals:1}")
    private int terminals;
    
    @Value("${benchmark.batch-size:500}")
    private int batchSize;
    
    private BenchmarkType benchmarkType;
    
    @PostConstruct
    public void initialize() {
        try {
            benchmarkType = BenchmarkType.fromString(benchmarkTypeString);
            log.info("API HTTP module configured for {} benchmark", benchmarkType.getDisplayName());
            log.info("Scale Factor: {}, Terminals: {}, Batch Size: {}", scaleFactor, terminals, batchSize);
        } catch (IllegalArgumentException e) {
            log.error("Invalid benchmark type configuration: {}", benchmarkTypeString);
            throw e;
        }
    }
    
    public BenchmarkType getBenchmarkType() {
        return benchmarkType;
    }
    
    public String getBenchmarkTypeString() {
        return benchmarkTypeString;
    }
    
    public double getScaleFactor() {
        return scaleFactor;
    }
    
    public int getTerminals() {
        return terminals;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public boolean isTPCC() {
        return benchmarkType == BenchmarkType.TPCC;
    }
    
    public boolean isTPCH() {
        return benchmarkType == BenchmarkType.TPCH;
    }
    
    /**
     * Get the base API path for this benchmark
     */
    public String getApiBasePath() {
        return switch (benchmarkType) {
            case TPCC -> "/api/tpcc";
            case TPCH -> "/api/reports";
        };
    }
    
    /**
     * Get the database name for this benchmark
     */
    public String getDatabaseName() {
        return benchmarkType.getDefaultDatabaseName();
    }
    
    /**
     * Validate if the current configuration supports a specific operation
     */
    public boolean supportsOperation(String operation) {
        return switch (benchmarkType) {
            case TPCC -> operation.startsWith("tpcc") || operation.startsWith("transaction");
            case TPCH -> operation.startsWith("tpch") || operation.startsWith("report") || operation.startsWith("query");
        };
    }
} 
