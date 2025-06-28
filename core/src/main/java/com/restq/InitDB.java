package com.restq;

import com.restq.core.benchmark.BenchmarkConfig;
import com.restq.core.benchmark.BenchmarkFactory;
import com.restq.core.benchmark.BenchmarkType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;

@SpringBootApplication
@Slf4j
public class InitDB implements CommandLineRunner {

    @Autowired
    private BenchmarkFactory benchmarkFactory;

    // Benchmark Configuration
    @Value("${benchmark.type:TPCC}")
    private String benchmarkType;

    @Value("${benchmark.scale-factor:1.0}")
    private double scaleFactor;

    @Value("${benchmark.terminals:1}")
    private int terminals;

    @Value("${benchmark.batch-size:0}")
    private int batchSize;

    // Database Configuration
    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    public static void main(String[] args) {
        log.info("Starting RestQ Framework Database Initialization...");
        
        try {
            SpringApplication.run(InitDB.class, args);
            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            log.error("Database initialization failed", e);
            System.exit(1);
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // Parse and validate benchmark type
        BenchmarkType benchmark;
        try {
            benchmark = BenchmarkType.fromString(benchmarkType);
        } catch (IllegalArgumentException e) {
            log.error("Invalid benchmark type: '{}'. {}", benchmarkType, e.getMessage());
            log.error("Available benchmark types: {}", benchmarkFactory.getAvailableBenchmarkTypes());
            throw e;
        }

        log.info("=== RestQ Framework Database Initialization ===");
        log.info("Benchmark Type: {} ({})", benchmark.getDisplayName(), benchmark.getDescription());
        log.info("Database URL: {}", dbUrl);
        log.info("Database User: {}", dbUsername);

        // Create configuration with strategy-based validation
        BenchmarkConfig config;
        try {
            if (batchSize <= 0) {
                // Use default batch size from strategy
                config = benchmarkFactory.createDefaultConfiguration(benchmark);
                config = BenchmarkConfig.create(benchmark, scaleFactor, config.getBatchSize(), terminals);
            } else {
                config = benchmarkFactory.createConfiguration(benchmark, scaleFactor, batchSize, terminals);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid benchmark parameters: {}", e.getMessage());
            throw e;
        }

        log.info("Scale Factor: {}", config.getScaleFactor());
        log.info("Terminals: {}", config.getTerminals());
        log.info("Batch Size: {}", config.getBatchSize());

        // Update EntityScan based on benchmark type
        updateEntityScanPackages(benchmark);

        // Check if database is already populated
        try {
            if (benchmarkFactory.isDatabasePopulated(benchmark, dbUrl, dbUsername, dbPassword)) {
                log.info("{} database is already populated. Skipping initialization.", benchmark.getDisplayName());
                logDatabaseInfo(benchmark);
                return;
            }
        } catch (Exception e) {
            log.warn("Could not check database population status: {}", e.getMessage());
            log.info("Proceeding with database initialization...");
        }

        // Initialize database using the appropriate strategy
        try {
            log.info("Initializing {} database...", benchmark.getDisplayName());
            benchmarkFactory.initializeDatabase(config, dbUrl, dbUsername, dbPassword);
            log.info("{} database initialization completed successfully!", benchmark.getDisplayName());
            
            logDatabaseInfo(benchmark);
        } catch (Exception e) {
            log.error("Failed to initialize {} database", benchmark.getDisplayName(), e);
            throw e;
        }
    }

    private void updateEntityScanPackages(BenchmarkType benchmarkType) {
        String[] packages = benchmarkType.getEntityPackages();
        log.info("Entity packages for {}: {}", benchmarkType.getDisplayName(), String.join(", ", packages));
        
        // Note: In a production system, you might want to dynamically configure EntityScan
        // For now, we log the information for verification
        log.debug("EntityScan configuration should include: {}", String.join(", ", packages));
    }

    private void logDatabaseInfo(BenchmarkType benchmarkType) {
        log.info("=== Database Initialization Summary ===");
        log.info("Benchmark: {}", benchmarkType.getDisplayName());
        log.info("Database: {}", benchmarkType.getDefaultDatabaseName());
        log.info("Entities: {}", String.join(", ", benchmarkType.getEntityPackages()));
        log.info("Ready for {} workload execution", benchmarkType.getDisplayName());
    }
}


