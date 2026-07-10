"""Unit tests for restq_viz.data (stdlib unittest; needs pandas).

Run from visualisation/:  ~/.global_venv/bin/python3 -m unittest discover tests
"""

import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from restq_viz.data import (ExperimentFolder, FolderError,
                            ALL_EXPERIMENTS, ALL_WITHOUT_WARMUP)

FIXTURES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "fixtures")
MINI = os.path.join(FIXTURES, "mini_experiment")
BROKEN = os.path.join(FIXTURES, "broken_experiment")
REAL = os.path.expanduser(
    "~/Desktop/Results_rest_q_xml_benchmarks/experiment_TPCH_2026-07-05T16:14:46Z")


def load_mini():
    return ExperimentFolder.load(MINI)


class TestLoading(unittest.TestCase):
    def test_broken_folder_raises_with_reason(self):
        with self.assertRaises(FolderError) as ctx:
            ExperimentFolder.load(BROKEN)
        self.assertIn("combined_results.json", str(ctx.exception))

    def test_nonexistent_folder_raises(self):
        with self.assertRaises(FolderError):
            ExperimentFolder.load("/nonexistent/place")

    def test_runs_dataframe(self):
        f = load_mini()
        self.assertEqual(len(f.runs), 3)
        self.assertEqual(set(f.runs["experiment"]), {"EXP_1", "EXP_1_warmup"})
        self.assertTrue(f.runs["start_ms"].notna().all())
        self.assertTrue(f.runs["end_ms"].notna().all())
        self.assertTrue((f.runs["end_ms"] > f.runs["start_ms"]).all())
        self.assertTrue(f.runs["start_ms"].is_monotonic_increasing)

    def test_measurement_mode_and_source_names(self):
        f = load_mini()
        self.assertEqual(f.measurement_mode, "powermetrics-macos")
        self.assertEqual(f.source_names, ("powermetrics", "powermetrics (overlay)"))

    def test_source_names_default_to_scaphandre(self):
        f = load_mini()
        f.info = {}
        self.assertEqual(f.source_names, ("Scaphandre", "PowerAPI"))


class TestEnergy(unittest.TestCase):
    def test_energy_tidy_frame_and_unit_conversion(self):
        f = load_mini()
        for source in ("db", "api"):
            sub = f.energy[f.energy["source"] == source]
            self.assertEqual(len(sub[sub["series"] == "host"]), 12)
            self.assertEqual(len(sub[sub["series"] == "container"]), 12)
        with open(os.path.join(MINI, "combined_results.json")) as fh:
            raw = json.load(fh)
        first = raw["db_server_energy"][0]
        got = f.energy[(f.energy["source"] == "db")
                       & (f.energy["series"] == "host")].iloc[0]
        self.assertAlmostEqual(got["watts"], first["host"]["consumption"] / 1e6)
        self.assertAlmostEqual(got["timestamp_ms"],
                               first["host"]["timestamp"] * 1000, delta=1)

    def test_millisecond_timestamps_normalized(self):
        f = load_mini()
        with open(os.path.join(MINI, "combined_results.json")) as fh:
            raw = json.load(fh)
        entry = raw["db_server_energy"][0]
        entry["host"]["timestamp"] = entry["host"]["timestamp"] * 1000
        df = f._build_energy({"db_server_energy": [entry],
                              "api_server_energy": []})
        host_row = df[df["series"] == "host"].iloc[0]
        self.assertAlmostEqual(host_row["timestamp_ms"],
                               entry["host"]["timestamp"], delta=1)

    def test_overlays_parsed(self):
        f = load_mini()
        self.assertEqual(set(f.overlays["target"]),
                         {"postgres", "spring-api", "host-total"})
        self.assertTrue((f.overlays["watts"] >= 0).all())

    def test_overlay_host_total_deduplicated(self):
        f = load_mini()
        host_total = f.overlays[f.overlays["target"] == "host-total"]
        postgres = f.overlays[f.overlays["target"] == "postgres"]
        self.assertEqual(len(host_total), len(postgres))
        self.assertFalse(host_total.duplicated(
            ["timestamp_ms", "watts"]).any())

    def test_duplicate_host_sources_identical(self):
        f = load_mini()
        self.assertEqual(f.duplicate_host_sources(), {"api"})

    def test_duplicate_host_sources_divergent(self):
        f = load_mini()
        energy = f.energy.copy()
        mask = (energy["source"] == "api") & (energy["series"] == "host")
        energy.loc[energy[mask].index[0], "watts"] += 1.0
        f.energy = energy
        self.assertEqual(f.duplicate_host_sources(), set())


class TestLatencies(unittest.TestCase):
    def test_latencies_frame(self):
        f = load_mini()
        self.assertFalse(f.latencies.empty)
        self.assertEqual(set(f.latencies["experiment"]),
                         {"EXP_1", "EXP_1_warmup"})
        with open(os.path.join(MINI, "combined_results.json")) as fh:
            raw = json.load(fh)
        run0 = raw["benchmark_results"]["experiments"]["EXP_1"]["runs"][0]
        first = run0["latencies"][0]
        got = f.latencies[
            (f.latencies["experiment"] == "EXP_1") & (f.latencies["run"] == 0)]
        row = got[got["timestamp_ms"] == first["timestamp"]].iloc[0]
        self.assertAlmostEqual(row["latency_ms"], first["latency_ns"] / 1e6)
        self.assertTrue(f.latencies["timestamp_ms"].is_monotonic_increasing)

    def test_runs_without_latencies_yield_empty_frame(self):
        f = load_mini()
        with open(os.path.join(MINI, "combined_results.json")) as fh:
            raw = json.load(fh)
        for exp in raw["benchmark_results"]["experiments"].values():
            for run in exp["runs"]:
                run.pop("latencies", None)
        df = f._build_latencies(raw)
        self.assertTrue(df.empty)
        self.assertIn("latency_ms", df.columns)

    def test_latencies_in_strict_and_lenient(self):
        f = load_mini()
        self.assertTrue(f.latencies_in(None, None, strict=True).empty)
        self.assertEqual(len(f.latencies_in(None, None, strict=False)),
                         len(f.latencies))
        start, end = f.experiment_boundaries("EXP_1")
        sub = f.latencies_in(start, end, strict=True)
        self.assertGreater(len(sub), 0)
        self.assertEqual(set(sub["experiment"]), {"EXP_1"})


