"""Smoke tests for restq_viz.figures."""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from restq_viz.data import ExperimentFolder, ALL_EXPERIMENTS
from restq_viz import figures

MINI = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "fixtures", "mini_experiment")


class TestFigures(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.folder = ExperimentFolder.load(MINI)

    def test_timeline_traces_and_linked_axes(self):
        fig = figures.timeline([self.folder], ALL_EXPERIMENTS)
        # energy: (db|api) container + 1 shared host (macOS fixture:
        # identical host series are deduped), + throughput + latency
        self.assertEqual(len(fig.data), 5)
        names = [t.name for t in fig.data]
        self.assertIn("powermetrics host (shared)", names)
        # shared x axes: rows 1-2 match the bottom (anchor) axis
        self.assertEqual(fig.layout.xaxis.matches, "x3")
        self.assertEqual(fig.layout.xaxis2.matches, "x3")

    def test_timeline_dedupe_off_shows_both_hosts(self):
        fig = figures.timeline([self.folder], ALL_EXPERIMENTS,
                               dedupe_host=False)
        self.assertEqual(len(fig.data), 6)
        names = [t.name for t in fig.data]
        self.assertIn("powermetrics db (host)", names)
        self.assertIn("powermetrics api (host)", names)

    def test_timeline_x_values_are_datetimes(self):
        fig = figures.timeline([self.folder], ALL_EXPERIMENTS)
        for trace in fig.data:
            self.assertTrue(all(hasattr(x, "isoformat") for x in trace.x))
        # hover shows a real date down to milliseconds on every subplot axis
        for axis in ("xaxis", "xaxis2", "xaxis3"):
            self.assertEqual(fig.layout[axis].hoverformat,
                             figures.X_HOVERFORMAT)
            self.assertEqual(fig.layout[axis].type, "date")

    def test_timeline_with_overlays(self):
        fig = figures.timeline([self.folder], ALL_EXPERIMENTS,
                               show_overlays=True)
        overlay_names = [t.name for t in fig.data if "overlay" in t.name]
        self.assertTrue(overlay_names)  # powermetrics (overlay) targets present

    def test_timeline_two_folders_prefixes_names(self):
        fig = figures.timeline([self.folder, self.folder], ALL_EXPERIMENTS)
        self.assertEqual(len(fig.data), 10)
        self.assertTrue(all("·" in t.name for t in fig.data))

    def test_timeline_run_markers_default_on(self):
        fig = figures.timeline([self.folder], ALL_EXPERIMENTS)
        # 3 runs x (span + start line + end line) = 9 shapes minimum
        self.assertGreaterEqual(len(fig.layout.shapes), 9)
        bare = figures.timeline([self.folder], ALL_EXPERIMENTS,
                                show_boundaries=False)
        self.assertEqual(len(bare.layout.shapes), 0)

    def test_correlation(self):
        fig = figures.correlation(self.folder, ALL_EXPERIMENTS)
        # 2 sources x (bar + scatter)
        self.assertEqual(len(fig.data), 4)

    def test_correlation_single_experiment(self):
        fig = figures.correlation(self.folder, "EXP_1")
        for trace in fig.data:
            if trace.type == "bar":
                self.assertTrue(all(x.startswith("EXP_1 ") for x in trace.x))

    def test_comparison_and_table(self):
        fig = figures.comparison([self.folder, self.folder], ALL_EXPERIMENTS)
        self.assertEqual(len(fig.data), 4)  # 2 folders x 2 sources
        rows = figures.comparison_table_rows([self.folder], ALL_EXPERIMENTS)
        self.assertEqual(len(rows), 1)
        self.assertGreater(rows[0]["energy_db_j"], 0)
        self.assertEqual(rows[0]["mode"], "powermetrics-macos")

    def test_empty_selection_no_exception(self):
        fig = figures.timeline([self.folder], "NO_SUCH_EXPERIMENT")
        self.assertIsNotNone(fig)

    @staticmethod
    def _segments(fig):
        """Requests render as duration segments: (start, end, None) triplets
        per request. Returns total request count across traces."""
        for t in fig.data:
            assert len(t.x) % 3 == 0
        return sum(len(t.x) // 3 for t in fig.data)

    def test_requests_duration_segments(self):
        fig = figures.requests([self.folder], ALL_EXPERIMENTS)
        # one trace per experiment; each request is one duration segment.
        # SVG scatter, NOT scattergl: float32 in WebGL quantizes epoch-ms
        # x values to ~3-minute steps, breaking zoom.
        self.assertEqual(len(fig.data), 2)
        self.assertTrue(all(t.type == "scatter" for t in fig.data))
        self.assertEqual(self._segments(fig), len(self.folder.latencies))
        # segment spans [start, start + latency]; flat at y = latency
        t = fig.data[0]
        span_ms = (t.x[1] - t.x[0]).total_seconds() * 1000
        self.assertAlmostEqual(span_ms, t.y[0], places=2)
        self.assertEqual(t.y[0], t.y[1])
        self.assertIsNone(t.x[2])
        self.assertEqual(fig.layout.xaxis.hoverformat, figures.X_HOVERFORMAT)

    def test_requests_single_experiment_labels_runs(self):
        fig = figures.requests([self.folder], "EXP_1")
        self.assertEqual(len(fig.data), 1)
        run_labels = [a.text for a in fig.layout.annotations
                      if a.text.startswith("Run ")]
        # EXP_1 has two runs inside its own boundaries
        self.assertEqual(len(run_labels), 2)

    def _dense_folder(self, n=2000, spike=123.0):
        """Mini folder with a dense synthetic latency frame inside its
        boundaries (the real fixture's 9 points sit under the 50-point
        per-trace decimation floor)."""
        import pandas as pd
        f = ExperimentFolder.load(MINI)
        start, end = f.all_boundaries()
        ts = [start + (end - start) * i / (n - 1) for i in range(n)]
        f.latencies = pd.DataFrame(
            {"experiment": "EXP_1", "run": 0,
             "timestamp_ms": ts, "latency_ms": [1.0] * n})
        f.latencies.loc[n // 2, "latency_ms"] = spike
        return f

    def test_requests_decimation_caps_points_and_keeps_spikes(self):
        f = self._dense_folder()
        fig = figures.requests([f], ALL_EXPERIMENTS, max_points=200)
        shown = self._segments(fig)
        self.assertLess(shown, 2000)
        self.assertLessEqual(shown, 230)  # budget + outlier/dedup slack
        # the spike must survive decimation
        self.assertTrue(any(123.0 in t.y for t in fig.data))
        # caption tells the user this is a sample
        captions = [a.text for a in fig.layout.annotations
                    if "zoom in for full detail" in a.text]
        self.assertEqual(len(captions), 1)

    def test_requests_small_data_never_decimated(self):
        # 9 fixture points sit under the per-trace floor: all shown, no caption
        fig = figures.requests([self.folder], ALL_EXPERIMENTS, max_points=5)
        self.assertEqual(self._segments(fig), len(self.folder.latencies))
        self.assertFalse([a for a in fig.layout.annotations
                          if "zoom in for full detail" in a.text])

    def test_requests_x_range_gives_full_resolution_window(self):
        import pandas as pd
        f = self._dense_folder()
        lat = f.latencies
        t0, t1 = lat["timestamp_ms"].quantile([0.45, 0.55])
        window = lat[(lat["timestamp_ms"] <= t1)
                     & (lat["timestamp_ms"] + lat["latency_ms"] >= t0)]
        fig = figures.requests([f], ALL_EXPERIMENTS,
                               x_range=(t0, t1), max_points=500)
        self.assertEqual(self._segments(fig), len(window))  # full res
        # every rendered segment overlaps the window
        for t in fig.data:
            for i in range(0, len(t.x), 3):
                s = pd.Timestamp(t.x[i]).value / 1e6
                e = pd.Timestamp(t.x[i + 1]).value / 1e6
                self.assertTrue(s <= t1 and e >= t0)

    def test_requests_boundaries_toggle(self):
        fig = figures.requests([self.folder], ALL_EXPERIMENTS,
                               show_boundaries=False)
        self.assertEqual(len(fig.layout.shapes), 0)

    def test_pause_markers_and_label(self):
        # mini fixture: EXP_1_warmup ends before EXP_1 starts -> one pause
        fig = figures.requests([self.folder], ALL_EXPERIMENTS)
        pause_labels = [a.text for a in fig.layout.annotations
                        if a.text.startswith("Pause:")]
        gaps = self.folder.pauses()
        expect = [1 for _, _, s, e in gaps if (e - s) / 1000 > 5]
        self.assertEqual(len(pause_labels), len(expect))


if __name__ == "__main__":
    unittest.main()
