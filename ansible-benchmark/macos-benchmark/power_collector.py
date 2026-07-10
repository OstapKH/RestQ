#!/usr/bin/env python3
"""Collect and attribute macOS power measurements for RestQ container benchmarks.

Runs on Apple silicon (macOS 26+) against containers launched with Apple's
`container` tool, where each container executes in its own lightweight VM and
therefore appears as a separate macOS process
(com.apple.Virtualization.VirtualMachine).

Three subcommands:

  collect    Discover the VM process of each benchmark container, then record
             `powermetrics` samples (cpu_power + tasks samplers) to a raw plist
             stream plus a meta.json sidecar with the PID<->container mapping.
             With --sample-guests, additionally runs guest_sampler.sh inside
             the db/api guests (per-second /proc process-group CPU counters)
             and a 5 s pg_stat_statements+pg_stat_kcache snapshot loop.

  attribute  Post-process the raw stream into per-container power series in the
             PowerAPI mongodump JSON schema consumed by the visualizer, plus an
             attribution summary with the sanity metric. When guest samples
             exist, each container's power is further split by in-guest CPU
             share into process-group targets (pg-backends, pg-background,
             db-guest-other, java, api-guest-other).

  queries    Join the pg stats snapshots with the benchmark run windows and the
             pg-backends power series into per_query_energy.json: per run and
             query type, CPU seconds and attributed energy.

Attribution model: per sample, a container's power is the machine CPU power
scaled by the container VM process's share of system-wide CPU time
(cputime_ms_per_s / all_tasks.cputime_ms_per_s). The `host-total` target is the
SoC combined power (CPU+GPU+ANE) and serves as ground truth. The benchmark
client's share is tracked for the sanity metric but never emitted as a series.

Requires passwordless sudo for /usr/bin/powermetrics, and for unbounded
collection also for `/usr/bin/pkill -INT -x powermetrics` (used to stop the
root-owned powermetrics process cleanly).

Stdlib only; no third-party dependencies.
"""

import argparse
import bisect
import csv
import datetime
import json
import os
import plistlib
import re
import signal
import subprocess
import sys
import threading
import time

VM_PROCESS_PATTERN = "Virtualization.VirtualMachine"
POWERMETRICS = "/usr/bin/powermetrics"
PKILL = "/usr/bin/pkill"
GUEST_SAMPLER = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                             "guest_sampler.sh")

# Stage-2 emitted target names per role, keyed by in-guest process group.
# The "other" group covers guest busy time not claimed by any known group.
GUEST_TARGETS = {
    "db": {"backends": "pg-backends", "background": "pg-background",
           "other": "db-guest-other"},
    "api": {"java": "java", "other": "api-guest-other"},
}

# Snapshot of the cumulative per-query counters. pg_stat_statements reports
# total_exec_time in milliseconds; pg_stat_kcache reports exec_*_time in
# seconds of CPU (getrusage).
PG_SNAPSHOT_SQL = (
    "COPY (SELECT extract(epoch from clock_timestamp()), s.queryid, s.calls,"
    " s.total_exec_time, k.exec_user_time, k.exec_system_time,"
    " left(regexp_replace(s.query, E'[\\n\\r]+', ' ', 'g'), 120)"
    " FROM pg_stat_statements s"
    " JOIN pg_stat_kcache() k"
    " ON k.queryid = s.queryid AND k.userid = s.userid AND k.dbid = s.dbid"
    " WHERE k.top) TO STDOUT (FORMAT csv)")

# The same CSV shape produced from MySQL >= 8.0.28. SUM_CPU_TIME is in
# picoseconds and has no user/system split, so all CPU lands in the user
# column. Unvalidated: no MySQL end-to-end run exists yet, which is why the
# statement stays behind the provider seam.
MYSQL_SNAPSHOT_SQL = (
    "SELECT CONCAT_WS(',', UNIX_TIMESTAMP(NOW(6)), CONCAT('\"', DIGEST, '\"'),"
    " COUNT_STAR, SUM_TIMER_WAIT/1e9, SUM_CPU_TIME/1e12, 0,"
    " CONCAT('\"', LEFT(REPLACE(REPLACE(DIGEST_TEXT, '\"', ''), ',', ' '), 120), '\"'))"
    " FROM performance_schema.events_statements_summary_by_digest"
    " WHERE SCHEMA_NAME IS NOT NULL")


