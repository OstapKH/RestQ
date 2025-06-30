package com.restq.api_http.Benchmark;

import java.util.Map;

public class Config {
    private int connections;              // Number of client connections
    private int requestsPerSecond;        // Requests each client sends per second
    private Map<String, Double> probabilities;  // Probabilities for selecting endpoints
    private long duration;                // Duration in milliseconds for the benchmark
    private Map<String, String> endpoints;

    public int getConnections() {
        return connections;
    }

    public void setConnections(int connections) {
        this.connections = connections;
    }

    public int getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(int requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }

    public void setProbabilities(Map<String, Double> probabilities) {
        this.probabilities = probabilities;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Map<String, String> getEndpoints() {
        return endpoints;
    }

    public void setEndpoints(Map<String, String> endpoints) {
        this.endpoints = endpoints;
    }
}
