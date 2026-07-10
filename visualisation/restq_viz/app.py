"""Dash application for the RestQ visualizer.

Layout: top bar (folder chips, experiment selector, window size) + three tabs
(Timeline / Correlation / Compare). Folders are parsed once into
ExperimentFolder objects and cached in memory; callbacks only slice cached
DataFrames and rebuild figures.
"""

from __future__ import annotations

import os
import subprocess
import sys

import pandas as pd
from dash import Dash, dcc, html, dash_table, ctx, no_update, ALL
from dash.dependencies import Input, Output, State

from .data import (ExperimentFolder, FolderError,
                   ALL_EXPERIMENTS, ALL_WITHOUT_WARMUP)
from . import figures

RESULTS_ROOT = os.path.expanduser("~/Desktop/Results_rest_q_xml_benchmarks")

# In-memory cache: path -> ExperimentFolder (parsed once per process).
_FOLDER_CACHE = {}


def get_folder(path):
    if path not in _FOLDER_CACHE:
        _FOLDER_CACHE[path] = ExperimentFolder.load(path)
    return _FOLDER_CACHE[path]


def native_folder_dialog():
    """Open the macOS Finder folder picker; return the chosen path or None.

    The app runs locally, so a server-side native dialog is fine. Returns
    None when the user cancels or on non-macOS platforms.
    """
    if sys.platform != "darwin":
        return None
    default = RESULTS_ROOT if os.path.isdir(RESULTS_ROOT) else os.path.expanduser("~")
    script = (
        'tell application "System Events" to activate\n'
        f'POSIX path of (choose folder with prompt '
        f'"Select an experiment folder" default location POSIX file "{default}")'
    )
    try:
        result = subprocess.run(["osascript", "-e", script],
                                capture_output=True, text=True, timeout=120)
    except (subprocess.TimeoutExpired, OSError):
        return None
    if result.returncode != 0:  # cancelled
        return None
    path = result.stdout.strip()
    return path or None


def known_result_folders():
    """Experiment folders found under the standard results directory."""
    if not os.path.isdir(RESULTS_ROOT):
        return []
    return sorted(
        os.path.join(RESULTS_ROOT, name)
        for name in os.listdir(RESULTS_ROOT)
        if os.path.isdir(os.path.join(RESULTS_ROOT, name)))


def _chip(entry):
    """Render one folder chip (or an error chip)."""
    if entry.get("error"):
        body = [html.Span(os.path.basename(entry["path"]), className="chip-name"),
                html.Span(f" ⚠ {entry['error']}", className="chip-error")]
        cls = "chip chip-broken"
    else:
        folder = get_folder(entry["path"])
        body = [html.Span(folder.label, className="chip-name")]
        cls = "chip"
    body.append(html.Button(
        "×", id={"type": "chip-remove", "path": entry["path"]},
        className="chip-x", n_clicks=0))
    return html.Div(body, className=cls)


def _loaded(entries):
    """ExperimentFolder objects for the non-broken entries."""
    return [get_folder(e["path"]) for e in entries if not e.get("error")]


