#!/usr/bin/env python3
"""RestQ Visualizer entry point.

Usage:
    python visualizer.py [experiment_folder ...]

Starts a local Dash server and opens the browser. Folders can also be added
from the UI (dropdown scans ~/Desktop/Results_rest_q_xml_benchmarks).
"""

import socket
import sys
import threading
import webbrowser

from restq_viz.app import create_app


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


def main():
    folders = sys.argv[1:]
    app = create_app(initial_folders=folders)
    port = free_port()
    url = f"http://127.0.0.1:{port}"
    print(f"RestQ Visualizer: {url}")
    threading.Timer(1.0, lambda: webbrowser.open(url)).start()
    app.run(host="127.0.0.1", port=port, debug=False)


if __name__ == "__main__":
    main()
