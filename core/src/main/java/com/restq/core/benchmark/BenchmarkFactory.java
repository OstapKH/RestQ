package com.restq.core.benchmark;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BenchmarkFactory {
    
    private final List<BenchmarkStrategy> strategies;
    private Map<BenchmarkType, BenchmarkStrategy> strategyMap;
    
    /**
     * Initialize the strategy map after dependency injection
     */
    private void initializeStrategyMap() {
        if (strategyMap == null) {
            strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                    BenchmarkStrategy::getBenchmarkType,
                    Function.identity()
                ));
            
            log.info("Initialized benchmark strategies: {}", strategyMap.keySet());
        }
    }
    
    /**
     * Get strategy for a specific benchmark type
     */
    public BenchmarkStrategy getStrategy(BenchmarkType benchmarkType) {
        initializeStrategyMap();
        
        BenchmarkStrategy strategy = strategyMap.get(benchmarkType);
        if (strategy == null) {
            throw new IllegalArgumentException("No strategy found for benchmark type: " + benchmarkType);
        }
        
        log.debug("Retrieved strategy for benchmark type: {}", benchmarkType);
        return strategy;
    }
    
    /**
     * Get strategy by parsing benchmark type string
     */
    public BenchmarkStrategy getStrategy(String benchmarkTypeString) {
        BenchmarkType benchmarkType = BenchmarkType.fromString(benchmarkTypeString);
        return getStrategy(benchmarkType);
    }
    
    /**
     * Get all available benchmark types
     */
    public List<BenchmarkType> getAvailableBenchmarkTypes() {
        initializeStrategyMap();
        return List.copyOf(strategyMap.keySet());
    }
    
    /**
     * Create a benchmark configuration with validation
     */
    public BenchmarkConfig createConfiguration(BenchmarkType benchmarkType, 
                                             double scaleFactor, 
                                             int batchSize, 
                                             int terminals) {
        BenchmarkStrategy strategy = getStrategy(benchmarkType);
        
        // Validate parameters using the strategy
        strategy.validateParameters(scaleFactor, terminals, batchSize);
        
        return BenchmarkConfig.create(benchmarkType, scaleFactor, batchSize, terminals);
    }
    
    /**
     * Create a default configuration for a benchmark type
     */
    public BenchmarkConfig createDefaultConfiguration(BenchmarkType benchmarkType) {
        BenchmarkStrategy strategy = getStrategy(benchmarkType);
        return strategy.getDefaultConfig();
    }
    
    /**
     * Initialize database using the appropriate strategy
     */
    public void initializeDatabase(BenchmarkConfig config, String dbUrl, String username, String password) throws Exception {
        BenchmarkStrategy strategy = getStrategy(config.getBenchmarkType());
        
        log.info("Initializing {} database with scale factor {}", 
                config.getBenchmarkType().getDisplayName(), config.getScaleFactor());
        
        strategy.initializeDatabase(dbUrl, username, password, 
                                  config.getScaleFactor(), 
                                  config.getBatchSize(), 
                                  config.getTerminals());
    }
    
    /**
     * Check if database is already populated for a benchmark type
     */
    public boolean isDatabasePopulated(BenchmarkType benchmarkType, String dbUrl, String username, String password) throws Exception {
        BenchmarkStrategy strategy = getStrategy(benchmarkType);
        return strategy.isDatabasePopulated(dbUrl, username, password);
    }
} 
