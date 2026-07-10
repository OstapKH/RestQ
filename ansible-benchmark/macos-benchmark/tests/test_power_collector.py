"""Unit tests for power_collector.py (stdlib unittest; no third-party deps).

Run from ansible-benchmark/macos-benchmark:  python3 -m unittest discover tests
"""

import datetime
import json
import os
import sys
import tempfile
import unittest
from argparse import Namespace

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from power_collector import (attribute_samples, cmd_attribute, cmd_queries,
                             load_guest_intervals, guest_shares_at,
                             parse_stream, parse_system_power,
                             FILE_LAYOUT, SCAPHANDRE_LAYOUT)

FIXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
STREAM = os.path.join(FIXTURES, "powermetrics_stream.plist")
META = os.path.join(FIXTURES, "power_meta.json")


def load_fixture():
    with open(STREAM, "rb") as f:
        raw = f.read()
    with open(META) as f:
        meta = json.load(f)
    return raw, meta


class TestParseStream(unittest.TestCase):
    def test_parses_all_documents(self):
        raw, _ = load_fixture()
        samples, truncated = parse_stream(raw)
        self.assertEqual(len(samples), 4)
        self.assertFalse(truncated)
        for s in samples:
            self.assertIn("processor", s)
            self.assertIn("tasks", s)
            self.assertIn("all_tasks", s)

    def test_truncated_stream_flagged_and_partial_doc_dropped(self):
        raw, _ = load_fixture()
        samples, truncated = parse_stream(raw[:-1000])
        self.assertEqual(len(samples), 3)
        self.assertTrue(truncated)


class TestAttributionMath(unittest.TestCase):
    def make_sample(self, cpu_mw, combined_mw, total_ms, per_pid_ms):
        return {
            "timestamp": datetime.datetime(2026, 7, 5, 12, 0, 0),
            "elapsed_ns": 1_000_000_000,
            "processor": {"cpu_power": cpu_mw, "combined_power": combined_mw},
            "all_tasks": {"cputime_ms_per_s": total_ms},
            "tasks": [{"pid": pid, "cputime_ms_per_s": ms}
                      for pid, ms in per_pid_ms.items()],
        }

    def test_proportional_split_and_energy(self):
        meta = {"roles": {
            "db": {"container": "db", "pid": 1, "target": "postgres"},
            "api": {"container": "api", "pid": 2, "target": "spring-api"},
            "client": {"container": "cl", "pid": 3, "target": None},
        }}
        # CPU power is 2000 mW; db holds 50% of system CPU time, api 25%,
        # and the client 10%.
        sample = self.make_sample(2000.0, 2500.0, 1000.0,
                                  {1: 500.0, 2: 250.0, 3: 100.0})
        rows, scaphandre, summary = attribute_samples([sample, sample], meta)

        self.assertAlmostEqual(rows["postgres"][0]["power"], 1.0)
        self.assertAlmostEqual(rows["spring-api"][0]["power"], 0.5)
        self.assertAlmostEqual(rows["host-total"][0]["power"], 2.5)

        # Scaphandre-format entries report consumption in microwatts.
        db_entry = scaphandre["db"][0]
        self.assertAlmostEqual(db_entry["host"]["consumption"], 2.5e6)
        self.assertAlmostEqual(db_entry["consumers"][0]["consumption"], 1.0e6)
        self.assertEqual(db_entry["consumers"][0]["container"]["id"], "db")
        self.assertAlmostEqual(db_entry["host"]["timestamp"],
                               datetime.datetime(2026, 7, 5, 12, 0, 0,
                                   tzinfo=datetime.timezone.utc).timestamp())
        # Two samples of one second each integrate to twice the power.
        self.assertAlmostEqual(summary["attributed_energy_j"]["db"], 2.0)
        self.assertAlmostEqual(summary["attributed_energy_j"]["api"], 1.0)
        self.assertAlmostEqual(summary["attributed_energy_j"]["cl"], 0.4)
        self.assertAlmostEqual(summary["host_cpu_energy_j"], 4.0)
        self.assertAlmostEqual(summary["host_combined_energy_j"], 5.0)
        self.assertAlmostEqual(summary["attribution_coverage_of_cpu"], 0.85)

    def test_absent_pid_gets_zero_power(self):
        meta = {"roles": {
            "db": {"container": "db", "pid": 99, "target": "postgres"}}}
        sample = self.make_sample(1000.0, 1100.0, 500.0, {1: 500.0})
        rows, _, summary = attribute_samples([sample], meta)
        self.assertEqual(rows["postgres"][0]["power"], 0.0)
        self.assertEqual(summary["attributed_energy_j"]["db"], 0.0)


