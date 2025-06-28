package com.restq.core.benchmark;

/**
 * Strategy interface for different benchmark implementations
 */
public interface BenchmarkStrategy {
    
    /**
     * Gets the benchmark type this strategy handles
     */
    BenchmarkType getBenchmarkType();
    
    /**
     * Initializes the database with benchmark-specific data
     */
    void initializeDatabase(String dbUrl, String username, String password, 
                          double scaleFactor, int batchSize, int terminals) throws Exception;
    
    /**
     * Checks if the database is already populated with data for this benchmark
     */
    boolean isDatabasePopulated(String dbUrl, String username, String password) throws Exception;
    
    /**
     * Gets the default scale factor for this benchmark
     */
    double getDefaultScaleFactor();
    
    /**
     * Gets the default batch size for this benchmark
     */
    int getDefaultBatchSize();
    
    /**
     * Gets benchmark-specific configuration properties
     */
    BenchmarkConfig getDefaultConfig();
    
    /**
     * Validates benchmark-specific parameters
     */
    void validateParameters(double scaleFactor, int terminals, int batchSize) throws IllegalArgumentException;
} 
