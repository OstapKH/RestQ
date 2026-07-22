package com.restq.api_http.Benchmark;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class BalancedParameterSelector {
    private final Map<String, List<RequestTarget>> catalogs;
    private final Map<String, Deque<RequestTarget>> queues;
    private final Random random;

    public BalancedParameterSelector(Map<String, List<RequestTarget>> targets, long seed) {
        Objects.requireNonNull(targets, "targets must not be null");
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("targets must not be empty");
        }

        this.catalogs = new LinkedHashMap<>();
        this.queues = new LinkedHashMap<>();
        for (Map.Entry<String, List<RequestTarget>> entry : targets.entrySet()) {
            String endpoint = requireNonBlank(entry.getKey(), "endpoint");
            List<RequestTarget> catalog = List.copyOf(
                    Objects.requireNonNull(entry.getValue(), "target list must not be null"));
            if (catalog.isEmpty()) {
                throw new IllegalArgumentException("target list must not be empty for endpoint " + endpoint);
            }
            catalogs.put(endpoint, catalog);
            queues.put(endpoint, new ArrayDeque<>());
        }
        this.random = new Random(seed);
    }

    public synchronized RequestTarget next(String endpoint) {
        Deque<RequestTarget> queue = queues.get(endpoint);
        if (queue == null) {
            throw new IllegalArgumentException("Unknown endpoint: " + endpoint);
        }
        if (queue.isEmpty()) {
            refill(endpoint, queue);
        }
        return queue.removeFirst();
    }

    private void refill(String endpoint, Deque<RequestTarget> queue) {
        List<RequestTarget> shuffled = new ArrayList<>(catalogs.get(endpoint));
        Collections.shuffle(shuffled, random);
        queue.addAll(shuffled);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