class TestOutputFiles(unittest.TestCase):
    def run_attribute(self):
        tmp = tempfile.mkdtemp()
        cmd_attribute(Namespace(raw=STREAM, meta=META, out_dir=tmp))
        return tmp

    def test_visualizer_schema_and_layout(self):
        tmp = self.run_attribute()
        expected_targets = {"mongodump-DB-server.json": {"postgres", "host-total"},
                            "mongodump-API-server.json": {"spring-api", "host-total"}}
        self.assertEqual(set(FILE_LAYOUT), set(expected_targets))
        for filename, targets in expected_targets.items():
            with open(os.path.join(tmp, filename)) as f:
                entries = json.load(f)
            self.assertEqual({e["target"] for e in entries}, targets)
            for e in entries:
                # These are the exact expressions the visualizer evaluates
                # at load time.
                ts = e["timestamp"]["$date"]
                datetime.datetime.fromisoformat(ts.replace("Z", "+00:00"))
                self.assertIsInstance(e["power"], float)
                self.assertGreaterEqual(e["power"], 0.0)

    def test_client_never_emitted(self):
        tmp = self.run_attribute()
        for filename in FILE_LAYOUT:
            with open(os.path.join(tmp, filename)) as f:
                targets = {e["target"] for e in json.load(f)}
            self.assertNotIn("restq-client", targets)
            self.assertNotIn("client", targets)

    def test_scaphandre_files_emitted_for_db_and_api_only(self):
        tmp = self.run_attribute()
        self.assertEqual(set(SCAPHANDRE_LAYOUT),
                         {"fixed_experiments_summary_dbserver.json",
                          "fixed_experiments_summary_apiserver.json"})
        for filename, role in SCAPHANDRE_LAYOUT.items():
            with open(os.path.join(tmp, filename)) as f:
                entries = json.load(f)
            # The fixture stream contains four samples, one entry each.
            self.assertEqual(len(entries), 4)
            for e in entries:
                # These are the exact fields _process_energy_data reads.
                self.assertIn("timestamp", e["host"])
                self.assertGreaterEqual(e["host"]["consumption"], 0)
                c = e["consumers"][0]
                self.assertIn("id", c["container"])
                self.assertGreaterEqual(c["consumption"], 0)
        # The client role must not be written anywhere.
        written = set(os.listdir(tmp))
        self.assertEqual(written, set(FILE_LAYOUT) | set(SCAPHANDRE_LAYOUT)
                         | {"attribution_summary.json"})

    def test_summary_contains_sanity_metric(self):
        tmp = self.run_attribute()
        with open(os.path.join(tmp, "attribution_summary.json")) as f:
            summary = json.load(f)
        self.assertEqual(summary["measurement_mode"], "powermetrics-macos")
        self.assertEqual(summary["samples"], 4)
        self.assertFalse(summary["truncated"])
        self.assertIn("restq-client", summary["attributed_energy_j"])
        self.assertGreater(summary["host_combined_energy_j"],
                           summary["host_cpu_energy_j"] * 0.9)
        self.assertLessEqual(summary["attribution_coverage_of_cpu"], 1.0)
        # The fixture recorded heavy in-container load: the db VM held about
        # 1.2 of roughly 2.5 busy cores.
        db_j = summary["attributed_energy_j"]["restq-db"]
        host_j = summary["host_cpu_energy_j"]
        self.assertGreater(db_j / host_j, 0.3)


def write_guest_file(path, lines, hz=100):
    with open(path, "w") as f:
        f.write(json.dumps({"hz": hz}) + "\n")
        for ts, busy, groups in lines:
            f.write(json.dumps({"ts": ts, "busy": busy, "groups": groups})
                    + "\n")


