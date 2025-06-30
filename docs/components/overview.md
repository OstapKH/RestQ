# Component Overview

This document provides an overview of the main components of the RestQ project. The project is structured into several modules, each with distinct responsibilities.

## Core

The `core` module is the central part of the application. Its primary responsibilities are:

*   **Object-Oriented Entity Mapping**: It provides an object-oriented representation of the entities for the TPC-H and TPC-C benchmarks. This simplifies interaction with the database schemas.
*   **BenchBase Integration**: It integrates BenchBase (if needed) to handle data loading and fulfillment for the benchmark databases.

## api-http

The `api-http` module provides the HTTP interface to the application. It is responsible for:

*   **REST API Endpoints**: It exposes a set of RESTful endpoints that allow clients to interact with the functionalities provided by the `core` module.
*   **Application Server**: It contains the main application logic to run as a web service.

### Client Simulation

Within the `api-http` module, `ApiBenchmark.java` is a key component for performance testing. Its role is to:

*   **Simulate Client Behavior**: It acts as a client that sends a configurable workload of requests to the API endpoints.
*   **Performance Measurement**: It gathers metrics on response times and throughput to evaluate the system's performance under load.
