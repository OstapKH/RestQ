# macOS Energy Measurement Mode

Run the full RestQ benchmark stack (PostgreSQL, Spring API, benchmark client) in
containers on a single macOS machine and measure energy consumption with
macOS-native tooling. This mode replaces the PowerAPI/Scaphandre pipeline — which requires
Intel RAPL and therefore cannot run on Apple hardware — while producing result
files in the same format, loadable by the RestQ visualizer without changes.

## How it works: four attribution layers

Containers run with Apple's [`container`](https://github.com/apple/container)
tool, which executes **each container in its own lightweight virtual machine**.
Each container therefore appears as a separate macOS process, which makes a
layered attribution possible. From ground truth to finest grain:

| Layer | Series / output | How it is obtained | Status |
| --- | --- | --- | --- |
| Whole system | `system-total` | SMC battery-controller telemetry (`SystemPowerIn` on AC, discharge power on battery) | **Measured** |
| SoC (CPU+GPU+ANE) | `host-total` | `powermetrics` `combined_power` | **Measured** |
| Per container | `postgres`, `spring-api` | SoC CPU power × the container VM's share of system-wide CPU time | Accounted CPU time, modeled joules |
| Per process group | `pg-backends`, `pg-background`, `java`, `*-guest-other` | container power × the group's share of in-guest CPU time (per-second `/proc` sampler inside each guest) | Accounted CPU time, modeled joules |
| Per query | `per_query_energy.json` | `pg_stat_statements` + `pg_stat_kcache` CPU counters × the backends' joules-per-CPU-second | Accounted CPU time, modeled joules |

The per-container split is the same family of approach as SmartWatts
distributing RAPL package power across cgroups, which keeps macOS results
comparable in kind with the Grid5000 pipeline. The benchmark client's share is
tracked internally (for the sanity metric) but not reported — it is a load
generator, not a system under test.

The process groups are:

- `pg-backends` — Postgres workers executing benchmark queries
- `pg-background` — postmaster, checkpointer, WAL writer, autovacuum, …
- `java` — the Spring Boot process
- `db-guest-other` / `api-guest-other` — guest kernel and everything else
  in that container's VM

Per-query energy is computed per benchmark run window; the `_unattributed`
row in `per_query_energy.json` is backend CPU spent outside statement
execution (connection/protocol handling), so the books always balance.

## Requirements

- Apple silicon machine, macOS 26 or later. (Desktop machines work too, but the
  `system-total` series requires battery-controller telemetry and silently
  drops out on machines without a battery.)
