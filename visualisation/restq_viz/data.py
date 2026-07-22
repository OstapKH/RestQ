"""Data layer for the RestQ visualizer.

Loads one experiment folder (as produced by the Grid5000 or macOS benchmark
pipelines) into tidy pandas DataFrames and provides the time-boundary and
derived-metric computations the views need.

This module is pure data — no UI imports. Its semantics are ported from the
legacy visualizer_containers.py (removed; see git history), which was the
reference implementation for: experiment chronology, warmup exclusion,
strict/lenient time filtering, and the seconds-vs-milliseconds timestamp
normalization of Scaphandre data.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, field

import pandas as pd

REQUIRED_FILES = ["combined_results.json"]
OPTIONAL_FILES = ["experiment_info.json",
                  "mongodump-DB-server.json", "mongodump-API-server.json",
                  "per_query_energy.json"]

#: Sentinel experiment selections, mirroring the legacy combobox entries.
ALL_EXPERIMENTS = "All Experiments"
ALL_WITHOUT_WARMUP = "All Experiments without warmup"


class FolderError(Exception):
    """A folder is not a loadable experiment folder. str() names the problem."""


def _normalize_ts_seconds(ts):
    """Scaphandre timestamps are seconds; treat suspiciously large values as ms
    (legacy rule: > 1e10 means milliseconds)."""
    return ts / 1000 if ts > 1e10 else ts


@dataclass
class ExperimentFolder:
    """One loaded experiment folder."""

    path: str
    info: dict = field(default_factory=dict)
    runs: pd.DataFrame = None          # one row per (experiment, run)
    energy: pd.DataFrame = None        # tidy: source, series, timestamp_ms, watts
    overlays: pd.DataFrame = None      # tidy: target, timestamp_ms, watts
    latencies: pd.DataFrame = None     # tidy: experiment, run, timestamp_ms, latency_ms
    queries: pd.DataFrame = None       # per (experiment, run, query): cpu_s, energy_j
    validation: dict = field(default_factory=dict)
    client_validation: dict = field(default_factory=dict)
    preflight_validation: list = field(default_factory=list)
    parameter_sets: dict = field(default_factory=dict)

    # ------------------------------------------------------------------
    # Loading
    # ------------------------------------------------------------------

    @classmethod
    def load(cls, path):
        path = os.path.expanduser(path)
        if not os.path.isdir(path):
            raise FolderError(f"not a directory: {path}")
        combined_path = os.path.join(path, "combined_results.json")
        if not os.path.exists(combined_path):
            raise FolderError("missing combined_results.json")
        try:
            with open(combined_path) as f:
                combined = json.load(f)
        except json.JSONDecodeError as e:
            raise FolderError(f"combined_results.json is not valid JSON: {e}")

        missing = [k for k in ("api_server_energy", "db_server_energy",
                               "benchmark_results")
                   if k not in combined or combined[k] is None]
        if missing:
            raise FolderError(
                f"combined_results.json missing keys: {', '.join(missing)}")

        self = cls(path=path)
        self.info = self._load_info(path)
        self.validation = (combined.get("validation")
                           or combined["benchmark_results"].get("validation")
                           or {})
        self.client_validation = (combined["benchmark_results"].get(
            "client_validation") or {})
        self.preflight_validation = (combined["benchmark_results"].get(
            "preflight_validation") or [])
        self.parameter_sets = (combined["benchmark_results"].get("parameter_sets")
                               or {})
        self.runs = self._build_runs(combined)
        self.energy = self._build_energy(combined)
        self.overlays = self._build_overlays(path)
        self.latencies = self._build_latencies(combined)
        self.queries = self._build_queries(path)
        return self

    @staticmethod
    def _load_info(path):
        info_path = os.path.join(path, "experiment_info.json")
        if not os.path.exists(info_path):
            return {}
        try:
            with open(info_path) as f:
                info = json.load(f)
            return info if isinstance(info, dict) else {}
        except json.JSONDecodeError:
            return {}

    def _build_runs(self, combined):
        rows = []
        experiments = combined["benchmark_results"].get("experiments", {})
        for exp_id, exp in experiments.items():
            # explicit warmup flag (newer clients) wins over the name heuristic
            is_warmup = bool(exp.get("warmup", "warmup" in exp_id.lower()))
            for run in exp.get("runs", []):
                start = run.get("start_timestamp")
                end = run.get("end_timestamp")
                # legacy fallbacks for missing boundaries
                if end is None and start is not None and "elapsed_time_ms" in run:
                    end = start + run["elapsed_time_ms"]
                if start is None and "timestamp" in run:
                    start = (run["timestamp"] - run["elapsed_time_ms"]
                             if "elapsed_time_ms" in run else run["timestamp"])
                if end is None and "timestamp" in run:
                    end = run["timestamp"]
                pct = (run.get("latency_distribution") or {}).get("percentiles", {})
                first_latency = next(iter(run.get("latencies") or []), {})
                query_id = first_latency.get("query_id")
                if query_id is None and exp_id.startswith("Q"):
                    query_id = exp_id.split("_", 1)[0]
                responses = run.get("responses") or {}
                energy = run.get("run_energy") or {}
                db_energy = energy.get("db") or {}
                api_energy = energy.get("api") or {}
                combined_energy = energy.get("combined") or {}
                rows.append({
                    "experiment": exp_id,
                    "query_id": query_id,
                    "run": run.get("run_number"),
                    "start_ms": start,
                    "end_ms": end,
                    "throughput": run.get("throughput"),
                    "goodput": run.get("goodput"),
                    "total_requests": run.get("total_requests"),
                    "successful_requests": run.get("successful_requests"),
                    "errors": sum(responses.get(key, 0) or 0 for key in
                                  ("status_4xx", "status_5xx", "status_other",
                                   "transport_errors")),
                    "latency_p50_ns": pct.get("p50"),
                    "latency_p90_ns": pct.get("p90"),
                    "latency_p95_ns": pct.get("p95"),
                    "latency_p99_ns": pct.get("p99"),
                    "is_warmup": is_warmup,
                    "db_energy_j": db_energy.get("energy_j"),
                    "api_energy_j": api_energy.get("energy_j"),
                    "combined_energy_j": combined_energy.get("energy_j"),
                    "combined_mean_power_w": combined_energy.get("mean_power_w"),
                    "joules_per_success": combined_energy.get(
                        "joules_per_successful_request"),
                })
        df = pd.DataFrame(rows)
        if not df.empty:
            df.sort_values(["start_ms"], inplace=True, na_position="last")
            df.reset_index(drop=True, inplace=True)
        return df

    def _build_energy(self, combined):
        rows = []
        for source, key in (("db", "db_server_energy"),
                            ("api", "api_server_energy")):
            for entry in combined.get(key, []):
                host = entry.get("host") or {}
                if "timestamp" in host and "consumption" in host:
                    ts = _normalize_ts_seconds(host["timestamp"])
                    rows.append({"source": source, "series": "host",
                                 "timestamp_ms": ts * 1000,
                                 "watts": host["consumption"] / 1e6})
                for consumer in entry.get("consumers", []):
                    ts = consumer.get("timestamp")
                    if ts is None and "timestamp" in host:
                        ts = host["timestamp"]
                    if ts is None or consumer.get("consumption") is None:
                        continue
                    ts = _normalize_ts_seconds(ts)
                    rows.append({"source": source, "series": "container",
                                 "timestamp_ms": ts * 1000,
                                 "watts": consumer["consumption"] / 1e6})
        df = pd.DataFrame(rows)
        if not df.empty:
            df.sort_values("timestamp_ms", inplace=True)
            df.reset_index(drop=True, inplace=True)
        return df

    def _build_overlays(self, path):
        rows = []
        for filename in ("mongodump-DB-server.json", "mongodump-API-server.json"):
            fpath = os.path.join(path, filename)
            if not os.path.exists(fpath):
                continue
            try:
                with open(fpath) as f:
                    data = json.load(f)
            except json.JSONDecodeError:
                continue
            for e in data:
                try:
                    ts = pd.Timestamp(e["timestamp"]["$date"])
                    rows.append({"target": e["target"],
                                 "timestamp_ms": ts.value // 1_000_000,
                                 "watts": e["power"]})
                except (KeyError, TypeError, ValueError):
                    continue
        df = pd.DataFrame(rows)
        if not df.empty:
            # host-total appears identically in both mongodump files (one
            # machine) — the doubled rows are the same data, drop them.
            df.drop_duplicates(["target", "timestamp_ms", "watts"], inplace=True)
            df.sort_values("timestamp_ms", inplace=True)
            df.reset_index(drop=True, inplace=True)
        return df

    @staticmethod
    def _build_latencies(combined):
        """Per-request latencies (legacy 'Latency' data source). Runs without
        a 'latencies' key are skipped, so Grid5000 folders load unchanged."""
        rows = []
        experiments = combined["benchmark_results"].get("experiments", {})
        for exp_id, exp in experiments.items():
            for run in exp.get("runs", []):
                for entry in run.get("latencies") or []:
                    ts = entry.get("timestamp")
                    ns = entry.get("latency_ns")
                    if ts is None or ns is None:
                        continue
                    rows.append({"experiment": exp_id,
                                 "run": run.get("run_number"),
                                 "timestamp_ms": ts,
                                 "latency_ms": ns / 1e6,
                                 "query_id": entry.get("query_id"),
                                 "parameter_set_id": entry.get("parameter_set_id"),
                                 "status_code": entry.get("status_code"),
                                 "is_warmup": bool(exp.get(
                                     "warmup", "warmup" in exp_id.lower()))})
        df = pd.DataFrame(
            rows, columns=["experiment", "run", "timestamp_ms", "latency_ms",
                           "query_id", "parameter_set_id", "status_code",
                           "is_warmup"])
        if not df.empty:
            df.sort_values("timestamp_ms", inplace=True)
            df.reset_index(drop=True, inplace=True)
        return df

    # ------------------------------------------------------------------
    # TPC-H study summaries
    # ------------------------------------------------------------------

    def query_summary(self):
        """One row per measured query, including run-level energy only."""
        columns = [
            "query_id", "experiment", "runs", "p50_ms", "p95_ms", "p99_ms",
            "throughput", "goodput", "errors", "db_energy_j", "api_energy_j",
            "combined_energy_j", "mean_power_w", "joules_per_success",
            "run_spread_p95_ms",
        ]
        if self.runs is None or self.runs.empty or "query_id" not in self.runs:
            return pd.DataFrame(columns=columns)
        measured = self.runs[(~self.runs["is_warmup"])
                             & self.runs["query_id"].notna()]
        rows = []
        for query_id, group in measured.groupby("query_id", sort=True):
            p50 = group["latency_p50_ns"] / 1e6
            p95 = group["latency_p95_ns"] / 1e6
            p99 = group["latency_p99_ns"] / 1e6
            total_energy = group["combined_energy_j"].sum(min_count=1)
            successes = group["successful_requests"].sum(min_count=1)
            rows.append({
                "query_id": query_id,
                "experiment": group["experiment"].iloc[0],
                "runs": len(group),
                "p50_ms": p50.mean(),
                "p95_ms": p95.mean(),
                "p99_ms": p99.mean(),
                "throughput": group["throughput"].mean(),
                "goodput": group["goodput"].mean(),
                "errors": group["errors"].sum(),
                "db_energy_j": group["db_energy_j"].sum(min_count=1),
                "api_energy_j": group["api_energy_j"].sum(min_count=1),
                "combined_energy_j": total_energy,
                "mean_power_w": group["combined_mean_power_w"].mean(),
                "joules_per_success": (total_energy / successes
                                        if pd.notna(total_energy)
                                        and pd.notna(successes) and successes else None),
                "run_spread_p95_ms": p95.max() - p95.min(),
            })
        return pd.DataFrame(rows, columns=columns)

    def parameter_summary(self, query_id):
        """Latency/error summary for P1–P5; energy is not attributable here."""
        columns = ["query_id", "parameter_set_id", "definition", "count",
                   "errors", "p50_ms", "p95_ms", "p99_ms"]
        if self.latencies is None or self.latencies.empty:
            return pd.DataFrame(columns=columns)
        rows = self.latencies[(self.latencies["query_id"] == query_id)
                              & (~self.latencies["is_warmup"])]
        output = []
        for parameter_id, group in rows.groupby("parameter_set_id", sort=True):
            definition = self.parameter_sets.get(parameter_id) or {}
            status = pd.to_numeric(group["status_code"], errors="coerce")
            errors = ((status < 200) | (status >= 300) | status.isna()).sum()
            output.append({
                "query_id": query_id,
                "parameter_set_id": parameter_id,
                "definition": definition.get("path") or definition.get("url") or "",
                "count": len(group),
                "errors": int(errors),
                "p50_ms": group["latency_ms"].quantile(0.50),
                "p95_ms": group["latency_ms"].quantile(0.95),
                "p99_ms": group["latency_ms"].quantile(0.99),
            })
        return pd.DataFrame(output, columns=columns)

    _QUERY_COLUMNS = ["experiment", "run", "queryid", "query", "calls",
                      "cpu_s", "energy_j", "mj_per_call"]

    @classmethod
    def _build_queries(cls, path):
        """Per-query energy rows from per_query_energy.json (macOS pipeline
        with guest sampling). Optional — empty frame when absent/invalid."""
        fpath = os.path.join(path, "per_query_energy.json")
        empty = pd.DataFrame(columns=cls._QUERY_COLUMNS)
        if not os.path.exists(fpath):
            return empty
        try:
            with open(fpath) as f:
                rows = json.load(f)
        except json.JSONDecodeError:
            return empty
        if not isinstance(rows, list) or not rows:
            return empty
        df = pd.DataFrame(rows)
        for col in cls._QUERY_COLUMNS:
            if col not in df.columns:
                df[col] = None
        return df[cls._QUERY_COLUMNS]

    # ------------------------------------------------------------------
    # Identity / display
    # ------------------------------------------------------------------

    @property
    def label(self):
        """Short display label for folder chips."""
        base = os.path.basename(self.path.rstrip("/"))
        mode = self.measurement_mode or "scaphandre"
        return f"{base} [{mode}]"

    @property
    def measurement_mode(self):
        return self.info.get("measurement_mode", "")

    @property
    def source_names(self):
        """(embedded_series_name, overlay_series_name) — legacy naming rule."""
        if "powermetrics" in self.measurement_mode:
            return "powermetrics", "powermetrics (overlay)"
        return "Scaphandre", "PowerAPI"

    @property
    def experiment_ids(self):
        """Experiment IDs sorted by first-run start time (legacy ordering)."""
        if self.runs is None or self.runs.empty:
            return []
        firsts = self.runs.groupby("experiment")["start_ms"].min()
        return list(firsts.sort_values(na_position="last").index)

    # ------------------------------------------------------------------
    # Time boundaries (legacy semantics)
    # ------------------------------------------------------------------

    def experiment_boundaries(self, experiment_id):
        """(start_ms, end_ms) of one experiment: first run start, last run end."""
        runs = self.runs[self.runs["experiment"] == experiment_id]
        if runs.empty:
            return None, None
        start = runs["start_ms"].iloc[0]
        end = runs["end_ms"].iloc[-1]
        if pd.isna(start) or pd.isna(end):
            return self._energy_range()
        return int(start), int(end)

    def all_boundaries(self):
        """Broadest (start_ms, end_ms) over all experiments."""
        if self.runs.empty:
            return self._energy_range()
        start = self.runs["start_ms"].min()
        end = self.runs["end_ms"].max()
        if pd.isna(start) or pd.isna(end):
            return self._energy_range()
        return int(start), int(end)

    def without_warmup_boundaries(self):
        """Range covering non-warmup experiments, starting no earlier than the
        end of the last warmup experiment (legacy rule)."""
        non_warmup = self.runs[~self.runs["is_warmup"]]
        if non_warmup.empty:
            return None, None
        warmup = self.runs[self.runs["is_warmup"]]
        if warmup.empty:
            return self.all_boundaries()
        latest_warmup_end = warmup["end_ms"].max()
        start = non_warmup["start_ms"].min()
        if not pd.isna(latest_warmup_end):
            start = max(latest_warmup_end, start)
        end = non_warmup["end_ms"].max()
        if pd.isna(start) or pd.isna(end):
            return None, None
        return int(start), int(end)

    def boundaries_for(self, selection):
        """Boundaries for a selector value: an experiment ID or a sentinel."""
        if selection == ALL_EXPERIMENTS:
            return self.all_boundaries()
        if selection == ALL_WITHOUT_WARMUP:
            return self.without_warmup_boundaries()
        return self.experiment_boundaries(selection)

    def _energy_range(self):
        """Fallback range from energy data (legacy behavior)."""
        if self.energy is None or self.energy.empty:
            return None, None
        return (int(self.energy["timestamp_ms"].min()),
                int(self.energy["timestamp_ms"].max()))

    # ------------------------------------------------------------------
    # Filtering
    # ------------------------------------------------------------------

    def energy_in(self, start_ms=None, end_ms=None, strict=True):
        """Energy rows within [start_ms, end_ms].

        Legacy semantics: with no boundaries, strict yields nothing and
        lenient yields everything; with boundaries but no matching rows,
        lenient falls back to all rows.
        """
        df = self.energy
        if start_ms is None or end_ms is None:
            return df.iloc[0:0] if strict else df
        out = df[(df["timestamp_ms"] >= start_ms) & (df["timestamp_ms"] <= end_ms)]
        if out.empty and not strict:
            return df
        return out

    def overlays_in(self, start_ms=None, end_ms=None, strict=True):
        df = self.overlays
        if df is None or df.empty:
            return df
        if start_ms is None or end_ms is None:
            return df.iloc[0:0] if strict else df
        out = df[(df["timestamp_ms"] >= start_ms) & (df["timestamp_ms"] <= end_ms)]
        if out.empty and not strict:
            return df
        return out

    def latencies_in(self, start_ms=None, end_ms=None, strict=True):
        df = self.latencies
        if df is None or df.empty:
            return df
        if start_ms is None or end_ms is None:
            return df.iloc[0:0] if strict else df
        out = df[(df["timestamp_ms"] >= start_ms) & (df["timestamp_ms"] <= end_ms)]
        if out.empty and not strict:
            return df
        return out

    # ------------------------------------------------------------------
    # Redundancy / chronology
    # ------------------------------------------------------------------

    def duplicate_host_sources(self):
        """Sources whose host power series duplicates another source's.

        On macOS both containers run on one machine, so the api and db host
        series are identical; report 'api' as redundant (db is canonical).
        Equality of values — not measurement_mode — so genuine two-machine
        Grid5000 folders never collapse.
        """
        if self.energy is None or self.energy.empty:
            return set()
        host = self.energy[self.energy["series"] == "host"]
        cols = ["timestamp_ms", "watts"]
        db = host[host["source"] == "db"][cols].reset_index(drop=True)
        api = host[host["source"] == "api"][cols].reset_index(drop=True)
        if not db.empty and db.equals(api):
            return {"api"}
        return set()

    def pauses(self):
        """Gaps between consecutive experiments, in chronological order:
        list of (prev_experiment, next_experiment, gap_start_ms, gap_end_ms)."""
        if self.runs is None or self.runs.empty:
            return []
        valid = self.runs.dropna(subset=["start_ms", "end_ms"])
        if valid.empty:
            return []
        bounds = (valid.groupby("experiment")
                  .agg(start=("start_ms", "min"), end=("end_ms", "max"))
                  .sort_values("start"))
        out = []
        for (prev_id, prev), (next_id, nxt) in zip(bounds.iterrows(),
                                                   bounds.iloc[1:].iterrows()):
            if nxt["start"] > prev["end"]:
                out.append((prev_id, next_id,
                            int(prev["end"]), int(nxt["start"])))
        return out

    # ------------------------------------------------------------------
    # Derived metrics (Correlation tab)
    # ------------------------------------------------------------------

    def run_metrics(self):
        """Per (experiment, run, source): energy J over the run window, mean
        container power, energy per request, and the run's performance."""
        rows = []
        for _, run in self.runs.iterrows():
            if pd.isna(run["start_ms"]) or pd.isna(run["end_ms"]):
                continue
            window = self.energy[
                (self.energy["timestamp_ms"] >= run["start_ms"]) &
                (self.energy["timestamp_ms"] <= run["end_ms"]) &
                (self.energy["series"] == "container")]
            duration_s = (run["end_ms"] - run["start_ms"]) / 1000.0
            for source, grp in window.groupby("source"):
                if grp.empty or len(grp) < 2:
                    continue
                # trapezoidal integration of power over time
                t = grp["timestamp_ms"].to_numpy() / 1000.0
                p = grp["watts"].to_numpy()
                energy_j = float(((p[1:] + p[:-1]) / 2 * (t[1:] - t[:-1])).sum())
                requests = run["total_requests"]
                rows.append({
                    "experiment": run["experiment"],
                    "run": run["run"],
                    "source": source,
                    "energy_j": energy_j,
                    "mean_power_w": energy_j / duration_s if duration_s else None,
                    "j_per_request": (energy_j / requests
                                      if requests else None),
                    "throughput": run["throughput"],
                    "latency_p99_ns": run["latency_p99_ns"],
                    "is_warmup": run["is_warmup"],
                })
        return pd.DataFrame(rows)
