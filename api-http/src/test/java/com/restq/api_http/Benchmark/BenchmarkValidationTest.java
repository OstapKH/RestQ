package com.restq.api_http.Benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkValidationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> CHECK_CODES = Set.of(
            "PREFLIGHT",
            "RUN_COUNT",
            "RUN_DURATION",
            "PARAMETER_COVERAGE",
            "HTTP_ERRORS",
            "TRANSPORT_ERRORS");
    private static final List<String> QUERY_IDS = List.of(
            "Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q09", "Q10",
            "Q11", "Q12", "Q13", "Q14", "Q16", "Q17", "Q19", "Q21", "Q22");

    @Test
    void acceptsCompleteNineteenQueryClientResults() {
        var result = BenchmarkValidation.validateClient(validResults());

        assertTrue(result.valid());
        assertTrue(result.report().path("valid").booleanValue());
        Set<String> actualCodes = new HashSet<>();
        result.report().path("checks").forEach(check -> {
            actualCodes.add(check.path("code").textValue());
            assertTrue(check.path("valid").booleanValue(), check.toPrettyString());
        });
        assertEquals(CHECK_CODES, actualCodes);
    }

    @Test
    void acceptsExplicitTwoQuerySmokeProfileWithoutWeakeningFullDefault() {
        ObjectNode results = validResults();
        results.put("validation_profile", "smoke");
        for (String queryId : QUERY_IDS) {
            if (!Set.of("Q01", "Q06").contains(queryId)) {
                removeQuery(results, queryId);
            }
        }
        for (String queryId : List.of("Q01", "Q06")) {
            ObjectNode measured = (ObjectNode) results.path("experiments")
                    .path(queryId + "_measured");
            ((ArrayNode) measured.path("runs")).remove(2);
            ((ArrayNode) measured.path("runs")).remove(1);
            measured.put("runs_configured", 1);
        }

        assertTrue(BenchmarkValidation.validateClient(results).valid());
    }

    @Test
    void rejectsFailedPreflightWithStableCode() {
        ObjectNode results = validResults();
        results.put("preflight_passed", false);
        ((ObjectNode) results.path("preflight_validation").get(0)).put("ok", false);

        assertOnlyCheckFails(results, "PREFLIGHT");
    }

    @Test
    void rejectsNineteenQueriesWhenTheyAreNotTheApprovedQuerySet() {
        ObjectNode results = validResults();
        replaceQuery(results, "Q22", "Q15");

        var validation = BenchmarkValidation.validateClient(results);

        assertFalse(validation.valid());
        assertFalse(check(validation.report(), "PREFLIGHT").path("valid").booleanValue());
        assertFalse(check(validation.report(), "RUN_COUNT").path("valid").booleanValue());
    }

    @Test
    void rejectsMissingMeasuredRunWithStableCode() {
        ObjectNode results = validResults();
        ((ArrayNode) results.path("experiments").path("Q01_measured").path("runs")).remove(2);

        assertOnlyCheckFails(results, "RUN_COUNT");
    }

    @Test
    void runCountIndependentlyRejectsQueryMissingFromCatalogAndSchedule() {
        ObjectNode results = validResults();
        removeQuery(results, "Q22");

        var validation = BenchmarkValidation.validateClient(results);

        assertFalse(validation.valid());
        assertFalse(check(validation.report(), "PREFLIGHT").path("valid").booleanValue());
        assertFalse(check(validation.report(), "RUN_COUNT").path("valid").booleanValue());
    }

    @Test
    void rejectsMeasuredRunShorterThanExpectedWithStableCode() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        run.put("elapsed_time_ms", run.path("expected_duration_ms").longValue() - 1);

        assertOnlyCheckFails(results, "RUN_DURATION");
    }

    @Test
    void rejectsNonPositiveOrFractionalMeasuredDurations() {
        ObjectNode negative = validResults();
        ObjectNode negativeRun = measuredRun(negative, "Q01_measured", 0);
        negativeRun.put("elapsed_time_ms", -1L);
        negativeRun.put("expected_duration_ms", -1L);
        assertOnlyCheckFails(negative, "RUN_DURATION");

        ObjectNode fractional = validResults();
        measuredRun(fractional, "Q01_measured", 0).put("expected_duration_ms", 44_999.5);
        assertOnlyCheckFails(fractional, "RUN_DURATION");
    }

    @Test
    void acceptsMeasuredRunExactlyAsLongAsExpected() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        run.put("elapsed_time_ms", run.path("expected_duration_ms").longValue());

        assertTrue(BenchmarkValidation.validateClient(results).valid());
    }

    @Test
    void rejectsMissingMeasuredParameterSetWithStableCode() {
        ObjectNode results = validResults();
        ((ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("parameter_results")).remove("Q01-P5");

        assertOnlyCheckFails(results, "PARAMETER_COVERAGE");
    }

    @Test
    void rejectsParameterSummaryThatIsNotBackedByRequestRows() {
        ObjectNode results = validResults();
        ArrayNode latencies = (ArrayNode) measuredRun(results, "Q01_measured", 0).path("latencies");
        for (int index = latencies.size() - 1; index >= 0; index--) {
            if ("Q01-P5".equals(latencies.get(index).path("parameter_set_id").textValue())) {
                latencies.remove(index);
            }
        }
        measuredRun(results, "Q01_measured", 0).put("total_requests", latencies.size());
        measuredRun(results, "Q01_measured", 0).put("successful_requests", latencies.size());
        ((ObjectNode) measuredRun(results, "Q01_measured", 0).path("responses"))
                .put("status_2xx", latencies.size());

        assertOnlyCheckFails(results, "PARAMETER_COVERAGE");
    }

    @Test
    void rejectsMeasuredHttpErrorWithStableCode() {
        ObjectNode results = validResults();
        ((ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("responses")).put("status_5xx", 1);

        assertOnlyCheckFails(results, "HTTP_ERRORS");
    }

    @Test
    void rejectsMeasuredTransportErrorWithStableCode() {
        ObjectNode results = validResults();
        ((ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("responses")).put("transport_errors", 1);

        assertOnlyCheckFails(results, "TRANSPORT_ERRORS");
    }

    @Test
    void rejectsHttpErrorHiddenByZeroAggregateCounters() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        ((ObjectNode) run.path("latencies").get(0)).put("status_code", 503);
        ((ObjectNode) run.path("responses")).put("status_2xx", 19);
        run.put("successful_requests", 19);

        assertOnlyCheckFails(results, "HTTP_ERRORS");
    }

    @Test
    void rejectsPerParameterResponseSummaryThatContradictsRequestRows() {
        ObjectNode results = validResults();
        ObjectNode parameter = (ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("parameter_results").path("Q01-P1");
        ((ObjectNode) parameter.path("responses")).put("status_5xx", 1);

        assertOnlyCheckFails(results, "HTTP_ERRORS");
    }

    @Test
    void rejectsTransportErrorHiddenByZeroAggregateCounter() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        ObjectNode row = (ObjectNode) run.path("latencies").get(0);
        row.put("status_code", -1);
        row.put("latency_ns", -1);
        ((ObjectNode) run.path("responses")).put("status_2xx", 19);
        ((ObjectNode) run.path("parameter_results").path("Q01-P1").path("responses"))
                .put("status_2xx", 3);
        run.put("successful_requests", 19);

        assertOnlyCheckFails(results, "TRANSPORT_ERRORS");
    }

    @Test
    void rejectsMeasuredRunWithoutRequestRows() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        run.remove("latencies");
        run.remove("total_requests");
        run.remove("successful_requests");

        var validation = BenchmarkValidation.validateClient(results);

        assertFalse(validation.valid());
        assertFalse(check(validation.report(), "PARAMETER_COVERAGE").path("valid").booleanValue());
        assertFalse(check(validation.report(), "HTTP_ERRORS").path("valid").booleanValue());
        assertFalse(check(validation.report(), "TRANSPORT_ERRORS").path("valid").booleanValue());
    }

    @Test
    void rejectsIncompleteOrInvalidCompletedRequestRows() {
        ObjectNode missingTimestamp = validResults();
        ((ObjectNode) measuredRun(missingTimestamp, "Q01_measured", 0)
                .path("latencies").get(0)).remove("timestamp");
        assertFalse(check(BenchmarkValidation.validateClient(missingTimestamp).report(), "HTTP_ERRORS")
                .path("valid").booleanValue());

        ObjectNode negativeCompletedLatency = validResults();
        ((ObjectNode) measuredRun(negativeCompletedLatency, "Q01_measured", 0)
                .path("latencies").get(0)).put("latency_ns", -1L);
        assertFalse(check(BenchmarkValidation.validateClient(negativeCompletedLatency).report(),
                "HTTP_ERRORS").path("valid").booleanValue());

        ObjectNode missingLatency = validResults();
        ((ObjectNode) measuredRun(missingLatency, "Q01_measured", 0)
                .path("latencies").get(0)).remove("latency_ns");
        assertFalse(check(BenchmarkValidation.validateClient(missingLatency).report(), "HTTP_ERRORS")
                .path("valid").booleanValue());
    }

    @Test
    void rejectsTransportStatusWithoutTransportLatencySentinel() {
        ObjectNode results = validResults();
        ObjectNode run = measuredRun(results, "Q01_measured", 0);
        ObjectNode row = (ObjectNode) run.path("latencies").get(0);
        row.put("status_code", -1);
        row.put("latency_ns", 10_000L);
        ((ObjectNode) run.path("responses")).put("status_2xx", 19).put("transport_errors", 1);
        run.put("successful_requests", 19);

        JsonNode transportCheck = check(BenchmarkValidation.validateClient(results).report(),
                "TRANSPORT_ERRORS");
        assertFalse(transportCheck.path("valid").booleanValue());
        assertTrue(transportCheck.path("problems").get(0).textValue().contains("contradictory"));
    }

    @Test
    void attachesValidClientReportAndReturnsSuccessCode() {
        ObjectNode results = validResults();

        int exitCode = ApiBenchmark.validateClientResults(results);

        assertEquals(0, exitCode);
        assertTrue(results.path("client_validation").path("valid").booleanValue());
    }

    @Test
    void attachesInvalidClientReportBeforeReturningValidationFailureCode() {
        ObjectNode results = validResults();
        ((ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("responses")).put("status_4xx", 1);

        int exitCode = ApiBenchmark.validateClientResults(results);

        assertEquals(3, exitCode);
        assertFalse(results.path("client_validation").path("valid").booleanValue());
        assertFalse(check(results.path("client_validation"), "HTTP_ERRORS")
                .path("valid").booleanValue());
    }

    @Test
    void persistsAttachedInvalidReportToACompleteJsonSnapshot(@TempDir Path directory)
            throws IOException {
        ObjectNode results = validResults();
        ((ObjectNode) measuredRun(results, "Q01_measured", 0)
                .path("responses")).put("status_4xx", 1);
        Path output = directory.resolve("benchmark-results.json");

        int exitCode = ApiBenchmark.validateClientResults(results);
        ApiBenchmark.persistResults(MAPPER, results, output);

        assertEquals(3, exitCode);
        assertFalse(MAPPER.readTree(output.toFile())
                .path("client_validation").path("valid").booleanValue());
        try (var files = Files.list(directory)) {
            assertEquals(List.of(output), files.toList());
        }
    }

    @Test
    void failedSnapshotSerializationPreservesPreviousResult(@TempDir Path directory)
            throws IOException {
        Path output = directory.resolve("benchmark-results.json");
        Files.writeString(output, "{\"previous\":true}");
        ObjectMapper failingMapper = new ObjectMapper() {
            @Override
            public void writeValue(java.io.File resultFile, Object value) throws IOException {
                Files.writeString(resultFile.toPath(), "{\"partial\":");
                throw new IOException("synthetic serialization failure");
            }
        };

        assertThrows(IOException.class,
                () -> ApiBenchmark.persistResults(failingMapper, validResults(), output));

        assertTrue(MAPPER.readTree(output.toFile()).path("previous").booleanValue());
        try (var files = Files.list(directory)) {
            assertEquals(List.of(output), files.toList());
        }
    }

    private static void assertOnlyCheckFails(ObjectNode results, String expectedCode) {
        var validation = BenchmarkValidation.validateClient(results);

        assertFalse(validation.valid());
        assertFalse(validation.report().path("valid").booleanValue());
        Set<String> failedCodes = new HashSet<>();
        validation.report().path("checks").forEach(check -> {
            if (!check.path("valid").booleanValue()) {
                failedCodes.add(check.path("code").textValue());
            }
        });
        assertEquals(Set.of(expectedCode), failedCodes, validation.report().toPrettyString());
    }

    private static JsonNode check(JsonNode report, String code) {
        for (JsonNode check : report.path("checks")) {
            if (code.equals(check.path("code").textValue())) {
                return check;
            }
        }
        throw new AssertionError("Missing validation check " + code);
    }

    private static ObjectNode validResults() {
        ObjectNode results = MAPPER.createObjectNode();
        results.put("preflight_passed", true);
        ArrayNode preflight = results.putArray("preflight_validation");
        ObjectNode parameterSets = results.putObject("parameter_sets");
        ObjectNode experiments = results.putObject("experiments");

        for (String queryId : QUERY_IDS) {
            for (int parameter = 1; parameter <= 5; parameter++) {
                String parameterId = queryId + "-P" + parameter;
                preflight.addObject()
                        .put("query_id", queryId)
                        .put("parameter_set_id", parameterId)
                        .put("ok", true);
                parameterSets.putObject(parameterId).put("query_id", queryId);
            }

            ObjectNode warmup = experiments.putObject(queryId + "_warmup");
            warmup.put("warmup", true);
            warmup.put("runs_configured", 1);
            warmup.putArray("runs").add(run(queryId, 20_000L));

            ObjectNode measured = experiments.putObject(queryId + "_measured");
            measured.put("warmup", false);
            measured.put("runs_configured", 3);
            ArrayNode runs = measured.putArray("runs");
            for (int run = 0; run < 3; run++) {
                runs.add(run(queryId, 45_000L));
            }
        }
        return results;
    }

    private static ObjectNode run(String queryId, long durationMs) {
        ObjectNode run = MAPPER.createObjectNode();
        run.put("elapsed_time_ms", durationMs);
        run.put("expected_duration_ms", durationMs);
        ObjectNode responses = run.putObject("responses");
        responses.put("status_2xx", 20);
        responses.put("status_4xx", 0);
        responses.put("status_5xx", 0);
        responses.put("status_other", 0);
        responses.put("transport_errors", 0);
        ObjectNode parameterResults = run.putObject("parameter_results");
        ArrayNode latencies = run.putArray("latencies");
        for (int parameter = 1; parameter <= 5; parameter++) {
            String parameterId = queryId + "-P" + parameter;
            ObjectNode parameterResult = parameterResults.putObject(parameterId);
            parameterResult.put("query_id", queryId);
            parameterResult.put("count", 4);
            ObjectNode parameterResponses = parameterResult.putObject("responses");
            parameterResponses.put("status_2xx", 4);
            parameterResponses.put("status_4xx", 0);
            parameterResponses.put("status_5xx", 0);
            parameterResponses.put("status_other", 0);
            parameterResponses.put("transport_errors", 0);
            for (int request = 0; request < 4; request++) {
                latencies.addObject()
                        .put("timestamp", parameter * 100L + request)
                        .put("query_id", queryId)
                        .put("parameter_set_id", parameterId)
                        .put("status_code", 200)
                        .put("latency_ns", 10_000L);
            }
        }
        run.put("total_requests", 20);
        run.put("successful_requests", 20);
        return run;
    }

    private static void replaceQuery(ObjectNode results, String oldId, String newId) {
        ObjectNode parameterSets = (ObjectNode) results.path("parameter_sets");
        for (int parameter = 1; parameter <= 5; parameter++) {
            String oldParameter = oldId + "-P" + parameter;
            String newParameter = newId + "-P" + parameter;
            ObjectNode definition = (ObjectNode) parameterSets.remove(oldParameter);
            definition.put("query_id", newId);
            parameterSets.set(newParameter, definition);
        }
        results.path("preflight_validation").forEach(row -> {
            ObjectNode object = (ObjectNode) row;
            if (oldId.equals(object.path("query_id").textValue())) {
                object.put("query_id", newId);
                object.put("parameter_set_id",
                        object.path("parameter_set_id").textValue().replace(oldId, newId));
            }
        });
        ObjectNode experiments = (ObjectNode) results.path("experiments");
        ObjectNode warmup = (ObjectNode) experiments.remove(oldId + "_warmup");
        ObjectNode measured = (ObjectNode) experiments.remove(oldId + "_measured");
        replaceQueryInRuns(warmup, oldId, newId);
        replaceQueryInRuns(measured, oldId, newId);
        experiments.set(newId + "_warmup", warmup);
        experiments.set(newId + "_measured", measured);
    }

    private static void removeQuery(ObjectNode results, String queryId) {
        ObjectNode parameterSets = (ObjectNode) results.path("parameter_sets");
        for (int parameter = 1; parameter <= 5; parameter++) {
            parameterSets.remove(queryId + "-P" + parameter);
        }
        ArrayNode preflight = (ArrayNode) results.path("preflight_validation");
        for (int index = preflight.size() - 1; index >= 0; index--) {
            if (queryId.equals(preflight.get(index).path("query_id").textValue())) {
                preflight.remove(index);
            }
        }
        ObjectNode experiments = (ObjectNode) results.path("experiments");
        experiments.remove(queryId + "_warmup");
        experiments.remove(queryId + "_measured");
    }

    private static void replaceQueryInRuns(ObjectNode experiment, String oldId, String newId) {
        experiment.path("runs").forEach(runNode -> {
            ObjectNode run = (ObjectNode) runNode;
            ObjectNode parameterResults = (ObjectNode) run.path("parameter_results");
            for (int parameter = 1; parameter <= 5; parameter++) {
                String oldParameter = oldId + "-P" + parameter;
                String newParameter = newId + "-P" + parameter;
                ObjectNode result = (ObjectNode) parameterResults.remove(oldParameter);
                result.put("query_id", newId);
                parameterResults.set(newParameter, result);
            }
            run.path("latencies").forEach(rowNode -> {
                ObjectNode row = (ObjectNode) rowNode;
                row.put("query_id", newId);
                row.put("parameter_set_id",
                        row.path("parameter_set_id").textValue().replace(oldId, newId));
            });
        });
    }

    private static ObjectNode measuredRun(ObjectNode results, String experiment, int index) {
        JsonNode run = results.path("experiments").path(experiment).path("runs").get(index);
        return (ObjectNode) run;
    }
}