def query_stats_command(args):
    """Return the argv, run inside the db container, that emits one cumulative
    per-query counter CSV row per statement.

    Row shape: ts, queryid, calls, total_exec_ms, cpu_user_s, cpu_system_s,
    query_text. Returns None when the engine has no per-query CPU counters;
    per-query energy then degrades to absent, like system-total on machines
    without a battery."""
    if args.db_engine == "postgres":
        return ["psql", "-U", args.pg_user, "-d", args.pg_db,
                "-Atc", PG_SNAPSHOT_SQL]
    if args.db_engine == "mysql":
        return ["mysql", "-u", args.pg_user, f"-p{args.pg_password}",
                "-N", "-B", "-e", MYSQL_SNAPSHOT_SQL]
    return None

# Emitted target name per role, fixed by the design spec. The client is
# tracked internally (the sanity metric needs it) but never emitted.
ROLE_TARGETS = {"db": "postgres", "api": "spring-api", "client": None}

# Emitted targets per mongodump output file. The split mirrors the two-server
# layout of the Grid5000 experiments so the visualizer loads the files
# unchanged.
FILE_LAYOUT = {
    "mongodump-DB-server.json": ["postgres", "pg-backends", "pg-background",
                                 "db-guest-other", "host-total",
                                 "system-total"],
    "mongodump-API-server.json": ["spring-api", "java", "api-guest-other",
                                  "host-total", "system-total"],
}

# Scaphandre-compatible per-role outputs. JsonCombiner globs
# fixed_experiments_summary_*.json and embeds them into combined_results.json
# as db_server_energy / api_server_energy — top-level keys the visualizer
# requires at load time. Schema per entry (consumption in microwatts,
# timestamps in epoch seconds):
#   {"host": {"timestamp": s, "consumption": uW},
#    "consumers": [{"container": {"id": name}, "consumption": uW, "timestamp": s}]}
SCAPHANDRE_LAYOUT = {
    "fixed_experiments_summary_dbserver.json": "db",
    "fixed_experiments_summary_apiserver.json": "api",
}


# --------------------------------------------------------------------------
# Discovery
# --------------------------------------------------------------------------

def list_vm_pids():
    """Return the PIDs of all VirtualMachine XPC processes currently running."""
    result = subprocess.run(["pgrep", "-f", VM_PROCESS_PATTERN],
                            capture_output=True, text=True)
    return [int(line) for line in result.stdout.split() if line.strip()]


def container_for_pid(pid):
    """Return the container ID whose backing files this VM process holds open.

    Returns None when the process belongs to no `container` VM.

    Every `container` VM keeps kernel.bin/initfs.ext4 open from
    .../com.apple.container/containers/<id>/ — other VMs (e.g. Docker
    Desktop's) hold none of these, which makes this mapping unambiguous.
    """
    result = subprocess.run(["lsof", "-a", "-p", str(pid), "-Fn"],
                            capture_output=True, text=True)
    for line in result.stdout.splitlines():
        if line.startswith("n") and "/com.apple.container/containers/" in line:
            path = line[1:]
            tail = path.split("/com.apple.container/containers/", 1)[1]
            return tail.split("/", 1)[0]
    return None


def discover_container_pids(container_names):
    """Return a mapping of container name to VM process PID.

    Raises RuntimeError when any requested container has no VM process."""
    mapping = {}
    for pid in list_vm_pids():
        cid = container_for_pid(pid)
        if cid in container_names:
            mapping[cid] = pid
    missing = set(container_names) - set(mapping)
    if missing:
        raise RuntimeError(
            f"No VM process found for container(s): {', '.join(sorted(missing))}. "
            "Are they running under Apple `container`?")
    return mapping


# --------------------------------------------------------------------------
# collect
# --------------------------------------------------------------------------

def guest_sample_path(output, role):
    """Return the path of guest_samples_<role>.ndjson next to the raw plist output."""
    return os.path.join(os.path.dirname(os.path.abspath(output)) or ".",
                        f"guest_samples_{role}.ndjson")