class TestGuestIntervals(unittest.TestCase):
    def test_shares_from_cumulative_counters(self):
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "g.ndjson")
        write_guest_file(path, [
            (1000.0, 0,   {"backends": 0,   "background": 0}),
            (1001.0, 100, {"backends": 60,  "background": 10}),
            (1002.0, 200, {"backends": 120, "background": 20}),
        ])
        starts, intervals, hz = load_guest_intervals(path)
        self.assertEqual(hz, 100)
        self.assertEqual(len(intervals), 2)
        _, _, shares = intervals[0]
        self.assertAlmostEqual(shares["backends"], 0.6)
        self.assertAlmostEqual(shares["background"], 0.1)

    def test_torn_line_and_nonpositive_busy_skipped(self):
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "g.ndjson")
        write_guest_file(path, [
            (1000.0, 100, {"backends": 10}),
            # This line has no busy delta, so its interval is skipped.
            (1001.0, 100, {"backends": 10}),
            (1002.0, 200, {"backends": 60}),
        ])
        with open(path, "a") as f:
            # Simulate a torn write at kill time.
            f.write('{"ts": 1003.0, "busy": 3')
        starts, intervals, _ = load_guest_intervals(path)
        self.assertEqual(len(intervals), 1)
        self.assertAlmostEqual(intervals[0][2]["backends"], 0.5)

    def test_clock_offset_applied_only_beyond_threshold(self):
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "g.ndjson")
        write_guest_file(path, [(1000.0, 0, {"backends": 0}),
                                (1001.0, 100, {"backends": 50})])
        # A 0.8 s discrepancy is exec startup latency and causes no shift.
        starts, _, _ = load_guest_intervals(path, host_epoch_at_start=999.2)
        self.assertAlmostEqual(starts[0], 1000.0)
        # A 10 s discrepancy is genuine skew and shifts the timestamps onto
        # the host clock.
        starts, _, _ = load_guest_intervals(path, host_epoch_at_start=990.0)
        self.assertAlmostEqual(starts[0], 990.0)

    def test_lookup_outside_intervals_is_none(self):
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "g.ndjson")
        write_guest_file(path, [(1000.0, 0, {"backends": 0}),
                                (1001.0, 100, {"backends": 50})])
        guest = load_guest_intervals(path)
        self.assertIsNone(guest_shares_at(guest, 999.0))
        self.assertIsNotNone(guest_shares_at(guest, 1000.5))
        self.assertIsNone(guest_shares_at(guest, 1005.0))


class TestGuestAttribution(TestAttributionMath):
    def guest_for(self, tmp, lines):
        path = os.path.join(tmp, "guest_samples_db.ndjson")
        write_guest_file(path, lines)
        return load_guest_intervals(path)

    def epoch_sample(self, epoch, cpu_mw, total_ms, per_pid_ms):
        s = self.make_sample(cpu_mw, cpu_mw, total_ms, per_pid_ms)
        s["timestamp"] = datetime.datetime.fromtimestamp(
            epoch, datetime.timezone.utc).replace(tzinfo=None)
        return s

    def test_stage2_splits_container_power(self):
        tmp = tempfile.mkdtemp()
        meta = {"roles": {
            "db": {"container": "db", "pid": 1, "target": "postgres"}}}
        guests = {"db": self.guest_for(tmp, [
            (1000.0, 0,   {"backends": 0,  "background": 0}),
            (1001.0, 100, {"backends": 60, "background": 10}),
        ])}
        # The container holds 50% of 2000 mW, giving 1.0 W; the sample falls
        # inside the guest interval.
        sample = self.epoch_sample(1000.5, 2000.0, 1000.0, {1: 500.0})
        rows, _, summary = attribute_samples([sample], meta, guests)

        self.assertAlmostEqual(rows["postgres"][0]["power"], 1.0)
        self.assertAlmostEqual(rows["pg-backends"][0]["power"], 0.6)
        self.assertAlmostEqual(rows["pg-background"][0]["power"], 0.1)
        self.assertAlmostEqual(rows["db-guest-other"][0]["power"], 0.3)
        groups = summary["process_groups"]["db"]
        self.assertAlmostEqual(groups["backends"], 0.6)
        self.assertAlmostEqual(groups["other"], 0.3)
        self.assertAlmostEqual(summary["guest_coverage"]["db"], 0.7)

    def test_sample_outside_guest_data_adds_no_group_rows(self):
        tmp = tempfile.mkdtemp()
        meta = {"roles": {
            "db": {"container": "db", "pid": 1, "target": "postgres"}}}
        guests = {"db": self.guest_for(tmp, [
            (1000.0, 0, {"backends": 0}), (1001.0, 100, {"backends": 50})])}
        sample = self.epoch_sample(1200.0, 2000.0, 1000.0, {1: 500.0})
        rows, _, summary = attribute_samples([sample], meta, guests)
        self.assertEqual(rows["pg-backends"], [])
        self.assertAlmostEqual(rows["postgres"][0]["power"], 1.0)
        self.assertEqual(summary["guest_coverage"]["db"], 0.0)


IOREG_AC = '''
      "AppleRawAdapterDetails" = ({"Watts"=60,"AdapterVoltage"=20000})
      "Amperage" = 0
      "ExternalConnected" = Yes
      "BatteryData" = {"Voltage"=12554,"SystemPower"=}
      "PowerTelemetryData" = {"AdapterEfficiencyLoss"=562,"SystemCurrentIn"=1224,"SystemVoltageIn"=20108,"SystemPowerIn"=24611,"BatteryPower"=0}
      "InstantAmperage" = 0
      "IsCharging" = No
      "Voltage" = 12555
'''

