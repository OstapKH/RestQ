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
                    JSONObject benchmarkJson = new JSONObject(benchmarkContent);
                    combinedJson.put("benchmark_results", benchmarkJson);
                } catch (JSONException | IOException e) {
                    System.err.println("Error reading/parsing benchmark results file " + benchmarkFile + ": " + e.getMessage());
                }
            } else {
                 System.out.println("Benchmark results file not found in " + inputDir);
            }

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
                         if (filename.contains("dbserver")) {
                             combinedJson.put("db_server_energy", jsonArray);
                         } else if (filename.contains("apiserver")) {
                             combinedJson.put("api_server_energy", jsonArray);
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
        }
    }
} 

