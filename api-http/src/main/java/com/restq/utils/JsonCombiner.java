package com.restq.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class JsonCombiner {
    private static final long MILLIS_TIMESTAMP_THRESHOLD = 100_000_000_000L;
    private static final long BOUNDARY_TOLERANCE_MS = 1_000L;
    private static final long MAX_INTERNAL_GAP_MS = 3_000L;

    record ExperimentWindow(long startMs, long endMs) {}
    record EnergyMetrics(double energyJ, double meanPowerW) {}
    private record PowerPoint(long timestampMs, double watts) {}

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage: JsonCombiner <inputDirectory> <outputFile>");
            System.exit(1);
}
        int exitCode;
        try {
            exitCode = combine(Paths.get(args[0]), Paths.get(args[1]));
        } catch (Exception e) {
            System.err.println("Error combining JSON files: " + e.getMessage());
            e.printStackTrace();
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    /**
     * Combines the client result and Scaphandre streams. Invalid experiments are
     * still written for diagnosis and return status 2.
     */
    static int combine(Path inputPath, Path outputFile) throws IOException {
        JsonFixer.fixJsonFilesInFolder(inputPath.toString());

        JSONObject combined = new JSONObject();
        combined.put("timestamp", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date()));

        JSONObject benchmark = readBenchmark(inputPath);
        ExperimentWindow experimentWindow = findExperimentWindow(benchmark);
        ExperimentWindow paddedEnergyWindow = new ExperimentWindow(
                experimentWindow.startMs() - BOUNDARY_TOLERANCE_MS,
                experimentWindow.endMs() + BOUNDARY_TOLERANCE_MS);
        JSONArray dbEnergy = readEnergy(inputPath, "dbserver", paddedEnergyWindow);
        JSONArray apiEnergy = readEnergy(inputPath, "apiserver", paddedEnergyWindow);

        JSONObject enriched = enrichAndValidate(benchmark, dbEnergy, apiEnergy);
        combined.put("benchmark_results", enriched);
        combined.put("validation", enriched.getJSONObject("validation"));
        combined.put("db_server_energy", dbEnergy);
        combined.put("api_server_energy", apiEnergy);

        Path containerInfo = inputPath.resolve("container_info.json");
        if (Files.exists(containerInfo)) {
            combined.put("container_info", new JSONObject(Files.readString(containerInfo)));
        }

        Path parent = outputFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(outputFile, combined.toString(4));
        System.out.println("Combined JSON written to " + outputFile);
        return enriched.getJSONObject("validation").getBoolean("valid") ? 0 : 2;
    }

    private static JSONObject readBenchmark(Path inputPath) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath, "benchmark_results_*.json")) {
            for (Path path : stream) {
                System.out.println("Found benchmark file: " + path);
                return new JSONObject(Files.readString(path));
            }
        }
        throw new IllegalArgumentException("Benchmark results file not found in " + inputPath);
    }

    private static JSONArray readEnergy(Path inputPath, String server, ExperimentWindow window)
            throws IOException {
        JSONArray result = new JSONArray();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(inputPath, "fixed_experiments_summary_*.json")) {
            for (Path path : stream) {
                if (!path.getFileName().toString().contains(server)) {
                    continue;
                }
                JSONArray source = new JSONArray(Files.readString(path));
                JSONArray filtered = filterEnergyToWindow(source, window);
                for (Object entry : filtered) {
                    result.put(entry);
                }
            }
        }
        return result;
    }

    /**
     * Adds run-level energy and a strict final validation report to benchmark.
     * Energy is intentionally attached to a whole run, never to parameter sets.
     */
    static JSONObject enrichAndValidate(JSONObject benchmark, JSONArray db, JSONArray api) {
        JSONArray completenessProblems = new JSONArray();
        JSONArray dbProblems = new JSONArray();
        JSONArray apiProblems = new JSONArray();
        int measuredRuns = 0;

        JSONObject experiments = benchmark.optJSONObject("experiments");
        if (experiments == null) {
            completenessProblems.put("Missing experiments object");
        } else {
            for (String experimentName : experiments.keySet()) {
                JSONObject experiment = experiments.optJSONObject(experimentName);
                if (experiment == null || experiment.optBoolean("warmup", false)) {
                    continue;
                }
                JSONArray runs = experiment.optJSONArray("runs");
                int configured = experiment.optInt("runs_configured", -1);
                int actual = runs == null ? 0 : runs.length();
                if (configured <= 0 || actual != configured) {
                    completenessProblems.put(experimentName + ": expected " + configured
                            + " measured runs, found " + actual);
                }
                if (runs == null) {
                    continue;
                }

                for (int index = 0; index < runs.length(); index++) {
                    measuredRuns++;
                    JSONObject run = runs.optJSONObject(index);
                    String runLabel = experimentName + " run " + (index + 1);
                    if (run == null) {
                        completenessProblems.put(runLabel + ": run is not an object");
                        continue;
                    }
                    Long start = timestampValue(run, "start_timestamp");
                    Long end = timestampValue(run, "end_timestamp");
                    if (start == null || end == null || end <= start) {
                        completenessProblems.put(runLabel + ": invalid run boundaries");
                        continue;
                    }

                    EnergyMetrics dbMetrics = integrateForValidation(
                            db, start, end, runLabel, dbProblems);
                    EnergyMetrics apiMetrics = integrateForValidation(
                            api, start, end, runLabel, apiProblems);
                    if (dbMetrics != null && apiMetrics != null) {
                        attachEnergy(run, dbMetrics, apiMetrics);
                    }
                }
            }
        }
        if (measuredRuns == 0) {
            completenessProblems.put("No measured runs found");
        }

        JSONObject validation = new JSONObject();
        JSONArray checks = new JSONArray();
        boolean clientValid = benchmark.optJSONObject("client_validation") != null
                && benchmark.getJSONObject("client_validation").optBoolean("valid", false);
        checks.put(check("CLIENT_VALIDATION", clientValid,
                clientValid ? new JSONArray() : new JSONArray().put("Client validation did not pass")));
        checks.put(check("RUN_COMPLETENESS", completenessProblems.isEmpty(), completenessProblems));
        checks.put(check("DB_ENERGY_COVERAGE", dbProblems.isEmpty(), dbProblems));
        checks.put(check("API_ENERGY_COVERAGE", apiProblems.isEmpty(), apiProblems));

        boolean valid = clientValid && completenessProblems.isEmpty()
                && dbProblems.isEmpty() && apiProblems.isEmpty();
        validation.put("valid", valid);
        validation.put("checks", checks);
        benchmark.put("validation", validation);
        return benchmark;
    }

    private static EnergyMetrics integrateForValidation(
            JSONArray samples, long startMs, long endMs, String runLabel, JSONArray problems) {
        try {
            return integrate(samples, startMs, endMs);
        } catch (IllegalArgumentException e) {
            problems.put(runLabel + ": " + e.getMessage());
            return null;
        }
    }

    private static JSONObject check(String code, boolean valid, JSONArray problems) {
        return new JSONObject()
                .put("code", code)
                .put("valid", valid)
                .put("problems", problems);
    }

    private static void attachEnergy(
            JSONObject run, EnergyMetrics dbMetrics, EnergyMetrics apiMetrics) {
        JSONObject energy = new JSONObject();
        energy.put("db", metricJson(dbMetrics));
        energy.put("api", metricJson(apiMetrics));

        double combinedJoules = dbMetrics.energyJ() + apiMetrics.energyJ();
        double combinedWatts = dbMetrics.meanPowerW() + apiMetrics.meanPowerW();
        JSONObject combined = new JSONObject()
                .put("energy_j", combinedJoules)
                .put("mean_power_w", combinedWatts);
        long successful = run.optLong("successful_requests", 0);
        if (successful > 0) {
            combined.put("joules_per_successful_request", combinedJoules / successful);
        } else {
            combined.put("joules_per_successful_request", JSONObject.NULL);
        }
        energy.put("combined", combined);
        run.put("run_energy", energy);
    }

    private static JSONObject metricJson(EnergyMetrics metrics) {
        return new JSONObject()
                .put("energy_j", metrics.energyJ())
                .put("mean_power_w", metrics.meanPowerW());
    }

    /**
     * Integrates a Scaphandre power series using linear boundary interpolation
     * and the trapezoidal rule.
     */
    static EnergyMetrics integrate(JSONArray samples, long startMs, long endMs) {
        if (endMs <= startMs) {
            throw new IllegalArgumentException("Run end must be after run start");
        }
        List<PowerPoint> points = powerPoints(samples);
        if (points.size() < 2) {
            throw new IllegalArgumentException("At least two power samples are required");
        }

        PowerPoint before = null;
        PowerPoint after = null;
        for (PowerPoint point : points) {
            if (point.timestampMs() <= startMs) {
                before = point;
            }
            if (after == null && point.timestampMs() >= endMs) {
                after = point;
            }
        }
        if (before == null || startMs - before.timestampMs() > BOUNDARY_TOLERANCE_MS) {
            throw new IllegalArgumentException("No sample brackets the run start within 1000 ms");
        }
        if (after == null || after.timestampMs() - endMs > BOUNDARY_TOLERANCE_MS) {
            throw new IllegalArgumentException("No sample brackets the run end within 1000 ms");
        }

        int first = points.indexOf(before);
        int last = points.indexOf(after);
        for (int i = first + 1; i <= last; i++) {
            long gap = points.get(i).timestampMs() - points.get(i - 1).timestampMs();
            if (gap > MAX_INTERNAL_GAP_MS) {
                throw new IllegalArgumentException("Power sample gap " + gap + " ms exceeds 3000 ms");
            }
        }

        List<PowerPoint> window = new ArrayList<>();
        window.add(new PowerPoint(startMs, interpolate(points, startMs)));
        for (PowerPoint point : points) {
            if (point.timestampMs() > startMs && point.timestampMs() < endMs) {
                window.add(point);
            }
        }
        window.add(new PowerPoint(endMs, interpolate(points, endMs)));

        double energyJ = 0.0;
        for (int i = 1; i < window.size(); i++) {
            PowerPoint left = window.get(i - 1);
            PowerPoint right = window.get(i);
            double seconds = (right.timestampMs() - left.timestampMs()) / 1000.0;
            energyJ += (left.watts() + right.watts()) * 0.5 * seconds;
        }
        double durationSeconds = (endMs - startMs) / 1000.0;
        return new EnergyMetrics(energyJ, energyJ / durationSeconds);
    }

    private static double interpolate(List<PowerPoint> points, long timestampMs) {
        PowerPoint left = null;
        for (PowerPoint point : points) {
            if (point.timestampMs() == timestampMs) {
                return point.watts();
            }
            if (point.timestampMs() > timestampMs) {
                if (left == null) {
                    throw new IllegalArgumentException("Cannot interpolate before first power sample");
                }
                double fraction = (timestampMs - left.timestampMs())
                        / (double) (point.timestampMs() - left.timestampMs());
                return left.watts() + fraction * (point.watts() - left.watts());
            }
            left = point;
        }
        throw new IllegalArgumentException("Cannot interpolate after last power sample");
    }

    private static List<PowerPoint> powerPoints(JSONArray measurements) {
        boolean useConsumers = false;
        for (int i = 0; i < measurements.length(); i++) {
            JSONArray consumers = measurements.optJSONObject(i) == null
                    ? null : measurements.optJSONObject(i).optJSONArray("consumers");
            if (consumers != null && !consumers.isEmpty()) {
                useConsumers = true;
                break;
            }
        }

        List<PowerPoint> points = new ArrayList<>();
        for (int i = 0; i < measurements.length(); i++) {
            JSONObject measurement = measurements.optJSONObject(i);
            Long timestamp = measurementTimestampMs(measurement);
            if (timestamp == null) {
                continue;
            }

            Double watts = useConsumers
                    ? consumerWatts(measurement == null ? null : measurement.optJSONArray("consumers"))
                    : hostWatts(measurement == null ? null : measurement.optJSONObject("host"));
            if (watts != null && Double.isFinite(watts) && watts >= 0.0) {
                points.add(new PowerPoint(timestamp, watts));
            }
        }
        points.sort(Comparator.comparingLong(PowerPoint::timestampMs));

        List<PowerPoint> deduplicated = new ArrayList<>();
        for (PowerPoint point : points) {
            if (!deduplicated.isEmpty()
                    && deduplicated.getLast().timestampMs() == point.timestampMs()) {
                deduplicated.set(deduplicated.size() - 1, point);
            } else {
                deduplicated.add(point);
            }
        }
        return deduplicated;
    }

    private static Double consumerWatts(JSONArray consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return null;
        }
        double microwatts = 0.0;
        boolean found = false;
        for (int i = 0; i < consumers.length(); i++) {
            JSONObject consumer = consumers.optJSONObject(i);
            if (consumer != null && consumer.opt("consumption") instanceof Number value) {
                microwatts += value.doubleValue();
                found = true;
            }
        }
        return found ? microwatts / 1_000_000.0 : null;
    }

    private static Double hostWatts(JSONObject host) {
        if (host != null && host.opt("consumption") instanceof Number value) {
            return value.doubleValue() / 1_000_000.0;
        }
        return null;
    }

    static ExperimentWindow findExperimentWindow(JSONObject benchmarkJson) {
        if (benchmarkJson == null) {
            throw new IllegalArgumentException("Benchmark results are required to filter energy measurements");
        }
        JSONObject experiments = benchmarkJson.optJSONObject("experiments");
        if (experiments == null) {
            throw new IllegalArgumentException("Benchmark results contain no experiments");
        }

        Long earliestStart = null;
        Long latestEnd = null;
        for (String experimentId : experiments.keySet()) {
            JSONObject experiment = experiments.optJSONObject(experimentId);
            JSONArray runs = experiment == null ? null : experiment.optJSONArray("runs");
            if (runs == null) {
                continue;
            }
            for (int i = 0; i < runs.length(); i++) {
                JSONObject run = runs.optJSONObject(i);
                Long start = timestampValue(run, "start_timestamp");
                Long end = timestampValue(run, "end_timestamp");
                if (start == null || end == null || end < start) {
                    continue;
                }
                earliestStart = earliestStart == null ? start : Math.min(earliestStart, start);
                latestEnd = latestEnd == null ? end : Math.max(latestEnd, end);
            }
        }
        if (earliestStart == null || latestEnd == null) {
            throw new IllegalArgumentException(
                    "Benchmark results contain no valid start_timestamp/end_timestamp run boundaries");
        }
        return new ExperimentWindow(earliestStart, latestEnd);
    }

    static JSONArray filterEnergyToWindow(JSONArray measurements, ExperimentWindow window) {
        JSONArray filtered = new JSONArray();
        for (int i = 0; i < measurements.length(); i++) {
            JSONObject measurement = measurements.optJSONObject(i);
            Long timestampMs = measurementTimestampMs(measurement);
            if (timestampMs != null
                    && timestampMs >= window.startMs()
                    && timestampMs <= window.endMs()) {
                filtered.put(measurement);
            }
        }
        return filtered;
    }

    private static Long measurementTimestampMs(JSONObject measurement) {
        if (measurement == null) {
            return null;
        }
        Long timestamp = timestampValue(measurement.optJSONObject("host"), "timestamp");
        if (timestamp == null) {
            JSONArray consumers = measurement.optJSONArray("consumers");
            if (consumers != null) {
                for (int i = 0; i < consumers.length() && timestamp == null; i++) {
                    timestamp = timestampValue(consumers.optJSONObject(i), "timestamp");
                }
            }
        }
        if (timestamp == null) {
            return null;
        }
        return timestamp < MILLIS_TIMESTAMP_THRESHOLD ? timestamp * 1000 : timestamp;
    }

    private static Long timestampValue(JSONObject object, String key) {
        if (object == null || !object.has(key)) {
            return null;
        }
        Object value = object.opt(key);
        return value instanceof Number number ? number.longValue() : null;
    }
}
