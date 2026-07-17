package com.restq.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private static JSONObject measurement(long timestamp) {
        return new JSONObject()
                .put("host", new JSONObject()
                        .put("timestamp", timestamp)
                        .put("consumption", 1_000_000))
                .put("consumers", new JSONArray());
    }
}
