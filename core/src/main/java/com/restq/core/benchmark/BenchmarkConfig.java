package com.restq.core.benchmark;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration class for benchmark parameters
 */
@Data
@Builder
public class BenchmarkConfig {
    
    private final BenchmarkType benchmarkType;
    private final double scaleFactor;
    private final int batchSize;
    private final int terminals;
    private final String databaseName;
    private final String[] entityPackages;
    
    /**
     * Create a default configuration for a benchmark type
     */
    public static BenchmarkConfig createDefault(BenchmarkType benchmarkType) {
        return BenchmarkConfig.builder()
            .benchmarkType(benchmarkType)
            .scaleFactor(getDefaultScaleFactor(benchmarkType))
            .batchSize(getDefaultBatchSize(benchmarkType))
            .terminals(1)
            .databaseName(benchmarkType.getDefaultDatabaseName())
            .entityPackages(benchmarkType.getEntityPackages())
            .build();
    }
    
    private static double getDefaultScaleFactor(BenchmarkType benchmarkType) {
        return switch (benchmarkType) {
            case TPCC -> 1.0; // 1 warehouse
            case TPCH -> 1.0; // SF 1
        };
    }
    
    private static int getDefaultBatchSize(BenchmarkType benchmarkType) {
        return switch (benchmarkType) {
            case TPCC -> 500;
            case TPCH -> 1000;
        };
    }
    
    /**
     * Create a custom configuration
     */
    public static BenchmarkConfig create(BenchmarkType benchmarkType, double scaleFactor, 
                                       int batchSize, int terminals) {
        return BenchmarkConfig.builder()
            .benchmarkType(benchmarkType)
            .scaleFactor(scaleFactor)
            .batchSize(batchSize)
            .terminals(terminals)
            .databaseName(benchmarkType.getDefaultDatabaseName())
            .entityPackages(benchmarkType.getEntityPackages())
            .build();
    }
} 