IOREG_BATTERY = '''
      "ExternalConnected" = No
      "BatteryData" = {"Voltage"=12000}
      "InstantAmperage" = 18446744073709550116
      "Voltage" = 12300
'''


class TestSystemPower(unittest.TestCase):
    def test_ac_uses_system_power_in(self):
        watts, source = parse_system_power(IOREG_AC)
        self.assertAlmostEqual(watts, 24.611)
        self.assertEqual(source, "ac")

    def test_battery_uses_amperage_times_voltage(self):
        watts, source = parse_system_power(IOREG_BATTERY)
        # The unsigned 64-bit value decodes to -1500 mA; at 12300 mV that
        # is 18.45 W.
        self.assertAlmostEqual(watts, 18.45)
        self.assertEqual(source, "battery")

    def test_garbage_yields_none(self):
        self.assertIsNone(parse_system_power("no telemetry here"))

    def test_attribute_merges_system_series(self):
        tmp = tempfile.mkdtemp()
        with open(META) as f:
            meta = json.load(f)
        meta["system_samples"] = "system_samples.ndjson"
        meta_path = os.path.join(tmp, "meta.json")
        with open(meta_path, "w") as f:
            json.dump(meta, f)
        raw_path = os.path.join(tmp, "raw.plist")
        with open(STREAM, "rb") as src, open(raw_path, "wb") as dst:
            dst.write(src.read())
        with open(os.path.join(tmp, "system_samples.ndjson"), "w") as f:
            for i in range(3):
                f.write(json.dumps({"ts": 1000.0 + i, "system_w": 24.0,
                                    "source": "ac"}) + "\n")

        out = os.path.join(tmp, "out")
        cmd_attribute(Namespace(raw=raw_path, meta=meta_path, out_dir=out))

        for filename in FILE_LAYOUT:
            with open(os.path.join(out, filename)) as f:
                entries = json.load(f)
            sys_rows = [e for e in entries if e["target"] == "system-total"]
            self.assertEqual(len(sys_rows), 3)
            self.assertEqual(sys_rows[0]["sensor"], "smc")
            self.assertAlmostEqual(sys_rows[0]["power"], 24.0)
        with open(os.path.join(out, "attribution_summary.json")) as f:
            summary = json.load(f)
        # 24 W over the 2 s sample span integrates to 48 J.
        self.assertAlmostEqual(summary["system_energy_j"], 48.0)
        self.assertAlmostEqual(summary["unmeasured_rest_j"],
                               48.0 - summary["host_combined_energy_j"])

    def test_absent_samples_leave_attribution_unchanged(self):
        tmp = tempfile.mkdtemp()
        cmd_attribute(Namespace(raw=STREAM, meta=META, out_dir=tmp))
        with open(os.path.join(tmp, "attribution_summary.json")) as f:
            summary = json.load(f)
        self.assertNotIn("system_energy_j", summary)
        for filename in FILE_LAYOUT:
            with open(os.path.join(tmp, filename)) as f:
                targets = {e["target"] for e in json.load(f)}
            self.assertNotIn("system-total", targets)


class TestPgSnapshotLoop(unittest.TestCase):
    def test_truncates_stale_samples_from_previous_run(self):
        import threading
        from power_collector import pg_snapshot_loop
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "pg_query_samples.csv")
        with open(path, "w") as f:
            f.write("stale,previous,run,rows\n")
        stop = threading.Event()
        # With the event pre-set the loop body never runs; only the open()
        # behaviour is under test.
        stop.set()
        pg_snapshot_loop(Namespace(container_cli="container", pg_user="u",
                                   pg_db="d", pg_password="p",
                                   db_engine="postgres",
                                   pg_snapshot_interval_s=5),
                         "db", path, stop)
        self.assertEqual(os.path.getsize(path), 0)

    def test_provider_seam(self):
        from power_collector import query_stats_command, PG_SNAPSHOT_SQL
        pg = query_stats_command(Namespace(db_engine="postgres", pg_user="u",
                                           pg_db="d", pg_password="p"))
        # The postgres provider must stay byte-identical to the historical
        # command.
        self.assertEqual(pg, ["psql", "-U", "u", "-d", "d",
                              "-Atc", PG_SNAPSHOT_SQL])
        my = query_stats_command(Namespace(db_engine="mysql", pg_user="u",
                                           pg_db="d", pg_password="p"))
        self.assertEqual(my[0], "mysql")
        self.assertIn("events_statements_summary_by_digest", my[-1])
        self.assertIsNone(query_stats_command(
            Namespace(db_engine="sqlite", pg_user="u", pg_db="d",
                      pg_password="p")))

    def test_unknown_engine_writes_no_samples(self):
        import threading
        from power_collector import pg_snapshot_loop
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "pg_query_samples.csv")
        stop = threading.Event()
        stop.set()
        pg_snapshot_loop(Namespace(container_cli="container", pg_user="u",
                                   pg_db="d", pg_password="p",
                                   db_engine="sqlite",
                                   pg_snapshot_interval_s=5),
                         "db", path, stop)
        self.assertFalse(os.path.exists(path))

    def test_string_queryids_parse(self):
        from power_collector import load_pg_snapshots
        tmp = tempfile.mkdtemp()
        path = os.path.join(tmp, "s.csv")
        with open(path, "w") as f:
            f.write('1000.0,"abc123def",5,10.0,0.5,0,"SELECT ?"\n')
        q = load_pg_snapshots(path)
        self.assertIn("abc123def", q)
        self.assertEqual(q["abc123def"]["series"][0][1], 5)


