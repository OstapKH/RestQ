package com.restq.core.DBDataLoad;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.benchmarks.tpcc.TPCCBenchmark;
import com.oltpbenchmark.benchmarks.tpcc.TPCCLoader;
import com.oltpbenchmark.benchmarks.tpch.TPCHBenchmark;
import com.oltpbenchmark.benchmarks.tpch.TPCHLoader;
import com.oltpbenchmark.types.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Service
@Slf4j
public class DatabaseInitializationService {

    /**
     * Initialize TPCC database with the specified configuration
     */
    public void initializeTPCCDatabase(String dbUrl, String username, String password, 
                                     double scaleFactor, int batchSize, int terminals) throws Exception {
        log.info("Starting TPCC database initialization...");
        log.info("Database: {}", dbUrl);
        log.info("Scale Factor: {} (warehouses)", (int)scaleFactor);
        
        try (Connection conn = DriverManager.getConnection(dbUrl, username, password)) {
            if (isTPCCDatabasePopulated(conn)) {
                log.info("TPCC database is already populated with data");
                return;
            }

            WorkloadConfiguration workConf = createWorkloadConfiguration(
                dbUrl, username, password, scaleFactor, batchSize, terminals);

            // Create and configure TPCC benchmark
            TPCCBenchmark benchmark = new TPCCBenchmark(workConf);
            
            // Create database schema
            log.info("Creating TPCC database schema...");
            benchmark.createDatabase();
            
            // Refresh catalog
            log.info("Refreshing TPCC catalog...");
            benchmark.refreshCatalog();
            
            // Load data
            log.info("Loading TPCC data...");
            TPCCLoader loader = new TPCCLoader(benchmark);
            List<LoaderThread> loaderThreads = loader.createLoaderThreads();
            
            for (LoaderThread thread : loaderThreads) {
                thread.run();
            }
            
            log.info("TPCC database initialization completed successfully");
            printTPCCStatistics(conn);
            
        } catch (Exception e) {
            log.error("Failed to initialize TPCC database", e);
            throw e;
        }
    }

    /**
     * Initialize TPC-H database with the specified configuration
     */
    public void initializeTPCHDatabase(String dbUrl, String username, String password, 
                                     double scaleFactor, int batchSize, int terminals) throws Exception {
        log.info("Starting TPC-H database initialization...");
        log.info("Database: {}", dbUrl);
        log.info("Scale Factor: {}", scaleFactor);
        
        try (Connection conn = DriverManager.getConnection(dbUrl, username, password)) {
            if (isTPCHDatabasePopulated(conn)) {
                log.info("TPC-H database is already populated with data");
                return;
            }

            WorkloadConfiguration workConf = createWorkloadConfiguration(
                dbUrl, username, password, scaleFactor, batchSize, terminals);

            // Create and configure TPC-H benchmark
            TPCHBenchmark benchmark = new TPCHBenchmark(workConf);
            
            // Create database schema
            log.info("Creating TPC-H database schema...");
            benchmark.createDatabase();
            
            // Refresh catalog
            log.info("Refreshing TPC-H catalog...");
            benchmark.refreshCatalog();
            
            // Load data
            log.info("Loading TPC-H data...");
            TPCHLoader loader = new TPCHLoader(benchmark);
            List<LoaderThread> loaderThreads = loader.createLoaderThreads();
            
            for (LoaderThread thread : loaderThreads) {
                thread.run();
            }
            
            log.info("TPC-H database initialization completed successfully");
            printTPCHStatistics(conn);
            
        } catch (Exception e) {
            log.error("Failed to initialize TPC-H database", e);
            throw e;
        }
    }

    private WorkloadConfiguration createWorkloadConfiguration(String dbUrl, String username, 
                                                            String password, double scaleFactor, 
                                                            int batchSize, int terminals) {
        WorkloadConfiguration workConf = new WorkloadConfiguration();
        workConf.setUrl(dbUrl);
        workConf.setUsername(username);
        workConf.setPassword(password);
        workConf.setScaleFactor(scaleFactor);
        workConf.setBatchSize(batchSize);
        workConf.setTerminals(terminals);
        workConf.setDatabaseType(determineDatabaseType(dbUrl));
        
        return workConf;
    }

    private boolean isTPCCDatabasePopulated(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM WAREHOUSE");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTPCHDatabasePopulated(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CUSTOMER");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void printTPCCStatistics(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            log.info("=== TPCC Database Statistics ===");
            
            String[] tables = {"WAREHOUSE", "DISTRICT", "CUSTOMER", "ITEM", "STOCK", "OORDER", "NEW_ORDER", "ORDER_LINE", "HISTORY"};
            
            for (String table : tables) {
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    rs.next();
                    log.info("{} table: {} records", table, rs.getInt(1));
                } catch (Exception e) {
                    log.warn("Could not get count for table {}: {}", table, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not print TPCC statistics", e);
        }
    }

    private void printTPCHStatistics(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            log.info("=== TPC-H Database Statistics ===");
            
            String[] tables = {"CUSTOMER", "LINEITEM", "NATION", "ORDERS", "PART", "PARTSUPP", "REGION", "SUPPLIER"};
            
            for (String table : tables) {
                try {
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + table);
                    rs.next();
                    log.info("{} table: {} records", table, rs.getInt(1));
                } catch (Exception e) {
                    log.warn("Could not get count for table {}: {}", table, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Could not print TPC-H statistics", e);
        }
    }

    private DatabaseType determineDatabaseType(String jdbcUrl) {
        jdbcUrl = jdbcUrl.toLowerCase();
        if (jdbcUrl.contains("postgresql")) {
            return DatabaseType.POSTGRES;
        } else if (jdbcUrl.contains("mysql")) {
            return DatabaseType.MYSQL;
        } else if (jdbcUrl.contains("sqlserver")) {
            return DatabaseType.SQLSERVER;
        } else if (jdbcUrl.contains("oracle")) {
            return DatabaseType.ORACLE;
        } else {
            throw new IllegalArgumentException("Unsupported database type in JDBC URL: " + jdbcUrl);
        }
    }
} 
