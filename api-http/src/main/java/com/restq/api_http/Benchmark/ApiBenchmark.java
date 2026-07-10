package com.restq.api_http.Benchmark;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
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
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.http.io.SocketConfig;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;

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

import org.apache.hc.core5.http.message.BasicHeader;

public class ApiBenchmark {

    private static final Logger logger = LoggerFactory.getLogger(ApiBenchmark.class);

    /** Classpath name of the bundled schedule configuration, overridable via $BENCHMARK_CONFIG. */
    private static final String CONFIG_FILE = "benchmark-config.xml";

    /** Classpath name of the bundled endpoint parameters, overridable via $BENCHMARK_PARAMETERS. */
    private static final String PARAMETERS_FILE = "parameters.xml";

    /** API base URL, from $BENCHMARK_BASE_URL. */
    private static String BASE_URL = System.getenv().getOrDefault("BENCHMARK_BASE_URL", "http://localhost:8086/api/reports");

    /** Benchmark type recorded in the results, from $BENCHMARK_TYPE. */
    private static String BENCHMARK_TYPE = System.getenv().getOrDefault("BENCHMARK_TYPE", "TPCH");

    /** Endpoint name to URL list, loaded from the parameters file. */
    private static Map<String, List<String>> ENDPOINTS;

    private static final Random random = new Random();

    /** Initial port used by each thread. */
    private static final ConcurrentMap<String, Integer> initialPortMap = new ConcurrentHashMap<>();

    private static ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Global JSON document accumulating the results of all experiments. */
    private static ObjectNode allResults;
    private static String resultFileName;