def parse_system_power(ioreg_text):
    """Parse the whole-system power draw from `ioreg -rn AppleSmartBattery` output.

    Returns (watts, source), or None when the telemetry is absent.

    On AC the battery controller's PowerTelemetryData reports SystemPowerIn
    (mW) — total system input power including adapter efficiency loss. On
    battery, InstantAmperage (mA, two's-complement negative when
    discharging) x Voltage (mV) gives the draw. Top-level keys are matched
    with their ' = ' spacing so the same-named keys nested inside
    BatteryData (which use '=' without spaces) are not confused.
    """
    external = re.search(r'"ExternalConnected" = (Yes|No)', ioreg_text)
    if external and external.group(1) == "Yes":
        m = re.search(r'"SystemPowerIn"=(\d+)', ioreg_text)
        if m:
            return int(m.group(1)) / 1000.0, "ac"
        return None
    amp = re.search(r'"InstantAmperage" = (-?\d+)', ioreg_text)
    volt = re.search(r'"Voltage" = (\d+)', ioreg_text)
    if not (amp and volt):
        return None
    amperage = int(amp.group(1))
    # ioreg may print negative values as unsigned 64-bit integers.
    if amperage >= 2 ** 63:
        amperage -= 2 ** 64
    return abs(amperage) * int(volt.group(1)) / 1e6, "battery"


def read_system_power():
    """Read one whole-system power sample, or None when unavailable."""
    result = subprocess.run(["ioreg", "-rn", "AppleSmartBattery"],
                            capture_output=True, text=True)
    if result.returncode != 0:
        return None
    return parse_system_power(result.stdout)


def system_sampler_loop(out_path, stop_event, interval_s=1.0):
    """Append one whole-system power sample per second until stopped.

    A failed read is skipped; a sampling error never kills collection."""
    with open(out_path, "w") as out:
        while not stop_event.is_set():
            reading = read_system_power()
            if reading is not None:
                watts, source = reading
                out.write(json.dumps({"ts": time.time(),
                                      "system_w": round(watts, 3),
                                      "source": source}) + "\n")
                out.flush()
            stop_event.wait(interval_s)


def start_guest_samplers(args, roles):
    """Run guest_sampler.sh inside the db/api guests via `exec -i`.

    Returns (procs, open_files, info) where info goes into the meta sidecar.
    host_epoch_at_start lets the attribute stage detect genuine clock skew
    (it includes exec startup latency, so small offsets are ignored there).
    """
    procs, files, info = [], [], {}
    for role in ("db", "api"):
        if role not in roles:
            continue
        out_path = guest_sample_path(args.output, role)
        outf = open(out_path, "w")
        script = open(GUEST_SAMPLER)
        started = time.time()
        p = subprocess.Popen(
            [args.container_cli, "exec", "-i", roles[role], "sh", "-s"],
            stdin=script, stdout=outf, stderr=subprocess.DEVNULL)
        script.close()
        procs.append(p)
        files.append(outf)
        info[role] = {"file": os.path.basename(out_path),
                      "host_epoch_at_start": started}
    return procs, files, info


def pg_snapshot_loop(args, db_container, out_path, stop_event):
    """Snapshot the per-query counters to out_path on a fixed interval.

    Uses the engine-specific stats provider and writes one snapshot every
    --pg-snapshot-interval-s until stopped. A failed snapshot never kills
    collection, because the stats may not be ready in the first seconds.

    The file opens with "w", never "a": the workdir persists across runs, and
    stale samples from a previous run would mix two epochs of the cumulative
    counters (the database restarts between runs), corrupting window deltas."""
    stats_cmd = query_stats_command(args)
    if stats_cmd is None:
        logger_print(f"no per-query stats provider for engine "
                     f"'{args.db_engine}'; skipping query sampling")
        return
    with open(out_path, "w") as out:
        while not stop_event.is_set():
            result = subprocess.run(
                [args.container_cli, "exec", db_container] + stats_cmd,
                capture_output=True, text=True)
            if result.returncode == 0 and result.stdout:
                out.write(result.stdout)
                out.flush()
            stop_event.wait(args.pg_snapshot_interval_s)


def logger_print(msg):
    """Print a progress message for the collect stage."""
    print(f"collect: {msg}", flush=True)


