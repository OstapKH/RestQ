package com.restq.api_http.Benchmark;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiBenchmarkResultTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void preservesIdentityAndStatusInEveryLatencyRow() {
        var target = new RequestTarget("Q06", "Q06-P1", "/revenue-increase?discount=0.06");
        ObjectNode run = MAPPER.createObjectNode();

        ApiBenchmark.addRequestResults(run, List.of(
                new ApiBenchmark.TimestampedLatency(1_000L, 25L, 503, target)));

        var row = run.path("latencies").get(0);
        assertEquals(1_000L, row.path("timestamp").longValue());
        assertEquals(25L, row.path("latency_ns").longValue());
        assertEquals("Q06", row.path("query_id").textValue());
        assertEquals("Q06-P1", row.path("parameter_set_id").textValue());
        assertEquals(503, row.path("status_code").intValue());
    }

    @Test
    void aggregatesCountsStatusesAndPercentilesByParameterSet() {
        var first = new RequestTarget("Q06", "Q06-P1", "/revenue-increase?discount=0.06");
        var second = new RequestTarget("Q06", "Q06-P2", "/revenue-increase?discount=0.05");
        ObjectNode run = MAPPER.createObjectNode();

        ApiBenchmark.addRequestResults(run, List.of(
                new ApiBenchmark.TimestampedLatency(1L, 50L, 200, first),
                new ApiBenchmark.TimestampedLatency(2L, 10L, 204, first),
                new ApiBenchmark.TimestampedLatency(3L, 30L, 404, first),
                new ApiBenchmark.TimestampedLatency(4L, 20L, 503, first),
                new ApiBenchmark.TimestampedLatency(5L, 40L, 302, first),
                new ApiBenchmark.TimestampedLatency(6L, 60L, 200, second)));

        var result = run.path("parameter_results").path("Q06-P1");
        assertEquals("Q06", result.path("query_id").textValue());
        assertEquals(5, result.path("count").intValue());
        assertEquals(2, result.path("responses").path("status_2xx").intValue());
        assertEquals(1, result.path("responses").path("status_4xx").intValue());
        assertEquals(1, result.path("responses").path("status_5xx").intValue());
        assertEquals(1, result.path("responses").path("status_other").intValue());
        assertEquals(30L, result.path("latency_distribution").path("percentiles").path("p50").longValue());
        assertEquals(50L, result.path("latency_distribution").path("percentiles").path("p95").longValue());
        assertEquals(50L, result.path("latency_distribution").path("percentiles").path("p99").longValue());
    }

    @Test
    void recordsEachFullUrlOnceInTheTopLevelParameterSetCatalog() {
        ObjectNode root = MAPPER.createObjectNode();
        var target = new RequestTarget("Q06", "Q06-P1", "/revenue-increase?discount=0.06");

        ApiBenchmark.addParameterSets(root, Map.of("revenue-increase", List.of(target)),
                "http://localhost:8086/api/reports");

        var parameterSet = root.path("parameter_sets").path("Q06-P1");
        assertEquals("Q06", parameterSet.path("query_id").textValue());
        assertEquals("revenue-increase", parameterSet.path("endpoint").textValue());
        assertEquals("http://localhost:8086/api/reports/revenue-increase?discount=0.06",
                parameterSet.path("url").textValue());
    }

    @Test
    void recordsOuterRequestFailuresWithTheirTargetIdentity() {
        var target = new RequestTarget("Q06", "Q06-P1", "/invalid path");
        var samples = new ArrayList<ApiBenchmark.TimestampedLatency>();

        ApiBenchmark.addTransportFailure(samples, target, 1_234L);

        assertEquals(1, samples.size());
        assertEquals(target, samples.getFirst().getTarget());
        assertEquals(1_234L, samples.getFirst().getTimestamp());
        assertEquals(-1L, samples.getFirst().getLatency());
        assertEquals(-1, samples.getFirst().getStatusCode());
    }
}
