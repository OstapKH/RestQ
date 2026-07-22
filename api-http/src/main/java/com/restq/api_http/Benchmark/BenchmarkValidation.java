package com.restq.api_http.Benchmark;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure validation of the benchmark-client result document. */
public final class BenchmarkValidation {
    private static final Set<String> EXPECTED_QUERY_IDS = Set.of(
            "Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08", "Q09", "Q10",
            "Q11", "Q12", "Q13", "Q14", "Q16", "Q17", "Q19", "Q21", "Q22");
    private static final int EXPECTED_PARAMETER_SETS_PER_QUERY = 5;
    private static final int EXPECTED_WARMUP_RUNS = 1;
    private static final int EXPECTED_MEASURED_RUNS = 3;
    private static final Set<String> SMOKE_QUERY_IDS = Set.of("Q01", "Q06");

    private BenchmarkValidation() {
    }

    /** The overall client verdict and its machine-readable check report. */
    public record ValidationResult(boolean valid, ObjectNode report) {
    }

    /**
     * Validates preflight, schedule completeness, measured-run duration,
     * parameter coverage, and request errors without mutating {@code results}.
     */
    public static ValidationResult validateClient(ObjectNode results) {
        List<Check> checks = new ArrayList<>();
        ValidationContract contract = validationContract(results);
        Map<String, Set<String>> expectedParameters = expectedParameters(results);
        Map<String, List<Experiment>> experiments = experimentsByQuery(results, contract.queryIds());

        checks.add(validatePreflight(results, expectedParameters, contract));
        checks.add(validateRunCounts(results, experiments, contract));
        checks.add(validateDurations(expectedParameters, experiments));
        checks.add(validateParameterCoverage(expectedParameters, experiments));
        checks.add(validateHttpErrors(expectedParameters, experiments));
        checks.add(validateTransportErrors(expectedParameters, experiments));

        boolean valid = checks.stream().allMatch(Check::valid);
        ObjectNode report = results.objectNode();
        report.put("valid", valid);
        ArrayNode checkNodes = report.putArray("checks");
        for (Check check : checks) {
            ObjectNode node = checkNodes.addObject();
            node.put("code", check.code());
            node.put("valid", check.valid());
            ArrayNode problems = node.putArray("problems");
            check.problems().forEach(problems::add);
        }
        return new ValidationResult(valid, report);
    }

    private static Check validatePreflight(ObjectNode results,
                                           Map<String, Set<String>> expectedParameters,
                                           ValidationContract contract) {
        List<String> problems = new ArrayList<>();
        if (!results.path("preflight_passed").asBoolean(false)) {
            problems.add("preflight_passed is not true");
        }

        Set<String> expectedIds = flatten(expectedParameters);
        Set<String> observedIds = new HashSet<>();
        JsonNode preflight = results.path("preflight_validation");
        if (!preflight.isArray()) {
            problems.add("preflight_validation is missing or is not an array");
        } else {
            for (JsonNode row : preflight) {
                String parameterId = text(row, "parameter_set_id");
                if (parameterId != null && !observedIds.add(parameterId)) {
                    problems.add("duplicate preflight parameter set " + parameterId);
                }
                if (!row.path("ok").asBoolean(false)) {
                    problems.add("failed preflight parameter set "
                            + (parameterId == null ? "<unknown>" : parameterId));
                }
            }
            if (preflight.size() != expectedIds.size() || !observedIds.equals(expectedIds)) {
                problems.add("preflight parameter sets do not match the parameter catalog");
            }
        }
        boolean exactParameterCatalog = expectedParameters.keySet().equals(contract.queryIds())
                && expectedParameters.entrySet().stream().allMatch(entry ->
                        entry.getValue().equals(expectedParameterIds(entry.getKey())));
        if (!exactParameterCatalog
                || expectedIds.size() != contract.queryIds().size() * EXPECTED_PARAMETER_SETS_PER_QUERY) {
            problems.add("parameter catalog does not match the " + contract.name()
                    + " profile with five parameter sets per query");
        }
        return check("PREFLIGHT", problems);
    }

    private static Check validateRunCounts(ObjectNode results,
                                           Map<String, List<Experiment>> experiments,
                                           ValidationContract contract) {
        List<String> problems = new ArrayList<>();
        for (String queryId : contract.queryIds()) {
            List<Experiment> queryExperiments = experiments.getOrDefault(queryId, List.of());
            List<Experiment> warmups = queryExperiments.stream().filter(Experiment::warmup).toList();
            List<Experiment> measured = queryExperiments.stream().filter(experiment -> !experiment.warmup()).toList();
            validateExperimentCount(queryId, "warm-up", warmups, EXPECTED_WARMUP_RUNS, problems);
            validateExperimentCount(queryId, "measured", measured,
                    contract.measuredRuns(), problems);
        }
        int recognizedExperiments = experiments.values().stream().mapToInt(List::size).sum();
        JsonNode allExperiments = results.path("experiments");
        if (!allExperiments.isObject()) {
            problems.add("experiments is missing or is not an object");
        } else if (allExperiments.size() != recognizedExperiments) {
            problems.add("one or more experiments could not be assigned to a configured query");
        }
        return check("RUN_COUNT", problems);
    }