class TestQueries(unittest.TestCase):
    def test_queries_frame_loaded(self):
        f = load_mini()
        self.assertEqual(len(f.queries), 5)
        self.assertEqual(set(f.queries["experiment"]),
                         {"EXP_1", "EXP_1_warmup"})
        exp1 = f.queries[f.queries["experiment"] == "EXP_1"]
        self.assertAlmostEqual(exp1["energy_j"].sum(), 0.72)
        self.assertIn("_unattributed", set(f.queries["query"]))

    def test_missing_file_yields_empty_frame_with_columns(self):
        f = load_mini()
        df = f._build_queries("/nonexistent")
        self.assertTrue(df.empty)
        self.assertIn("energy_j", df.columns)


class TestPauses(unittest.TestCase):
    def test_pause_between_warmup_and_main(self):
        f = load_mini()
        gaps = f.pauses()
        self.assertEqual(len(gaps), 1)
        prev_id, next_id, gap_start, gap_end = gaps[0]
        self.assertEqual((prev_id, next_id), ("EXP_1_warmup", "EXP_1"))
        self.assertEqual(gap_start,
                         f.runs[f.runs["is_warmup"]]["end_ms"].max())
        self.assertGreater(gap_end, gap_start)

    def test_no_pause_without_gap(self):
        f = load_mini()
        runs = f.runs.copy()
        warmup_end = runs[runs["is_warmup"]]["end_ms"].max()
        shift = runs[~runs["is_warmup"]]["start_ms"].min() - warmup_end
        runs.loc[~runs["is_warmup"], ["start_ms", "end_ms"]] -= shift
        f.runs = runs
        self.assertEqual(f.pauses(), [])


class TestBoundariesAndFiltering(unittest.TestCase):
    def test_experiment_ids_chronological(self):
        f = load_mini()
        self.assertEqual(f.experiment_ids, ["EXP_1_warmup", "EXP_1"])

    def test_experiment_boundaries_match_runs(self):
        f = load_mini()
        runs = f.runs[f.runs["experiment"] == "EXP_1"]
        start, end = f.experiment_boundaries("EXP_1")
        self.assertEqual(start, runs["start_ms"].iloc[0])
        self.assertEqual(end, runs["end_ms"].iloc[-1])

    def test_without_warmup_starts_after_warmup_end(self):
        f = load_mini()
        warmup_end = f.runs[f.runs["is_warmup"]]["end_ms"].max()
        start, end = f.without_warmup_boundaries()
        self.assertGreaterEqual(start, warmup_end)
        all_start, all_end = f.all_boundaries()
        self.assertEqual(end, all_end)
        self.assertGreater(start, all_start)

    def test_boundaries_for_sentinels(self):
        f = load_mini()
        self.assertEqual(f.boundaries_for(ALL_EXPERIMENTS), f.all_boundaries())
        self.assertEqual(f.boundaries_for(ALL_WITHOUT_WARMUP),
                         f.without_warmup_boundaries())
        self.assertEqual(f.boundaries_for("EXP_1"),
                         f.experiment_boundaries("EXP_1"))

    def test_strict_vs_lenient_filtering(self):
        f = load_mini()
        self.assertTrue(f.energy_in(None, None, strict=True).empty)
        self.assertEqual(len(f.energy_in(None, None, strict=False)),
                         len(f.energy))
        self.assertTrue(f.energy_in(0, 1, strict=True).empty)
        self.assertEqual(len(f.energy_in(0, 1, strict=False)), len(f.energy))
        start, end = f.all_boundaries()
        sub = f.energy_in(start, end, strict=True)
        self.assertGreater(len(sub), 0)
        self.assertLessEqual(len(sub), len(f.energy))


class TestRunMetrics(unittest.TestCase):
    def test_metrics_shape_and_positivity(self):
        f = load_mini()
        m = f.run_metrics()
        self.assertFalse(m.empty)
        self.assertEqual(set(m["source"]), {"db", "api"})
        self.assertTrue((m["energy_j"] >= 0).all())
        row = m.iloc[0]
        self.assertAlmostEqual(row["j_per_request"],
                               row["energy_j"] /
                               f.runs.iloc[0]["total_requests"])

    @unittest.skipUnless(os.path.isdir(REAL), "real experiment folder absent")
    def test_real_folder_cross_check_against_attribution_summary(self):
        f = ExperimentFolder.load(REAL)
        with open(os.path.join(REAL, "attribution_summary.json")) as fh:
            summary = json.load(fh)
        expected_db_j = summary["attributed_energy_j"]["restq-db"]
        grp = f.energy[(f.energy["source"] == "db")
                       & (f.energy["series"] == "container")]
        t = grp["timestamp_ms"].to_numpy() / 1000.0
        p = grp["watts"].to_numpy()
        energy_j = float(((p[1:] + p[:-1]) / 2 * (t[1:] - t[:-1])).sum())
        self.assertAlmostEqual(energy_j, expected_db_j,
                               delta=expected_db_j * 0.05)


if __name__ == "__main__":
    unittest.main()
