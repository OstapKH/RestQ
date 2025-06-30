# Benchmark Configuration

The benchmark is configured primarily through the Ansible inventory file located at `ansible_benchmark/configurable-ansible/configurable-inventory.yml`. This file uses YAML format and allows you to control various aspects of the benchmark execution, from deployment environment to benchmark-specific parameters.

## Deployment Mode Configuration

The `deployment_mode` variable is the most important setting. It determines where the benchmark components will be deployed. There are three possible values:

-   `grid5000`: For deployment on the Grid'5000 testbed.
-   `manual`: For deployment on a set of user-provided machines.
-   `localhost`: For running everything on the local machine, useful for development and testing.

```yaml
# Deployment modes: 
# - grid5000: Auto-allocate nodes on Grid5000
# - manual: Use manually specified nodes
# - localhost: Run everything on current laptop
deployment_mode: grid5000 # Options: grid5000, manual, localhost
```

### Grid5000 Configuration

When `deployment_mode` is set to `grid5000`, the following parameters are used:

-   `grid_site`: The Grid'5000 site to use (e.g., `lille`).
-   `grid_time`: The reservation time for the Grid'5000 nodes (e.g., `'02:00:00'`).
-   `scaphandre_timestep_s`: Timestep in seconds for power monitoring with Scaphandre.
-   `ansible_ssh_private_key_file_g5k`: Path to the SSH private key for accessing Grid'5000.

```yaml
grid_site: lille
grid_time: '02:00:00'
scaphandre_timestep_s: 1
ansible_ssh_private_key_file_g5k: ~/.ssh/grid5000_key
```

### Manual Node Configuration

If `deployment_mode` is `manual`, you need to specify the nodes yourself:

-   `manual_nodes`: A dictionary containing host IPs for database, API server, and benchmark client.
    -   `node1_host`: Database Server IP.
    -   `node2_host`: API Server IP.
    -   `node3_host`: Benchmark Client IP.
-   `ssh_user`: SSH username for the manual nodes.
-   `ssh_key`: Path to the SSH private key for the manual nodes.

```yaml
manual_nodes:
  node1_host: "192.168.1.100"  # Database Server
  node2_host: "192.168.1.101"  # API Server  
  node3_host: "192.168.1.102"  # Benchmark Client
  ssh_user: "ubuntu"           # SSH username for manual nodes
  ssh_key: "~/.ssh/id_rsa"     # SSH private key for manual nodes
```

### Localhost Configuration

For `localhost` mode, you can configure the ports for the services:

-   `localhost_ports`:
    -   `postgres_port`: Port for PostgreSQL.
    -   `api_port`: Port for the RestQ API.
    -   `mongodb_port`: Port for MongoDB.

```yaml
localhost_ports:
  postgres_port: 5432
  api_port: 8086
  mongodb_port: 27017
```

## Repository Configuration

This section defines where to get the source code from.

-   `github_repo_url`: The URL of the RestQFramework GitHub repository.
-   `project_directory`: The directory where the project will be cloned on the remote machines.

```yaml
github_repo_url: "https://github.com/OstapKH/RestQ.git"
project_directory: "/root/RestQ"
```

## Database Configuration

The `database_source` parameter determines how the database is populated.

-   `huggingface`: Downloads a pre-made database dump from a Hugging Face repository. You need to specify `huggingface_repo`.
-   `dumps`: Uses a local database dump. The path is specified in `dumps_directory`. The dump file should follow the naming convention: `{benchmark_type}_sc_f_{scale_factor}.zip`.
-   `benchbase`: Uses BenchBase to generate the data.

```yaml
database_source: huggingface
huggingface_repo: "OstapK/tpch_sc_f_1"
dumps_directory: "{{ project_directory }}/sql_dumps/postgresql"
```

## Benchmark Configuration

These parameters control the benchmark workload.

-   `benchmark_type`: The type of benchmark to run. Options are `TPCC` and `TPCH`.
-   `scale_factor`: Defines the size of the dataset.
    -   For `TPCC`: it's the number of warehouses (e.g., `1.0`, `5.0`).
    -   For `TPCH`: it's the data size multiplier (e.g., `0.01`, `1.0`, `10.0`).

```yaml
benchmark_type: TPCH
scale_factor: "1.0"
```

## Experiment Tracking

-   `experiment_duration`: Used for grid reservation time.
-   `timestamp`: A timestamp for the experiment run.

```yaml
experiment_duration: '02:00:00'
timestamp: "{{ ansible_date_time.iso8601 }}"
```

## Benchmark Execution Configuration

In addition to the Ansible-level configuration, you can fine-tune the benchmark execution for both `TPC-H` and `TPC-C`. This is done via XML files located in `ansible-benchmark/benchmark-config.xml`.

### TPC-H Configuration (`benchmark-config.xml`)

The `TPC-H` benchmark is configured using `benchmark-config.xml`. This file allows you to define a series of experiments with varying load conditions and request mixes.