def create_app(initial_folders=()):
    app = Dash(__name__, title="RestQ Visualizer",
               suppress_callback_exceptions=True)

    entries = []
    for path in initial_folders:
        try:
            get_folder(path)
            entries.append({"path": path})
        except FolderError as e:
            entries.append({"path": path, "error": str(e)})

    app.layout = html.Div(className="app", children=[
        html.Div(className="topbar", children=[
            html.H1("RestQ Visualizer"),
            html.Div(className="controls", children=[
                html.Button("📂 Add folder…", id="folder-browse",
                            n_clicks=0, className="browse-btn"),
                dcc.Dropdown(
                    id="folder-picker",
                    options=[{"label": os.path.basename(p), "value": p}
                             for p in known_result_folders()],
                    placeholder="Add experiment folder…",
                    className="folder-picker"),
                dcc.Input(id="folder-path", type="text",
                          placeholder="…or paste a folder path",
                          debounce=True, className="folder-path"),
                dcc.Dropdown(id="experiment-select", clearable=False,
                             value=ALL_EXPERIMENTS,
                             className="experiment-select"),
                dcc.Input(id="window-ms", type="number", min=0, step=500,
                          value=0, placeholder="window ms",
                          className="window-ms"),
                dcc.Checklist(id="display-opts",
                              options=[{"label": " overlay series",
                                        "value": "overlays"},
                                       {"label": " run boundaries",
                                        "value": "boundaries"},
                                       {"label": " all host series",
                                        "value": "all-hosts"}],
                              value=["boundaries"], className="overlay-check"),
            ]),
            html.Div(id="chips", className="chips"),
        ]),
        dcc.Store(id="folders", data=entries),
        dcc.Tabs(id="tabs", value="timeline", className="tabs", children=[
            dcc.Tab(label="Timeline", value="timeline"),
            dcc.Tab(label="Requests", value="requests"),
            dcc.Tab(label="Queries", value="queries"),
            dcc.Tab(label="Correlation", value="correlation"),
            dcc.Tab(label="Compare runs", value="compare"),
        ]),

        dcc.Loading(
            id="tab-loading", type="circle", color="#2a78d6",
            delay_show=200, delay_hide=100,
            overlay_style={"visibility": "visible", "opacity": 0.35},
            children=html.Div(id="tab-content", className="tab-content"),
        ),
    ])

    # -- folder management --------------------------------------------------

    @app.callback(
        Output("folders", "data"),
        Output("folder-picker", "value"),
        Output("folder-path", "value"),
        Input("folder-picker", "value"),
        Input("folder-path", "value"),
        Input("folder-browse", "n_clicks"),
        Input({"type": "chip-remove", "path": ALL}, "n_clicks"),
        State("folders", "data"),
        prevent_initial_call=True)
    def update_folders(picked, typed, _browse, _removes, entries):
        trigger = ctx.triggered_id
        if isinstance(trigger, dict) and trigger.get("type") == "chip-remove":
            if any(ctx.triggered) and ctx.triggered[0]["value"]:
                entries = [e for e in entries
                           if e["path"] != trigger["path"]]
            return entries, no_update, no_update

        if trigger == "folder-browse":
            new_path = native_folder_dialog()
        elif trigger == "folder-picker":
            new_path = picked
        else:
            new_path = typed
        if not new_path:
            return no_update, no_update, no_update
        new_path = os.path.expanduser(new_path.strip())
        if any(e["path"] == new_path for e in entries):
            return no_update, None, ""
        try:
            get_folder(new_path)
            entries = entries + [{"path": new_path}]
        except FolderError as e:
            entries = entries + [{"path": new_path, "error": str(e)}]
        return entries, None, ""

    @app.callback(Output("chips", "children"), Input("folders", "data"))
    def render_chips(entries):
        return [_chip(e) for e in entries]

    @app.callback(
        Output("experiment-select", "options"),
        Output("experiment-select", "value"),
        Input("folders", "data"),
        State("experiment-select", "value"))
    def experiment_options(entries, current):
        ids = []
        for folder in _loaded(entries):
            for exp_id in folder.experiment_ids:
                if exp_id not in ids:
                    ids.append(exp_id)
        options = [ALL_EXPERIMENTS, ALL_WITHOUT_WARMUP] + ids
        value = current if current in options else ALL_EXPERIMENTS
        return options, value

    # -- tabs ----------------------------------------------------------------

    @app.callback(
        Output("tab-content", "children"),
        Input("tabs", "value"),
        Input("folders", "data"),
        Input("experiment-select", "value"),
        Input("window-ms", "value"),
        Input("display-opts", "value"))
    def render_tab(tab, entries, selection, window_ms, opts):
        folders = _loaded(entries)
        if not folders:
            return html.Div("Add an experiment folder to begin.",
                            className="empty-state")
        window_ms = int(window_ms) if window_ms else None
        opts = opts or []
        show_boundaries = "boundaries" in opts

        if tab == "timeline":
            fig = figures.timeline(folders, selection,
                                   show_overlays="overlays" in opts,
                                   window_ms=window_ms,
                                   dedupe_host="all-hosts" not in opts,
                                   show_boundaries=show_boundaries)
            return dcc.Graph(figure=fig, className="graph",
                             config={"displaylogo": False})

        if tab == "requests":
            if all(f.latencies is None or f.latencies.empty for f in folders):
                return html.Div(
                    "No per-request latency data in the loaded folders.",
                    className="empty-state")
            fig = figures.requests(folders, selection,
                                   show_boundaries=show_boundaries)
            return dcc.Graph(id="requests-graph", figure=fig,
                             className="graph",
                             config={"displaylogo": False})

        if tab == "queries":
            blocks = []
            for folder in folders:
                q = folder.queries
                if q is None or q.empty:
                    continue
                if selection == ALL_WITHOUT_WARMUP:
                    q = q[~q["experiment"].str.lower().str.contains("warmup")]
                elif selection != ALL_EXPERIMENTS:
                    q = q[q["experiment"] == selection]
                if q.empty:
                    continue
                q = q.sort_values("energy_j", ascending=False)
                table = dash_table.DataTable(
                    data=q.to_dict("records"),
                    columns=[{"name": c, "id": c} for c in q.columns],
                    style_as_list_view=True, page_size=25,
                    style_cell={"fontFamily": "inherit", "padding": "6px 12px",
                                "maxWidth": "480px", "overflow": "hidden",
                                "textOverflow": "ellipsis"},
                    style_header={"fontWeight": "600"})
                blocks.append(html.Div([html.H3(folder.label), table]))
            if not blocks:
                return html.Div(
                    "No per-query energy data in the loaded folders "
                    "(produced by macOS runs with guest sampling).",
                    className="empty-state")
            return html.Div(blocks)

        if tab == "correlation":
            blocks = []
            for folder in folders:
                fig = figures.correlation(folder, selection)
                blocks.append(html.Div([
                    html.H3(folder.label),
                    dcc.Graph(figure=fig, config={"displaylogo": False}),
                ]))
            return html.Div(blocks)

        # compare
        fig = figures.comparison(folders, selection)
        rows = figures.comparison_table_rows(folders, selection)
        table = dash_table.DataTable(
            data=rows,
            columns=[{"name": c, "id": c} for c in rows[0].keys()] if rows else [],
            style_as_list_view=True,
            style_cell={"fontFamily": "inherit", "padding": "6px 12px"},
            style_header={"fontWeight": "600"})
        return html.Div([
            dcc.Graph(figure=fig, config={"displaylogo": False}),
            html.H3("Totals"),
            table,
        ])

    # -- Requests tab: windowed full-resolution reload on zoom ---------------

    @app.callback(
        Output("requests-graph", "figure"),
        Input("requests-graph", "relayoutData"),
        State("folders", "data"),
        State("experiment-select", "value"),
        State("display-opts", "value"),
        prevent_initial_call=True)
    def rezoom_requests(relayout, entries, selection, opts):
        """SVG stays responsive by capping rendered points; on every x-zoom
        rebuild the figure with full resolution inside the visible window."""
        if not relayout:
            return no_update
        folders = _loaded(entries)
        if not folders:
            return no_update
        opts = opts or []
        show_boundaries = "boundaries" in opts

        if "xaxis.range[0]" in relayout and "xaxis.range[1]" in relayout:
            x0 = pd.Timestamp(relayout["xaxis.range[0]"]).value // 10 ** 6
            x1 = pd.Timestamp(relayout["xaxis.range[1]"]).value // 10 ** 6
            fig = figures.requests(folders, selection,
                                   show_boundaries=show_boundaries,
                                   x_range=(x0, x1))
            fig.update_xaxes(range=[relayout["xaxis.range[0]"],
                                    relayout["xaxis.range[1]"]])
            if ("yaxis.range[0]" in relayout
                    and "yaxis.range[1]" in relayout):
                fig.update_yaxes(range=[relayout["yaxis.range[0]"],
                                        relayout["yaxis.range[1]"]])
            return fig

        if relayout.get("xaxis.autorange"):  # double-click / reset axes
            return figures.requests(folders, selection,
                                    show_boundaries=show_boundaries)
        return no_update

    return app
