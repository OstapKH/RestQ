package com.restq.core.DBDataLoad;

import com.oltpbenchmark.WorkloadConfiguration;
import com.oltpbenchmark.api.LoaderThread;
import com.oltpbenchmark.benchmarks.tpcc.TPCCBenchmark;
import com.oltpbenchmark.benchmarks.tpcc.TPCCLoader;
import com.oltpbenchmark.types.DatabaseType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@Configuration
@Slf4j
public class TPCCDatabaseLoaderConfig {

    @Value("${app.database.tpcc.url}")
    private String dbUrl;

    @Value("${app.database.username}")
    private String dbUsername;

    @Value("${app.database.password}")
    private String dbPassword;

    @Value("${app.database.scale-factor:1}")
    private double scaleFactor;

    @Value("${app.database.tpcc.batch-size:#{null}}")
    private Integer tpccBatchSize;

    @Value("${app.database.batch-size:128}")
    private int defaultBatchSize;

    @Value("${app.database.terminals:1}")
    private int terminals;

    private boolean isTPCCDatabasePopulated(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            // Check if WAREHOUSE table has any records
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM WAREHOUSE");
            rs.next();
            return rs.getInt(1) > 0;
        } catch (Exception e) {
            // If table doesn't exist or other error, we assume database is not populated
            return false;
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
