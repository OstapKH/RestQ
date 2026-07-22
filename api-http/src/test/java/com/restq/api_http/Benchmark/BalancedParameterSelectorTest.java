package com.restq.api_http.Benchmark;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalancedParameterSelectorTest {

    @Test
    void distributes103SequentialChoicesWithASpreadOfAtMostOne() {
        var selector = new BalancedParameterSelector(Map.of("q6", targets()), 5000L);

        var counts = IntStream.range(0, 103)
                .mapToObj(i -> selector.next("q6").parameterSetId())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        assertEquals(5, counts.size());
        assertTrue(Collections.max(counts.values()) - Collections.min(counts.values()) <= 1);
    }

    @Test
    void producesTheSameSequenceForTheSameSeed() {
        var first = new BalancedParameterSelector(Map.of("q6", targets()), 42L);
        var second = new BalancedParameterSelector(Map.of("q6", targets()), 42L);

        var firstSequence = selections(first, 50);
        var secondSequence = selections(second, 50);

        assertEquals(firstSequence, secondSequence);
    }

    @Test
    void distributes400ConcurrentChoicesWithASpreadOfAtMostOne() throws Exception {
        var selector = new BalancedParameterSelector(Map.of("q6", targets()), 5000L);
        var start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(16);

        try {
            List<Future<RequestTarget>> futures = IntStream.range(0, 400)
                    .mapToObj(i -> executor.submit(() -> {
                        start.await();
                        return selector.next("q6");
                    }))
                    .toList();
            start.countDown();

            var counts = new ArrayList<String>();
            for (Future<RequestTarget> future : futures) {
                counts.add(future.get().parameterSetId());
            }

            var distribution = counts.stream()
                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
            assertEquals(400, counts.size());
            assertEquals(5, distribution.size());
            assertTrue(Collections.max(distribution.values())
                    - Collections.min(distribution.values()) <= 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsEmptyCatalogs() {
        assertThrows(IllegalArgumentException.class,
                () -> new BalancedParameterSelector(Map.of(), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> new BalancedParameterSelector(Map.of("q6", List.of()), 1L));
    }

    @Test
    void rejectsUnknownEndpoints() {
        var selector = new BalancedParameterSelector(Map.of("q6", targets()), 1L);

        assertThrows(IllegalArgumentException.class, () -> selector.next("q7"));
    }

    @Test
    void makesDefensiveCopiesOfTargetLists() {
        var mutableTargets = new ArrayList<>(targets());
        var selector = new BalancedParameterSelector(Map.of("q6", mutableTargets), 1L);
        mutableTargets.clear();

        assertEquals(5, selections(selector, 5).stream().distinct().count());
    }

    @Test
    void rejectsInvalidRequestTargetValues() {
        assertThrows(IllegalArgumentException.class, () -> new RequestTarget("", "set-1", "/q6"));
        assertThrows(IllegalArgumentException.class, () -> new RequestTarget("q6", "", "/q6"));
        assertThrows(IllegalArgumentException.class, () -> new RequestTarget("q6", "set-1", ""));
        assertThrows(NullPointerException.class, () -> new RequestTarget(null, "set-1", "/q6"));
    }

    private static List<String> selections(BalancedParameterSelector selector, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> selector.next("q6").parameterSetId())
                .toList();
    }

    private static List<RequestTarget> targets() {
        return IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new RequestTarget("q6", "set-" + i, "/q6/" + i))
                .toList();
    }
}
