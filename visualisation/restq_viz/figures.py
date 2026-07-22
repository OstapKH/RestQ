"""Plotly figure builders for the RestQ visualizer.

Pure functions from DataFrames (produced by restq_viz.data) to
plotly.graph_objects.Figure. No Dash imports — testable headlessly.
"""

from __future__ import annotations

import pandas as pd
import plotly.graph_objects as go
from plotly.subplots import make_subplots

from .data import ExperimentFolder, ALL_EXPERIMENTS, ALL_WITHOUT_WARMUP

CATEGORICAL = ["#2a78d6", "#1baf7a", "#008300", "#e34948",
               "#4a3aa7", "#eb6834"]
HOST_COLORS = {"db": "#52514e", "api": "#9b9a94"}
SOURCE_DASH = {"container": "solid", "host": "dash"}

RUN_SPAN_COLORS = ["rgba(144,238,144,0.18)", "rgba(173,216,230,0.18)",
                   "rgba(255,255,224,0.30)", "rgba(255,182,193,0.18)",
                   "rgba(240,128,128,0.18)", "rgba(135,206,250,0.18)"]
RUN_START_LINE = "rgba(0,128,0,0.6)"
RUN_END_LINE = "rgba(200,0,0,0.6)"
PAUSE_FILL = "rgba(128,128,128,0.20)"
PAUSE_EDGE = "rgba(0,0,0,0.7)"
PAUSE_LABEL_MIN_S = 5

X_HOVERFORMAT = "%Y-%m-%d %H:%M:%S.%L"


def _dt(ms):
    """Epoch-ms (scalar or series) -> datetime (UTC, tz-naive)."""
    return pd.to_datetime(ms, unit="ms")


def _folder_slots(i):
    """(db_hue, api_hue) for folder index i — consecutive palette pair."""
    a = (2 * i) % len(CATEGORICAL)
    return CATEGORICAL[a], CATEGORICAL[(a + 1) % len(CATEGORICAL)]

_LAYOUT_DEFAULTS = dict(
    template="plotly_white",
    margin=dict(l=60, r=20, t=40, b=40),
    legend=dict(orientation="h", yanchor="bottom", y=1.02, x=0),
    hovermode="x unified",
)




def add_run_markers(fig, folders, selection, subplots=False):
    """Legacy run/pause chronology markers.

    Per run: a light shaded span (tint cycled per experiment), a green dashed
    start line and a red dashed end line, plus a 'Run N' label when a single
    experiment is selected. Between experiments: a gray pause span with black
    edge lines, labeled with its duration when longer than PAUSE_LABEL_MIN_S.
    """
    span_kwargs = dict(row="all", col=1) if subplots else {}
    single = selection not in (ALL_EXPERIMENTS, ALL_WITHOUT_WARMUP)
    for folder in folders:
        start, end = folder.boundaries_for(selection)
        runs = folder.runs.dropna(subset=["start_ms", "end_ms"])
        if start is not None and end is not None:
            runs = runs[(runs["start_ms"] >= start) & (runs["end_ms"] <= end)]
        exp_index = {e: i for i, e in enumerate(folder.experiment_ids)}
        for _, run in runs.iterrows():
            x0, x1 = _dt(run["start_ms"]), _dt(run["end_ms"])
            tint = RUN_SPAN_COLORS[exp_index.get(run["experiment"], 0)
                                   % len(RUN_SPAN_COLORS)]
            fig.add_vrect(x0=x0, x1=x1, fillcolor=tint, line_width=0,
                          layer="below", **span_kwargs)
            fig.add_vline(x=x0, line=dict(color=RUN_START_LINE, dash="dash",
                                          width=1), **span_kwargs)
            fig.add_vline(x=x1, line=dict(color=RUN_END_LINE, dash="dash",
                                          width=1), **span_kwargs)
            if single:
                fig.add_annotation(
                    x=x0 + (x1 - x0) / 2, y=1, yref="paper", yanchor="bottom",
                    text=f"Run {run['run']}", showarrow=False,
                    font=dict(size=11), bgcolor="rgba(255,255,255,0.8)")
        for prev_id, next_id, gap_start, gap_end in folder.pauses():
            if (start is not None and end is not None
                    and (gap_end < start or gap_start > end)):
                continue
            x0, x1 = _dt(gap_start), _dt(gap_end)
            fig.add_vrect(x0=x0, x1=x1, fillcolor=PAUSE_FILL, line_width=0,
                          layer="below", **span_kwargs)
            fig.add_vline(x=x0, line=dict(color=PAUSE_EDGE, width=2),
                          **span_kwargs)
            fig.add_vline(x=x1, line=dict(color=PAUSE_EDGE, width=2),
                          **span_kwargs)
            pause_s = (gap_end - gap_start) / 1000
            if pause_s > PAUSE_LABEL_MIN_S:
                if pause_s < 60:
                    txt = f"{pause_s:.1f}s"
                elif pause_s < 3600:
                    txt = f"{pause_s / 60:.1f}min"
                else:
                    txt = f"{pause_s / 3600:.1f}h"
                fig.add_annotation(
                    x=x0 + (x1 - x0) / 2, y=0.85, yref="paper",
                    text=f"Pause: {txt}<br>{prev_id} → {next_id}",
                    showarrow=False, font=dict(size=10),
                    bgcolor="rgba(255,255,255,0.8)")
    return fig


