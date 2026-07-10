package com.restq.core.DBDataLoad;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.benchmarks.tpcc.TPCCBenchmark;
import com.oltpbenchmark.benchmarks.tpcc.TPCCLoader;
import com.oltpbenchmark.benchmarks.tpch.TPCHBenchmark;
import com.oltpbenchmark.benchmarks.tpch.TPCHLoader;
import com.oltpbenchmark.types.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
     * Default false: the schema is exactly what BenchBase's per-engine DDL
     * provides, unmodified. Set benchmark.index-parity=true for cross-engine
     * comparisons: BenchBase's DDL files disagree (ddl-postgres.sql creates
     * 15 secondary indexes, ddl-mysql.sql none beyond PK/FK-implied), so
     * without parity the engines run different physical schemas — measured
     * at 2x on TPC-H Q4. Either way, record the setting with the results.
     */
    @Value("${benchmark.index-parity:false}")
    private boolean indexParity;

    /**
     * Initializes the TPC-C database: creates the schema via BenchBase and
     * loads the data. Returns early when the database is already populated.
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

            TPCCBenchmark benchmark = new TPCCBenchmark(workConf);

            log.info("Creating TPCC database schema...");
            benchmark.createDatabase();

            log.info("Refreshing TPCC catalog...");
            benchmark.refreshCatalog();

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
     * Initializes the TPC-H database: creates the schema via BenchBase,
     * loads the data, and applies the optional index parity set. Returns
     * early when the database is already populated.
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

            TPCHBenchmark benchmark = new TPCHBenchmark(workConf);

            log.info("Creating TPC-H database schema...");
            benchmark.createDatabase();

            log.info("Refreshing TPC-H catalog...");
            benchmark.refreshCatalog();

            log.info("Loading TPC-H data...");
            TPCHLoader loader = new TPCHLoader(benchmark);
            List<LoaderThread> loaderThreads = loader.createLoaderThreads();

            for (LoaderThread thread : loaderThreads) {
                thread.run();
            }
            
            ensureTPCHIndexParity(conn, determineDatabaseType(dbUrl));

            log.info("TPC-H database initialization completed successfully");
            printTPCHStatistics(conn);

        } catch (Exception e) {
            log.error("Failed to initialize TPC-H database", e);
            throw e;
        }
    }

    /**
     * Opt-in via benchmark.index-parity=true (see the field docs): adds the
     * secondary indexes MySQL is missing relative to BenchBase's postgres
     * DDL; engines whose DDL is already index-complete are untouched.
     * MySQL error 1061 (duplicate key name) is tolerated — it means the
     * index already exists.
     */
    private void ensureTPCHIndexParity(Connection conn, DatabaseType dbType) {
        if (!indexParity) {
            log.info("Index parity disabled (benchmark.index-parity=false): "
                    + "schema is exactly BenchBase's per-engine DDL");
            return;
        }
        if (dbType != DatabaseType.MYSQL) {
            return;
        }
        String[] indexes = {
            "CREATE INDEX o_od ON orders (o_orderdate)",
            "CREATE INDEX l_cd ON lineitem (l_commitdate)",
            "CREATE INDEX l_sd ON lineitem (l_shipdate)",
            "CREATE INDEX l_rd ON lineitem (l_receiptdate)",
            "CREATE INDEX l_pk_sk ON lineitem (l_partkey, l_suppkey)",
            "CREATE INDEX l_sk_pk ON lineitem (l_suppkey, l_partkey)",
        };
        log.info("Creating TPC-H index parity set for {} ({} indexes)...",
                dbType, indexes.length);
        for (String ddl : indexes) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(ddl);
            } catch (java.sql.SQLException e) {
                if (e.getErrorCode() == 1061) {
                    log.info("Index already exists, skipping: {}", ddl);
                } else {
                    throw new RuntimeException("Index parity DDL failed: " + ddl, e);
                }
            }
        }
    }

    /** Builds the BenchBase workload configuration for the given connection settings. */
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

    /** Returns true when the WAREHOUSE table exists and contains rows. */
    private boolean isTPCCDatabasePopulated(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM WAREHOUSE");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Returns true when the CUSTOMER table exists and contains rows. */
    private boolean isTPCHDatabasePopulated(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM CUSTOMER");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Logs the row count of every TPC-C table; failures only produce warnings. */
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

    /** Logs the row count of every TPC-H table; failures only produce warnings. */
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

    /** Derives the BenchBase database type from the JDBC URL; throws on unsupported engines. */
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