Here's an example configuration:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<benchmark-config>
<pauseBetweenExperiments-ms>10000</pauseBetweenExperiments-ms>

    <endpoints>
        <endpoint name="pricing-summary">
            <url>/pricing-summary?delta=3&amp;shipDate=1998-12-01</url>
            <url>/pricing-summary?delta=5&amp;shipDate=1999-06-01</url>
            <url>/pricing-summary?delta=2&amp;shipDate=2000-01-15</url>
        </endpoint>
        <endpoint name="supplier-part-info">
            <url>/supplier-part-info?size=10&amp;type=A&amp;region=America</url>
            <url>/supplier-part-info?size=15&amp;type=B&amp;region=Europe</url>
            <url>/supplier-part-info?size=20&amp;type=C&amp;region=Asia</url>
        </endpoint>
        <endpoint name="order-revenue-info">
            <url>/order-revenue-info?segment=Automobile&amp;date=2023-01-01</url>
            <url>/order-revenue-info?segment=Building&amp;date=2023-06-01</url>
            <url>/order-revenue-info?segment=Furniture&amp;date=2023-12-01</url>
        </endpoint>
        <endpoint name="order-priority-count">
            <url>/order-priority-count?date=2023-01-01</url>
            <url>/order-priority-count?date=2023-06-01</url>
            <url>/order-priority-count?date=2023-12-01</url>
        </endpoint>
        <endpoint name="local-supplier-volume">
            <url>/local-supplier-volume?region=Asia&amp;startDate=2023-01-01</url>
            <url>/local-supplier-volume?region=Europe&amp;startDate=2023-06-01</url>
            <url>/local-supplier-volume?region=America&amp;startDate=2023-12-01</url>
        </endpoint>
        <endpoint name="revenue-increase">
            <url>/revenue-increase?discount=0.2&amp;quantity=10&amp;startDate=2023-01-01</url>
            <url>/revenue-increase?discount=0.15&amp;quantity=20&amp;startDate=2023-06-01</url>
            <url>/revenue-increase?discount=0.3&amp;quantity=5&amp;startDate=2022-01-01</url>
            <url>/revenue-increase?discount=0.25&amp;quantity=15&amp;startDate=2022-06-01</url>
        </endpoint>
    </endpoints>

<experiment>
    <experiment_name>EXP_1_warmup</experiment_name>
    <runs>5</runs>
    <pause-between-runs-ms>5000</pause-between-runs-ms>
    <connections>1</connections>
    <requests-per-second>20</requests-per-second>
    <duration-seconds>30</duration-seconds>
    
    <probabilities>
        <probability endpoint="pricing-summary">0.0</probability>
        <probability endpoint="supplier-part-info">0.0</probability>
        <probability endpoint="order-revenue-info">0.0</probability>
        <probability endpoint="order-priority-count">1.0</probability>
        <probability endpoint="local-supplier-volume">0.0</probability>
        <probability endpoint="revenue-increase">0.0</probability>
    </probabilities>
</experiment>

<!-- ... other experiments ... -->

</benchmark-config>
```

#### Key elements:

*   `<endpoints>`: This section defines the API endpoints that will be called during the benchmark.
    *   Each `<endpoint>` has a `name` and contains one or more `<url>` tags.
    *   The benchmark will randomly pick one of the `<url>`s for a given endpoint when it's selected for execution. This allows for parameter randomization.
*   `<experiment>`: Defines a single benchmark experiment. You can have multiple `<experiment>` blocks to run them sequentially.
    *   `experiment_name`: A unique name for the experiment.
    *   `runs`: How many times to repeat this experiment.
    *   `pause-between-runs-ms`: The pause duration in milliseconds between each run.
    *   `connections`: The number of concurrent connections to the API.
    *   `requests-per-second`: The target request rate.
    *   `duration-seconds`: The duration of the experiment in seconds.
    *   `<probabilities>`: This crucial section determines the request mix.
        *   Each `<probability>` tag maps an endpoint (by its `name`) to a probability value between 0.0 and 1.0. The sum of all probabilities should ideally be 1.0.

### TPC-C Configuration (`benchmark-config.xml`)

Here's an example:

```xml
<?xml version="1.0"?>
<parameters>

    <!-- Connection details -->
    <type>POSTGRES</type>
    <driver>org.postgresql.Driver</driver>
    <url>jdbc:postgresql://localhost:5432/tpccdb</url>
    <username>admin</username>
    <password>password</password>
    <reconnectOnConnectionFailure>true</reconnectOnConnectionFailure>
    <isolation>TRANSACTION_SERIALIZABLE</isolation>
    <batchsize>128</batchsize>

    <!-- Scale factor is the number of warehouses in TPCC -->
    <scalefactor>1</scalefactor>

    <!-- The workload -->
    <terminals>1</terminals>
    <works>
        <work>
            <time>60</time>
            <rate>10000</rate>
            <weights>45,43,4,4,4</weights>
        </work>
    </works>

    <!-- TPCC specific -->
    <transactiontypes>
        <transactiontype>
            <name>NewOrder</name>
        </transactiontype>
        <transactiontype>
            <name>Payment</name>
        </transactiontype>
        <transactiontype>
            <name>OrderStatus</name>
        </transactiontype>
        <transactiontype>
            <name>Delivery</name>
        </transactiontype>
        <transactiontype>
            <name>StockLevel</name>
        </transactiontype>
    </transactiontypes>
</parameters> 
```

#### Key elements:

*   **Connection details**: Standard JDBC connection parameters (`<type>`, `<driver>`, `<url>`, `<username>`, `<password>`).
*   `<scalefactor>`: For `TPC-C`, this represents the number of warehouses.
*   **Workload definition**:
    *   `<terminals>`: The number of concurrent terminals (clients).
    *   `<works>`: Defines the workload phases.
        *   `<time>`: Duration of the work phase in seconds.
        *   `<rate>`: The transaction rate.
        *   `<weights>`: A comma-separated list of weights for the transaction types. The order of weights corresponds to the order of `<transactiontype>` elements. In the example, `NewOrder` has a weight of 45, `Payment` has 43, and so on.
*   `<transactiontypes>`: Lists the `TPC-C` transaction types. The order is important as it maps to the `<weights>`.