    private static ValidationContract validationContract(ObjectNode results) {
        String profile = results.path("validation_profile").asText("full");
        if ("smoke".equalsIgnoreCase(profile)) {
            return new ValidationContract("smoke", SMOKE_QUERY_IDS, 1);
        }
        return new ValidationContract("full", EXPECTED_QUERY_IDS, EXPECTED_MEASURED_RUNS);
    }

    private static void validateExperimentCount(String queryId, String kind,
                                                List<Experiment> experiments, int expectedRuns,
                                                List<String> problems) {
        if (experiments.size() != 1) {
            problems.add(queryId + " must have exactly one " + kind + " experiment");
            return;
        }
        Experiment experiment = experiments.getFirst();
        if (experiment.configuredRuns() != expectedRuns || experiment.runs().size() != expectedRuns) {
            problems.add(experiment.name() + " must contain exactly " + expectedRuns + " run(s)");
        }
    }

    private static Check validateDurations(Map<String, Set<String>> expectedParameters,
                                           Map<String, List<Experiment>> experiments) {
        List<String> problems = new ArrayList<>();
        for (Experiment experiment : measuredExperiments(expectedParameters, experiments)) {
            for (int index = 0; index < experiment.runs().size(); index++) {
                JsonNode run = experiment.runs().get(index);
                JsonNode elapsed = run.path("elapsed_time_ms");
                JsonNode expected = run.path("expected_duration_ms");
                if (!elapsed.isIntegralNumber() || !expected.isIntegralNumber()
                        || elapsed.longValue() < 0 || expected.longValue() <= 0
                        || elapsed.longValue() < expected.longValue()) {
                    problems.add(runLabel(experiment, index) + " did not reach its expected duration");
                }
            }
        }
        return check("RUN_DURATION", problems);
    }