    /**
     * Loads the XML configuration, validates the endpoints, runs every
     * experiment in sequence, and writes all results to one timestamped
     * JSON file.
     */
    public static void main(String[] args) throws IOException, InterruptedException {
        try {

            logger.info("Starting {} benchmark with API endpoint: {}", BENCHMARK_TYPE, BASE_URL);

            allResults = mapper.createObjectNode();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            resultFileName = "benchmark_results_" + BENCHMARK_TYPE.toLowerCase() + "_" + timestamp + ".json";
            
            ClassLoader classLoader = ApiBenchmark.class.getClassLoader();

            JAXBContext configContext = JAXBContext.newInstance(BenchmarkConfig.class);
            Unmarshaller configUnmarshaller = configContext.createUnmarshaller();
            BenchmarkConfig benchmarkConfig;
            try (InputStream configIn = openConfig("BENCHMARK_CONFIG", CONFIG_FILE, classLoader)) {
                benchmarkConfig = (BenchmarkConfig) configUnmarshaller.unmarshal(configIn);
            }

            JAXBContext parametersContext = JAXBContext.newInstance(ParametersConfig.class);
            Unmarshaller parametersUnmarshaller = parametersContext.createUnmarshaller();
            ParametersConfig parametersConfig;
            try (InputStream paramsIn = openConfig("BENCHMARK_PARAMETERS", PARAMETERS_FILE, classLoader)) {
                parametersConfig = (ParametersConfig) parametersUnmarshaller.unmarshal(paramsIn);
            }
            
            allResults.put("timestamp", timestamp);
            allResults.put("benchmark_type", BENCHMARK_TYPE);
            allResults.put("config_file", CONFIG_FILE);
            allResults.put("parameters_file", PARAMETERS_FILE);
            allResults.put("base_url", BASE_URL);
            allResults.put("pauseBetweenExperiments_ms", benchmarkConfig.getPauseBetweenExperiments());
            
            ObjectNode endpointsNode = allResults.putObject("endpoints");
            for (ParameterEndpointConfig paramEndpoint : parametersConfig.getEndpoints()) {
                ArrayNode urlsArray = endpointsNode.putArray(paramEndpoint.getName());
                for (String url : paramEndpoint.getUrls()) {
                    urlsArray.add(url);
                }
            }
            
            ENDPOINTS = new HashMap<>();
            for (ParameterEndpointConfig paramEndpoint : parametersConfig.getEndpoints()) {
                ENDPOINTS.put(paramEndpoint.getName(), paramEndpoint.getUrls());
            }
            
            ObjectNode globalConfigNode = allResults.putObject("global_config");
            globalConfigNode.put("pauseBetweenExperiments_ms", benchmarkConfig.getPauseBetweenExperiments());

            preflightValidation(benchmarkConfig);

            ObjectNode experimentsNode = allResults.putObject("experiments");

            for (ExperimentConfig experiment : benchmarkConfig.getExperiments()) {
                logger.info("Starting experiment: {}", experiment.getExperimentName());

                ObjectNode experimentNode = experimentsNode.putObject(experiment.getExperimentName());
                experimentNode.put("runs_configured", experiment.getRuns());
                experimentNode.put("connections", experiment.getConnections());
                experimentNode.put("requests_per_second", experiment.getRequestsPerSecond());
                experimentNode.put("duration_seconds", experiment.getDuration());
                experimentNode.put("pause_between_runs_ms", experiment.getPauseBetweenRuns());
                experimentNode.put("pacing", experiment.getPacing());
                experimentNode.put("warmup", experiment.isWarmup());
                
                ObjectNode expProbNode = experimentNode.putObject("probabilities");
                for (Map.Entry<String, Double> entry : experiment.getProbabilitiesMap().entrySet()) {
                    expProbNode.put(entry.getKey(), entry.getValue());
                }
                
                ArrayNode runsArray = experimentNode.putArray("runs");

                runExperiment(experiment, benchmarkConfig, runsArray);

                if (benchmarkConfig.getExperiments().indexOf(experiment) < benchmarkConfig.getExperiments().size() - 1) {
                    logger.info("Pausing for {} ms before next experiment", benchmarkConfig.getPauseBetweenExperiments());
                    Thread.sleep(benchmarkConfig.getPauseBetweenExperiments());
                }
            }
            
            mapper.writeValue(new File(resultFileName), allResults);
            logger.info("All experiments completed. Results saved to {}", resultFileName);
            
        } catch (JAXBException e) {
            logger.error("Error parsing XML configuration: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Opens a configuration stream. An external file named by envVar wins
     * over the copy bundled in the jar, so schedules change without a
     * rebuild; without the variable the bundled resource is used.
     */
    private static InputStream openConfig(String envVar, String classpathName, ClassLoader cl) throws IOException {
        String path = System.getenv(envVar);
        if (path != null && !path.isBlank()) {
            logger.info("Loading {} from external file: {}", classpathName, path);
            return new FileInputStream(path);
        }
        logger.info("Loading {} from classpath (set ${} to override without rebuilding)",
                classpathName, envVar);
        return cl.getResourceAsStream(classpathName);
    }

    /**
     * Fire every endpoint URL referenced (probability > 0) by any experiment
     * once, before the schedule starts. A benchmark against endpoints that
     * error or return empty result sets measures nothing — abort instead.
     * The report is embedded in the results JSON either way.
     */
    private static void preflightValidation(BenchmarkConfig benchmarkConfig) throws IOException {
        Set<String> usedEndpoints = new LinkedHashSet<>();
        for (ExperimentConfig experiment : benchmarkConfig.getExperiments()) {
            for (Map.Entry<String, Double> e : experiment.getProbabilitiesMap().entrySet()) {
                if (e.getValue() > 0) {
                    usedEndpoints.add(e.getKey());
                }
            }
        }

        ArrayNode report = allResults.putArray("preflight_validation");
        boolean allOk = true;
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            for (String name : usedEndpoints) {
                List<String> urls = ENDPOINTS.getOrDefault(name, List.of());
                if (urls.isEmpty()) {
                    allOk = false;
                    report.addObject().put("endpoint", name)
                          .put("ok", false).put("problem", "no URLs in parameters file");
                    continue;
                }
                for (String url : urls) {
                    ObjectNode row = report.addObject();
                    row.put("endpoint", name).put("url", url);
                    try (CloseableHttpResponse response = client.execute(new HttpGet(BASE_URL + url))) {
                        int status = response.getCode();
                        String body = response.getEntity() != null
                                ? EntityUtils.toString(response.getEntity()) : "";
                        String stripped = body.strip();
                        boolean emptyResult = stripped.isEmpty()
                                || stripped.equals("[]") || stripped.equals("null");
                        boolean ok = status >= 200 && status < 300 && !emptyResult;
                        row.put("status", status).put("body_bytes", body.length()).put("ok", ok);
                        if (!ok) {
                            allOk = false;
                            row.put("problem", status >= 300
                                    ? "non-2xx status" : "empty result set");
                            logger.error("Preflight FAILED for {} {}: status={}, body={} bytes",
                                    name, url, status, body.length());
                        }
                    } catch (Exception e) {
                        allOk = false;
                        row.put("ok", false).put("problem", "request failed: " + e.getMessage());
                        logger.error("Preflight request failed for {} {}: {}", name, url, e.getMessage());
                    }
                }
            }
        }
        allResults.put("preflight_passed", allOk);
        if (!allOk) {
            mapper.writeValue(new File(resultFileName), allResults);
            logger.error("Preflight validation failed — aborting benchmark. Report in {}", resultFileName);
            System.exit(2);
        }
        logger.info("Preflight validation passed: {} endpoint URL(s) return non-empty 2xx responses",
                report.size());
    }

    /**
     * Executes the configured number of runs for one experiment. Each run
     * pairs every connection with its own request queue, producer thread,
     * and client task, then aggregates the latencies in chronological order
     * and records the run's results. Latency distributions are computed but
     * chart images are not saved.
     */
    private static void runExperiment(ExperimentConfig experiment, BenchmarkConfig benchmarkConfig, ArrayNode runsArray) throws InterruptedException, IOException {
        for (int run = 0; run < experiment.getRuns(); run++) {
            logger.info("Starting run {} of {} for experiment {}", run + 1, experiment.getRuns(), experiment.getExperimentName());

            initialPortMap.clear();

            ExecutorService executor = Executors.newFixedThreadPool(experiment.getConnections());
            List<Future<ClientTaskResult>> futures = new ArrayList<>();
            List<TimestampedLatency> allLatencies = new ArrayList<>();

            List<BlockingQueue<String>> connectionQueues = new ArrayList<>();
            for (int i = 0; i < experiment.getConnections(); i++) {
                connectionQueues.add(new LinkedBlockingQueue<>());
            }

            long startTimestamp = System.currentTimeMillis();
            long endTimestamp = startTimestamp + experiment.getDuration() * 1000;

            List<Thread> producerThreads = new ArrayList<>();
            for (int i = 0; i < experiment.getConnections(); i++) {
                final int connectionIndex = i;
                Thread producerThread = new Thread(() ->
                        produceRequests(experiment, connectionQueues.get(connectionIndex),
                                        endTimestamp, connectionIndex));
                producerThread.start();
                producerThreads.add(producerThread);
            }

            for (int i = 0; i < experiment.getConnections(); i++) {
                futures.add(executor.submit(new ClientTask(experiment, connectionQueues.get(i), endTimestamp)));
            }

            for (Thread producerThread : producerThreads) {
                producerThread.join();
            }

            int totalSuccessfulRequests = 0;
            ResponseCounts totalCounts = new ResponseCounts();
            for (Future<ClientTaskResult> future : futures) {
                try {
                    ClientTaskResult result = future.get();
                    allLatencies.addAll(result.getLatencies());
                    totalSuccessfulRequests += result.getSuccessfulRequests();
                    totalCounts.add(result.getCounts());
                } catch (ExecutionException | InterruptedException e) {
                    logger.error("Error in executing client task: {}", e.getMessage(), e);
                }
            }

            allLatencies.sort(Comparator.comparing(TimestampedLatency::getTimestamp));

            List<Long> orderedLatencyValues = allLatencies.stream()
                .map(TimestampedLatency::getLatency)
                .collect(Collectors.toList());

            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.SECONDS);

            long actualEndTimestamp = System.currentTimeMillis();

            addRunResults(startTimestamp, actualEndTimestamp, orderedLatencyValues, allLatencies,
                    totalSuccessfulRequests, totalCounts, experiment.getConnections(), run, experiment, runsArray);

            createLatencyHistogram(orderedLatencyValues, experiment.getExperimentName() + "_run" + run);
            createLatencyCDF(orderedLatencyValues, experiment.getExperimentName() + "_run" + run);

            if (run < experiment.getRuns() - 1) {
                logger.info("Pausing for {} ms before next run", experiment.getPauseBetweenRuns());
                Thread.sleep(experiment.getPauseBetweenRuns());
            }
        }
    }

    /**
     * Feeds one connection's queue at the configured rate.
     *
     * <p>Pacing modes:
     * <ul>
     *   <li><b>burst</b> — the historical behavior: enqueues the whole
     *       second's quota at once, then sleeps to the next second. All
     *       connections fire simultaneously at each tick (synchronized
     *       bursts).</li>
     *   <li><b>uniform</b> — requests spaced evenly at 1/rps intervals on an
     *       absolute (drift-free) schedule.</li>
     *   <li><b>poisson</b> — exponential inter-arrival times with mean
     *       1/rps: an open-loop memoryless arrival process, the standard for
     *       latency studies.</li>
     * </ul>
     *
     * <p>When the producer falls more than one second behind (a stalled
     * consumer or an unreachable rate), it resynchronizes to the current
     * time instead of bursting. The mode is recorded in the results JSON so
     * runs stay interpretable.
     */
    private static void produceRequests(ExperimentConfig experiment, BlockingQueue<String> queue,
                                        long endTimestamp, int connectionIndex) {
        String pacing = experiment.getPacing();
        try {
            if ("burst".equals(pacing)) {
                while (System.currentTimeMillis() < endTimestamp) {
                    long startTime = System.currentTimeMillis();
                    for (int j = 0; j < experiment.getRequestsPerSecond(); j++) {
                        queue.put(chooseEndpoint(experiment.getProbabilitiesMap()));
                    }
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    if (elapsedTime < 1000) {
                        Thread.sleep(1000 - elapsedTime);
                    } else {
                        logger.warn("Warning: Adding requests took longer than 1 second for connection {}", connectionIndex);
                    }
                }
                return;
            }

            double meanIntervalNs = 1e9 / experiment.getRequestsPerSecond();
            boolean poisson = "poisson".equals(pacing);
            long nextNs = System.nanoTime();
            while (System.currentTimeMillis() < endTimestamp) {
                queue.put(chooseEndpoint(experiment.getProbabilitiesMap()));
                nextNs += poisson
                        ? (long) (-Math.log(1.0 - random.nextDouble()) * meanIntervalNs)
                        : (long) meanIntervalNs;
                long waitNs = nextNs - System.nanoTime();
                if (waitNs > 0) {
                    LockSupport.parkNanos(waitNs);
                } else if (waitNs < -1_000_000_000L) {
                    nextNs = System.nanoTime();
                    logger.warn("Pacing fell behind on connection {}; resynchronized", connectionIndex);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * One connection worker: consumes endpoints from its queue with a
     * dedicated HTTP client and records a timestamped latency per request.
     */
    private static class ClientTask implements Callable<ClientTaskResult> {
        private final ExperimentConfig experiment;
        private final CloseableHttpClient httpClient;
        private final BlockingQueue<String> queue;
        private final long endTimestamp;
        private int successfulRequests = 0;
        private final String threadName;

        public ClientTask(ExperimentConfig experiment, BlockingQueue<String> queue, long endTimestamp) {
            this.experiment = experiment;
            this.queue = queue;
            this.endTimestamp = endTimestamp;
            this.threadName = Thread.currentThread().getName();

            PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();

            this.httpClient = HttpClients.custom()
                    .setConnectionManager(connectionManager)
                    .build();
        }

        /**
         * Drains the queue until the deadline. Every completed response
         * contributes a latency, but only 2xx responses count as successful
         * requests.
         */
        @Override
        public ClientTaskResult call() {
            List<TimestampedLatency> latencies = new ArrayList<>();
            ResponseCounts counts = new ResponseCounts();
            try {
                while (System.currentTimeMillis() < endTimestamp) {
                    String endpoint = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (endpoint != null) {
                        try {
                            HttpGet request = new HttpGet(BASE_URL + endpoint);
                            TimestampedLatency result = sendRequest(request);
                            counts.record(result.getStatusCode());
                            if (result.getLatency() >= 0) {
                                latencies.add(result);
                                if (result.isSuccess()) {
                                    successfulRequests++;
                                }
                            }
                        } catch (Exception e) {
                            counts.record(-1);
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
            return new ClientTaskResult(latencies, successfulRequests, counts);
        }

        private TimestampedLatency sendRequest(HttpGet request) {
            long requestTimestamp = System.currentTimeMillis();
            long start = System.nanoTime();

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int status = response.getCode();
                EntityUtils.consume(response.getEntity());
                return new TimestampedLatency(requestTimestamp, System.nanoTime() - start, status);
            } catch (NoHttpResponseException e) {
                logger.error("NoHttpResponseException: The server did not respond. Details:");
                logger.error("Request: {}", request.toString());
                e.printStackTrace();
            } catch (IOException e) {
                logger.error("IOException occurred while sending request");
                logger.error("Request: {}", request.toString());
                logger.error("Message: {}", e.getMessage());
                e.printStackTrace();
            }

            return new TimestampedLatency(requestTimestamp, -1, -1);
        }
    }

    /** One request's latency sample, tied to the moment the request was made. */
    private static class TimestampedLatency {
        /** Epoch milliseconds at which the request was made. */
        private final long timestamp;

        /** Latency in nanoseconds; -1 when the request never completed. */
        private final long latency;

        /** HTTP status code; -1 denotes a transport error. */
        private final int statusCode;

        public TimestampedLatency(long timestamp, long latency, int statusCode) {
            this.timestamp = timestamp;
            this.latency = latency;
            this.statusCode = statusCode;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getLatency() {
            return latency;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    /** Per-status-class response counts; merged across connections. */
    private static class ResponseCounts {
        int ok2xx, err4xx, err5xx, otherStatus, transportErrors;

        void record(int statusCode) {
            if (statusCode == -1) transportErrors++;
            else if (statusCode >= 200 && statusCode < 300) ok2xx++;
            else if (statusCode >= 400 && statusCode < 500) err4xx++;
            else if (statusCode >= 500) err5xx++;
            else otherStatus++;
        }

        void add(ResponseCounts other) {
            ok2xx += other.ok2xx;
            err4xx += other.err4xx;
            err5xx += other.err5xx;
            otherStatus += other.otherStatus;
            transportErrors += other.transportErrors;
        }
    }

    /** Aggregated outcome of one client task. */
    private static class ClientTaskResult {
        private final List<TimestampedLatency> latencies;
        private final int successfulRequests;
        private final ResponseCounts counts;

        public ClientTaskResult(List<TimestampedLatency> latencies, int successfulRequests,
                                ResponseCounts counts) {
            this.latencies = latencies;
            this.successfulRequests = successfulRequests;
            this.counts = counts;
        }

        public List<TimestampedLatency> getLatencies() {
            return latencies;
        }

        public int getSuccessfulRequests() {
            return successfulRequests;
        }

        public ResponseCounts getCounts() {
            return counts;
        }
    }

    /**
     * Picks an endpoint by cumulative probability and returns a random URL
     * from its list. Falls back to the first URL of the first endpoint when
     * no entry matches.
     */
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
        List<String> fallbackUrls = ENDPOINTS.values().iterator().next();
        return fallbackUrls.get(0);
    }

    /**
     * Appends one run's metrics to the runs array and persists intermediate
     * results after every run. Records the chronological latency series, the
     * latency distribution with percentiles, throughput and goodput, and
     * per-status-class response counts — successful_requests counts 2xx
     * only, so a run full of 500s is distinguishable from a healthy one.
     */
    private static void addRunResults(long startTimestamp, long endTimestamp, List<Long> latencyValues,
            List<TimestampedLatency> allLatencies, int totalSuccessfulRequests, ResponseCounts counts,
            int connections, int run, ExperimentConfig experiment, ArrayNode runsArray) throws IOException {

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

        ArrayNode latenciesArray = runNode.putArray("latencies");
        for (TimestampedLatency latency : allLatencies) {
            ObjectNode latencyNode = latenciesArray.addObject();
            latencyNode.put("timestamp", latency.getTimestamp());
            latencyNode.put("latency_ns", latency.getLatency());
        }
        
        List<Long> sortedLatencies = latencyValues.stream().sorted().collect(Collectors.toList());
        ObjectNode latencyNode = runNode.putObject("latency_distribution");
        if (!sortedLatencies.isEmpty()) {
            latencyNode.put("median_latency_ns", median(sortedLatencies));
            latencyNode.put("min_latency_ns", sortedLatencies.get(0));
            latencyNode.put("max_latency_ns", sortedLatencies.get(sortedLatencies.size() - 1));

            ObjectNode percentileNode = latencyNode.putObject("percentiles");
            addPercentile(sortedLatencies, 25, percentileNode);
            addPercentile(sortedLatencies, 75, percentileNode);
            addPercentile(sortedLatencies, 90, percentileNode);
            addPercentile(sortedLatencies, 95, percentileNode);
            addPercentile(sortedLatencies, 99, percentileNode);
        }
        
        long totalRequests = latencyValues.size();
        double elapsedTimeInSeconds = (endTimestamp - startTimestamp) / 1000.0;
        double throughput = totalRequests / elapsedTimeInSeconds;
        runNode.put("throughput", throughput);
        
        double goodput = totalSuccessfulRequests / elapsedTimeInSeconds;
        runNode.put("goodput", goodput);
        runNode.put("total_requests", totalRequests);
        runNode.put("successful_requests", totalSuccessfulRequests);

        ObjectNode responsesNode = runNode.putObject("responses");
        responsesNode.put("status_2xx", counts.ok2xx);
        responsesNode.put("status_4xx", counts.err4xx);
        responsesNode.put("status_5xx", counts.err5xx);
        responsesNode.put("status_other", counts.otherStatus);
        responsesNode.put("transport_errors", counts.transportErrors);

        runsArray.add(runNode);

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

    /**
     * Builds a 50-bin histogram of the latencies in milliseconds. Saving the
     * chart image is intentionally disabled; the computation is kept so the
     * data path stays exercised.
     */
    private static void createLatencyHistogram(List<Long> latencies, String filePrefix) {
        try {
            double[] latencyMs = latencies.stream()
                    .mapToDouble(l -> l / 1_000_000.0)
                    .toArray();

            HistogramDataset dataset = new HistogramDataset();
            dataset.addSeries("Latency", latencyMs, 50);

            JFreeChart histogram = ChartFactory.createHistogram(
                    "Latency Distribution",
                    "Latency (ms)",
                    "Frequency",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false);

            String fileName = "latency_histogram_" + filePrefix + ".png";

            /*
            ChartUtils.saveChartAsPNG(
                    new File(fileName),
                    histogram,
                    800,
                    600);
            */

            logger.debug("Histogram calculated for {}", filePrefix);
        } catch (Exception e) {
            logger.error("Error creating latency histogram: {}", e.getMessage());
        }
    }

    /**
     * Builds the latency cumulative distribution function in milliseconds.
     * Saving the chart image is intentionally disabled; the computation is
     * kept so the data path stays exercised.
     */
    private static void createLatencyCDF(List<Long> latencies, String filePrefix) {
        try {
            List<Long> sortedLatencies = new ArrayList<>(latencies);
            Collections.sort(sortedLatencies);

            XYSeries series = new XYSeries("CDF");
            int totalPoints = sortedLatencies.size();

            for (int i = 0; i < totalPoints; i++) {
                double percentile = (i + 1.0) / totalPoints * 100.0;
                double latencyMs = sortedLatencies.get(i) / 1_000_000.0;
                series.add(latencyMs, percentile);
            }

            XYSeriesCollection dataset = new XYSeriesCollection(series);

            JFreeChart chart = ChartFactory.createXYLineChart(
                    "Latency Cumulative Distribution Function",
                    "Latency (ms)",
                    "Percentile",
                    dataset,
                    PlotOrientation.VERTICAL,
                    true,
                    true,
                    false);

            String fileName = "latency_cdf_" + filePrefix + ".png";

            /*
            ChartUtils.saveChartAsPNG(
                    new File(fileName),
                    chart,
                    800,
                    600);
            */

            logger.debug("CDF calculated for {}", filePrefix);
        } catch (Exception e) {
            logger.error("Error creating latency CDF: {}", e.getMessage());
        }
    }
    
    /** JAXB mapping of benchmark-config.xml: the experiment schedule. */
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

        /** Pacing mode: burst (default, historical), uniform, or poisson; see produceRequests. */
        @XmlElement(name = "pacing", defaultValue = "burst")
        private String pacing = "burst";

        /**
         * Marks JIT/cache warm-up phases. Carried into the results so
         * tooling can exclude them without relying on the experiment name
         * containing "warmup".
         */
        @XmlElement(name = "warmup")
        private boolean warmup;

        @XmlElementWrapper(name = "probabilities")
        @XmlElement(name = "probability")
        private List<ProbabilityConfig> probabilities;

        /** Returns the probabilities as an endpoint-name-to-value map. */
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

        public String getPacing() {
            return pacing == null || pacing.isBlank() ? "burst" : pacing.toLowerCase();
        }

        public boolean isWarmup() {
            return warmup;
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
    
    /** JAXB mapping of parameters.xml: the endpoint URL sets. */
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
