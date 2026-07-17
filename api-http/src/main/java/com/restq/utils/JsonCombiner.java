package com.restq.utils;

import java.io.*;
import java.nio.file.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.json.JSONException;

public class JsonCombiner {
    private static final long MILLIS_TIMESTAMP_THRESHOLD = 100_000_000_000L;

    record ExperimentWindow(long startMs, long endMs) {}

    public static void main(String[] args) {
       if (args.length < 2) {
           System.err.println("Usage: JsonCombiner <inputDirectory> <outputFile>");
           System.exit(1);
       }

       String inputDir = args[0];
       String outputFile = args[1];

        try {
            // First fix ONLY the Scaphandre JSON files
            JsonFixer.fixJsonFilesInFolder(inputDir);
            
            // Now combine the necessary files
            JSONObject combinedJson = new JSONObject();
            String timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
            combinedJson.put("timestamp", timestamp);
            
            Path inputPath = Paths.get(inputDir);
            JSONObject benchmarkJson = null;
            
            // Find and add ORIGINAL benchmark results file
            Path benchmarkFile = null;
            // Search in the original input directory
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath, "benchmark_results_*.json")) {
                for (Path path : stream) {
                    benchmarkFile = path;
                    System.out.println("Found benchmark file: " + benchmarkFile);
                    break; 
                }
            }
            if (benchmarkFile != null && Files.exists(benchmarkFile)) {
                try {
                    String benchmarkContent = new String(Files.readAllBytes(benchmarkFile));
                    benchmarkJson = new JSONObject(benchmarkContent);
                    combinedJson.put("benchmark_results", benchmarkJson);
                } catch (JSONException | IOException e) {
                    System.err.println("Error reading/parsing benchmark results file " + benchmarkFile + ": " + e.getMessage());
                }
            } else {
                 System.out.println("Benchmark results file not found in " + inputDir);
            }

            ExperimentWindow experimentWindow = findExperimentWindow(benchmarkJson);
            System.out.printf("Filtering energy measurements to experiment window [%d, %d] ms%n",
                    experimentWindow.startMs(), experimentWindow.endMs());

            // Find and add ORIGINAL container info file
            Path containerInfoFile = inputPath.resolve("container_info.json");
            if (Files.exists(containerInfoFile)) {
                 try {
                     String containerInfoContent = new String(Files.readAllBytes(containerInfoFile));
                     JSONObject containerInfoJson = new JSONObject(containerInfoContent);
                     combinedJson.put("container_info", containerInfoJson);
                 } catch (JSONException | IOException e) {
                      System.err.println("Error reading/parsing container_info.json: " + e.getMessage());
                 }
            } else {
                 System.out.println("container_info.json not found in " + inputDir);
            }
            
            // Add energy measurements from FIXED files
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(inputPath, "fixed_experiments_summary_*.json")) {
                 for (Path fixedEnergyFile : stream) {
                     String filename = fixedEnergyFile.getFileName().toString();
                     System.out.println("Processing fixed energy file: " + filename);
                     try {
                         String content = new String(Files.readAllBytes(fixedEnergyFile));
                         JSONArray jsonArray = new JSONArray(content);
                         JSONArray filteredArray = filterEnergyToWindow(jsonArray, experimentWindow);
                         System.out.printf("Kept %d of %d measurements from %s%n",
                                 filteredArray.length(), jsonArray.length(), filename);
                         if (filename.contains("dbserver")) {
                             combinedJson.put("db_server_energy", filteredArray);
                         } else if (filename.contains("apiserver")) {
                             combinedJson.put("api_server_energy", filteredArray);
                         }
                     } catch (JSONException | IOException e) {
                         System.err.println("Error reading/parsing fixed energy file " + filename + ": " + e.getMessage());
                     }
                 }
             } // No need for the old 'fixedFiles' list
            
            // Write the combined file
            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(combinedJson.toString(4));
                System.out.println("Combined JSON written to " + outputFile);
            }
            
        } catch (Exception e) {
            System.err.println("Error combining JSON files: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
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
            if (experiment == null) {
                continue;
            }
            JSONArray runs = experiment.optJSONArray("runs");
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

        JSONObject host = measurement.optJSONObject("host");
        Long timestamp = timestampValue(host, "timestamp");
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