    private static Check validateParameterCoverage(Map<String, Set<String>> expectedParameters,
                                                   Map<String, List<Experiment>> experiments) {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : expectedParameters.entrySet()) {
            if (entry.getValue().size() != EXPECTED_PARAMETER_SETS_PER_QUERY) {
                problems.add(entry.getKey() + " must define exactly five parameter sets");
            }
            for (Experiment experiment : experiments.getOrDefault(entry.getKey(), List.of())) {
                if (experiment.warmup()) {
                    continue;
                }
                for (int index = 0; index < experiment.runs().size(); index++) {
                    JsonNode run = experiment.runs().get(index);
                    RunEvidence evidence = requestEvidence(run, entry.getKey(), entry.getValue());
                    JsonNode parameterResults = run.path("parameter_results");
                    boolean validCoverage = evidence.requestsPresent() && evidence.identitiesValid()
                            && parameterResults.isObject()
                            && parameterResults.size() == entry.getValue().size();
                    for (String parameterId : entry.getValue()) {
                        JsonNode result = parameterResults.path(parameterId);
                        JsonNode count = result.path("count");
                        validCoverage &= result.isObject()
                                && entry.getKey().equals(text(result, "query_id"))
                                && count.isIntegralNumber()
                                && count.longValue() > 0
                                && count.longValue() == evidence.parameterCounts()
                                        .getOrDefault(parameterId, 0);
                    }
                    if (!validCoverage || !evidence.parameterCounts().keySet().equals(entry.getValue())) {
                        problems.add(runLabel(experiment, index)
                                + " does not cover all five configured parameter sets");
                    }
                }
            }
        }
        return check("PARAMETER_COVERAGE", problems);
    }

    private static Check validateHttpErrors(Map<String, Set<String>> expectedParameters,
                                            Map<String, List<Experiment>> experiments) {
        List<String> problems = new ArrayList<>();
        for (Experiment experiment : measuredExperiments(expectedParameters, experiments)) {
            for (int index = 0; index < experiment.runs().size(); index++) {
                JsonNode run = experiment.runs().get(index);
                RunEvidence evidence = requestEvidence(run, null, Set.of());
                JsonNode responses = run.path("responses");
                boolean consistent = evidence.requestsPresent() && evidence.httpRowsValid()
                        && countEquals(responses, "status_2xx", evidence.ok2xx())
                        && countEquals(responses, "status_4xx", evidence.err4xx())
                        && countEquals(responses, "status_5xx", evidence.err5xx())
                        && countEquals(responses, "status_other", evidence.otherStatus())
                        && countEquals(run, "total_requests", evidence.totalRequests())
                        && countEquals(run, "successful_requests", evidence.ok2xx())
                        && parameterResponsesConsistent(run, false);
                if (!consistent) {
                    problems.add(runLabel(experiment, index)
                            + " has missing or contradictory HTTP request evidence");
                } else if (evidence.err4xx() != 0 || evidence.err5xx() != 0
                        || evidence.otherStatus() != 0) {
                    problems.add(runLabel(experiment, index) + " contains HTTP errors");
                }
            }
        }
        return check("HTTP_ERRORS", problems);
    }

    private static Check validateTransportErrors(Map<String, Set<String>> expectedParameters,
                                                 Map<String, List<Experiment>> experiments) {
        List<String> problems = new ArrayList<>();
        for (Experiment experiment : measuredExperiments(expectedParameters, experiments)) {
            for (int index = 0; index < experiment.runs().size(); index++) {
                JsonNode run = experiment.runs().get(index);
                RunEvidence evidence = requestEvidence(run, null, Set.of());
                JsonNode responses = run.path("responses");
                if (!evidence.requestsPresent() || !evidence.transportRowsValid()
                        || !countEquals(responses, "transport_errors", evidence.transportErrors())
                        || !parameterResponsesConsistent(run, true)) {
                    problems.add(runLabel(experiment, index)
                            + " has missing or contradictory transport-error evidence");
                } else if (evidence.transportErrors() != 0) {
                    problems.add(runLabel(experiment, index) + " contains transport errors");
                }
            }
        }
        return check("TRANSPORT_ERRORS", problems);
    }

    private static Map<String, Set<String>> expectedParameters(ObjectNode results) {
        Map<String, Set<String>> expected = new LinkedHashMap<>();
        JsonNode parameterSets = results.path("parameter_sets");
        if (!parameterSets.isObject()) {
            return expected;
        }
        parameterSets.fields().forEachRemaining(entry -> {
            String queryId = text(entry.getValue(), "query_id");
            if (queryId != null) {
                expected.computeIfAbsent(queryId, ignored -> new LinkedHashSet<>()).add(entry.getKey());
            }
        });
        return expected;
    }

    private static Map<String, List<Experiment>> experimentsByQuery(ObjectNode results,
                                                                    Set<String> queryIds) {
        Map<String, List<Experiment>> byQuery = new HashMap<>();
        JsonNode experiments = results.path("experiments");
        if (!experiments.isObject()) {
            return byQuery;
        }
        experiments.fields().forEachRemaining(entry -> {
            JsonNode node = entry.getValue();
            String queryId = queryIds.stream()
                    .filter(id -> entry.getKey().startsWith(id + "_"))
                    .findFirst()
                    .orElseGet(() -> queryIdFromRuns(node));
            if (queryId == null || !queryIds.contains(queryId)) {
                return;
            }
            List<JsonNode> runs = new ArrayList<>();
            JsonNode runNodes = node.path("runs");
            if (runNodes.isArray()) {
                runNodes.forEach(runs::add);
            }
            Experiment experiment = new Experiment(entry.getKey(), node.path("warmup").asBoolean(false),
                    node.path("runs_configured").asInt(-1), runs);
            byQuery.computeIfAbsent(queryId, ignored -> new ArrayList<>()).add(experiment);
        });
        return byQuery;
    }

    private static String queryIdFromRuns(JsonNode experiment) {
        for (JsonNode run : experiment.path("runs")) {
            Iterator<JsonNode> parameters = run.path("parameter_results").elements();
            while (parameters.hasNext()) {
                String queryId = text(parameters.next(), "query_id");
                if (queryId != null) {
                    return queryId;
                }
            }
        }
        return null;
    }

    private static List<Experiment> measuredExperiments(Map<String, Set<String>> expectedParameters,
                                                        Map<String, List<Experiment>> experiments) {
        List<Experiment> measured = new ArrayList<>();
        for (String queryId : expectedParameters.keySet()) {
            experiments.getOrDefault(queryId, List.of()).stream()
                    .filter(experiment -> !experiment.warmup())
                    .forEach(measured::add);
        }
        return measured;
    }

    private static Set<String> flatten(Map<String, Set<String>> values) {
        Set<String> flattened = new HashSet<>();
        values.values().forEach(flattened::addAll);
        return flattened;
    }

    private static Set<String> expectedParameterIds(String queryId) {
        Set<String> ids = new HashSet<>();
        for (int parameter = 1; parameter <= EXPECTED_PARAMETER_SETS_PER_QUERY; parameter++) {
            ids.add(queryId + "-P" + parameter);
        }
        return ids;
    }

    private record ValidationContract(String name, Set<String> queryIds, int measuredRuns) {}

    private static RunEvidence requestEvidence(JsonNode run, String expectedQueryId,
                                               Set<String> expectedParameterIds) {
        JsonNode latencies = run.path("latencies");
        if (!latencies.isArray() || latencies.isEmpty()) {
            return new RunEvidence(false, false, false, false, 0, Map.of(), 0, 0, 0, 0, 0);
        }

        boolean identitiesValid = true;
        boolean httpRowsValid = true;
        boolean transportRowsValid = true;
        Map<String, Integer> parameterCounts = new HashMap<>();
        int ok2xx = 0;
        int err4xx = 0;
        int err5xx = 0;
        int otherStatus = 0;
        int transportErrors = 0;
        for (JsonNode row : latencies) {
            JsonNode timestamp = row.path("timestamp");
            if (!timestamp.isIntegralNumber() || timestamp.longValue() < 0) {
                httpRowsValid = false;
                transportRowsValid = false;
            }
            if (expectedQueryId != null) {
                String queryId = text(row, "query_id");
                String parameterId = text(row, "parameter_set_id");
                if (!expectedQueryId.equals(queryId) || !expectedParameterIds.contains(parameterId)) {
                    identitiesValid = false;
                } else {
                    parameterCounts.merge(parameterId, 1, Integer::sum);
                }
            }

            JsonNode statusNode = row.path("status_code");
            if (!statusNode.isIntegralNumber()) {
                httpRowsValid = false;
                transportRowsValid = false;
                continue;
            }
            int status = statusNode.intValue();
            JsonNode latency = row.path("latency_ns");
            if (status == -1) {
                if (!latency.isIntegralNumber() || latency.longValue() != -1) {
                    transportRowsValid = false;
                }
                transportErrors++;
            } else {
                if (!latency.isIntegralNumber() || latency.longValue() < 0) {
                    httpRowsValid = false;
                }
                if (status >= 200 && status < 300) {
                    ok2xx++;
                } else if (status >= 400 && status < 500) {
                    err4xx++;
                } else if (status >= 500) {
                    err5xx++;
                } else {
                    otherStatus++;
                }
            }
        }
        return new RunEvidence(true, identitiesValid, httpRowsValid, transportRowsValid,
                latencies.size(),
                Map.copyOf(parameterCounts), ok2xx, err4xx, err5xx, otherStatus, transportErrors);
    }

    private static boolean countEquals(JsonNode object, String field, long expected) {
        JsonNode count = object.path(field);
        return count.isIntegralNumber() && count.longValue() >= 0 && count.longValue() == expected;
    }

    private static boolean parameterResponsesConsistent(JsonNode run, boolean transportOnly) {
        JsonNode latencies = run.path("latencies");
        JsonNode parameterResults = run.path("parameter_results");
        if (!latencies.isArray() || !parameterResults.isObject()) {
            return false;
        }

        Map<String, int[]> countsByParameter = new HashMap<>();
        for (JsonNode row : latencies) {
            String parameterId = text(row, "parameter_set_id");
            JsonNode statusNode = row.path("status_code");
            if (parameterId == null || !statusNode.isIntegralNumber()) {
                return false;
            }
            int[] counts = countsByParameter.computeIfAbsent(parameterId, ignored -> new int[5]);
            int status = statusNode.intValue();
            if (status == -1) {
                counts[4]++;
            } else if (status >= 200 && status < 300) {
                counts[0]++;
            } else if (status >= 400 && status < 500) {
                counts[1]++;
            } else if (status >= 500) {
                counts[2]++;
            } else {
                counts[3]++;
            }
        }
        for (Map.Entry<String, int[]> entry : countsByParameter.entrySet()) {
            JsonNode result = parameterResults.get(entry.getKey());
            if (result == null) {
                continue;
            }
            JsonNode responses = result.path("responses");
            int[] counts = entry.getValue();
            if (transportOnly) {
                if (!countEquals(responses, "transport_errors", counts[4])) {
                    return false;
                }
            } else if (!countEquals(responses, "status_2xx", counts[0])
                    || !countEquals(responses, "status_4xx", counts[1])
                    || !countEquals(responses, "status_5xx", counts[2])
                    || !countEquals(responses, "status_other", counts[3])) {
                return false;
            }
        }
        return true;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static String runLabel(Experiment experiment, int index) {
        return experiment.name() + " run " + (index + 1);
    }

    private static Check check(String code, List<String> problems) {
        return new Check(code, problems.isEmpty(), List.copyOf(problems));
    }

    private record Experiment(String name, boolean warmup, int configuredRuns, List<JsonNode> runs) {
    }

    private record Check(String code, boolean valid, List<String> problems) {
    }

    private record RunEvidence(boolean requestsPresent, boolean identitiesValid,
                               boolean httpRowsValid, boolean transportRowsValid,
                               int totalRequests,
                               Map<String, Integer> parameterCounts,
                               int ok2xx, int err4xx, int err5xx, int otherStatus,
                               int transportErrors) {
    }

}