def cmd_collect(args):
    """Record powermetrics samples plus the sidecar sampler outputs."""
    roles = {"db": args.db_container, "api": args.api_container,
             "client": args.client_container}
    roles = {role: name for role, name in roles.items() if name}
    if not roles:
        sys.exit("collect: provide at least one of --db-container/--api-container/--client-container")

    pid_by_name = discover_container_pids(set(roles.values()))
    meta = {
        "started": utc_now_iso(),
        "interval_ms": args.interval_ms,
        "roles": {
            role: {"container": name, "pid": pid_by_name[name],
                   "target": ROLE_TARGETS[role]}
            for role, name in roles.items()
        },
    }

    # The whole-system sampler (SMC telemetry) is always on: it is
    # unprivileged and cheap.
    sys_path = os.path.join(
        os.path.dirname(os.path.abspath(args.output)) or ".",
        "system_samples.ndjson")
    meta["system_samples"] = os.path.basename(sys_path)
    sys_stop = threading.Event()
    threading.Thread(target=system_sampler_loop,
                     args=(sys_path, sys_stop), daemon=True).start()

    guest_procs, guest_files, pg_stop = [], [], None
    if args.sample_guests:
        guest_procs, guest_files, guest_info = start_guest_samplers(args, roles)
        meta["guest_sampling"] = guest_info
        if "db" in roles:
            pg_out = os.path.join(
                os.path.dirname(os.path.abspath(args.output)) or ".",
                "pg_query_samples.csv")
            meta["pg_query_samples"] = os.path.basename(pg_out)
            pg_stop = threading.Event()
            pg_thread = threading.Thread(
                target=pg_snapshot_loop,
                args=(args, roles["db"], pg_out, pg_stop), daemon=True)
            pg_thread.start()

    with open(args.meta, "w") as f:
        json.dump(meta, f, indent=2)

    cmd = ["sudo", "-n", POWERMETRICS, "--samplers", "cpu_power,tasks",
           "-i", str(args.interval_ms), "-f", "plist", "-o", args.output]
    if args.duration_s:
        samples = max(1, round(args.duration_s * 1000 / args.interval_ms))
        cmd += ["-n", str(samples)]
    proc = subprocess.Popen(cmd)

    # powermetrics runs as root and cannot be signalled directly; on our own
    # termination, sudo delivers SIGINT, its clean-exit signal.
    def stop(signum, frame):
        subprocess.run(["sudo", "-n", PKILL, "-INT", "-x", "powermetrics"])

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    proc.wait()
    sys_stop.set()
    if pg_stop is not None:
        pg_stop.set()
    for p in guest_procs:
        p.terminate()
    for p in guest_procs:
        try:
            p.wait(timeout=5)
        except subprocess.TimeoutExpired:
            p.kill()
    for f in guest_files:
        f.close()
    # Give the root-owned process a moment to flush its output file on the
    # signal path.
    time.sleep(0.5)
    print(f"collect: raw samples in {args.output}, metadata in {args.meta}")


# --------------------------------------------------------------------------
# attribute
# --------------------------------------------------------------------------

def parse_stream(raw):
    """Split a powermetrics plist stream into sample dicts.

    Documents are NUL-separated. A partially written trailing document (e.g.
    the collector was killed mid-sample) is dropped and reported as truncation.
    """
    samples, truncated = [], False
    for doc in raw.split(b"\x00"):
        if not doc.strip():
            continue
        try:
            samples.append(plistlib.loads(doc))
        except Exception:
            truncated = True
    return samples, truncated