class TestQueriesCommand(unittest.TestCase):
    def iso(self, epoch):
        return datetime.datetime.fromtimestamp(
            epoch, datetime.timezone.utc).strftime(
            "%Y-%m-%dT%H:%M:%S.%f")[:-3] + "Z"

    def test_per_window_energy(self):
        tmp = tempfile.mkdtemp()
        # Backends accumulate 5 CPU-seconds in the window (500 jiffies at
        # hz=100).
        guest = os.path.join(tmp, "guest_samples_db.ndjson")
        write_guest_file(guest, [
            (1000.0, 0,    {"backends": 0}),
            (1010.0, 1000, {"backends": 500}),
        ])
        # Cumulative counters: query 42 gains 100 calls and 2.0 CPU-seconds
        # over the window, query 43 gains 50 calls and 1.0 CPU-seconds.
        pg = os.path.join(tmp, "pg_query_samples.csv")
        with open(pg, "w") as f:
            f.write('1000.0,42,0,0,0,0,"SELECT a, b FROM t"\n')
            f.write('1000.0,43,0,0,0,0,SELECT 2\n')
            f.write('1010.0,42,100,900,1.5,0.5,"SELECT a, b FROM t"\n')
            f.write('1010.0,43,50,400,0.8,0.2,SELECT 2\n')
        # pg-backends draws a constant 2 W across the 10 s window, which
        # integrates to 20 J.
        power_dir = tmp
        rows = [{"timestamp": {"$date": self.iso(1000 + i)},
                 "sensor": "powermetrics-guest", "target": "pg-backends",
                 "power": 2.0} for i in range(11)]
        with open(os.path.join(power_dir, "mongodump-DB-server.json"), "w") as f:
            json.dump(rows, f)
        combined = os.path.join(tmp, "combined_results.json")
        with open(combined, "w") as f:
            json.dump({"benchmark_results": {"experiments": {"RAMP": {
                "runs": [{"run_number": 0, "start_timestamp": 1000000,
                          "end_timestamp": 1010000,
                          "successful_requests": 150}]}}}}, f)

        out = os.path.join(tmp, "per_query_energy.json")
        cmd_queries(Namespace(pg_samples=pg, combined=combined,
                              guest_samples=guest, power_dir=power_dir,
                              out=out))
        with open(out) as f:
            rows = json.load(f)
        result = {r["queryid"]: r for r in rows if r["queryid"] is not None}
        special = {r["query"]: r for r in rows if r["queryid"] is None}

        # Joules per CPU-second: 20 J / 5 CPU-s = 4.
        self.assertAlmostEqual(result[42]["energy_j"], 8.0)
        self.assertAlmostEqual(result[42]["mj_per_call"], 80.0)
        self.assertEqual(result[42]["calls"], 100)
        self.assertEqual(result[42]["query"], "SELECT a, b FROM t")
        self.assertAlmostEqual(result[43]["energy_j"], 4.0)
        unattributed = special["_unattributed"]
        self.assertAlmostEqual(unattributed["cpu_s"], 2.0)
        self.assertAlmostEqual(unattributed["energy_j"], 8.0)
        # Client/server consistency: 150 server calls against 150 client
        # successes.
        consistency = special["_consistency"]
        self.assertEqual(consistency["calls"], 150)
        self.assertEqual(consistency["client_successful_requests"], 150)
        self.assertAlmostEqual(consistency["server_to_client_ratio"], 1.0)
        self.assertTrue(consistency["ok"])


if __name__ == "__main__":
    unittest.main()