- Apple `container` CLI: install the signed pkg from the
  [releases page](https://github.com/apple/container/releases), then run
  `container system start`.
- Passwordless sudo for powermetrics (put both lines in a file under
  `/etc/sudoers.d/`, replacing `<user>` with your username):

  ```
  <user> ALL=(root) NOPASSWD: /usr/bin/powermetrics
  <user> ALL=(root) NOPASSWD: /usr/bin/pkill -INT -x powermetrics
  ```

  The second line lets the collector stop the root-owned powermetrics process
  cleanly at the end of a run.
- Java 23+, Maven, and Ansible (`brew install ansible`).

No other setup is needed for the finer layers: the guest sampler is injected
over `container exec`, and `pg_stat_kcache` is baked into the `restq-db`
image (the playbook downloads the checksum-pinned PGDG package on the host
because containers have no DNS at image-build time).

## Running a benchmark

The load schedule (experiments, request rates, durations, pauses, pacing)
lives in `ansible-benchmark/benchmark-config/benchmark-config.xml`, which the
playbook mounts into the client container — **editing it requires no
rebuild**. (A copy bundled in the jar serves as fallback when
`$BENCHMARK_CONFIG` is unset; endpoint parameter sets come from
`parameters.xml`, overridable the same way via `$BENCHMARK_PARAMETERS`.)

Per experiment you can set `<pacing>`: `burst` (historical default — the
whole second's quota at once), `uniform` (evenly spaced), or `poisson`
(open-loop exponential inter-arrivals), and `<warmup>true</warmup>` to mark
JIT/cache warm-up phases that analysis should exclude. Before the schedule
starts, the client **pre-flight validates** every referenced endpoint URL
(non-2xx or empty result set aborts the run — a benchmark against empty
queries measures nothing) and per-status-class response counts are recorded
per run (`successful_requests` counts 2xx only).

Parameter values must select real data: TPC-H dates span 1992–1998, region
and segment values are UPPERCASE. With real (non-empty) parameters the Q4
endpoint costs ~1.3 s per call at scale 1 — schedule rates accordingly.

Infrastructure settings (`benchmark_type`, `scale_factor`, `database_source`,
`db_engine`, `postgresql_config`, `hikari_config`, container resource
limits, sampling interval) live in
`ansible-benchmark/configurable-ansible/configurable-inventory.yml`.
`db_engine: postgres` is the fully supported default; `mysql` is validated
end-to-end (requires `database_source: benchbase` for data loading; per-query
energy uses MySQL's performance_schema CPU counters, which the playbook
enables and grants automatically). By default the physical schema is exactly
what BenchBase's per-engine DDL provides (`index_parity: false`); BenchBase
gives PostgreSQL 15 secondary indexes and MySQL none, so for **cross-engine
comparisons set `index_parity: true`** or the engines run different physical
schemas (~2x on TPC-H Q4). Record the setting with the results either way. The power series keep their historical
target names (`pg-backends` = the database server process group) regardless
of engine. Then:

```bash
cd ansible-benchmark/configurable-ansible
caffeinate -i ansible-playbook -i configurable-inventory.yml \
    configurable-playbook-macos.yml -e deployment_mode=macos
```

The playbook verifies the environment, builds images, restores the database
(Hugging Face dump by default), starts the API, arms the power collector (with
guest and system samplers), runs the benchmark client to completion,
post-processes, and writes the experiment folder to
`~/Desktop/Results_rest_q_xml_benchmarks/experiment_<TYPE>_<timestamp>/`.
Each run gets its own timestamped folder; nothing from previous runs is
touched.

| File | Content |
| --- | --- |
| `combined_results.json` | Throughput/latency results plus embedded `db_server_energy` / `api_server_energy` power series (same structure as other modes) |
| `benchmark_results_*.json` | Raw client results file for this run |
| `experiment_info.json` | Experiment metadata + `measurement_mode` + attribution summary |
| `mongodump-DB-server.json` | Power series: `postgres`, `pg-backends`, `pg-background`, `db-guest-other`, `host-total`, `system-total` |
| `mongodump-API-server.json` | Power series: `spring-api`, `java`, `api-guest-other`, `host-total`, `system-total` |
| `per_query_energy.json` | Per run and query type: calls, CPU seconds, attributed energy (J, mJ/call) |
| `attribution_summary.json` | Energy totals per container and process group, sanity metrics |
| `benchmark_client.log` | Client log |
| `raw_powermetrics.plist`, `power_meta.json` | Raw samples + PID/container map, for auditing |
| `guest_samples_*.ndjson`, `pg_query_samples.csv`, `system_samples.ndjson` | Raw in-guest process/query counters and SMC samples, for auditing |

Analyze the folder with the [RestQ visualizer](visualizer.md).

## Reading the quality metrics

`attribution_summary.json` carries three metrics, one per attribution stage:

- **`attribution_coverage_of_cpu`** — fraction of the machine's CPU energy
  attributed to the three benchmark containers. 0.7–0.9 on an otherwise idle machine is
  healthy. A low value means other software competed for CPU: per-container
  *absolute* joules drift (CPU frequency scaling changes joules per
  CPU-second), but thanks to the in-guest split the workload numbers remain
  internally clean.
- **`guest_coverage`** — per container, the (energy-weighted) fraction of
  guest busy time explained by the known process groups. Expect ~0.7+ for
  the DB and ~0.85+ for the API under load.
- **`unmeasured_rest_j`** = `system_energy_j − host_combined_energy_j` — a
  **measured bound** on everything outside the SoC: DRAM, SSD, display,
  adapter losses. It is a whole-machine bound, not a per-container number,
  and on a laptop it normally dwarfs the SoC figure (display and base draw).

## Methodology statement (for write-ups)

> Whole-system and SoC power are measured (SMC telemetry and Apple
> powermetrics respectively). Per-container, per-process-group, and per-query
> energy are attributed by CPU-time share of the measured SoC CPU power
> (SmartWatts-style proportional attribution). Attribution quality is
> reported per run (`attribution_coverage_of_cpu`, `guest_coverage`);
> non-SoC consumption is bounded by the measured system−SoC difference.

Known limitations to state alongside results:

- Joules-per-CPU-second varies with the machine's power state; background
  load shifts absolute joules even though CPU-time accounting stays exact.
- Attribution covers CPU energy; per-container memory/storage energy is not
  observable on Apple silicon (bounded collectively by `unmeasured_rest_j`).
- Short-lived postgres processes (parallel query workers, one-shot
  connections) are captured via child-reap accounting: the postmaster's
  `cutime`/`cstime` carries the full lifetime CPU of every reaped child,
  which the sampler credits to `pg-backends`. Non-postgres processes not
  reaped by a sampled parent remain invisible to the split when they live
  under the sampling interval (the JVM does not fork, so the api container
  is unaffected in practice).
- Per-statement CPU (kcache) captures execution only; per-request protocol
  and session overhead appears as the `_unattributed` row.
- Hypervisor overhead is not visible to the guest and remains distributed
  proportionally across groups.

## Practical notes

- The pipeline tolerates a machine in normal use (that is what the per-process
  split is for), but for headline absolute numbers, one clean run on an idle
  machine is still the gold standard — it also calibrates how much your
  normal use perturbs the estimates.
- Keep the machine on mains power and prevent sleep for long runs
  (`caffeinate -i ansible-playbook …`).
- The first run downloads base images and the database dump (hundreds of MB);
  subsequent runs reuse them.
- Container resource limits (`macos_config.containers`) are part of the
  experiment definition — record them alongside results when comparing runs.
- p99 latency typically *falls* across early load steps (JIT and cache
  warm-up); schedule a warmup experiment (name containing `warmup`) if you
  want steady-state numbers — the visualizer can exclude it.
