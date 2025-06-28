package com.restq.api_http.Controllers;

import com.restq.api_http.config.BenchmarkConfigurationService;
import com.restq.core.benchmark.BenchmarkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BenchmarkController {
    
    private static final Logger log = LoggerFactory.getLogger(BenchmarkController.class);
    
    @Autowired
    private BenchmarkConfigurationService benchmarkConfig;
    
    /**
     * Get the current benchmark configuration
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("benchmarkType", benchmarkConfig.getBenchmarkTypeString());
        config.put("displayName", benchmarkConfig.getBenchmarkType().getDisplayName());
        config.put("description", benchmarkConfig.getBenchmarkType().getDescription());
        config.put("scaleFactor", benchmarkConfig.getScaleFactor());
        config.put("terminals", benchmarkConfig.getTerminals());
        config.put("batchSize", benchmarkConfig.getBatchSize());
        config.put("databaseName", benchmarkConfig.getDatabaseName());
        config.put("apiBasePath", benchmarkConfig.getApiBasePath());
        
        log.info("Configuration requested: {}", config);
        return ResponseEntity.ok(config);
    }
    
    /**
     * Get available benchmark types
     */
    @GetMapping("/benchmarks")
    public ResponseEntity<Map<String, Object>> getAvailableBenchmarks() {
        Map<String, Object> benchmarks = new HashMap<>();
        
        for (BenchmarkType type : BenchmarkType.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("displayName", type.getDisplayName());
            info.put("description", type.getDescription());
            info.put("defaultDatabase", type.getDefaultDatabaseName());
            benchmarks.put(type.name(), info);
        }
        
        benchmarks.put("current", benchmarkConfig.getBenchmarkTypeString());
        
        return ResponseEntity.ok(benchmarks);
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("benchmark", benchmarkConfig.getBenchmarkTypeString());
        status.put("apiVersion", "1.0");
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * Redirect based on benchmark type
     */
    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        Map<String, String> response = new HashMap<>();
        response.put("message", "RestQ Framework API Server");
        response.put("benchmark", benchmarkConfig.getBenchmarkType().getDisplayName());
        response.put("apiPath", benchmarkConfig.getApiBasePath());
        
        if (benchmarkConfig.isTPCC()) {
            response.put("description", "TPC-C transaction processing endpoints available at /api/tpcc/*");
        } else if (benchmarkConfig.isTPCH()) {
            response.put("description", "TPC-H decision support queries available at /api/reports/*");
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Validate operation compatibility
     */
    @GetMapping("/validate/{operation}")
    public ResponseEntity<Map<String, Object>> validateOperation(@PathVariable String operation) {
        Map<String, Object> result = new HashMap<>();
        
        boolean supported = benchmarkConfig.supportsOperation(operation);
        result.put("operation", operation);
        result.put("supported", supported);
        result.put("benchmarkType", benchmarkConfig.getBenchmarkTypeString());
        
        if (!supported) {
            result.put("message", String.format("Operation '%s' is not supported by %s benchmark", 
                                              operation, benchmarkConfig.getBenchmarkType().getDisplayName()));
        }
        
        return ResponseEntity.ok(result);
    }
} 
