# RestQ Visualizer

Browser-based tool for analyzing experiment folders — energy, performance,
per-request latencies, and per-query energy — from both the Grid5000 and
macOS pipelines.

## Starting it

```bash
cd visualisation
python visualizer.py [experiment_folder ...]
```

Starts a local Dash server and opens your browser. Requirements are in
`visualisation/requirements.txt` (`pip install -r requirements.txt`).

**Adding folders:** pass paths on the command line, pick from the dropdown
(it scans `~/Desktop/Results_rest_q_xml_benchmarks`), use the **📂 Add
folder…** native picker, or paste a path. Several folders can be loaded at
once for comparison; each appears as a removable chip showing its
measurement mode. Broken folders show the load error on the chip.

> The server parses each folder once and caches it — after re-running an
> experiment or changing the code, restart `visualizer.py`.

## Controls (top bar)

- **Experiment selector** — a single experiment, *All Experiments*, or
  *All Experiments without warmup* (warmup = the experiment's
  `<warmup>true</warmup>` flag when present, else a name containing
  `warmup`; the view then starts after the last warmup ends).
- **window ms** — bucket size for averaging dense power series (0 = raw).
  Applies to power series only, never to per-request points.
- **overlay series** (off by default) — the mongodump power series:
  container-level targets (dotted), process-level targets from guest
  sampling (dash-dot), and the `host-total` / `system-total` references.
- **run boundaries** (on by default) — legacy chronology markers: a shaded
  span per run (tint cycled per experiment), green dashed start and red
  dashed end lines, `Run N` labels when a single experiment is selected,
  and gray pause regions between experiments labeled with their duration
  (pauses longer than 5 s).
- **all host series** (off by default) — on single-machine (macOS) data the
  db and api host series are byte-identical, so by default they collapse
  into one `host (shared)` trace; check this to see both raw series.

All time axes are UTC; hover shows full date and time down to milliseconds.

## Tabs

### Timeline
Three stacked panels on a shared time axis: power (W), throughput (req/s),
and p99 latency (ms). One color pair per folder (db/api); host references in
gray.

### Requests
Every request as a horizontal **duration segment**: it starts when the client
sent the request, ends when the response arrived, and sits at height
y = latency (ms) — so a segment's length on the time axis equals its height's
value, and overlapping segments show concurrency. One trace per experiment;
run boundaries apply here too. Only present when the client recorded
per-request latencies.

Zoomed out, a spike-preserving sample of at most ~15 000 requests renders (a
caption reports the sampling); every zoom reloads **full resolution** for the
visible window, including requests that only partially overlap it.
Double-click to reset.

### Queries
Per-query energy table (macOS runs with guest sampling): for each run and
query type — calls, CPU seconds, energy (J), mJ/call, sorted by energy.
The `_unattributed` row is backend CPU outside statement execution
(per-request protocol/session overhead). Respects the experiment selector.

### Correlation
Per-run derived metrics for each folder: energy per request (J/req) bars and
mean power vs achieved throughput.

### Compare runs
Container power series of all loaded folders overlaid on a
seconds-from-start axis, plus a totals table (energy, mean throughput, p99).

## Reading macOS folders

See [macOS Energy Measurement](macos-energy-measurement.md) for what each
series means and how to judge attribution quality. Quick key:

| Series | Meaning |
| --- | --- |
| `db`/`api (container)` | per-container attributed power (embedded series) |
| `host (shared)` / `host-total` | SoC (CPU+GPU+ANE) power — measured |
| `system-total` | whole-system input power — measured bound incl. DRAM/SSD/display |
| `pg-backends` | Postgres workers executing your queries |
| `pg-background` | Postgres housekeeping (checkpointer, autovacuum, …) |
| `java` | Spring Boot process |
| `*-guest-other` | guest kernel + everything else in that container VM |

The `postgres`/`system-total` style overlays and the process-level series
appear when **overlay series** is checked.
