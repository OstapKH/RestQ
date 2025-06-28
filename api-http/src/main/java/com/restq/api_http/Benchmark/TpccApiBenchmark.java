package com.restq.api_http.Benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.*;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * TPC-C style API benchmark that follows the benchbase TPC-C transaction patterns.
 * This benchmark implements the 5 TPC-C transaction types as API calls with proper
 * terminal distribution, warehouse scaling, and transaction mix ratios.
 */
public class TpccApiBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(TpccApiBenchmark.class);

    // TPC-C Configuration
    private static final String BASE_URL = "http://localhost:8086/api/tpcc";
    private static final String BENCHMARK_TYPE = "TPCC";
    private static final String CONFIG_FILE = "tpcc-config.xml";

    // TPC-C Transaction Types and their standard mix ratios
    public enum TpccTransactionType {
        NEW_ORDER("/new-order", 0.45, 18000, 12000),
        PAYMENT("/payment", 0.43, 3000, 12000),
        ORDER_STATUS("/order-status", 0.04, 2000, 10000),
        DELIVERY("/delivery", 0.04, 2000, 5000),
        STOCK_LEVEL("/stock-level", 0.04, 2000, 5000);

        private final String endpoint;
        private final double probability;
        private final long preExecutionWaitMs;  // Keying time
        private final long postExecutionWaitMs; // Think time mean

        TpccTransactionType(String endpoint, double probability, long preWait, long postWait) {
            this.endpoint = endpoint;
            this.probability = probability;
            this.preExecutionWaitMs = preWait;
            this.postExecutionWaitMs = postWait;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public double getProbability() {
            return probability;
        }

        public long getPreExecutionWaitMs() {
            return preExecutionWaitMs;
        }

        public long getPostExecutionWaitMs() {
            return postExecutionWaitMs;
        }
    }

    // TPC-C Constants (from benchbase)
    private static final int DISTRICTS_PER_WAREHOUSE = 10;
    private static final int CUSTOMERS_PER_DISTRICT = 3000;
    private static final int ITEMS_COUNT = 100000;

    private static final Random random = new Random();
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ObjectNode allResults;
    private static String resultFileName;

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            logger.info("Starting TPC-C API benchmark with endpoint: {}", BASE_URL);
            
            // Initialize global result variables
            allResults = mapper.createObjectNode();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            resultFileName = "tpcc_benchmark_results_" + timestamp + ".json";
            
            // Load TPC-C specific configuration
            ClassLoader classLoader = TpccApiBenchmark.class.getClassLoader();
            
            JAXBContext configContext = JAXBContext.newInstance(TpccConfig.class);
            Unmarshaller configUnmarshaller = configContext.createUnmarshaller();
            TpccConfig tpccConfig = (TpccConfig) configUnmarshaller.unmarshal(
                classLoader.getResourceAsStream(CONFIG_FILE));
            
            // Add metadata to results
            allResults.put("timestamp", timestamp);
            allResults.put("benchmark_type", BENCHMARK_TYPE);
            allResults.put("config_file", CONFIG_FILE);
            allResults.put("base_url", BASE_URL);
            allResults.put("warehouses", tpccConfig.getWarehouses());
            allResults.put("duration_seconds", tpccConfig.getDurationSeconds());
            allResults.put("terminals", tpccConfig.getTerminals());
            
            // Add TPC-C transaction mix
            ObjectNode transactionMixNode = allResults.putObject("transaction_mix");
            for (TpccTransactionType txType : TpccTransactionType.values()) {
                ObjectNode txNode = transactionMixNode.putObject(txType.name());
                txNode.put("endpoint", txType.getEndpoint());
                txNode.put("probability", txType.getProbability());
                txNode.put("pre_execution_wait_ms", txType.getPreExecutionWaitMs());
                txNode.put("post_execution_wait_ms", txType.getPostExecutionWaitMs());
            }
            
            // Add experiments container
            ObjectNode experimentsNode = allResults.putObject("experiments");
            
            // Run TPC-C benchmark
            runTpccBenchmark(tpccConfig, experimentsNode);
            
            // Write all results
            mapper.writeValue(new File(resultFileName), allResults);
            logger.info("TPC-C benchmark completed. Results saved to {}", resultFileName);
            
        } catch (JAXBException e) {
            logger.error("Error parsing XML configuration: {}", e.getMessage(), e);
        }
    }

    private static void runTpccBenchmark(TpccConfig config, ObjectNode experimentsNode) throws InterruptedException, IOException {
        logger.info("Starting TPC-C benchmark with {} warehouses, {} terminals for {} seconds", 
                   config.getWarehouses(), config.getTerminals(), config.getDurationSeconds());

        // Create experiment node
        ObjectNode experimentNode = experimentsNode.putObject("tpcc_benchmark");
        experimentNode.put("warehouses", config.getWarehouses());
        experimentNode.put("terminals", config.getTerminals());
        experimentNode.put("duration_seconds", config.getDurationSeconds());
        
        // Create terminals with warehouse/district distribution
        List<TpccTerminal> terminals = createTerminals(config.getWarehouses(), config.getTerminals());
        
        // Add terminal distribution info
        ArrayNode terminalsArray = experimentNode.putArray("terminal_distribution");
        for (TpccTerminal terminal : terminals) {
            ObjectNode terminalNode = terminalsArray.addObject();
            terminalNode.put("terminal_id", terminal.getTerminalId());
            terminalNode.put("warehouse_id", terminal.getWarehouseId());
            terminalNode.put("lower_district_id", terminal.getLowerDistrictId());
            terminalNode.put("upper_district_id", terminal.getUpperDistrictId());
        }

        ExecutorService executor = Executors.newFixedThreadPool(terminals.size());
        List<Future<TpccTerminalResult>> futures = new ArrayList<>();
        List<TpccTimestampedLatency> allLatencies = new ArrayList<>();

        long startTimestamp = System.currentTimeMillis();
        long endTimestamp = startTimestamp + config.getDurationSeconds() * 1000;

        // Start all terminals
        for (TpccTerminal terminal : terminals) {
            futures.add(executor.submit(new TpccTerminalTask(terminal, endTimestamp, config.getWarehouses())));
        }

        // Wait for all terminals to complete
        int totalTransactions = 0;
        int totalSuccessfulTransactions = 0;
        Map<TpccTransactionType, Integer> transactionCounts = new HashMap<>();
        Map<TpccTransactionType, List<Long>> transactionLatencies = new HashMap<>();

        for (TpccTransactionType txType : TpccTransactionType.values()) {
            transactionCounts.put(txType, 0);
            transactionLatencies.put(txType, new ArrayList<>());
        }

        for (Future<TpccTerminalResult> future : futures) {
            try {
                TpccTerminalResult result = future.get();
                allLatencies.addAll(result.getLatencies());
                totalTransactions += result.getTotalTransactions();
                totalSuccessfulTransactions += result.getSuccessfulTransactions();
                
                // Aggregate transaction type statistics
                for (Map.Entry<TpccTransactionType, Integer> entry : result.getTransactionCounts().entrySet()) {
                    transactionCounts.put(entry.getKey(), 
                        transactionCounts.get(entry.getKey()) + entry.getValue());
                }
                
                for (Map.Entry<TpccTransactionType, List<Long>> entry : result.getTransactionLatencies().entrySet()) {
                    transactionLatencies.get(entry.getKey()).addAll(entry.getValue());
                }
                
            } catch (ExecutionException | InterruptedException e) {
                logger.error("Error in executing terminal task: {}", e.getMessage(), e);
            }
        }

        executor.shutdown();
        executor.awaitTermination(2, TimeUnit.SECONDS);

        long actualEndTimestamp = System.currentTimeMillis();

        // Sort all latencies by timestamp
        allLatencies.sort(Comparator.comparing(TpccTimestampedLatency::getTimestamp));

        // Add results
        addTpccResults(startTimestamp, actualEndTimestamp, allLatencies, totalTransactions, 
                      totalSuccessfulTransactions, transactionCounts, transactionLatencies, 
                      config, experimentNode);
    }

    private static List<TpccTerminal> createTerminals(int numWarehouses, int numTerminals) {
        List<TpccTerminal> terminals = new ArrayList<>();
        
        // Distribute terminals evenly across warehouses (following benchbase logic)
        final double terminalsPerWarehouse = (double) numTerminals / numWarehouses;
        int terminalId = 0;

        for (int w = 0; w < numWarehouses; w++) {
            int lowerTerminalId = (int) (w * terminalsPerWarehouse);
            int upperTerminalId = (int) ((w + 1) * terminalsPerWarehouse);
            int warehouseId = w + 1;
            
            if (warehouseId == numWarehouses) {
                upperTerminalId = numTerminals;
            }
            int numWarehouseTerminals = upperTerminalId - lowerTerminalId;

            // Distribute districts within the warehouse across terminals
            final double districtsPerTerminal = DISTRICTS_PER_WAREHOUSE / (double) numWarehouseTerminals;
            
            for (int t = 0; t < numWarehouseTerminals; t++) {
                int lowerDistrictId = (int) (t * districtsPerTerminal) + 1;
                int upperDistrictId = (int) ((t + 1) * districtsPerTerminal);
                
                if (t + 1 == numWarehouseTerminals) {
                    upperDistrictId = DISTRICTS_PER_WAREHOUSE;
                }

                terminals.add(new TpccTerminal(terminalId++, warehouseId, lowerDistrictId, upperDistrictId));
                
                logger.debug("Created terminal {} for warehouse {} with districts {}-{}", 
                           terminalId - 1, warehouseId, lowerDistrictId, upperDistrictId);
            }
        }

        return terminals;
    }

    private static class TpccTerminal {
        private final int terminalId;
        private final int warehouseId;
        private final int lowerDistrictId;
        private final int upperDistrictId;

        public TpccTerminal(int terminalId, int warehouseId, int lowerDistrictId, int upperDistrictId) {
            this.terminalId = terminalId;
            this.warehouseId = warehouseId;
            this.lowerDistrictId = lowerDistrictId;
            this.upperDistrictId = upperDistrictId;
        }

        public int getTerminalId() { return terminalId; }
        public int getWarehouseId() { return warehouseId; }
        public int getLowerDistrictId() { return lowerDistrictId; }
        public int getUpperDistrictId() { return upperDistrictId; }
    }

    private static class TpccTerminalTask implements Callable<TpccTerminalResult> {
        private final TpccTerminal terminal;
        private final long endTimestamp;
        private final int numWarehouses;
        private final CloseableHttpClient httpClient;
        private final Random terminalRandom;

        public TpccTerminalTask(TpccTerminal terminal, long endTimestamp, int numWarehouses) {
            this.terminal = terminal;
            this.endTimestamp = endTimestamp;
            this.numWarehouses = numWarehouses;
            this.terminalRandom = new Random();

            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            this.httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
        }

        @Override
        public TpccTerminalResult call() {
            List<TpccTimestampedLatency> latencies = new ArrayList<>();
            Map<TpccTransactionType, Integer> transactionCounts = new HashMap<>();
            Map<TpccTransactionType, List<Long>> transactionLatencies = new HashMap<>();
            
            for (TpccTransactionType txType : TpccTransactionType.values()) {
                transactionCounts.put(txType, 0);
                transactionLatencies.put(txType, new ArrayList<>());
            }

            int totalTransactions = 0;
            int successfulTransactions = 0;

            try {
                while (System.currentTimeMillis() < endTimestamp) {
                    // Choose transaction type based on TPC-C probabilities
                    TpccTransactionType transactionType = chooseTransactionType();
                    
                    // Pre-execution wait (keying time)
                    if (transactionType.getPreExecutionWaitMs() > 0) {
                        Thread.sleep(transactionType.getPreExecutionWaitMs());
                    }

                    // Execute transaction
                    TpccTimestampedLatency result = executeTransaction(transactionType);
                    
                    totalTransactions++;
                    transactionCounts.put(transactionType, transactionCounts.get(transactionType) + 1);
                    
                    if (result.getLatency() >= 0) {
                        latencies.add(result);
                        transactionLatencies.get(transactionType).add(result.getLatency());
                        successfulTransactions++;
                    }

                    // Post-execution wait (think time) - exponentially distributed
                    long thinkTime = calculateThinkTime(transactionType.getPostExecutionWaitMs());
                    if (thinkTime > 0) {
                        Thread.sleep(thinkTime);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Terminal {} interrupted", terminal.getTerminalId());
            } finally {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    logger.error("Error closing HttpClient for terminal {}", terminal.getTerminalId());
                }
            }

            return new TpccTerminalResult(latencies, totalTransactions, successfulTransactions, 
                                         transactionCounts, transactionLatencies);
        }

        private TpccTransactionType chooseTransactionType() {
            double rand = terminalRandom.nextDouble();
            double cumulative = 0.0;
            
            for (TpccTransactionType txType : TpccTransactionType.values()) {
                cumulative += txType.getProbability();
                if (rand <= cumulative) {
                    return txType;
                }
            }
            
            return TpccTransactionType.NEW_ORDER; // Fallback
        }

        private TpccTimestampedLatency executeTransaction(TpccTransactionType transactionType) {
            long requestTimestamp = System.currentTimeMillis();
            long start = System.nanoTime();

            try {
                String url = BASE_URL + transactionType.getEndpoint();
                String requestBody = generateRequestBody(transactionType);
                
                if (transactionType == TpccTransactionType.ORDER_STATUS || 
                    transactionType == TpccTransactionType.STOCK_LEVEL) {
                    // These are typically read-only transactions, use GET
                    HttpGet request = new HttpGet(url + "?" + requestBody);
                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        EntityUtils.consume(response.getEntity());
                        return new TpccTimestampedLatency(requestTimestamp, System.nanoTime() - start, transactionType);
                    }
                } else {
                    // These are write transactions, use POST
                    HttpPost request = new HttpPost(url);
                    request.setEntity(new StringEntity(requestBody));
                    request.setHeader("Content-Type", "application/json");
                    
                    try (CloseableHttpResponse response = httpClient.execute(request)) {
                        EntityUtils.consume(response.getEntity());
                        return new TpccTimestampedLatency(requestTimestamp, System.nanoTime() - start, transactionType);
                    }
                }
                
            } catch (Exception e) {
                logger.error("Error executing {} transaction for terminal {}: {}", 
                           transactionType, terminal.getTerminalId(), e.getMessage());
            }

            return new TpccTimestampedLatency(requestTimestamp, -1, transactionType);
        }

        private String generateRequestBody(TpccTransactionType transactionType) {
            // Generate realistic request parameters based on TPC-C specification
            int districtId = terminalRandom.nextInt(terminal.getUpperDistrictId() - terminal.getLowerDistrictId() + 1) 
                           + terminal.getLowerDistrictId();
            int customerId = terminalRandom.nextInt(CUSTOMERS_PER_DISTRICT) + 1;
            
            switch (transactionType) {
                case NEW_ORDER:
                    // Generate 5-15 order lines for new order
                    int numItems = terminalRandom.nextInt(11) + 5;
                    StringBuilder orderLines = new StringBuilder();
                    orderLines.append("[");
                    for (int i = 0; i < numItems; i++) {
                        if (i > 0) orderLines.append(",");
                        int itemId = terminalRandom.nextInt(ITEMS_COUNT) + 1;
                        int quantity = terminalRandom.nextInt(10) + 1;
                        orderLines.append(String.format(
                            "{\"itemId\":%d,\"supplierWarehouseId\":%d,\"quantity\":%d}",
                            itemId, terminal.getWarehouseId(), quantity));
                    }
                    orderLines.append("]");
                    return String.format(
                        "{\"warehouseId\":%d,\"districtId\":%d,\"customerId\":%d,\"orderLines\":%s}",
                        terminal.getWarehouseId(), districtId, customerId, orderLines.toString());
                
                case PAYMENT:
                    double amount = terminalRandom.nextDouble() * 5000 + 1; // $1-$5000
                    return String.format(
                        "{\"warehouseId\":%d,\"districtId\":%d,\"customerId\":%d,\"paymentAmount\":%.2f}",
                        terminal.getWarehouseId(), districtId, customerId, amount);
                
                case ORDER_STATUS:
                    return String.format("warehouseId=%d&districtId=%d&customerId=%d", 
                                       terminal.getWarehouseId(), districtId, customerId);
                
                case DELIVERY:
                    int carrierId = terminalRandom.nextInt(10) + 1; // 1-10
                    return String.format("{\"warehouseId\":%d,\"carrierId\":%d}", 
                                       terminal.getWarehouseId(), carrierId);
                
                case STOCK_LEVEL:
                    int threshold = terminalRandom.nextInt(20) + 10; // 10-20
                    return String.format("warehouseId=%d&districtId=%d&threshold=%d", 
                                       terminal.getWarehouseId(), districtId, threshold);
                
                default:
                    return String.format("warehouseId=%d&districtId=%d", terminal.getWarehouseId(), districtId);
            }
        }

        private long calculateThinkTime(long meanThinkTime) {
            // TPC-C uses exponentially distributed think times
            double c = terminalRandom.nextDouble();
            long thinkTime = (long) (-1 * Math.log(c) * meanThinkTime);
            return Math.min(thinkTime, 10 * meanThinkTime); // Cap at 10x mean
        }
    }

    private static class TpccTimestampedLatency {
        private final long timestamp;
        private final long latency;
        private final TpccTransactionType transactionType;

        public TpccTimestampedLatency(long timestamp, long latency, TpccTransactionType transactionType) {
            this.timestamp = timestamp;
            this.latency = latency;
            this.transactionType = transactionType;
        }

        public long getTimestamp() { return timestamp; }
        public long getLatency() { return latency; }
        public TpccTransactionType getTransactionType() { return transactionType; }
    }

    private static class TpccTerminalResult {
        private final List<TpccTimestampedLatency> latencies;
        private final int totalTransactions;
        private final int successfulTransactions;
        private final Map<TpccTransactionType, Integer> transactionCounts;
        private final Map<TpccTransactionType, List<Long>> transactionLatencies;

        public TpccTerminalResult(List<TpccTimestampedLatency> latencies, int totalTransactions, 
                                 int successfulTransactions, Map<TpccTransactionType, Integer> transactionCounts,
                                 Map<TpccTransactionType, List<Long>> transactionLatencies) {
            this.latencies = latencies;
            this.totalTransactions = totalTransactions;
            this.successfulTransactions = successfulTransactions;
            this.transactionCounts = transactionCounts;
            this.transactionLatencies = transactionLatencies;
        }

        public List<TpccTimestampedLatency> getLatencies() { return latencies; }
        public int getTotalTransactions() { return totalTransactions; }
        public int getSuccessfulTransactions() { return successfulTransactions; }
        public Map<TpccTransactionType, Integer> getTransactionCounts() { return transactionCounts; }
        public Map<TpccTransactionType, List<Long>> getTransactionLatencies() { return transactionLatencies; }
    }

    private static void addTpccResults(long startTimestamp, long endTimestamp, 
            List<TpccTimestampedLatency> allLatencies, int totalTransactions, 
            int totalSuccessfulTransactions, Map<TpccTransactionType, Integer> transactionCounts,
            Map<TpccTransactionType, List<Long>> transactionLatencies, TpccConfig config, 
            ObjectNode experimentNode) throws IOException {
        
        experimentNode.put("start_timestamp", startTimestamp);
        experimentNode.put("end_timestamp", endTimestamp);
        experimentNode.put("elapsed_time_ms", endTimestamp - startTimestamp);
        experimentNode.put("expected_duration_ms", config.getDurationSeconds() * 1000);
        
        // Overall metrics
        double elapsedTimeInSeconds = (endTimestamp - startTimestamp) / 1000.0;
        double throughput = totalTransactions / elapsedTimeInSeconds;
        double goodput = totalSuccessfulTransactions / elapsedTimeInSeconds;
        
        experimentNode.put("total_transactions", totalTransactions);
        experimentNode.put("successful_transactions", totalSuccessfulTransactions);
        experimentNode.put("throughput_tps", throughput);
        experimentNode.put("goodput_tps", goodput);
        
        // TPC-C specific metric: NewOrder transactions per minute (tpmC)
        int newOrderCount = transactionCounts.get(TpccTransactionType.NEW_ORDER);
        double tpmC = (newOrderCount / elapsedTimeInSeconds) * 60;
        experimentNode.put("new_order_tpm", tpmC);
        
        // Transaction type breakdown
        ObjectNode transactionBreakdownNode = experimentNode.putObject("transaction_breakdown");
        for (TpccTransactionType txType : TpccTransactionType.values()) {
            ObjectNode txNode = transactionBreakdownNode.putObject(txType.name());
            int count = transactionCounts.get(txType);
            List<Long> latencies = transactionLatencies.get(txType);
            
            txNode.put("count", count);
            txNode.put("percentage", totalTransactions > 0 ? (double) count / totalTransactions * 100 : 0);
            txNode.put("tps", count / elapsedTimeInSeconds);
            
            if (!latencies.isEmpty()) {
                List<Long> sortedLatencies = latencies.stream().sorted().collect(Collectors.toList());
                ObjectNode latencyNode = txNode.putObject("latency_stats");
                latencyNode.put("min_ns", sortedLatencies.get(0));
                latencyNode.put("max_ns", sortedLatencies.get(sortedLatencies.size() - 1));
                latencyNode.put("median_ns", median(sortedLatencies));
                latencyNode.put("p95_ns", percentile(sortedLatencies, 95));
                latencyNode.put("p99_ns", percentile(sortedLatencies, 99));
            }
        }
        
        // All latencies with timestamps
        ArrayNode latenciesArray = experimentNode.putArray("latencies");
        for (TpccTimestampedLatency latency : allLatencies) {
            ObjectNode latencyNode = latenciesArray.addObject();
            latencyNode.put("timestamp", latency.getTimestamp());
            latencyNode.put("latency_ns", latency.getLatency());
            latencyNode.put("transaction_type", latency.getTransactionType().name());
        }
        
        // Save intermediate results
        mapper.writeValue(new File(resultFileName), allResults);
        logger.info("TPC-C benchmark completed. tpmC: {:.2f}, Total TPS: {:.2f}", tpmC, throughput);
    }

    private static long median(List<Long> latencies) {
        int middle = latencies.size() / 2;
        return latencies.size() % 2 == 0 ? (latencies.get(middle - 1) + latencies.get(middle)) / 2
                : latencies.get(middle);
    }

    private static long percentile(List<Long> latencies, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * latencies.size()) - 1;
        index = Math.max(0, Math.min(index, latencies.size() - 1));
        return latencies.get(index);
    }

    // TPC-C Configuration Class
    @XmlRootElement(name = "tpcc-config")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TpccConfig {
        @XmlElement(name = "warehouses")
        private int warehouses = 1;
        
        @XmlElement(name = "terminals")
        private int terminals = 1;
        
        @XmlElement(name = "duration-seconds")
        private int durationSeconds = 300;

        public int getWarehouses() {
            return warehouses;
        }

        public int getTerminals() {
            return terminals;
        }

        public int getDurationSeconds() {
            return durationSeconds;
        }
    }
} 
