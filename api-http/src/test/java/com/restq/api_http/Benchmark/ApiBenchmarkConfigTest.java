package com.restq.api_http.Benchmark;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiBenchmarkConfigTest {

    @Test
    void preservesExplicitIdsAndGeneratesMissingParameterSetIdsByPosition() {
        var config = parse("""
                <parameters><endpoint name="pricing-summary" query-id="Q01">
                  <url id="Q01-P1">/pricing-summary?delta=60&amp;shipDate=1998-08-01</url>
                  <url>/pricing-summary?delta=90&amp;shipDate=1998-06-01</url>
                </endpoint></parameters>
                """);

        var targets = ApiBenchmark.buildTargetCatalog(config).get("pricing-summary");

        assertEquals(List.of("Q01-P1", "Q01-P2"),
                targets.stream().map(RequestTarget::parameterSetId).toList());
        assertEquals(List.of("Q01", "Q01"),
                targets.stream().map(RequestTarget::queryId).toList());
        assertEquals("/pricing-summary?delta=90&shipDate=1998-06-01", targets.get(1).path());
    }

    @Test
    void generatesQueryAndParameterSetIdsForLegacyEndpointCatalogs() {
        var config = parse("""
                <parameters>
                  <endpoint name="first"><url>/first/a</url><url>/first/b</url></endpoint>
                  <endpoint name="second"><url>/second/a</url></endpoint>
                </parameters>
                """);

        var catalog = ApiBenchmark.buildTargetCatalog(config);

        assertEquals(List.of("Q01-P1", "Q01-P2"), catalog.get("first").stream()
                .map(RequestTarget::parameterSetId).toList());
        assertEquals("Q02", catalog.get("second").getFirst().queryId());
        assertEquals("Q02-P1", catalog.get("second").getFirst().parameterSetId());
    }

    @Test
    void rejectsBlankUrlPaths() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                <parameters><endpoint name="pricing-summary" query-id="Q01">
                  <url id="Q01-P1">   </url>
                </endpoint></parameters>
                """));
    }

    @Test
    void rejectsDuplicateParameterSetIdsAcrossEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                <parameters>
                  <endpoint name="first" query-id="Q01"><url id="shared">/first</url></endpoint>
                  <endpoint name="second" query-id="Q02"><url id="shared">/second</url></endpoint>
                </parameters>
                """));
    }

    @Test
    void rejectsDuplicateNormalizedExplicitQueryIds() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                <parameters>
                  <endpoint name="first" query-id=" Q01 "><url id="first-set">/first</url></endpoint>
                  <endpoint name="second" query-id="Q01"><url id="second-set">/second</url></endpoint>
                </parameters>
                """));
    }

    @Test
    void rejectsExplicitQueryIdsThatCollideWithGeneratedIds() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                <parameters>
                  <endpoint name="first"><url>/first</url></endpoint>
                  <endpoint name="second" query-id="Q01"><url id="second-set">/second</url></endpoint>
                </parameters>
                """));
    }

    @Test
    void readsConfiguredRandomSeed() {
        var config = parseBenchmarkConfig("""
                <benchmark-config>
                  <pauseBetweenExperiments-ms>10000</pauseBetweenExperiments-ms>
                  <random-seed>1234</random-seed>
                </benchmark-config>
                """);

        assertEquals(1234L, config.getRandomSeed());
        assertEquals("full", config.getValidationProfile());
    }

    @Test
    void readsExplicitSmokeValidationProfile() {
        var config = parseBenchmarkConfig("""
                <benchmark-config>
                  <validation-profile> smoke </validation-profile>
                </benchmark-config>
                """);

        assertEquals("smoke", config.getValidationProfile());
    }

    @Test
    void defaultsLegacyBenchmarkConfigToStudySeed() {
        var config = parseBenchmarkConfig("""
                <benchmark-config>
                  <pauseBetweenExperiments-ms>10000</pauseBetweenExperiments-ms>
                </benchmark-config>
                """);

        assertEquals(5000L, config.getRandomSeed());
    }

    private static ApiBenchmark.ParametersConfig parse(String xml) {
        return ApiBenchmark.parseParameters(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static ApiBenchmark.BenchmarkConfig parseBenchmarkConfig(String xml) {
        return ApiBenchmark.parseBenchmarkConfig(new ByteArrayInputStream(
                xml.getBytes(StandardCharsets.UTF_8)));
    }
}