def _window_mean(df, window_ms):
    """Resample a tidy energy frame to window_ms buckets (mean power)."""
    if not window_ms or df.empty:
        return df
    df = df.copy()
    df["timestamp_ms"] = (df["timestamp_ms"] // window_ms) * window_ms
    return (df.groupby(["source", "series", "timestamp_ms"], as_index=False)
              ["watts"].mean())


def timeline(folders, selection, strict=True, show_overlays=False,
             window_ms=None, dedupe_host=True, show_boundaries=True):
    """Stacked subplots on a shared x-axis: energy, throughput, latency.

    folders: list[ExperimentFolder]; selection: experiment id or sentinel.
    window_ms: optional bucket size for averaging dense power series.
    dedupe_host: collapse per-source host series that are identical (one
    machine, e.g. macOS) into a single 'host (shared)' trace.
    show_boundaries: draw the legacy run/pause chronology markers.
    """
    fig = make_subplots(
        rows=3, cols=1, shared_xaxes=True, vertical_spacing=0.06,
        row_heights=[0.5, 0.25, 0.25],
        subplot_titles=("Power (W)", "Throughput (req/s)", "p99 latency (ms)"))

    for i, folder in enumerate(folders):
        db_hue, api_hue = _folder_slots(i)
        source_hue = {"db": db_hue, "api": api_hue}
        start, end = folder.boundaries_for(selection)
        energy = _window_mean(folder.energy_in(start, end, strict=strict),
                              window_ms)
        embedded_name, overlay_name = folder.source_names
        prefix = f"{folder.label} · " if len(folders) > 1 else ""
        dup_hosts = folder.duplicate_host_sources() if dedupe_host else set()

        for (source, series), grp in energy.groupby(["source", "series"]):
            if series == "host":
                if source in dup_hosts:
                    continue
                color, width = HOST_COLORS[source], 1.5
                host_label = "host (shared)" if dup_hosts else f"{source} (host)"
                name = f"{prefix}{embedded_name} {host_label}"
            else:
                color, width = source_hue[source], 2
                name = f"{prefix}{embedded_name} {source} ({series})"
            fig.add_trace(go.Scatter(
                x=_dt(grp["timestamp_ms"]), y=grp["watts"], mode="lines",
                name=name,
                line=dict(color=color, dash=SOURCE_DASH[series], width=width),
            ), row=1, col=1)

        if show_overlays and folder.overlays is not None:
            overlays = folder.overlays_in(start, end, strict=strict)
            overlay_hue = {"postgres": db_hue, "spring-api": api_hue,
                           "host-total": HOST_COLORS["db"],
                           "system-total": "#1c1b19",
                           "pg-backends": db_hue, "java": api_hue,
                           "pg-background": HOST_COLORS["db"],
                           "db-guest-other": HOST_COLORS["api"],
                           "api-guest-other": HOST_COLORS["api"]}
            process_targets = {"pg-backends", "pg-background",
                               "db-guest-other", "java", "api-guest-other"}
            for target, grp in overlays.groupby("target"):
                dash = "dashdot" if target in process_targets else "dot"
                fig.add_trace(go.Scatter(
                    x=_dt(grp["timestamp_ms"]), y=grp["watts"], mode="lines",
                    name=f"{prefix}{overlay_name} {target}",
                    line=dict(color=overlay_hue.get(target, HOST_COLORS["api"]),
                              dash=dash, width=1.5),
                ), row=1, col=1)

        runs = folder.runs
        if start is not None and end is not None:
            runs = runs[(runs["start_ms"] >= start) & (runs["end_ms"] <= end)]
        mid = _dt((runs["start_ms"] + runs["end_ms"]) / 2)
        fig.add_trace(go.Scatter(
            x=mid, y=runs["throughput"], mode="markers+lines",
            name=f"{prefix}throughput",
            marker=dict(color=db_hue, size=9, symbol="diamond"),
            line=dict(color=db_hue, width=1),
        ), row=2, col=1)
        fig.add_trace(go.Scatter(
            x=mid, y=runs["latency_p99_ns"] / 1e6, mode="markers+lines",
            name=f"{prefix}p99 latency",
            marker=dict(color=db_hue, size=9, symbol="square"),
            line=dict(color=db_hue, width=1),
        ), row=3, col=1)

    if show_boundaries:
        add_run_markers(fig, folders, selection, subplots=True)
    fig.update_layout(height=720, **_LAYOUT_DEFAULTS)
    fig.update_xaxes(type="date", hoverformat=X_HOVERFORMAT)
    return fig


REQUESTS_MAX_POINTS = 15_000


def _decimate(grp, budget):
    """Reduce a time-sorted latency frame to ~budget rows: uniform stride
    over time plus the largest latencies, so spikes survive decimation."""
    if len(grp) <= budget:
        return grp
    n_outliers = max(1, budget // 20)
    outliers = grp.nlargest(n_outliers, "latency_ms")
    stride = -(-len(grp) // max(1, budget - n_outliers))
    sampled = grp.iloc[::stride]
    return (pd.concat([sampled, outliers])
            .drop_duplicates().sort_values("timestamp_ms"))


def requests(folders, selection, strict=True, show_boundaries=True,
             x_range=None, max_points=REQUESTS_MAX_POINTS):
    """Per-request duration segments (legacy 'Latency' data source): each
    request is a horizontal line from its start to its end (the client
    records the timestamp *before* sending, so the segment is
    [timestamp, timestamp + latency]) at height y = latency, one trace per
    experiment. Small endpoint markers keep sub-millisecond requests
    visible when their segment is narrower than a pixel.

    SVG Scatter, not Scattergl: WebGL stores coordinates as float32, whose
    ~3-minute resolution at epoch-ms magnitudes makes points collapse onto
    quantized positions when zooming below a few minutes. To keep SVG
    responsive, at most max_points requests render at once: the app
    re-invokes this with x_range=(t0_ms, t1_ms) on zoom, so the visible
    window is always at full resolution while the zoomed-out view is a
    spike-preserving sample.
    """
    fig = go.Figure()
    groups, total = [], 0
    for folder in folders:
        start, end = folder.boundaries_for(selection)
        lat = folder.latencies_in(start, end, strict=strict)
        prefix = f"{folder.label} · " if len(folders) > 1 else ""
        if lat is None or lat.empty:
            continue
        if x_range is not None:
            lat = lat[(lat["timestamp_ms"] <= x_range[1])
                      & (lat["timestamp_ms"] + lat["latency_ms"]
                         >= x_range[0])]
        for exp_id in folder.experiment_ids:
            grp = lat[lat["experiment"] == exp_id]
            if grp.empty:
                continue
            groups.append((f"{prefix}{exp_id}", grp))
            total += len(grp)

    shown = 0
    for hue, (name, grp) in enumerate(groups):
        if total > max_points:
            budget = max(50, int(max_points * len(grp) / total))
            grp = _decimate(grp, budget)
        shown += len(grp)
        starts = _dt(grp["timestamp_ms"])
        ends = _dt(grp["timestamp_ms"] + grp["latency_ms"])
        xs, ys = [], []
        for s, e, lat_ms in zip(starts, ends, grp["latency_ms"]):
            xs += [s, e, None]
            ys += [lat_ms, lat_ms, None]
        fig.add_trace(go.Scatter(
            x=xs, y=ys, mode="lines+markers", name=name,
            line=dict(color=CATEGORICAL[hue % len(CATEGORICAL)], width=2),
            marker=dict(color=CATEGORICAL[hue % len(CATEGORICAL)], size=3,
                        opacity=0.8),
            opacity=0.7,
            hovertemplate=("%{x|" + X_HOVERFORMAT + "}<br>"
                           "%{y:.2f} ms<extra>%{fullData.name}</extra>"),
        ))
    if shown < total:
        fig.add_annotation(
            xref="paper", yref="paper", x=1, y=1.06, xanchor="right",
            showarrow=False, font=dict(size=11, color="#7a7974"),
            text=(f"showing {shown:,} of {total:,} requests "
                  "(spikes kept) — zoom in for full detail"))

    if show_boundaries:
        add_run_markers(fig, folders, selection)
    layout = dict(_LAYOUT_DEFAULTS, hovermode="closest")
    fig.update_layout(height=560, yaxis_title="request latency (ms)",
                      uirevision="requests", **layout)
    fig.update_xaxes(type="date", hoverformat=X_HOVERFORMAT)
    return fig


def correlation(folder, selection):
    """Per-run derived metrics for one folder: energy vs performance."""
    m = folder.run_metrics()
    if selection not in (None, "", "All Experiments",
                         "All Experiments without warmup"):
        m = m[m["experiment"] == selection]
    elif selection == "All Experiments without warmup":
        m = m[~m["is_warmup"]]

    fig = make_subplots(rows=1, cols=2, subplot_titles=(
        "Energy per request (J/req)", "Mean power vs throughput"))

    source_hue = {"db": CATEGORICAL[0], "api": CATEGORICAL[1]}
    for source, grp in m.groupby("source"):
        color = source_hue.get(source, HOST_COLORS["api"])
        labels = grp["experiment"] + " r" + grp["run"].astype(str)
        fig.add_trace(go.Bar(
            x=labels, y=grp["j_per_request"], name=f"{source} J/req",
            marker_color=color,
        ), row=1, col=1)
        fig.add_trace(go.Scatter(
            x=grp["throughput"], y=grp["mean_power_w"],
            mode="markers+text", text=labels, textposition="top center",
            name=f"{source} power vs tput", marker=dict(color=color, size=10),
        ), row=1, col=2)

    fig.update_layout(height=480, **_LAYOUT_DEFAULTS)
    fig.update_xaxes(title_text="throughput (req/s)", row=1, col=2)
    fig.update_yaxes(title_text="mean power (W)", row=1, col=2)
    return fig


def comparison(folders, selection, metric="watts"):
    """Overlay the container power series of several folders."""
    fig = go.Figure()
    for i, folder in enumerate(folders):
        db_hue, api_hue = _folder_slots(i)
        source_hue = {"db": db_hue, "api": api_hue}
        start, end = folder.boundaries_for(selection)
        energy = folder.energy_in(start, end, strict=True)
        grp = energy[energy["series"] == "container"]
        for source, sub in grp.groupby("source"):
            x = (sub["timestamp_ms"] - (start or sub["timestamp_ms"].min())) / 1000.0
            fig.add_trace(go.Scatter(
                x=x, y=sub["watts"], mode="lines",
                name=f"{folder.label} · {source}",
                line=dict(color=source_hue.get(source, HOST_COLORS["api"]),
                          width=2),
            ))
    fig.update_layout(
        height=480, xaxis_title="seconds from experiment start",
        yaxis_title="power (W)", **_LAYOUT_DEFAULTS)
    return fig


def comparison_table_rows(folders, selection):
    """Totals per folder for the Compare tab's table."""
    rows = []
    for folder in folders:
        start, end = folder.boundaries_for(selection)
        m = folder.run_metrics()
        runs = folder.runs[~folder.runs["is_warmup"]]
        row = {
            "folder": folder.label,
            "mode": folder.measurement_mode or "scaphandre/powerapi",
            "energy_db_j": round(m[m["source"] == "db"]["energy_j"].sum(), 2),
            "energy_api_j": round(m[m["source"] == "api"]["energy_j"].sum(), 2),
            "mean_throughput": round(runs["throughput"].mean(), 2)
            if not runs.empty else None,
            "p99_latency_ms": round(runs["latency_p99_ns"].max() / 1e6, 2)
            if not runs.empty and runs["latency_p99_ns"].notna().any() else None,
        }
        rows.append(row)
    return rows


def query_energy_map(folder):
    """TPC-H query map: latency on x, validated run energy per success on y."""
    summary = folder.query_summary()
    fig = go.Figure()
    if summary.empty:
        return fig
    custom = summary[["throughput", "db_energy_j", "api_energy_j",
                      "run_spread_p95_ms"]].to_numpy()
    fig.add_trace(go.Scatter(
        x=summary["p95_ms"], y=summary["joules_per_success"],
        text=summary["query_id"], customdata=custom,
        mode="markers+text", textposition="top center",
        marker=dict(size=14, color=summary["combined_energy_j"],
                    colorscale="Tealgrn", showscale=True,
                    colorbar=dict(title="total J"), line=dict(width=1, color="#173b36")),
        hovertemplate=("<b>%{text}</b><br>p95 %{x:.2f} ms"
                       "<br>%{y:.4f} J/success"
                       "<br>throughput %{customdata[0]:.2f} req/s"
                       "<br>DB %{customdata[1]:.2f} J · API %{customdata[2]:.2f} J"
                       "<br>p95 run spread %{customdata[3]:.2f} ms<extra></extra>"),
        name="queries",
    ))
    fig.update_layout(height=520, xaxis_title="mean p95 latency (ms)",
                      yaxis_title="combined joules / successful request",
                      **_LAYOUT_DEFAULTS)
    return fig


def run_stability(folder, query_id):
    """Per-run latency, throughput and combined energy for one query."""
    runs = folder.runs[(~folder.runs["is_warmup"])
                       & (folder.runs["query_id"] == query_id)]
    fig = make_subplots(specs=[[{"secondary_y": True}]])
    if runs.empty:
        return fig
    labels = [f"run {int(run) + 1}" for run in runs["run"]]
    fig.add_trace(go.Bar(x=labels, y=runs["combined_energy_j"],
                         name="combined energy (J)", marker_color="#0f766e"),
                  secondary_y=False)
    fig.add_trace(go.Scatter(x=labels, y=runs["latency_p95_ns"] / 1e6,
                             name="p95 latency (ms)", mode="lines+markers",
                             line=dict(color="#d97706", width=3)),
                  secondary_y=True)
    fig.add_trace(go.Scatter(x=labels, y=runs["throughput"],
                             name="throughput (req/s)", mode="lines+markers",
                             line=dict(color="#1d4ed8", dash="dot")),
                  secondary_y=True)
    fig.update_yaxes(title_text="energy (J)", secondary_y=False)
    fig.update_yaxes(title_text="latency / throughput", secondary_y=True)
    fig.update_layout(height=390, **_LAYOUT_DEFAULTS)
    return fig


def parameter_latency(folder, query_id):
    """Request-latency distributions for P1–P5, without energy attribution."""
    fig = go.Figure()
    if folder.latencies is None or folder.latencies.empty:
        return fig
    rows = folder.latencies[(folder.latencies["query_id"] == query_id)
                            & (~folder.latencies["is_warmup"])]
    for index, (parameter_id, group) in enumerate(
            rows.groupby("parameter_set_id", sort=True)):
        fig.add_trace(go.Box(
            y=group["latency_ms"], name=parameter_id, boxpoints="outliers",
            marker_color=CATEGORICAL[index % len(CATEGORICAL)],
            hovertemplate="%{fullData.name}<br>%{y:.2f} ms<extra></extra>"))
    fig.update_layout(height=480, yaxis_title="request latency (ms)",
                      **_LAYOUT_DEFAULTS)
    return fig
