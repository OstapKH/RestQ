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


def _data_table(records, columns=None, page_size=25):
    columns = columns or (list(records[0]) if records else [])
    return dash_table.DataTable(
        data=records,
        columns=[{"name": column.replace("_", " "), "id": column}
                 for column in columns],
        style_as_list_view=True,
        page_size=page_size,
        style_cell={"fontFamily": "inherit", "padding": "8px 12px",
                    "textAlign": "left", "maxWidth": "520px",
                    "overflow": "hidden", "textOverflow": "ellipsis"},
        style_header={"fontWeight": "700", "textTransform": "uppercase",
                      "fontSize": "11px", "letterSpacing": "0.08em"})


def _study_query_ids(folders):
    ids = []
    for folder in folders:
        summary = folder.query_summary()
        for query_id in summary.get("query_id", []):
            if query_id not in ids:
                ids.append(query_id)
    return ids


def render_tab_content(tab, folders, selection, window_ms, opts, query_id=None):
    """Pure tab renderer used by the Dash callback and headless UI tests."""
    if not folders:
        return html.Div("Add an experiment folder to begin.", className="empty-state")
    window_ms = int(window_ms) if window_ms else None
    opts = opts or []
    show_boundaries = "boundaries" in opts

    if tab == "overview":
        blocks = []
        for folder in folders:
            summary = folder.query_summary()
            if summary.empty:
                continue
            verdict = folder.validation.get("valid")
            state = "pass" if verdict is True else "fail" if verdict is False else "unknown"
            blocks.append(html.Section(className="study-panel", children=[
                html.Div(className="panel-heading", children=[
                    html.Div([html.P("TPC-H SF1 STUDY", className="eyebrow"),
                              html.H2(folder.label)]),
                    html.Div("VALID" if verdict else "INVALID",
                             className=f"verdict verdict-{state}"),
                ]),
                dcc.Graph(figure=figures.query_energy_map(folder),
                          config={"displaylogo": False}),
                _data_table(summary.round(4).to_dict("records")),
            ]))
        if not blocks:
            return html.Div("This folder predates query-labelled study results.",
                            className="empty-state")
        return html.Div(blocks)

    if tab == "timeline":
        return dcc.Graph(
            figure=figures.timeline(folders, selection,
                                    show_overlays="overlays" in opts,
                                    window_ms=window_ms,
                                    dedupe_host="all-hosts" not in opts,
                                    show_boundaries=show_boundaries),
            className="graph", config={"displaylogo": False})

    if tab == "requests":
        if all(folder.latencies is None or folder.latencies.empty for folder in folders):
            return html.Div("No per-request latency data in the loaded folders.",
                            className="empty-state")
        return dcc.Graph(id="requests-graph",
                         figure=figures.requests(folders, selection,
                                                 show_boundaries=show_boundaries),
                         className="graph", config={"displaylogo": False})

    if tab == "parameters":
        query_ids = _study_query_ids(folders)
        chosen = query_id if query_id in query_ids else (query_ids[0] if query_ids else None)
        blocks = []
        for folder in folders:
            summary = folder.parameter_summary(chosen) if chosen else pd.DataFrame()
            if summary.empty:
                continue
            blocks.append(html.Section(className="study-panel", children=[
                html.Div(className="panel-heading", children=[
                    html.Div([html.P("PARAMETER LENS", className="eyebrow"),
                              html.H2(f"{folder.label} · {chosen}")]),
                    html.Span("Energy remains run-level", className="scope-note"),
                ]),
                dcc.Graph(figure=figures.run_stability(folder, chosen),
                          config={"displaylogo": False}),
                dcc.Graph(figure=figures.parameter_latency(folder, chosen),
                          config={"displaylogo": False}),
                _data_table(summary.round(4).to_dict("records")),
            ]))
        if not blocks:
            return html.Div("No labelled parameter-set data in this folder.",
                            className="empty-state")
        return html.Div(blocks)

    if tab == "queries":
        blocks = []
        for folder in folders:
            query_rows = folder.queries
            if query_rows is None or query_rows.empty:
                continue
            if selection == ALL_WITHOUT_WARMUP:
                query_rows = query_rows[~query_rows["experiment"].str.lower().str.contains("warmup")]
            elif selection != ALL_EXPERIMENTS:
                query_rows = query_rows[query_rows["experiment"] == selection]
            if not query_rows.empty:
                blocks.append(html.Div([html.H3(folder.label), _data_table(
                    query_rows.sort_values("energy_j", ascending=False).to_dict("records"))]))
        if not blocks:
            return html.Div("No per-query database attribution data in the loaded folders.",
                            className="empty-state")
        return html.Div(blocks)

    if tab == "validation":
        blocks = []
        for folder in folders:
            final = folder.validation or {}
            client = folder.client_validation or {}
            if not final and not client:
                continue
            rows = []
            for layer, report in (("final", final), ("client", client)):
                for check in report.get("checks", []):
                    rows.append({"layer": layer, "code": check.get("code"),
                                 "valid": check.get("valid"),
                                 "problems": "; ".join(check.get("problems") or [])})
            valid = final.get("valid", client.get("valid"))
            blocks.append(html.Section(className="study-panel", children=[
                html.Div(className="panel-heading", children=[
                    html.Div([html.P("EVIDENCE GATE", className="eyebrow"),
                              html.H2(folder.label)]),
                    html.Div("PASS" if valid else "FAIL",
                             className=f"verdict verdict-{'pass' if valid else 'fail'}"),
                ]),
                _data_table(rows),
                html.H3("Preflight targets"),
                (_data_table(folder.preflight_validation)
                 if folder.preflight_validation else
                 html.P("No preflight rows stored.", className="scope-note")),
            ]))
        if not blocks:
            return html.Div("No validation report in this legacy folder.",
                            className="empty-state")
        return html.Div(blocks)

    if tab == "correlation":
        return html.Div([html.Div([
            html.H3(folder.label),
            dcc.Graph(figure=figures.correlation(folder, selection),
                      config={"displaylogo": False}),
        ]) for folder in folders])

    fig = figures.comparison(folders, selection)
    rows = figures.comparison_table_rows(folders, selection)
    return html.Div([dcc.Graph(figure=fig, config={"displaylogo": False}),
                     html.H3("Totals"), _data_table(rows)])


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
            html.Div(className="brand", children=[
                html.P("RESTQ / EXPERIMENT OBSERVATORY", className="eyebrow"),
                html.H1("Query performance, with an energy ledger."),
            ]),
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
                dcc.Dropdown(id="query-select", clearable=False,
                             placeholder="Study query",
                             className="query-select"),
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
            dcc.Tab(label="Overview", value="overview"),
            dcc.Tab(label="Timeline", value="timeline"),
            dcc.Tab(label="Requests", value="requests"),
            dcc.Tab(label="Parameters", value="parameters"),
            dcc.Tab(label="Queries", value="queries"),
            dcc.Tab(label="Validation", value="validation"),
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

    @app.callback(
        Output("query-select", "options"),
        Output("query-select", "value"),
        Input("folders", "data"),
        State("query-select", "value"))
    def query_options(entries, current):
        ids = _study_query_ids(_loaded(entries))
        value = current if current in ids else (ids[0] if ids else None)
        return ids, value

    # -- tabs ----------------------------------------------------------------

    @app.callback(
        Output("tab-content", "children"),
        Input("tabs", "value"),
        Input("folders", "data"),
        Input("experiment-select", "value"),
        Input("window-ms", "value"),
        Input("display-opts", "value"),
        Input("query-select", "value"))
    def render_tab(tab, entries, selection, window_ms, opts, query_id):
        return render_tab_content(tab, _loaded(entries), selection,
                                  window_ms, opts, query_id)

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
