package com.restq.core.benchmark;

/**
 * Enumeration of supported benchmark types in the RestQ Framework
 */
public enum BenchmarkType {
    TPCC("TPC-C", "Transaction Processing Performance Council Benchmark C"),
    TPCH("TPC-H", "Transaction Processing Performance Council Benchmark H");
    
    private final String displayName;
    private final String description;
    
    BenchmarkType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Parse benchmark type from string, case-insensitive
     */
    public static BenchmarkType fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Benchmark type cannot be null or empty");
        }
        
        String normalized = value.trim().toUpperCase();
        
        // Handle variations
        switch (normalized) {
            case "TPCC":
            case "TPC-C":
            case "TPC_C":
                return TPCC;
            case "TPCH":
            case "TPC-H":
            case "TPC_H":
                return TPCH;
            default:
                throw new IllegalArgumentException("Unknown benchmark type: " + value + 
                    ". Supported types: TPCC, TPCH");
        }
    }
    
    /**
     * Get default database name for this benchmark type
     */
    public String getDefaultDatabaseName() {
        return switch (this) {
            case TPCC -> "tpccdb";
            case TPCH -> "tpchdb";
        };
    }
    
    /**
     * Get entity packages for this benchmark type
     */
    public String[] getEntityPackages() {
        return switch (this) {
            case TPCC -> new String[]{
                "com.restq.core.Models.tpcc.Warehouse",
                "com.restq.core.Models.tpcc.District", 
                "com.restq.core.Models.tpcc.Customer",
                "com.restq.core.Models.tpcc.Item",
                "com.restq.core.Models.tpcc.Stock",
                "com.restq.core.Models.tpcc.NewOrder",
                "com.restq.core.Models.tpcc.History",
                "com.restq.core.Models.tpcc.OrderLine"
            };
            case TPCH -> new String[]{
                "com.restq.core.Models.tpch.Customer",
                "com.restq.core.Models.tpch.LineItem",
                "com.restq.core.Models.tpch.Nation",
                "com.restq.core.Models.tpch.Orders",
                "com.restq.core.Models.tpch.Part",
                "com.restq.core.Models.tpch.PartSupp",
                "com.restq.core.Models.tpch.Region",
                "com.restq.core.Models.tpch.Supplier"
            };
        };
    }
} 
