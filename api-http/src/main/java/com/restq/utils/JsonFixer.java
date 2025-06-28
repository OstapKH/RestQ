package com.restq.utils;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

public class JsonFixer {

    public static void fixJsonFilesInFolder(String inputFolder) {
        Path inputFolderPath = Paths.get(inputFolder);
        if (!Files.isDirectory(inputFolderPath)) {
            System.err.println("The input path is not a directory: " + inputFolder);
            return;
        }
        System.out.println("Processing ONLY Scaphandre JSON files in folder: " + inputFolder);

        try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(inputFolderPath, "experiments_summary_*.json")) {
            for (Path filePath : directoryStream) {
                String filename = filePath.getFileName().toString();
                Path outputFilePath = inputFolderPath.resolve("fixed_" + filename);
                String content = new String(Files.readAllBytes(filePath));

                System.out.println("Processing file: " + filename);

                // Handle Scaphandre JSON files (potentially multiple objects)
                JSONArray jsonArray = new JSONArray();
                // Correctly escaped regex pattern for Java
                Pattern pattern = Pattern.compile(
                    "\\{\\s*\"host\"\\s*:\\s*\\{.*?\\}\\s*,\\s*\"consumers\"\\s*:\\s*\\[.*?\\]\\s*,\\s*\"sockets\"\\s*:\\s*\\[.*?\\]\\s*\\}",
                    Pattern.DOTALL
                );
                Matcher matcher = pattern.matcher(content);
                int matchCount = 0;
                int validCount = 0;

                while (matcher.find()) {
                    matchCount++;
                    String jsonObjectStr = matcher.group();
                    try {
                        jsonObjectStr = jsonObjectStr.replaceAll(",\s*}", "}");
                        jsonObjectStr = jsonObjectStr.replaceAll(",\s*]", "]");
                        JSONObject jsonObject = new JSONObject(jsonObjectStr);
                        if (validateJsonStructure(jsonObject)) { // Use existing validation for Scaphandre structure
                            jsonArray.put(jsonObject);
                            validCount++;
                        } else {
                            System.out.println("Scaphandre JSON object " + matchCount + " in " + filename + " failed validation");
                        }
                    } catch (JSONException ex) {
                        System.out.println("Error parsing Scaphandre JSON object " + matchCount + " in " + filename + ": " + ex.getMessage());
                        // System.out.println("Problematic JSON string: " + jsonObjectStr.substring(0, Math.min(200, jsonObjectStr.length())) + "...");
                    }
                }

                System.out.println("Found " + matchCount + " potential Scaphandre objects in " + filename + ", validated " + validCount);

                try (FileWriter fileWriter = new FileWriter(outputFilePath.toFile())) {
                    fileWriter.write(jsonArray.toString(4)); // Write the array
                    System.out.println("Fixed Scaphandre file saved to " + outputFilePath + " with " + jsonArray.length() + " entries");
                    // Optional: Add verification for the array file as before
                } catch (IOException e) {
                    System.err.println("Error writing fixed Scaphandre file " + outputFilePath + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error processing Scaphandre files in directory " + inputFolder + ": " + e.getMessage());
        }
    }

    private static boolean validateJsonStructure(JSONObject jsonObject) {
        try {
            if (!jsonObject.has("host") || !jsonObject.has("consumers") || !jsonObject.has("sockets")) {
                // System.out.println("Missing required Scaphandre top-level fields");
                return false;
            }
            JSONObject host = jsonObject.getJSONObject("host");
            if (!host.has("consumption") || !host.has("timestamp")) { // Removed 'components' check for broader compatibility if needed
                // System.out.println("Missing required Scaphandre host fields");
                return false;
            }
            JSONArray consumers = jsonObject.getJSONArray("consumers");
            for (int i = 0; i < consumers.length(); i++) {
                JSONObject consumer = consumers.getJSONObject(i);
                if (!consumer.has("exe") || /*!consumer.has("cmdline") ||*/ !consumer.has("pid") ||
                    !consumer.has("consumption") || !consumer.has("timestamp")) { // cmdline might be empty
                    // System.out.println("Missing required Scaphandre consumer fields at index " + i);
                    return false;
                }
            }
            JSONArray sockets = jsonObject.getJSONArray("sockets");
            for (int i = 0; i < sockets.length(); i++) {
                JSONObject socket = sockets.getJSONObject(i);
                if (!socket.has("id") || !socket.has("consumption") || !socket.has("timestamp")) {
                    // System.out.println("Missing required Scaphandre socket fields at index " + i);
                    return false;
                }
            }
            return true;
        } catch (JSONException e) {
            System.out.println("Error validating Scaphandre JSON structure: " + e.getMessage());
            return false;
        }
    }

    // Optional: Main method for testing the fixer independently
    // public static void main(String[] args) {
    //     if (args.length < 1) {
    //         System.err.println("Usage: java com.restq.utils.JsonFixer <inputDirectory>");
    //         System.exit(1);
    //     }
    //     fixJsonFilesInFolder(args[0]);
    // }
}