def utc_now_iso():
    """Return the current UTC time as an ISO-8601 string with millisecond precision."""
    return datetime.datetime.now(datetime.timezone.utc).strftime(
        "%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def iso_from_plist_dt(dt):
    """Format a powermetrics timestamp (a naive UTC datetime) as ISO-8601."""
    return dt.strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"


def load_guest_intervals(path, host_epoch_at_start=None):
    """Parse a guest_sampler.sh NDJSON file into per-second share intervals.

    Returns (starts, intervals, hz), where intervals[i] = (t0, t1, shares),
    shares maps each group to its share of guest busy time in [0, 1], and
    starts is the sorted t0 list for bisect lookup. The counters are
    cumulative, so intervals are deltas between consecutive lines;
    non-positive busy deltas are skipped.

    Clock alignment: guest clocks are host-synced under
    Virtualization.framework, so the recorded host_epoch_at_start (which
    includes exec startup latency) only triggers an offset correction when
    the discrepancy exceeds 2 s — genuine skew, not latency.
    """
    lines = []
    hz = 100
    with open(path) as f:
        for raw in f:
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError:
                # A torn write at kill time produces an unparsable line.
                continue
            if "hz" in obj and "ts" not in obj:
                hz = obj["hz"] or 100
            elif "ts" in obj and "busy" in obj:
                lines.append(obj)
    offset = 0.0
    if host_epoch_at_start is not None and lines:
        skew = lines[0]["ts"] - host_epoch_at_start
        if abs(skew) > 2.0:
            offset = skew
    starts, intervals = [], []
    for prev, cur in zip(lines, lines[1:]):
        dbusy = cur["busy"] - prev["busy"]
        if dbusy <= 0:
            continue
        shares = {
            group: min(1.0, max(0.0, (cur["groups"].get(group, 0)
                                      - prev["groups"].get(group, 0)) / dbusy))
            for group in cur.get("groups", {})
        }
        t0, t1 = prev["ts"] - offset, cur["ts"] - offset
        starts.append(t0)
        intervals.append((t0, t1, shares))
    return starts, intervals, hz


def guest_shares_at(guest, epoch_s):
    """Return the shares dict for the interval covering epoch_s, or None."""
    starts, intervals, _ = guest
    i = bisect.bisect_right(starts, epoch_s) - 1
    if i < 0:
        return None
    t0, t1, shares = intervals[i]
    # Allow one extra second of slack for the interval still in progress.
    if epoch_s > t1 + 1.0:
        return None
    return shares


def attribute_samples(samples, meta, guests=None):
    """Build power rows per target and integrate energy per role.

    Returns (rows_by_target, scaphandre_by_role, summary). Client rows are
    computed for the summary's sanity metric but excluded from outputs.

    guests: optional {role: (starts, intervals, hz)} from
    load_guest_intervals. When present, each container's power is further
    split into process-group targets (GUEST_TARGETS) by in-guest CPU share;
    the residual is the guest-other target. Group energies and an
    energy-weighted guest coverage metric are added to the summary.
    """
    guests = guests or {}
    pid_to_role = {info["pid"]: role for role, info in meta["roles"].items()}
    rows = {info["target"]: [] for info in meta["roles"].values() if info["target"]}
    rows["host-total"] = []
    for role in guests:
        for target in GUEST_TARGETS.get(role, {}).values():
            rows[target] = []
    scaphandre = {role: [] for role in meta["roles"]}
    energy_j = {role: 0.0 for role in meta["roles"]}
    group_energy = {role: {group: 0.0 for group in GUEST_TARGETS.get(role, {})}
                    for role in guests}
    cov_num = {role: 0.0 for role in guests}
    cov_den = {role: 0.0 for role in guests}
    host_cpu_energy_j = 0.0
    host_combined_energy_j = 0.0

    for sample in samples:
        ts = iso_from_plist_dt(sample["timestamp"])
        epoch_s = sample["timestamp"].replace(
            tzinfo=datetime.timezone.utc).timestamp()
        dt_s = sample["elapsed_ns"] / 1e9
        cpu_power_w = sample["processor"]["cpu_power"] / 1000.0
        combined_power_w = sample["processor"]["combined_power"] / 1000.0
        total_cputime = sample["all_tasks"]["cputime_ms_per_s"]
        host_cpu_energy_j += cpu_power_w * dt_s
        host_combined_energy_j += combined_power_w * dt_s
        rows["host-total"].append(
            {"timestamp": {"$date": ts}, "sensor": "powermetrics",
             "target": "host-total", "power": combined_power_w})

        cputime_by_pid = {t["pid"]: t.get("cputime_ms_per_s", 0.0)
                          for t in sample["tasks"]}
        for pid, role in pid_to_role.items():
            share = (cputime_by_pid.get(pid, 0.0) / total_cputime
                     if total_cputime > 0 else 0.0)
            power_w = cpu_power_w * share
            energy_j[role] += power_w * dt_s
            target = meta["roles"][role]["target"]
            if target:
                rows[target].append(
                    {"timestamp": {"$date": ts}, "sensor": "powermetrics",
                     "target": target, "power": power_w})

            # Stage 2: split the container's power across process groups by
            # in-guest CPU share.
            if role in guests:
                shares = guest_shares_at(guests[role], epoch_s)
                if shares is not None:
                    known = 0.0
                    for group, tgt in GUEST_TARGETS[role].items():
                        if group == "other":
                            continue
                        g_share = shares.get(group, 0.0)
                        known = min(1.0, known + g_share)
                        g_power = power_w * g_share
                        group_energy[role][group] += g_power * dt_s
                        rows[tgt].append(
                            {"timestamp": {"$date": ts},
                             "sensor": "powermetrics-guest",
                             "target": tgt, "power": g_power})
                    other_power = power_w * (1.0 - known)
                    group_energy[role]["other"] += other_power * dt_s
                    rows[GUEST_TARGETS[role]["other"]].append(
                        {"timestamp": {"$date": ts},
                         "sensor": "powermetrics-guest",
                         "target": GUEST_TARGETS[role]["other"],
                         "power": other_power})
                    cov_num[role] += power_w * known * dt_s
                    cov_den[role] += power_w * dt_s
            scaphandre[role].append({
                "host": {"timestamp": epoch_s,
                         "consumption": combined_power_w * 1e6},
                "consumers": [{
                    "container": {"id": meta["roles"][role]["container"]},
                    "consumption": power_w * 1e6,
                    "timestamp": epoch_s,
                }],
            })

    attributed_total = sum(energy_j.values())
    summary = {
        "measurement_mode": "powermetrics-macos",
        "samples": len(samples),
        "attributed_energy_j": {
            meta["roles"][role]["container"]: round(v, 3)
            for role, v in energy_j.items()},
        "host_cpu_energy_j": round(host_cpu_energy_j, 3),
        "host_combined_energy_j": round(host_combined_energy_j, 3),
        "attribution_coverage_of_cpu": round(
            attributed_total / host_cpu_energy_j, 4) if host_cpu_energy_j else 0.0,
    }
    if guests:
        summary["process_groups"] = {
            meta["roles"][role]["container"]: {
                group: round(v, 3) for group, v in groups.items()}
            for role, groups in group_energy.items()}
        summary["guest_coverage"] = {
            meta["roles"][role]["container"]:
                round(cov_num[role] / cov_den[role], 4) if cov_den[role] else 0.0
            for role in guests}
    return rows, scaphandre, summary


def load_system_rows(path):
    """Load system_samples.ndjson into mongodump rows and total energy.

    Returns (rows, energy_j) with the energy integrated trapezoidally. A
    missing or empty file yields ([], 0.0), so runs recorded without the
    system sampler attribute unchanged."""
    if not os.path.exists(path):
        return [], 0.0
    pts = []
    with open(path) as f:
        for raw in f:
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
                pts.append((float(obj["ts"]), float(obj["system_w"])))
            except (json.JSONDecodeError, KeyError, ValueError):
                continue
    pts.sort()
    rows = []
    for ts, watts in pts:
        iso = datetime.datetime.fromtimestamp(
            ts, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"
        rows.append({"timestamp": {"$date": iso}, "sensor": "smc",
                     "target": "system-total", "power": watts})
    energy_j = sum((p0 + p1) / 2 * (t1 - t0)
                   for (t0, p0), (t1, p1) in zip(pts, pts[1:]))
    return rows, energy_j


def cmd_attribute(args):
    """Post-process the raw stream into power series and the attribution summary."""
    with open(args.meta) as f:
        meta = json.load(f)
    with open(args.raw, "rb") as f:
        raw = f.read()

    samples, truncated = parse_stream(raw)
    if not samples:
        sys.exit("attribute: no complete samples found in raw stream")

    guests = {}
    raw_dir = os.path.dirname(os.path.abspath(args.raw))
    for role, info in (meta.get("guest_sampling") or {}).items():
        path = os.path.join(raw_dir, info["file"])
        if os.path.exists(path):
            guest = load_guest_intervals(path, info.get("host_epoch_at_start"))
            # Keep the guest only when it yielded at least one usable interval.
            if guest[1]:
                guests[role] = guest

    rows, scaphandre, summary = attribute_samples(samples, meta, guests)
    summary["truncated"] = truncated
    summary["interval_ms"] = meta.get("interval_ms")

    # The whole-system series (SMC telemetry) is a measured bound on what
    # per-container attribution cannot see: DRAM, SSD, display, losses.
    if meta.get("system_samples"):
        sys_path = os.path.join(raw_dir, meta["system_samples"])
        sys_rows, sys_energy_j = load_system_rows(sys_path)
        if sys_rows:
            rows["system-total"] = sys_rows
            summary["system_energy_j"] = round(sys_energy_j, 3)
            summary["unmeasured_rest_j"] = round(
                sys_energy_j - summary["host_combined_energy_j"], 3)

    os.makedirs(args.out_dir, exist_ok=True)
    for filename, targets in FILE_LAYOUT.items():
        entries = [row for t in targets for row in rows.get(t, [])]
        with open(os.path.join(args.out_dir, filename), "w") as f:
            json.dump(entries, f)

    for filename, role in SCAPHANDRE_LAYOUT.items():
        with open(os.path.join(args.out_dir, filename), "w") as f:
            json.dump(scaphandre.get(role, []), f)

    summary_path = os.path.join(args.out_dir, "attribution_summary.json")
    with open(summary_path, "w") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps(summary, indent=2))


# --------------------------------------------------------------------------
# queries
# --------------------------------------------------------------------------

def load_pg_snapshots(path):
    """Parse pg_query_samples.csv into per-query cumulative counter series.

    Returns {queryid: {"query": text, "series": [(ts, calls, cpu_s)]}} with
    each series sorted by snapshot time. cpu_s = exec_user_time +
    exec_system_time (seconds, from getrusage via pg_stat_kcache).
    """
    queries = {}
    with open(path, newline="") as f:
        for row in csv.reader(f):
            if len(row) < 7:
                continue
            try:
                ts = float(row[0])
                calls = int(row[2])
                cpu_s = float(row[4]) + float(row[5])
            except ValueError:
                continue
            try:
                queryid = int(row[1])
            except ValueError:
                # MySQL digests are hex strings, not integers.
                queryid = row[1]
            entry = queries.setdefault(
                queryid, {"query": row[6], "series": []})
            entry["series"].append((ts, calls, cpu_s))
    for entry in queries.values():
        entry["series"].sort()
    return queries


def counters_at(series, t):
    """Return (calls, cpu_s) of the latest snapshot at or before t.

    Returns zeros when no snapshot precedes t. The snapshot cadence bounds
    the boundary error at one snapshot interval."""
    i = bisect.bisect_right([s[0] for s in series], t) - 1
    if i < 0:
        return 0, 0.0
    return series[i][1], series[i][2]


def load_backends_jiffies(path):
    """Load the cumulative backends jiffies series from a guest samples file.

    Returns (times, jiffies, hz) with times sorted ascending."""
    times, jiffies, hz = [], [], 100
    with open(path) as f:
        for raw in f:
            raw = raw.strip()
            if not raw:
                continue
            try:
                obj = json.loads(raw)
            except json.JSONDecodeError:
                continue
            if "hz" in obj and "ts" not in obj:
                hz = obj["hz"] or 100
            elif "ts" in obj:
                times.append(obj["ts"])
                jiffies.append(obj.get("groups", {}).get("backends", 0))
    return times, jiffies, hz


def jiffies_at(times, jiffies, t):
    """Return the cumulative jiffies at or before t, or 0 before the first sample."""
    i = bisect.bisect_right(times, t) - 1
    return jiffies[i] if i >= 0 else 0


def integrate_power(rows, t0, t1):
    """Return the trapezoidal energy (J) of a mongodump power series within [t0, t1]."""
    pts = []
    for row in rows:
        ts = datetime.datetime.strptime(
            row["timestamp"]["$date"], "%Y-%m-%dT%H:%M:%S.%fZ"
        ).replace(tzinfo=datetime.timezone.utc).timestamp()
        if t0 <= ts <= t1:
            pts.append((ts, row["power"]))
    pts.sort()
    return sum((p0 + p1) / 2 * (ts1 - ts0)
               for (ts0, p0), (ts1, p1) in zip(pts, pts[1:]))


def run_windows(combined):
    """Return (experiment, run_number, start_s, end_s, successful_requests)
    for every run that has both bounds, sorted by start time."""
    windows = []
    experiments = (combined.get("benchmark_results") or {}).get("experiments", {})
    for exp_id, exp in experiments.items():
        for run in exp.get("runs", []):
            start, end = run.get("start_timestamp"), run.get("end_timestamp")
            if start is None or end is None:
                continue
            windows.append((exp_id, run.get("run_number"),
                            start / 1000.0, end / 1000.0,
                            run.get("successful_requests")))
    windows.sort(key=lambda w: w[2])
    return windows


def cmd_queries(args):
    """Join stats snapshots, run windows, and backends power into per-query energy."""
    with open(args.combined) as f:
        combined = json.load(f)
    queries = load_pg_snapshots(args.pg_samples)
    times, jiffies, hz = load_backends_jiffies(args.guest_samples)
    with open(os.path.join(args.power_dir, "mongodump-DB-server.json")) as f:
        backend_rows = [r for r in json.load(f) if r["target"] == "pg-backends"]

    out = []
    for exp_id, run_no, t0, t1, client_ok in run_windows(combined):
        backends_energy_j = integrate_power(backend_rows, t0, t1)
        backends_cpu_s = (jiffies_at(times, jiffies, t1)
                          - jiffies_at(times, jiffies, t0)) / hz
        j_per_cpu_s = (backends_energy_j / backends_cpu_s
                       if backends_cpu_s > 0 else 0.0)
        attributed_cpu_s = 0.0
        server_calls = 0
        for queryid, entry in queries.items():
            calls0, cpu0 = counters_at(entry["series"], t0)
            calls1, cpu1 = counters_at(entry["series"], t1)
            calls, cpu_s = calls1 - calls0, cpu1 - cpu0
            if calls <= 0 and cpu_s <= 0:
                continue
            attributed_cpu_s += cpu_s
            server_calls += max(0, calls)
            energy_j = cpu_s * j_per_cpu_s
            out.append({
                "experiment": exp_id, "run": run_no, "queryid": queryid,
                "query": entry["query"], "calls": calls,
                "cpu_s": round(cpu_s, 4), "energy_j": round(energy_j, 4),
                "mj_per_call": round(energy_j / calls * 1000, 4) if calls else None,
            })
        residual_cpu_s = max(0.0, backends_cpu_s - attributed_cpu_s)
        out.append({
            "experiment": exp_id, "run": run_no, "queryid": None,
            "query": "_unattributed", "calls": None,
            "cpu_s": round(residual_cpu_s, 4),
            "energy_j": round(residual_cpu_s * j_per_cpu_s, 4),
            "mj_per_call": None,
        })
        # Client/server consistency: the server must have executed at least
        # the client's successful requests (the 5 s snapshot cadence allows
        # roughly 5% boundary slack on 2-minute windows). A low ratio means
        # statements were lost or the wrong window/counters were matched.
        if client_ok:
            ratio = server_calls / client_ok
            out.append({
                "experiment": exp_id, "run": run_no, "queryid": None,
                "query": "_consistency", "calls": server_calls,
                "cpu_s": None, "energy_j": None, "mj_per_call": None,
                "client_successful_requests": client_ok,
                "server_to_client_ratio": round(ratio, 4),
                "ok": ratio >= 0.9,
            })

    with open(args.out, "w") as f:
        json.dump(out, f, indent=2)
    print(f"queries: {len(out)} rows written to {args.out}")


# --------------------------------------------------------------------------

def main():
    """Parse the command line and dispatch to the selected subcommand."""
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)

    c = sub.add_parser("collect", help="record powermetrics samples")
    c.add_argument("--db-container", help="name of the database container")
    c.add_argument("--api-container", help="name of the API container")
    c.add_argument("--client-container", help="name of the benchmark client container")
    c.add_argument("--interval-ms", type=int, default=1000)
    c.add_argument("--duration-s", type=int,
                   help="stop after this many seconds; omit to run until SIGTERM")
    c.add_argument("--output", default="raw_powermetrics.plist")
    c.add_argument("--meta", default="power_meta.json")
    c.add_argument("--sample-guests", action="store_true",
                   help="run guest_sampler.sh inside db/api containers and "
                        "snapshot pg_stat_statements+pg_stat_kcache")
    c.add_argument("--container-cli", default="container",
                   help="Apple container CLI (for guest sampling)")
    c.add_argument("--db-engine", default="postgres",
                   help="per-query stats provider: postgres | mysql | none")
    c.add_argument("--pg-user", default="admin")
    c.add_argument("--pg-db", default="tpchdb")
    c.add_argument("--pg-password", default="password",
                   help="used by the mysql stats provider")
    c.add_argument("--pg-snapshot-interval-s", type=int, default=5)
    c.set_defaults(func=cmd_collect)

    a = sub.add_parser("attribute", help="produce per-container power series")
    a.add_argument("--raw", required=True, help="raw plist stream from collect")
    a.add_argument("--meta", required=True, help="meta.json from collect")
    a.add_argument("--out-dir", required=True)
    a.set_defaults(func=cmd_attribute)

    q = sub.add_parser("queries", help="per-query energy per run window")
    q.add_argument("--pg-samples", required=True,
                   help="pg_query_samples.csv from collect --sample-guests")
    q.add_argument("--combined", required=True,
                   help="combined_results.json (run windows)")
    q.add_argument("--guest-samples", required=True,
                   help="guest_samples_db.ndjson (backends CPU time)")
    q.add_argument("--power-dir", required=True,
                   help="attribute output dir (mongodump-DB-server.json)")
    q.add_argument("--out", required=True)
    q.set_defaults(func=cmd_queries)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
