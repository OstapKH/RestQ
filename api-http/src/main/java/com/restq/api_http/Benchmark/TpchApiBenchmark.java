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
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.statistics.HistogramDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * TPC-H style API benchmark that uses configurable endpoints and probabilistic request distribution.
 * This benchmark is designed for analytical workloads with complex queries.
 */
public class TpchApiBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(TpchApiBenchmark.class);

    // Configuration files
    private static final String CONFIG_FILE = "benchmark-config.xml";
    private static final String PARAMETERS_FILE = "parameters.xml";
    private static final String BASE_URL = "http://localhost:8086/api/reports";
    private static final String BENCHMARK_TYPE = "TPCH";
    private static Map<String, List<String>> ENDPOINTS;

    private static final Random random = new Random();

    // Global JSON result object to store all experiment results
    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static ObjectNode allResults;
    private static String resultFileName;

    public static void main(String[] args) throws IOException, InterruptedException {
        try {
            logger.info("Starting TPC-H API benchmark with endpoint: {}", BASE_URL);
            
            // Initialize global result variables
            allResults = mapper.createObjectNode();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            resultFileName = "tpch_benchmark_results_" + timestamp + ".json";
            
            // Load XML configurations from classpath
            ClassLoader classLoader = TpchApiBenchmark.class.getClassLoader();
            
            JAXBContext configContext = JAXBContext.newInstance(BenchmarkConfig.class);
            Unmarshaller configUnmarshaller = configContext.createUnmarshaller();
            BenchmarkConfig benchmarkConfig = (BenchmarkConfig) configUnmarshaller.unmarshal(
                classLoader.getResourceAsStream(CONFIG_FILE));
            
            JAXBContext parametersContext = JAXBContext.newInstance(ParametersConfig.class);
            Unmarshaller parametersUnmarshaller = parametersContext.createUnmarshaller();
            ParametersConfig parametersConfig = (ParametersConfig) parametersUnmarshaller.unmarshal(
                classLoader.getResourceAsStream(PARAMETERS_FILE));
            
            // Add metadata to results
            allResults.put("timestamp", timestamp);
            allResults.put("benchmark_type", BENCHMARK_TYPE);
            allResults.put("config_file", CONFIG_FILE);
            allResults.put("parameters_file", PARAMETERS_FILE);
            allResults.put("base_url", BASE_URL);
            allResults.put("pauseBetweenExperiments_ms", benchmarkConfig.getPauseBetweenExperiments());
            
            // Add endpoints configuration from parameters file
            ObjectNode endpointsNode = allResults.putObject("endpoints");
            for (ParameterEndpointConfig paramEndpoint : parametersConfig.getEndpoints()) {
                ArrayNode urlsArray = endpointsNode.putArray(paramEndpoint.getName());
                for (String url : paramEndpoint.getUrls()) {
                    urlsArray.add(url);
                }
            }
            
            // Set up endpoint mappings from parameters file
            ENDPOINTS = new HashMap<>();
            for (ParameterEndpointConfig paramEndpoint : parametersConfig.getEndpoints()) {
                ENDPOINTS.put(paramEndpoint.getName(), paramEndpoint.getUrls());
            }
            
            // Add global configuration section
            ObjectNode globalConfigNode = allResults.putObject("global_config");
            globalConfigNode.put("pauseBetweenExperiments_ms", benchmarkConfig.getPauseBetweenExperiments());
            
            // Add experiments container
            ObjectNode experimentsNode = allResults.putObject("experiments");
            
            // Run each experiment
            for (ExperimentConfig experiment : benchmarkConfig.getExperiments()) {
                logger.info("Starting experiment: {}", experiment.getExperimentName());
                
                // Create a node for this experiment
                ObjectNode experimentNode = experimentsNode.putObject(experiment.getExperimentName());
                experimentNode.put("runs_configured", experiment.getRuns());
                experimentNode.put("connections", experiment.getConnections());
                experimentNode.put("requests_per_second", experiment.getRequestsPerSecond());
                experimentNode.put("duration_seconds", experiment.getDuration());
                experimentNode.put("pause_between_runs_ms", experiment.getPauseBetweenRuns());
                
                // Add probabilities to experiment config
                ObjectNode expProbNode = experimentNode.putObject("probabilities");
                for (Map.Entry<String, Double> entry : experiment.getProbabilitiesMap().entrySet()) {
                    expProbNode.put(entry.getKey(), entry.getValue());
                }
                
                // Add runs array to this experiment
                ArrayNode runsArray = experimentNode.putArray("runs");
                
                runExperiment(experiment, benchmarkConfig, runsArray);
                
                // Pause between experiments if there are more to run
                if (benchmarkConfig.getExperiments().indexOf(experiment) < benchmarkConfig.getExperiments().size() - 1) {
                    logger.info("Pausing for {} ms before next experiment", benchmarkConfig.getPauseBetweenExperiments());
                    Thread.sleep(benchmarkConfig.getPauseBetweenExperiments());
                }
            }
            
            // Write all results to a single file
            mapper.writeValue(new File(resultFileName), allResults);
            logger.info("All experiments completed. Results saved to {}", resultFileName);
            
        } catch (JAXBException e) {
            logger.error("Error parsing XML configuration: {}", e.getMessage(), e);
        }
    }
    
    private static void runExperiment(ExperimentConfig experiment, BenchmarkConfig benchmarkConfig, ArrayNode runsArray) throws InterruptedException, IOException {
        for (int run = 0; run < experiment.getRuns(); run++) {
            logger.info("Starting run {} of {} for experiment {}", run + 1, experiment.getRuns(), experiment.getExperimentName());

            ExecutorService executor = Executors.newFixedThreadPool(experiment.getConnections());
            List<Future<ClientTaskResult>> futures = new ArrayList<>();
            List<TimestampedLatency> allLatencies = new ArrayList<>();

            // Create a queue for each connection
            List<BlockingQueue<String>> connectionQueues = new ArrayList<>();
            for (int i = 0; i < experiment.getConnections(); i++) {
                connectionQueues.add(new LinkedBlockingQueue<>());
            }

            long startTimestamp = System.currentTimeMillis();
            long endTimestamp = startTimestamp + experiment.getDuration() * 1000;

            // Create and start a producer thread for EACH connection
            List<Thread> producerThreads = new ArrayList<>();
            for (int i = 0; i < experiment.getConnections(); i++) {
                final int connectionIndex = i;
                Thread producerThread = new Thread(() -> {
                    try {
                        while (System.currentTimeMillis() < endTimestamp) {
                            long startTime = System.currentTimeMillis();
                            
                            for (int j = 0; j < experiment.getRequestsPerSecond(); j++) {
                                String endpoint = chooseEndpoint(experiment.getProbabilitiesMap());
                                connectionQueues.get(connectionIndex).put(endpoint);
                            }

                            long elapsedTime = System.currentTimeMillis() - startTime;
                            if (elapsedTime < 1000) {
                                Thread.sleep(1000 - elapsedTime);
                            } else {
                                logger.warn("Warning: Adding requests took longer than 1 second for connection {}", connectionIndex);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                producerThread.start();
                producerThreads.add(producerThread);
            }

            // Start a client task for each connection
            for (int i = 0; i < experiment.getConnections(); i++) {
                futures.add(executor.submit(new ClientTask(experiment, connectionQueues.get(i), endTimestamp)));
            }

            // Wait for all producer threads to finish
            for (Thread producerThread : producerThreads) {
                producerThread.join();
            }

            int totalSuccessfulRequests = 0;
            for (Future<ClientTaskResult> future : futures) {
                try {
                    ClientTaskResult result = future.get();
                    allLatencies.addAll(result.getLatencies());
                    totalSuccessfulRequests += result.getSuccessfulRequests();
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("Error in executing client task: {}", e.getMessage(), e);
                }
            }

            // Sort all latencies by timestamp to maintain chronological order
            allLatencies.sort(Comparator.comparing(TimestampedLatency::getTimestamp));

            // Extract just the latency values for calculations
            List<Long> orderedLatencyValues = allLatencies.stream()
                .map(TimestampedLatency::getLatency)
                .collect(Collectors.toList());

            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);

            long actualEndTimestamp = System.currentTimeMillis();

            // Add results for this run
            addRunResults(startTimestamp, actualEndTimestamp, orderedLatencyValues, allLatencies,
                    totalSuccessfulRequests, experiment.getConnections(), run, experiment, runsArray);

            // Pause between runs if there are more to run
            if (run < experiment.getRuns() - 1) {
                logger.info("Pausing for {} ms before next run", experiment.getPauseBetweenRuns());
                Thread.sleep(experiment.getPauseBetweenRuns());
            }
        }
    }

    private static class ClientTask implements Callable<ClientTaskResult> {
        private final ExperimentConfig experiment;
        private final CloseableHttpClient httpClient;
        private final BlockingQueue<String> queue;
        private final long endTimestamp;
        private int successfulRequests = 0;

        public ClientTask(ExperimentConfig experiment, BlockingQueue<String> queue, long endTimestamp) {
            this.experiment = experiment;
            this.queue = queue;
            this.endTimestamp = endTimestamp;

            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
            this.httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
        }

        @Override
        public ClientTaskResult call() {
            List<TimestampedLatency> latencies = new ArrayList<>();
            try {
                while (System.currentTimeMillis() < endTimestamp) {
                    String endpoint = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (endpoint != null) {
                        try {
                            HttpGet request = new HttpGet(BASE_URL + endpoint);
                            TimestampedLatency result = sendRequest(request);
                            if (result.getLatency() >= 0) {
                                latencies.add(result);
                                successfulRequests++;
                            }
                        } catch (Exception e) {
                            logger.error("Error making request for endpoint: {}", endpoint, e);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Client task interrupted", e);
            } finally {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    logger.error("Error closing HttpClient", e);
                }
            }
            return new ClientTaskResult(latencies, successfulRequests);
        }

        private TimestampedLatency sendRequest(HttpGet request) {
            long requestTimestamp = System.currentTimeMillis();
            long start = System.nanoTime();

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                EntityUtils.consume(response.getEntity());
                return new TimestampedLatency(requestTimestamp, System.nanoTime() - start);
            } catch (NoHttpResponseException e) {
                logger.error("NoHttpResponseException: The server did not respond. Request: {}", request.toString());
            } catch (IOException e) {
                logger.error("IOException occurred while sending request: {}, Message: {}", request.toString(), e.getMessage());
            }

            return new TimestampedLatency(requestTimestamp, -1);
        }
    }

    private static class TimestampedLatency {
        private final long timestamp;
        private final long latency;

        public TimestampedLatency(long timestamp, long latency) {
            this.timestamp = timestamp;
            this.latency = latency;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getLatency() {
            return latency;
        }
    }

    private static class ClientTaskResult {
        private final List<TimestampedLatency> latencies;
        private final int successfulRequests;

        public ClientTaskResult(List<TimestampedLatency> latencies, int successfulRequests) {
            this.latencies = latencies;
            this.successfulRequests = successfulRequests;
        }

        public List<TimestampedLatency> getLatencies() {
            return latencies;
        }

        public int getSuccessfulRequests() {
            return successfulRequests;
        }
    }

    private static String chooseEndpoint(Map<String, Double> probabilities) {
        double rand = random.nextDouble();
        double cumulative = 0.0;
        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                List<String> urls = ENDPOINTS.get(entry.getKey());
                if (urls != null && !urls.isEmpty()) {
                    return urls.get(random.nextInt(urls.size()));
                }
            }
        }
        // Fallback: get the first URL from the first endpoint
        List<String> fallbackUrls = ENDPOINTS.values().iterator().next();
        return fallbackUrls.get(0);
    }

    private static void addRunResults(long startTimestamp, long endTimestamp, List<Long> latencyValues,
            List<TimestampedLatency> allLatencies, int totalSuccessfulRequests, int connections, 
            int run, ExperimentConfig experiment, ArrayNode runsArray) throws IOException {
        
        ObjectNode runNode = mapper.createObjectNode();
        runNode.put("run_number", run);
        runNode.put("timestamp", System.currentTimeMillis());
        runNode.put("start_timestamp", startTimestamp);
        runNode.put("end_timestamp", endTimestamp);
        runNode.put("elapsed_time_ms", endTimestamp - startTimestamp);
        runNode.put("expected_duration_ms", experiment.getDuration() * 1000);
        runNode.put("terminals", connections);
        runNode.put("connections", experiment.getConnections());
        runNode.put("requests_per_second", experiment.getRequestsPerSecond());
        runNode.put("experiment_name", experiment.getExperimentName());
        
        // Add chronologically ordered latencies with timestamps
        ArrayNode latenciesArray = runNode.putArray("latencies");
        for (TimestampedLatency latency : allLatencies) {
            ObjectNode latencyNode = latenciesArray.addObject();
            latencyNode.put("timestamp", latency.getTimestamp());
            latencyNode.put("latency_ns", latency.getLatency());
        }
        
        // Add latency distribution
        List<Long> sortedLatencies = latencyValues.stream().sorted().collect(Collectors.toList());
        ObjectNode latencyNode = runNode.putObject("latency_distribution");
        if (!sortedLatencies.isEmpty()) {
            latencyNode.put("median_latency_ns", median(sortedLatencies));
            latencyNode.put("min_latency_ns", sortedLatencies.get(0));
            latencyNode.put("max_latency_ns", sortedLatencies.get(sortedLatencies.size() - 1));
            
            // Add percentiles
            ObjectNode percentileNode = latencyNode.putObject("percentiles");
            addPercentile(sortedLatencies, 25, percentileNode);
            addPercentile(sortedLatencies, 75, percentileNode);
            addPercentile(sortedLatencies, 90, percentileNode);
            addPercentile(sortedLatencies, 95, percentileNode);
            addPercentile(sortedLatencies, 99, percentileNode);
        }
        
        // Add performance metrics
        long totalRequests = latencyValues.size();
        double elapsedTimeInSeconds = (endTimestamp - startTimestamp) / 1000.0;
        double throughput = totalRequests / elapsedTimeInSeconds;
        runNode.put("throughput", throughput);
        
        double goodput = totalSuccessfulRequests / elapsedTimeInSeconds;
        runNode.put("goodput", goodput);
        runNode.put("total_requests", totalRequests);
        runNode.put("successful_requests", totalSuccessfulRequests);
        
        runsArray.add(runNode);
        
        // Save intermediate results after each run
        mapper.writeValue(new File(resultFileName), allResults);
        logger.info("Updated results for experiment: {}, run: {}", experiment.getExperimentName(), run);
    }

    private static void addPercentile(List<Long> latencies, int percentile, ObjectNode node) {
        int index = (int) Math.ceil(percentile / 100.0 * latencies.size()) - 1;
        index = Math.max(0, Math.min(index, latencies.size() - 1));
        node.put("p" + percentile, latencies.get(index));
    }

    private static long median(List<Long> latencies) {
        int middle = latencies.size() / 2;
        return latencies.size() % 2 == 0 ? (latencies.get(middle - 1) + latencies.get(middle)) / 2
                : latencies.get(middle);
    }
    
    // XML Configuration Classes - same as original
    
    @XmlRootElement(name = "benchmark-config")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class BenchmarkConfig {
        @XmlElement(name = "pauseBetweenExperiments-ms")
        private int pauseBetweenExperiments;
        
        @XmlElementWrapper(name = "endpoints")
        @XmlElement(name = "endpoint")
        private List<EndpointConfig> endpoints;
        
        @XmlElement(name = "experiment")
        private List<ExperimentConfig> experiments;

        public int getPauseBetweenExperiments() {
            return pauseBetweenExperiments;
        }

        public List<EndpointConfig> getEndpoints() {
            return endpoints;
        }

        public List<ExperimentConfig> getExperiments() {
            return experiments;
        }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class EndpointConfig {
        @XmlAttribute(name = "name")
        private String name;

        public String getName() {
            return name;
        }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ExperimentConfig {
        @XmlElement(name = "experiment_name")
        private String experimentName;
        
        @XmlElement(name = "runs")
        private int runs;
        
        @XmlElement(name = "pause-between-runs-ms")
        private int pauseBetweenRuns;
        
        @XmlElement(name = "connections")
        private int connections;
        
        @XmlElement(name = "requests-per-second")
        private int requestsPerSecond;
        
        @XmlElement(name = "duration-seconds")
        private int duration;
        
        @XmlElementWrapper(name = "probabilities")
        @XmlElement(name = "probability")
        private List<ProbabilityConfig> probabilities;
        
        public Map<String, Double> getProbabilitiesMap() {
            Map<String, Double> result = new HashMap<>();
            for (ProbabilityConfig probability : probabilities) {
                result.put(probability.getEndpoint(), probability.getValue());
            }
            return result;
        }

        public String getExperimentName() {
            return experimentName;
        }

        public int getRuns() {
            return runs;
        }

        public int getPauseBetweenRuns() {
            return pauseBetweenRuns;
        }

        public int getConnections() {
            return connections;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public int getDuration() {
            return duration;
        }

        public List<ProbabilityConfig> getProbabilities() {
            return probabilities;
        }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ProbabilityConfig {
        @XmlAttribute(name = "endpoint")
        private String endpoint;
        
        @XmlValue
        private double value;

        public String getEndpoint() {
            return endpoint;
        }

        public double getValue() {
            return value;
        }
    }
    
    @XmlRootElement(name = "parameters")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ParametersConfig {
        @XmlElement(name = "endpoint")
        private List<ParameterEndpointConfig> endpoints;

        public List<ParameterEndpointConfig> getEndpoints() {
            return endpoints;
        }
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class ParameterEndpointConfig {
        @XmlAttribute(name = "name")
        private String name;
        
        @XmlElement(name = "url")
        private List<String> urls;

        public String getName() {
            return name;
        }

        public List<String> getUrls() {
            return urls;
        }
    }
} 
