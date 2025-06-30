package com.restq.api_http.Benchmark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LatencyDistribution {
    private long minimumLatency;
    private long maximumLatency;
    private long medianLatency;
    private Map<String, Long> percentiles;

    void setPercentile(List<Long> latencies, int... percentiles) {
        this.percentiles = new HashMap<>();
        for (int p : percentiles) {
            this.percentiles.put(p + "th Percentile", latencies.get(latencies.size() * p / 100));
        }
    }

    public long getMinimumLatency() {
        return minimumLatency;
    }

    public void setMinimumLatency(long minimumLatency) {
        this.minimumLatency = minimumLatency;
    }

    public long getMaximumLatency() {
        return maximumLatency;
    }

    public void setMaximumLatency(long maximumLatency) {
        this.maximumLatency = maximumLatency;
    }

    public long getMedianLatency() {
        return medianLatency;
    }

    public void setMedianLatency(long medianLatency) {
        this.medianLatency = medianLatency;
    }

    public Map<String, Long> getPercentiles() {
        return percentiles;
    }

    public void setPercentiles(Map<String, Long> percentiles) {
        this.percentiles = percentiles;
    }

}