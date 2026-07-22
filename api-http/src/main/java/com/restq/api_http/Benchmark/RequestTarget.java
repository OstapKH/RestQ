package com.restq.api_http.Benchmark;

import java.util.Objects;

public record RequestTarget(String queryId, String parameterSetId, String path) {

    public RequestTarget {
        queryId = requireNonBlank(queryId, "queryId");
        parameterSetId = requireNonBlank(parameterSetId, "parameterSetId");
        path = requireNonBlank(path, "path");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
