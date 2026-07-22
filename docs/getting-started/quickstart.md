# Quick Start

This page gets you from a clean checkout to your first benchmark run and its
analysis. Install the [prerequisites](installation.md) first.

## 1. Pick a deployment mode

RestQ runs the same benchmark stack (database, Spring API, benchmark client)
in several environments, selected by `deployment_mode` in
`ansible-benchmark/configurable-ansible/configurable-inventory.yml`:

| Mode | Where it runs | Energy measurement |
| --- | --- | --- |
| `macos` | One Apple silicon machine, containers via Apple `container` | powermetrics + SMC telemetry ([details](../user-guide/macos-energy-measurement.md)) |
| `grid5000` | Grid5000 cluster nodes | PowerAPI / Scaphandre (Intel RAPL) |
| `localhost` | Current machine via Docker | none (performance only) |
| `manual` | Hosts you provide in the inventory | depends on hardware |

## 2. Configure the run

Two files drive an experiment:

- **Infrastructure** — `ansible-benchmark/configurable-ansible/configurable-inventory.yml`:
  benchmark type (TPC-C/TPC-H), scale factor, database engine
  (`postgres`/`mysql`), PostgreSQL and Hikari tuning, container resource
  limits.
- **Load schedule** — `ansible-benchmark/benchmark-config/benchmark-config.xml`:
  experiments, request rates, durations, pauses, pacing
  (`burst`/`uniform`/`poisson`), warmup flags. Editing it requires no
  rebuild.

See [Configuration](configuration.md) for the full reference.

## 3. Run the benchmark

For the macOS mode (single Apple silicon machine):

```bash
cd ansible-benchmark/configurable-ansible
caffeinate -i ansible-playbook -i configurable-inventory.yml \
    configurable-playbook-macos.yml -e deployment_mode=macos
```

For the other modes:

```bash
cd ansible-benchmark/configurable-ansible
ansible-playbook -i configurable-inventory.yml configurable-playbook.yml
```

The default Grid’5000 profile is the complete TPC-H SF1 query study: 19
queries, five labelled non-empty parameter sets per query, one 20-second
warm-up and three 45-second measured runs. Allow roughly **80–105 minutes**
including reservation, deployment and database restore; the result folder is
normally **10–20 MB**.

Before spending that reservation time, run the two-query Q01/Q06 smoke profile:

```bash
ansible-playbook -i configurable-inventory.yml configurable-playbook.yml \
  -e benchmark_config_file=benchmark-config-smoke.xml \
  -e benchmark_parameters_file=parameters-smoke.xml
```

The playbook clones `github_repo_url` on all remote nodes. Commit and push the
implementation you intend to measure before a Grid’5000 run; unpushed changes
from the laptop are not transferred, while the two selected XML files are.

The playbook builds the images, restores the database, starts the API, runs
the benchmark client to completion, and writes an experiment folder to
`~/Desktop/Results_rest_q_xml_benchmarks/experiment_<TYPE>_<timestamp>/`.

Scaphandre records throughout setup, but the energy series embedded in
`combined_results.json` are trimmed to the inclusive interval from the first
experiment start to the last experiment end. The raw Scaphandre files retain
the complete recording for diagnostics.

In `grid5000` mode, the playbook releases its OAR reservations only when the
benchmark process exits successfully, strict client and energy validation
passes, and `combined_results.json` is downloaded. Any failed preflight, HTTP
or transport error, missing run/parameter, incomplete energy coverage, or
collection failure preserves the jobs for investigation.

## 4. Analyze the results

```bash
cd visualisation
pip install -r requirements.txt
python visualizer.py ~/Desktop/Results_rest_q_xml_benchmarks/experiment_*
```

The [visualizer](../user-guide/visualizer.md) opens in the browser with
power/throughput/latency timelines, per-request latencies, and per-query
energy.
