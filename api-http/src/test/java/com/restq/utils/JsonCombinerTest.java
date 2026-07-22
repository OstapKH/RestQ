package com.restq.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonCombinerTest {

    @Test
    void findsBroadestWindowAcrossExperiments() {
        JSONObject benchmark = new JSONObject("""
                {
                  "experiments": {
                    "warmup": {"runs": [
                      {"start_timestamp": 1700000000000, "end_timestamp": 1700000001000}
                    ]},
                    "measured": {"runs": [
                      {"start_timestamp": 1700000003000, "end_timestamp": 1700000009000}
                    ]}
                  }
                }
                """);

        JsonCombiner.ExperimentWindow window = JsonCombiner.findExperimentWindow(benchmark);

        assertEquals(1700000000000L, window.startMs());
        assertEquals(1700000009000L, window.endMs());
    }

    @Test
    void filtersSecondAndMillisecondTimestampsInclusively() {
        JSONArray measurements = new JSONArray()
                .put(measurement(1699999999L))
                .put(measurement(1700000000L))
                .put(measurement(1700000001L))
                .put(measurement(1700000002000L))
                .put(measurement(1700000003L));
        JsonCombiner.ExperimentWindow window =
                new JsonCombiner.ExperimentWindow(1700000000000L, 1700000002000L);

        JSONArray filtered = JsonCombiner.filterEnergyToWindow(measurements, window);

        assertEquals(3, filtered.length());
        assertEquals(1700000000L,
                filtered.getJSONObject(0).getJSONObject("host").getLong("timestamp"));
        assertEquals(1700000002000L,
                filtered.getJSONObject(2).getJSONObject("host").getLong("timestamp"));
    }

    @Test
    void rejectsMissingRunBoundariesInsteadOfKeepingUnfilteredEnergy() {
        JSONObject benchmark = new JSONObject("""
                {"experiments": {"incomplete": {"runs": [{}]}}}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> JsonCombiner.findExperimentWindow(benchmark));
    }

    @Test
    void integratesDbAndApiPowerIntoMeasuredRunEnergy() {
        JSONObject benchmark = benchmark(1, true);

        JSONObject enriched = JsonCombiner.enrichAndValidate(
                benchmark, powerSeries(10.0, 0, 1, 2, 3, 4), powerSeries(5.0, 0, 1, 2, 3, 4));

        JSONObject energy = measuredRun(enriched).getJSONObject("run_energy");
        assertEquals(40.0, energy.getJSONObject("db").getDouble("energy_j"), 1e-9);
        assertEquals(20.0, energy.getJSONObject("api").getDouble("energy_j"), 1e-9);
        assertEquals(60.0, energy.getJSONObject("combined").getDouble("energy_j"), 1e-9);
        assertEquals(15.0, energy.getJSONObject("combined").getDouble("mean_power_w"), 1e-9);
        assertEquals(0.6,
                energy.getJSONObject("combined").getDouble("joules_per_successful_request"), 1e-9);
        assertTrue(enriched.getJSONObject("validation").getBoolean("valid"));
    }

    @Test
    void rejectsEnergySeriesWithInternalGapOverThreeSeconds() {
        JSONObject enriched = JsonCombiner.enrichAndValidate(
                benchmark(1, true), powerSeries(10.0, 0, 4), powerSeries(5.0, 0, 1, 2, 3, 4));

        assertFalse(enriched.getJSONObject("validation").getBoolean("valid"));
        assertFalse(check(enriched, "DB_ENERGY_COVERAGE").getBoolean("valid"));
    }

    @Test
    void rejectsMissingApiEnergy() {
        JSONObject enriched = JsonCombiner.enrichAndValidate(
                benchmark(1, true), powerSeries(10.0, 0, 1, 2, 3, 4), new JSONArray());

        assertFalse(enriched.getJSONObject("validation").getBoolean("valid"));
        assertFalse(check(enriched, "API_ENERGY_COVERAGE").getBoolean("valid"));
    }

    @Test
    void rejectsIncompleteMeasuredRuns() {
        JSONObject enriched = JsonCombiner.enrichAndValidate(
                benchmark(2, true), powerSeries(10.0, 0, 1, 2, 3, 4), powerSeries(5.0, 0, 1, 2, 3, 4));

        assertFalse(enriched.getJSONObject("validation").getBoolean("valid"));
        assertFalse(check(enriched, "RUN_COMPLETENESS").getBoolean("valid"));
    }

    @Test
    void rejectsFailedClientValidationEvenWithCompleteEnergy() {
        JSONObject enriched = JsonCombiner.enrichAndValidate(
                benchmark(1, false), powerSeries(10.0, 0, 1, 2, 3, 4), powerSeries(5.0, 0, 1, 2, 3, 4));

        assertFalse(enriched.getJSONObject("validation").getBoolean("valid"));
        assertFalse(check(enriched, "CLIENT_VALIDATION").getBoolean("valid"));
    }

    private static JSONObject measurement(long timestamp) {
        return new JSONObject()
                .put("host", new JSONObject()
                        .put("timestamp", timestamp)
                        .put("consumption", 1_000_000))
                .put("consumers", new JSONArray());
    }

    private static JSONObject benchmark(int configuredRuns, boolean clientValid) {
        JSONObject run = new JSONObject()
                .put("run_number", 0)
                .put("start_timestamp", 0L)
                .put("end_timestamp", 4_000L)
                .put("successful_requests", 100);
        JSONObject measured = new JSONObject()
                .put("warmup", false)
                .put("runs_configured", configuredRuns)
                .put("runs", new JSONArray().put(run));
        return new JSONObject()
                .put("client_validation", new JSONObject().put("valid", clientValid))
                .put("experiments", new JSONObject().put("Q01_measured", measured));
    }

    private static JSONObject measuredRun(JSONObject benchmark) {
        return benchmark.getJSONObject("experiments").getJSONObject("Q01_measured")
                .getJSONArray("runs").getJSONObject(0);
    }

    private static JSONArray powerSeries(double watts, long... seconds) {
        JSONArray result = new JSONArray();
        for (long second : seconds) {
            result.put(new JSONObject()
                    .put("host", new JSONObject()
                            .put("timestamp", second)
                            .put("consumption", watts * 1_000_000))
                    .put("consumers", new JSONArray()));
        }
        return result;
    }

    private static JSONObject check(JSONObject benchmark, String code) {
        for (Object value : benchmark.getJSONObject("validation").getJSONArray("checks")) {
            JSONObject check = (JSONObject) value;
            if (code.equals(check.getString("code"))) {
                return check;
            }
        }
        throw new AssertionError("Missing validation check " + code);
    }
}
