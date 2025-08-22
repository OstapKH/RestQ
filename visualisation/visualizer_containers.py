import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import json
import pandas as pd
import numpy as np
import os
from datetime import datetime, timedelta, timezone
import pytz
import matplotlib.pyplot as plt
from matplotlib.figure import Figure
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg, NavigationToolbar2Tk
from matplotlib.dates import DateFormatter
import matplotlib.dates as mdates
import matplotlib.ticker as mticker
import plotly.graph_objects as go
from plotly.subplots import make_subplots
import threading
import time

# TODO: Reload state of scrollbar when new data is loaded
# TODO: Change Experiment duration to actual one.
class LoadingWindow:
    """A loading window that shows progress while data is being loaded."""
    
    def __init__(self, parent):
        self.parent = parent
        self.window = None
        self.is_loading = False
        
    def show(self, message="Loading data..."):
        """Show the loading window with the specified message."""
        if self.window is not None:
            return
            
        self.is_loading = True
        self.window = tk.Toplevel(self.parent)
        self.window.title("Loading")
        self.window.geometry("300x150")
        self.window.resizable(False, False)
        
        # Center the window
        self.window.transient(self.parent)
        self.window.grab_set()
        
        # Make it modal
        self.window.focus_set()
        self.window.wait_visibility()
        self.window.grab_set()
        
        # Create content
        main_frame = ttk.Frame(self.window, padding="20")
        main_frame.pack(fill=tk.BOTH, expand=True)
        
        # Loading message
        self.message_label = ttk.Label(main_frame, text=message, font=("Arial", 12))
        self.message_label.pack(pady=(0, 20))
        
        # Progress bar
        self.progress = ttk.Progressbar(main_frame, mode='indeterminate')
        self.progress.pack(fill=tk.X, pady=(0, 10))
        self.progress.start()
        
        # Status label
        self.status_label = ttk.Label(main_frame, text="Please wait...", font=("Arial", 10))
        self.status_label.pack()
        
        # Update the window
        self.window.update()
        
    def update_message(self, message):
        """Update the loading message."""
        if self.window is not None:
            self.message_label.config(text=message)
            self.window.update()
            
    def update_status(self, status):
        """Update the status message."""
        if self.window is not None:
            self.status_label.config(text=status)
            self.window.update()
            
    def hide(self):
        """Hide the loading window."""
        if self.window is not None:
            self.is_loading = False
            self.progress.stop()
            self.window.destroy()
            self.window = None

class ExperimentVisualizer:
    def __init__(self, root):
        self.root = root
        self.root.title("Experiment Visualizer")
        
        # Initialize loading window
        self.loading_window = LoadingWindow(self.root)
        
        self.data = None # This will hold data from combined_results.json
        self.experiment_info = None # This will hold data from experiment_info.json
        self.mongodb_api_data = None # Raw data from mongodump-API-server.json
        self.mongodb_db_data = None # Raw data from mongodump-DB-server.json
        
        self.api_container_id = None
        self.db_container_id = None
        self.display_timezone = pytz.timezone('Europe/Helsinki')
        
        self.show_host_api_var = tk.BooleanVar(value=True)
        self.show_host_db_var = tk.BooleanVar(value=True)
        
        self.show_scaphandre_api_var = tk.BooleanVar(value=True)
        self.show_scaphandre_db_var = tk.BooleanVar(value=True)
        
        self.current_tab = "plot"
        
        self.main_frame = ttk.Frame(root)
        self.main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        self.setup_menu()
        self.setup_top_controls()
        self.setup_bottom_controls()
        self.setup_main_content_area()
        
        self.status_var = tk.StringVar()
        self.status_var.set("Ready. Please load a data folder.")
        self.status_bar = ttk.Label(root, textvariable=self.status_var, relief=tk.SUNKEN, anchor=tk.W)
        self.status_bar.pack(side=tk.BOTTOM, fill=tk.X)
    
    def setup_menu(self):
        menu_bar = tk.Menu(self.root)
        file_menu = tk.Menu(menu_bar, tearoff=0)
        file_menu.add_command(label="Load Data Folder", command=self.load_folder) # Changed
        file_menu.add_separator()
        file_menu.add_command(label="Exit", command=self.root.quit)
        menu_bar.add_cascade(label="File", menu=file_menu)
        
        view_menu = tk.Menu(menu_bar, tearoff=0)
        view_menu.add_command(label="Reset Plot View", command=self.reset_plot_view)
        menu_bar.add_cascade(label="View", menu=view_menu)
        
        self.root.config(menu=menu_bar)
    
    def setup_top_controls(self):
        """Setup the top area with tab buttons, load data button, and experiment selection"""
        top_frame = ttk.Frame(self.main_frame)
        top_frame.pack(fill=tk.X, pady=(0, 10))
        
        tab_frame = ttk.Frame(top_frame)
        tab_frame.pack(fill=tk.X, pady=(0, 10))
        
        style = ttk.Style()
        style.configure('Tab.TButton', font=('Arial', 10, 'bold'))
        
        self.plot_tab_button = ttk.Button(tab_frame, text="Plot Visualization", 
                                         command=lambda: self.switch_tab("plot"), 
                                         style='Tab.TButton')
        self.plot_tab_button.pack(side=tk.LEFT, padx=(0, 5))
        
        self.info_tab_button = ttk.Button(tab_frame, text="Experiment Info", 
                                         command=lambda: self.switch_tab("info"), 
                                         style='Tab.TButton')
        self.info_tab_button.pack(side=tk.LEFT, padx=(0, 20))
        
        style.configure('Big.TButton', font=('Arial', 11, 'bold'))
        load_button = ttk.Button(tab_frame, text="Load Data Folder", command=self.load_folder, style='Big.TButton') # Changed
        load_button.pack(side=tk.RIGHT)
        
        exp_frame = ttk.Frame(top_frame)
        exp_frame.pack(fill=tk.X, pady=(0, 5))
        
        ttk.Label(exp_frame, text="Select Experiment:", font=("Arial", 10, "bold")).pack(side=tk.LEFT, padx=5)
        self.experiment_var = tk.StringVar()
        self.experiment_selector = ttk.Combobox(exp_frame, textvariable=self.experiment_var, state="readonly", width=30)
        self.experiment_selector.pack(side=tk.LEFT, padx=5)
        self.experiment_selector.bind("<<ComboboxSelected>>", self.on_experiment_selected)
        
        refresh_button = ttk.Button(exp_frame, text="Refresh", command=self.force_plot_update)
        refresh_button.pack(side=tk.LEFT, padx=5)
        
        self.update_tab_buttons()
    
    def setup_main_content_area(self):
        """Setup the main content area that switches between plot and experiment info"""
        self.content_frame = ttk.Frame(self.main_frame)
        self.content_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 0))
        
        self.setup_plot_frame()
        
        self.setup_experiment_info_frame()
        
        self.switch_tab("plot")
    
    def setup_plot_frame(self):
        """Setup the plot visualization frame"""
        self.plot_frame = ttk.Frame(self.content_frame)
        
        mongodb_frame = ttk.LabelFrame(self.plot_frame, text="PowerAPI Energy Data")
        mongodb_frame.pack(fill=tk.X, padx=5, pady=2)
        
        api_frame = ttk.Frame(mongodb_frame)
        api_frame.pack(fill=tk.X, padx=3, pady=2)
        
        self.mongodb_api_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(api_frame, text="API Energy",
                       variable=self.mongodb_api_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        self.mongodb_api_file_var = tk.StringVar(value="No file loaded")
        # Changed from Entry with browse button to a read-only Entry displaying filename
        ttk.Entry(api_frame, textvariable=self.mongodb_api_file_var, width=25, state="readonly").pack(side=tk.LEFT, padx=3)
        
        self.mongodb_api_target_var = tk.StringVar()
        self.mongodb_api_target_combo = ttk.Combobox(api_frame, textvariable=self.mongodb_api_target_var, 
                                                    state="readonly", width=25)
        self.mongodb_api_target_combo.pack(side=tk.LEFT, padx=3)
        self.mongodb_api_target_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        db_frame = ttk.Frame(mongodb_frame)
        db_frame.pack(fill=tk.X, padx=3, pady=2)
        
        self.mongodb_db_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(db_frame, text="DB Energy",
                       variable=self.mongodb_db_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        self.mongodb_db_file_var = tk.StringVar(value="No file loaded")
        # Changed from Entry with browse button to a read-only Entry displaying filename
        ttk.Entry(db_frame, textvariable=self.mongodb_db_file_var, width=25, state="readonly").pack(side=tk.LEFT, padx=3)
        
        self.mongodb_db_target_var = tk.StringVar()
        self.mongodb_db_target_combo = ttk.Combobox(db_frame, textvariable=self.mongodb_db_target_var, 
                                                   state="readonly", width=25)
        self.mongodb_db_target_combo.pack(side=tk.LEFT, padx=3)
        self.mongodb_db_target_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        plot_area_frame = ttk.Frame(self.plot_frame)
        plot_area_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        self.fig = Figure(figsize=(8, 4), dpi=100)
        self.canvas = FigureCanvasTkAgg(self.fig, master=plot_area_frame)
        self.canvas.get_tk_widget().pack(fill=tk.BOTH, expand=True)
        
        toolbar_frame = ttk.Frame(plot_area_frame)
        toolbar_frame.pack(fill=tk.X)
        self.toolbar = NavigationToolbar2Tk(self.canvas, toolbar_frame)
        self.toolbar.update()
    
    def setup_experiment_info_frame(self):
        """Setup the experiment information frame"""
        self.info_frame = ttk.Frame(self.content_frame)
        
        ttk.Label(self.info_frame, text="Experiment Details", font=("Arial", 14, "bold")).pack(pady=10)
        
        frame_canvas = ttk.Frame(self.info_frame)
        frame_canvas.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        canvas = tk.Canvas(frame_canvas)
        self.details_frame = ttk.Frame(canvas)
        
        v_scrollbar = ttk.Scrollbar(frame_canvas, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=v_scrollbar.set)
        
        h_scrollbar = ttk.Scrollbar(frame_canvas, orient="horizontal", command=canvas.xview)
        canvas.configure(xscrollcommand=h_scrollbar.set)
        
        v_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        h_scrollbar.pack(side=tk.BOTTOM, fill=tk.X)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        canvas_frame = canvas.create_window((0, 0), window=self.details_frame, anchor="nw")
        
        def configure_canvas(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
        
        self.details_frame.bind("<Configure>", configure_canvas)
    
    def setup_bottom_controls(self):
        """Setup the bottom controls that are always visible"""
        bottom_frame = ttk.LabelFrame(self.main_frame, text="Plot Controls", relief=tk.RAISED, borderwidth=2)
        bottom_frame.pack(side=tk.BOTTOM, fill=tk.X, pady=(5, 5))
        bottom_frame.configure(height=100)
        
        controls_frame = ttk.Frame(bottom_frame)
        controls_frame.pack(fill=tk.X, padx=5, pady=5)
        
        scrollable_control_frame = controls_frame
        
        row1_frame = ttk.Frame(scrollable_control_frame)
        row1_frame.pack(fill=tk.X, pady=2)
        
        row2_frame = ttk.Frame(scrollable_control_frame)
        row2_frame.pack(fill=tk.X, pady=2)
        
        ttk.Button(row1_frame, text="Refresh Plot", command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        
        ttk.Label(row1_frame, text="Plot Type:").pack(side=tk.LEFT, padx=(10, 3))
        self.plot_type_var = tk.StringVar(value="Line")
        plot_type_combo = ttk.Combobox(row1_frame, textvariable=self.plot_type_var, 
                                      values=["Line", "Bar", "Scatter"], state="readonly", width=8)
        plot_type_combo.pack(side=tk.LEFT, padx=3)
        plot_type_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        ttk.Label(row1_frame, text="Data Source:").pack(side=tk.LEFT, padx=(10, 3))
        self.data_source_var = tk.StringVar(value="Energy")
        data_source_combo = ttk.Combobox(row1_frame, textvariable=self.data_source_var, 
                                      values=["Energy", "Energy Comparative", "Energy Consumed", "Latency", "Throughput"], state="readonly", width=12)
        data_source_combo.pack(side=tk.LEFT, padx=3)
        data_source_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        ttk.Label(row2_frame, text="Accumulation:").pack(side=tk.LEFT, padx=3)
        self.accumulation_var = tk.StringVar(value="Simple")
        self.accumulation_combo = ttk.Combobox(row2_frame, textvariable=self.accumulation_var, 
                                      values=["Simple", "Accumulated"], state="readonly", width=8)
        self.accumulation_combo.pack(side=tk.LEFT, padx=3)
        self.accumulation_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        self.update_accumulation_options()
        
        ttk.Label(row2_frame, text="Window Size (ms):").pack(side=tk.LEFT, padx=(10, 3))
        self.window_size_var = tk.StringVar(value="100")
        window_size_entry = ttk.Entry(row2_frame, textvariable=self.window_size_var, width=6)
        window_size_entry.pack(side=tk.LEFT, padx=3)
        window_size_entry.bind("<Return>", lambda e: self.force_plot_update())
        
        ttk.Checkbutton(row2_frame, text="Scaph. API Server Data", 
                       variable=self.show_scaphandre_api_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=(10, 3))
        ttk.Checkbutton(row2_frame, text="Scaph. DB Server Data", 
                       variable=self.show_scaphandre_db_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        ttk.Checkbutton(row2_frame, text="Scaph. Host machine API Data", 
                       variable=self.show_host_api_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        ttk.Checkbutton(row2_frame, text="Scaph. Host machine DB Data", 
                       variable=self.show_host_db_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
    
    def switch_tab(self, tab_name):
        """Switch between plot and experiment info tabs"""
        self.current_tab = tab_name
        
        self.plot_frame.pack_forget()
        self.info_frame.pack_forget()
        
        if tab_name == "plot":
            self.plot_frame.pack(fill=tk.BOTH, expand=True)
        elif tab_name == "info":
            self.info_frame.pack(fill=tk.BOTH, expand=True)
            self.refresh_experiment_info()
        
        self.update_tab_buttons()
    
    def update_tab_buttons(self):
        """Update the appearance of tab buttons to show which one is active"""
        style = ttk.Style()
        
        style.configure('TabActive.TButton', font=('Arial', 10, 'bold'), foreground='blue')
        style.configure('TabInactive.TButton', font=('Arial', 10, 'normal'), foreground='black')
        
        self.plot_tab_button.configure(style='TabInactive.TButton')
        self.info_tab_button.configure(style='TabInactive.TButton')
        
        if self.current_tab == "plot":
            self.plot_tab_button.configure(style='TabActive.TButton')
        elif self.current_tab == "info":
            self.info_tab_button.configure(style='TabActive.TButton')
    
    def refresh_experiment_info(self):
        """Refresh the experiment info display"""
        if self.current_tab == "info":
            self.on_experiment_selected(None)

    def reset_plot_view(self):
        """Reset the plot view to default - Placeholder for future implementation"""
        self.status_var.set("Plot view reset functionality will be implemented in the future")

    def load_folder(self):
        """Load all necessary data files from a selected folder."""
        folder_path = filedialog.askdirectory(title="Select Experiment Data Folder")
        if not folder_path:
            return

        # Show loading window
        self.loading_window.show(f"Loading data from {os.path.basename(folder_path)}...")
        
        # Start loading in a separate thread
        loading_thread = threading.Thread(target=self._load_folder_threaded, args=(folder_path,))
        loading_thread.daemon = True
        loading_thread.start()

    def _load_folder_threaded(self, folder_path):
        """Load data files in a separate thread to prevent GUI freezing."""
        try:
            self.loading_window.update_status("Preparing to load data...")
            
            combined_results_path = os.path.join(folder_path, "combined_results.json")
            experiment_info_path = os.path.join(folder_path, "experiment_info.json")
            powerapi_api_path = os.path.join(folder_path, "mongodump-API-server.json")
            powerapi_db_path = os.path.join(folder_path, "mongodump-DB-server.json")

            # Reset existing data
            self.data = None
            self.experiment_info = None
            self.mongodb_api_data = None
            self.mongodb_db_data = None
            self.api_container_id = None
            self.db_container_id = None
            
            # Update UI elements in main thread
            self.root.after(0, self._reset_ui_elements)
            
            all_files_loaded = True
            
            # Load experiment_info.json first to get container IDs
            self.loading_window.update_status("Loading experiment info...")
            if os.path.exists(experiment_info_path):
                if not self._load_experiment_info_json(experiment_info_path):
                    all_files_loaded = False
            else:
                self.root.after(0, lambda: messagebox.showwarning("Missing File", f"'{os.path.basename(experiment_info_path)}' not found in selected folder. Cannot extract container IDs for PowerAPI filtering."))
                all_files_loaded = False 

            # Load combined_results.json
            self.loading_window.update_status("Loading benchmark results...")
            if os.path.exists(combined_results_path):
                if not self._load_combined_results_json(combined_results_path):
                    all_files_loaded = False
            else:
                self.root.after(0, lambda: messagebox.showerror("Missing File", f"'{os.path.basename(combined_results_path)}' not found in selected folder. Benchmark results will be unavailable."))
                all_files_loaded = False

            # Load PowerAPI API data
            self.loading_window.update_status("Loading PowerAPI API data...")
            if os.path.exists(powerapi_api_path):
                self.root.after(0, lambda: self.mongodb_api_file_var.set(os.path.basename(powerapi_api_path)))
                if not self._load_mongodb_data(powerapi_api_path, "api"):
                    self.root.after(0, lambda: self.mongodb_api_var.set(False))
            else:
                self.root.after(0, lambda: messagebox.showwarning("Missing File", f"'{os.path.basename(powerapi_api_path)}' not found. PowerAPI API data will not be available."))
                self.root.after(0, lambda: self.mongodb_api_var.set(False))

            # Load PowerAPI DB data
            self.loading_window.update_status("Loading PowerAPI DB data...")
            if os.path.exists(powerapi_db_path):
                self.root.after(0, lambda: self.mongodb_db_file_var.set(os.path.basename(powerapi_db_path)))
                if not self._load_mongodb_data(powerapi_db_path, "db"):
                    self.root.after(0, lambda: self.mongodb_db_var.set(False))
            else:
                self.root.after(0, lambda: messagebox.showwarning("Missing File", f"'{os.path.basename(powerapi_db_path)}' not found. PowerAPI DB data will not be available."))
                self.root.after(0, lambda: self.mongodb_db_var.set(False))

            # Finalize loading
            self.loading_window.update_status("Finalizing...")
            
            if all_files_loaded and self.data:
                self.root.after(0, lambda: self.status_var.set(f"Successfully loaded data from {os.path.basename(folder_path)}"))
                self.root.after(0, lambda: self.on_experiment_selected(None))
            elif self.data:
                self.root.after(0, lambda: self.status_var.set(f"Loaded main data, but some files from {os.path.basename(folder_path)} had issues. Check warnings."))
                self.root.after(0, lambda: self.on_experiment_selected(None))
            else:
                self.root.after(0, lambda: self.status_var.set(f"Failed to load essential data from {os.path.basename(folder_path)}. Please check the folder content."))
                self.root.after(0, lambda: self._clear_plot_and_info())

        except Exception as e:
            self.root.after(0, lambda: messagebox.showerror("Error", f"An error occurred while loading data: {str(e)}"))
            self.root.after(0, lambda: self.status_var.set("Error loading data"))
        finally:
            # Hide loading window
            self.root.after(0, self.loading_window.hide)

    def _reset_ui_elements(self):
        """Reset UI elements in the main thread."""
        self.mongodb_api_file_var.set("No file loaded")
        self.mongodb_db_file_var.set("No file loaded")
        self.mongodb_api_target_combo['values'] = []
        self.mongodb_api_target_var.set("")
        self.mongodb_db_target_combo['values'] = []
        self.mongodb_db_target_var.set("")
        self.experiment_selector['values'] = []
        self.experiment_var.set("")
        
        for widget in self.details_frame.winfo_children():
            widget.destroy()
        self.fig.clear()
        self.canvas.draw()

    def _clear_plot_and_info(self):
        """Clear plot and info in the main thread."""
        self.fig.clear()
        self.canvas.draw()
        for widget in self.details_frame.winfo_children():
            widget.destroy()
    
    def _load_experiment_info_json(self, file_path):
        """Loads experiment_info.json and extracts container IDs."""
        try:
            with open(file_path, 'r') as file:
                self.experiment_info = json.load(file)
            
            if not isinstance(self.experiment_info, dict):
                messagebox.showerror("Error", f"Invalid format for '{os.path.basename(file_path)}': Expected a dictionary.")
                self.experiment_info = None
                return False

            self.api_container_id = self.experiment_info.get("api_container_id")
            self.db_container_id = self.experiment_info.get("db_container_id")

            if not self.api_container_id or not self.db_container_id:
                messagebox.showwarning("Warning", f"'{os.path.basename(file_path)}' missing 'api_container_id' or 'db_container_id'. PowerAPI filtering might be inaccurate.")
                print(f"WARNING: Missing 'api_container_id' or 'db_container_id' in '{file_path}'. API={self.api_container_id}, DB={self.db_container_id}") # DEBUG
            else:
                print(f"DEBUG: Loaded container IDs from '{file_path}': API={self.api_container_id}, DB={self.db_container_id}") # DEBUG
            
            return True
        except json.JSONDecodeError as e:
            messagebox.showerror("Error", f"Failed to parse JSON from '{os.path.basename(file_path)}': {e}")
            self.experiment_info = None
            return False
        except Exception as e:
            messagebox.showerror("Error", f"An error occurred loading '{os.path.basename(file_path)}': {e}")
            self.experiment_info = None
            return False

    def _load_combined_results_json(self, file_path):
        """Loads combined_results.json, formerly 'load_data'."""
        try:
            print(f"Attempting to load JSON from: {file_path}") # DEBUG
            with open(file_path, 'r') as file:
                self.data = json.load(file)
            
            print(f"Successfully loaded JSON. Data type: {type(self.data)}") # DEBUG
            if isinstance(self.data, dict):
                 print(f"Top-level keys found: {list(self.data.keys())}") # DEBUG
            else:
                 print("Loaded data is not a dictionary.") # DEBUG
                 
            expected_keys = ["api_server_energy", "db_server_energy", "benchmark_results"]
                          
            missing_keys = [key for key in expected_keys if key not in self.data or self.data[key] is None]
            
            if missing_keys:
                 print(f"ERROR: The file is missing the following expected top-level keys: {missing_keys}") # DEBUG
                 messagebox.showerror("Error", f"The file does not have the expected structure or is missing required keys: {', '.join(missing_keys)}")
                 self.data = None
                 return False
            
            print("File structure check passed. Found all expected keys.") # DEBUG
            
            # Container info is now loaded from experiment_info.json, no longer parsed here.
            
            if "benchmark_results" in self.data and isinstance(self.data["benchmark_results"], dict) and "experiments" in self.data["benchmark_results"] and isinstance(self.data["benchmark_results"]["experiments"], dict):
                experiments = self.data["benchmark_results"]["experiments"]
                
                # Sort experiments by their start timestamp
                experiment_ids = self._sort_experiments_by_start_time(experiments)
                print(f"Found {len(experiment_ids)} experiments in benchmark_results.") # DEBUG
                
                all_data_option = "All data"
                all_experiments_option = "All Experiments"
                all_experiments_no_warmup_option = "All Experiments without warmup"
                self.experiment_selector['values'] = [all_data_option, all_experiments_option, all_experiments_no_warmup_option] + experiment_ids
                
                self.experiment_selector.current(0)
                self.experiment_var.set(all_data_option)
                
                self.status_var.set(f"Loaded {len(experiment_ids)} experiments from {os.path.basename(file_path)}")
                return True
            else:
                print("ERROR: 'benchmark_results' key found, but it does not contain a valid 'experiments' dictionary.") # DEBUG
                err_msg = "Unknown structure issue with 'benchmark_results'."
                if "benchmark_results" not in self.data:
                    err_msg = "'benchmark_results' key is missing."
                elif not isinstance(self.data["benchmark_results"], dict):
                    err_msg = "'benchmark_results' is not a dictionary."
                elif "experiments" not in self.data["benchmark_results"]:
                    err_msg = "'experiments' key is missing within 'benchmark_results'."
                elif not isinstance(self.data["benchmark_results"]["experiments"], dict):
                    err_msg = "'experiments' within 'benchmark_results' is not a dictionary."
                    
                messagebox.showerror("Error", f"No valid experiments found in the data file ({os.path.basename(file_path)}).\nReason: {err_msg}")
                self.status_var.set("No experiments found in the data file.")
                self.experiment_selector['values'] = []
                self.experiment_var.set("")
                self.data = None
                return False
                
        except json.JSONDecodeError as e:
            print(f"ERROR: Failed to decode JSON from file: {file_path}\nError: {e}") # DEBUG
            messagebox.showerror("Error", f"Failed to parse JSON file ({os.path.basename(file_path)}): {str(e)}\nPlease check the file content.")
            self.status_var.set("Error loading data file: Invalid JSON")
            self.data = None
            return False
        except Exception as e:
            print(f"ERROR: An unexpected error occurred during data loading: {e}") # DEBUG
            import traceback
            traceback.print_exc()
            messagebox.showerror("Error", f"Failed to load data from {os.path.basename(file_path)}: {str(e)}")
            self.status_var.set("Error loading data file")
            self.data = None
            return False
    
    def on_experiment_selected(self, event):
        if not self.data and not self.experiment_info: # Ensure at least some data is loaded
            return
            
        selected_experiment = self.experiment_var.get()
        
        for widget in self.details_frame.winfo_children():
            widget.destroy()
            
        row = 0
        
        # Display general experiment info from experiment_info.json if available
        if self.experiment_info:
            ttk.Label(self.details_frame, text="Experiment Configuration:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
            row += 1
            for key, value in self.experiment_info.items():
                ttk.Label(self.details_frame, text=f"{key.replace('_', ' ').title()}:").grid(row=row, column=0, sticky=tk.W, padx=15, pady=2)
                ttk.Label(self.details_frame, text=f"{value}").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                row += 1
            row += 1 # Add a separator space
            ttk.Separator(self.details_frame, orient='horizontal').grid(row=row, column=0, columnspan=2, sticky='ew', padx=5, pady=5)
            row += 1
        else:
            ttk.Label(self.details_frame, text="Experiment Configuration: (Not Loaded)", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
            row += 1
            ttk.Separator(self.details_frame, orient='horizontal').grid(row=row, column=0, columnspan=2, sticky='ew', padx=5, pady=5)
            row += 1
            
        if not self.data: # If combined_results.json was not loaded
            ttk.Label(self.details_frame, text="No benchmark results data available.", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
            self.status_var.set("No benchmark results data loaded.")
            self.fig.clear()
            self.canvas.draw()
            return
            
        if selected_experiment == "All data":
            if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
                experiments = self.data["benchmark_results"]["experiments"]
                
                ttk.Label(self.details_frame, text="Benchmark Results Summary (All Data):", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
                row += 1
                
                ttk.Label(self.details_frame, text="Displaying all available energy data without time filtering", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Total Experiments: {len(experiments)}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                api_energy_count = len(self.data.get("api_server_energy", []))
                db_energy_count = len(self.data.get("db_server_energy", []))
                
                ttk.Label(self.details_frame, text=f"Scaphandre API Server Energy Entries: {api_energy_count}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Scaphandre DB Server Energy Entries: {db_energy_count}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                self.update_plot()
                return
        
        elif selected_experiment == "All Experiments":
            if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
                experiments = self.data["benchmark_results"]["experiments"]
                
                # Display a summary of all experiments
                ttk.Label(self.details_frame, text="Benchmark Results Summary (All Experiments):", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Total Experiments: {len(experiments)}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                # Add a brief overview of each experiment in sorted order
                sorted_experiment_ids = self._sort_experiments_by_start_time(experiments)
                for exp_id in sorted_experiment_ids:
                    exp_data = experiments[exp_id]
                    ttk.Label(self.details_frame, text=f"Experiment: {exp_id}", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                    row += 1
                    
                    runs_count = len(exp_data.get("runs", []))
                    ttk.Label(self.details_frame, text=f"Runs: {runs_count}", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                    row += 1
                    
                    if "duration_seconds" in exp_data:
                        ttk.Label(self.details_frame, text=f"Duration: {exp_data['duration_seconds']} seconds", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    if "requests_per_second" in exp_data:
                        ttk.Label(self.details_frame, text=f"Rate: {exp_data['requests_per_second']} req/s", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    if "connections" in exp_data:
                        ttk.Label(self.details_frame, text=f"Connections: {exp_data['connections']}", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    # Add a separator
                    ttk.Separator(self.details_frame, orient='horizontal').grid(row=row, column=0, columnspan=2, sticky='ew', padx=5, pady=5)
                    row += 1
                
                # Update the plot with all experiments
                self.update_plot()
                return
        
        elif selected_experiment == "All Experiments without warmup":
            if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
                experiments = self.data["benchmark_results"]["experiments"]
                
                # Filter out experiments with "warmup" in their name
                filtered_experiments = {exp_id: exp_data for exp_id, exp_data in experiments.items() 
                                     if "warmup" not in exp_id.lower()}
                
                # Display a summary of filtered experiments
                ttk.Label(self.details_frame, text="Benchmark Results Summary (All Experiments without warmup):", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Total Experiments: {len(filtered_experiments)} (filtered from {len(experiments)})", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                # Add a brief overview of each filtered experiment in sorted order
                sorted_experiment_ids = self._sort_experiments_by_start_time(filtered_experiments)
                for exp_id in sorted_experiment_ids:
                    exp_data = filtered_experiments[exp_id]
                    ttk.Label(self.details_frame, text=f"Experiment: {exp_id}", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                    row += 1
                    
                    runs_count = len(exp_data.get("runs", []))
                    ttk.Label(self.details_frame, text=f"Runs: {runs_count}", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                    row += 1
                    
                    if "duration_seconds" in exp_data:
                        ttk.Label(self.details_frame, text=f"Duration: {exp_data['duration_seconds']} seconds", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    if "requests_per_second" in exp_data:
                        ttk.Label(self.details_frame, text=f"Rate: {exp_data['requests_per_second']} req/s", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    if "connections" in exp_data:
                        ttk.Label(self.details_frame, text=f"Connections: {exp_data['connections']}", font=("Arial", 10)).grid(row=row, column=0, sticky=tk.W, padx=20, pady=2)
                        row += 1
                    
                    # Add a separator
                    ttk.Separator(self.details_frame, orient='horizontal').grid(row=row, column=0, columnspan=2, sticky='ew', padx=5, pady=5)
                    row += 1
                
                # Update the plot with filtered experiments
                self.update_plot()
                return
        
        # Regular single experiment selection
        experiment = self.data["benchmark_results"]["experiments"].get(selected_experiment, {})
        if not experiment:
            return
            
        # Show experiment details
        ttk.Label(self.details_frame, text="Benchmark Results Details:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
        row += 1
        
        # Show duration
        if "duration_seconds" in experiment:
            ttk.Label(self.details_frame, text="Duration:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
            ttk.Label(self.details_frame, text=f"{experiment['duration_seconds']} seconds").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
            row += 1
            
        # Show requests per second
        if "requests_per_second" in experiment:
            ttk.Label(self.details_frame, text="Requests/sec:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
            ttk.Label(self.details_frame, text=f"{experiment['requests_per_second']}").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
            row += 1
            
        # Show pause between runs
        if "pause_between_runs_ms" in experiment:
            ttk.Label(self.details_frame, text="Pause between runs:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
            ttk.Label(self.details_frame, text=f"{experiment['pause_between_runs_ms']} ms").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
            row += 1
            
        # Show number of runs
        if "runs_configured" in experiment:
            ttk.Label(self.details_frame, text="Runs configured:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
            ttk.Label(self.details_frame, text=f"{experiment['runs_configured']}").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
            row += 1
            
        # Show connections
        if "connections" in experiment:
            ttk.Label(self.details_frame, text="Connections:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
            ttk.Label(self.details_frame, text=f"{experiment['connections']}").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
            row += 1
            
        # Show endpoint probabilities
        if "probabilities" in experiment:
            row += 1
            ttk.Label(self.details_frame, text="Endpoint Probabilities:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
            row += 1
            
            for endpoint, prob in experiment["probabilities"].items():
                ttk.Label(self.details_frame, text=f"{endpoint}:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
                ttk.Label(self.details_frame, text=f"{prob * 100:.1f}%").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                row += 1
        
        # Show run summary if runs exist
        if "runs" in experiment and experiment["runs"]:
            row += 1
            ttk.Label(self.details_frame, text="Run Summary:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
            row += 1
            
            for i, run in enumerate(experiment["runs"]):
                ttk.Label(self.details_frame, text=f"Run {i+1}:").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
                
                if "start_timestamp" in run:
                    dt = self._convert_to_eet(run["start_timestamp"])
                    ttk.Label(self.details_frame, text=f"Started: {dt.strftime('%Y-%m-%d %H:%M:%S')} (EET)").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                    row += 1
                
                if "successful_requests" in run and "total_requests" in run:
                    success_rate = (run["successful_requests"] / run["total_requests"]) * 100 if run["total_requests"] > 0 else 0
                    ttk.Label(self.details_frame, text=f"{success_rate:.1f}% success ({run['successful_requests']}/{run['total_requests']})").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                elif "throughput" in run:
                    ttk.Label(self.details_frame, text=f"Throughput: {run['throughput']:.2f} req/s").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                
                row += 1
                
                if "latency_distribution" in run:
                    ttk.Label(self.details_frame, text="  Latency (ms):").grid(row=row, column=0, sticky=tk.W, padx=5, pady=2)
                    latency_info = run["latency_distribution"]
                    median = latency_info.get("median_latency_ns", 0) / 1_000_000  # Convert ns to ms
                    p95 = latency_info.get("percentiles", {}).get("p95", 0) / 1_000_000
                    p99 = latency_info.get("percentiles", {}).get("p99", 0) / 1_000_000
                    ttk.Label(self.details_frame, text=f"Median: {median:.2f}, P95: {p95:.2f}, P99: {p99:.2f}").grid(row=row, column=1, sticky=tk.W, padx=5, pady=2)
                    row += 1
        
        self.update_plot()
        
        self.status_var.set(f"Displaying data for experiment: {selected_experiment}")
    
    def update_plot(self):
        """Update the plot with selected experiment data"""
        if not self.data or not self.experiment_var.get():
            self.fig.clear()
            self.canvas.draw()
            self.status_var.set("No benchmark data loaded or no experiment selected for plotting.")
            return
        
        experiment_id = self.experiment_var.get()
        self.status_var.set(f"Updating plot for: {experiment_id}")
        self.root.update_idletasks()
        
        self.fig.clear()
        
        # Get the experiment time boundaries
        if experiment_id == "All data":
            # No time filtering for "All data" option
            start_time_ms, end_time_ms = None, None
            experiment_ids = list(self.data["benchmark_results"]["experiments"].keys())
        elif experiment_id == "All Experiments":
            # Get the combined time boundaries for all experiments
            start_time_ms, end_time_ms = self.get_all_experiments_time_boundaries()
            
            # Get the list of all experiment IDs
            experiment_ids = list(self.data["benchmark_results"]["experiments"].keys())
        elif experiment_id == "All Experiments without warmup":
            # Get the time boundaries for experiments without warmup
            start_time_ms, end_time_ms = self.get_experiments_without_warmup_time_boundaries()
            
            # Get the list of all experiment IDs, filtering out those with "warmup" in the name
            all_experiment_ids = list(self.data["benchmark_results"]["experiments"].keys())
            experiment_ids = [exp_id for exp_id in all_experiment_ids if "warmup" not in exp_id.lower()]
        else:
            # Get boundaries for the single selected experiment
            start_time_ms, end_time_ms = self.get_experiment_time_boundaries(experiment_id)
            experiment_ids = [experiment_id]
            
        # Debug info - show time boundaries in human-readable format
        if start_time_ms is not None and end_time_ms is not None:
            start_time_eet = self._convert_to_eet(start_time_ms).strftime('%Y-%m-%d %H:%M:%S')
            end_time_eet = self._convert_to_eet(end_time_ms).strftime('%Y-%m-%d %H:%M:%S')
            self.status_var.set(f"Time bounds: {start_time_eet} to {end_time_eet} (EET)")
            self.root.update_idletasks()
        else:
            if experiment_id == "All data":
                self.status_var.set("Showing all available energy data without time filtering")
            else:
                self.status_var.set("No time boundaries available for selected experiment")
            self.root.update_idletasks()
        
        api_energy_data = self.data.get("api_server_energy", [])
        db_energy_data = self.data.get("db_server_energy", [])
        
        use_strict_filtering = (experiment_id != "All Experiments" and experiment_id != "All data" and experiment_id != "All Experiments without warmup")
        
        filtered_api_data = self._filter_energy_data_by_time(api_energy_data, start_time_ms, end_time_ms, use_strict_filtering)
        filtered_db_data = self._filter_energy_data_by_time(db_energy_data, start_time_ms, end_time_ms, use_strict_filtering)
        

        plot_type = self.plot_type_var.get()
        data_source = self.data_source_var.get()
        accumulation_mode = self.accumulation_var.get()
        
        try:
            window_size_ms = int(self.window_size_var.get())
            if window_size_ms <= 0:
                raise ValueError("Window size must be positive")
        except ValueError:
            messagebox.showerror("Invalid Input", "Window size must be a positive integer.")
            window_size_ms = 100 # Default fallback
            self.window_size_var.set("100")
        
        # Process the energy data
        if data_source == "Energy":
            # Process API server data (target container by ID)
            api_df = self._process_energy_data(filtered_api_data, "api", window_size_ms)
            
            # Process DB server data (target container by name)
            db_df = self._process_energy_data(filtered_db_data, "db", window_size_ms)
            
            # Process Host data (using api_server_energy as source)
            host_api_df = self._process_energy_data(filtered_api_data, "host", window_size_ms)
            # Process Host data (using db_server_energy as source)
            host_db_df = self._process_energy_data(filtered_db_data, "host", window_size_ms)
            
            # Create a new subplot
            ax = self.fig.add_subplot(111)
            
            # Configure the axis
            ax.set_xlabel('Time (EET)', fontsize=8)
            ax.set_ylabel('Energy Consumption (Watts)', fontsize=8)  # Updated unit label
            
            # Set plot title based on selected experiments
            if experiment_id == "All data":
                ax.set_title(f'Energy Consumption - All Data (No Time Filtering)', fontsize=10)
            elif experiment_id == "All Experiments":
                ax.set_title(f'Energy Consumption for All Experiments', fontsize=10)
            elif experiment_id == "All Experiments without warmup":
                ax.set_title(f'Energy Consumption for All Experiments (excluding warmup)', fontsize=10)
            else:
                ax.set_title(f'Energy Consumption for Experiment: {experiment_id}', fontsize=10)
            
            # Format the time axis
            ax.xaxis.set_major_formatter(self._ms_time_formatter())
            ax.tick_params(axis='x', rotation=45)
            
            if api_df.empty and db_df.empty and host_api_df.empty and host_db_df.empty and (not self.mongodb_api_var.get() or self.mongodb_api_data is None) and (not self.mongodb_db_var.get() or self.mongodb_db_data is None):
                self.status_var.set("No energy data available for the selected time period")
                ax.text(0.5, 0.5, "No energy data available", 
                       horizontalalignment='center', verticalalignment='center',
                       transform=ax.transAxes, fontsize=10)
            else:
                # Plot the data based on the selected plot type and accumulation mode
                if accumulation_mode == "Simple":
                    if plot_type == "Line":
                        if not api_df.empty and self.show_scaphandre_api_var.get():
                            ax.plot(api_df['datetime'], api_df['consumption'], 
                                   label='Scaphandre API Server (Java)', color='red', linewidth=2, marker=None)
                        
                        if not db_df.empty and self.show_scaphandre_db_var.get():
                            ax.plot(db_df['datetime'], db_df['consumption'], 
                                   label='Scaphandre DB Server (Postgres)', color='blue', linewidth=2, marker=None)
                        
                        if not host_api_df.empty and self.show_host_api_var.get():
                             ax.plot(host_api_df['datetime'], host_api_df['consumption'], 
                                    label='Host Machine (API Server)', color='purple', linestyle='--', linewidth=1.5, marker=None)
                        
                        if not host_db_df.empty and self.show_host_db_var.get():
                             ax.plot(host_db_df['datetime'], host_db_df['consumption'], 
                                    label='Host Machine (DB Server)', color='grey', linestyle=':', linewidth=1.5, marker=None)
                        
                        if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                            api_target = self.mongodb_api_target_var.get()
                            if api_target:
                                mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_api_df is not None:
                                    processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                                    if not processed_mongo_api_df.empty:
                                        ax.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                               label='PowerAPI API', color='green', linewidth=2, marker=None)
                        
                        if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                            db_target = self.mongodb_db_target_var.get()
                            if db_target:
                                mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_db_df is not None:
                                    processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                    if not processed_mongo_db_df.empty:
                                        ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                               label='PowerAPI DB', color='orange', linewidth=2, marker=None)
                    
                    elif plot_type == "Bar":
                        bar_width = 0.0002
                        if not api_df.empty and self.show_scaphandre_api_var.get():
                            ax.bar(api_df['datetime'], api_df['consumption'], 
                                   width=bar_width, label='Scaphandre API Server (Java)', color='red', alpha=0.7)
                        
                        if not db_df.empty and self.show_scaphandre_db_var.get():
                            ax.bar(db_df['datetime'], db_df['consumption'], 
                                   width=bar_width, label='Scaphandre DB Server (Postgres)', color='blue', alpha=0.7)
                        
                        if not host_api_df.empty and self.show_host_api_var.get():
                            ax.bar(host_api_df['datetime'], host_api_df['consumption'], 
                                   width=bar_width * 0.8, label='Host Machine (API Server)', color='purple', alpha=0.5)
                        
                        if not host_db_df.empty and self.show_host_db_var.get():
                            ax.bar(host_db_df['datetime'], host_db_df['consumption'], 
                                   width=bar_width * 0.6, label='Host Machine (DB Server)', color='grey', alpha=0.5)
                        
                        if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                            api_target = self.mongodb_api_target_var.get()
                            if api_target:
                                mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_api_df is not None:
                                    processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                                    if not processed_mongo_api_df.empty:
                                        ax.bar(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                               width=bar_width, label='PowerAPI API', color='green', alpha=0.7)
                        
                        if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                            db_target = self.mongodb_db_target_var.get()
                            if db_target:
                                mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_db_df is not None:
                                    processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                    if not processed_mongo_db_df.empty:
                                        ax.bar(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                               width=bar_width, label='PowerAPI DB', color='orange', alpha=0.7)
                
                elif accumulation_mode == "Accumulated":
                    ax.set_ylabel('Cumulative Energy Consumption (Watts)', fontsize=8)
                    
                    if not api_df.empty and self.show_scaphandre_api_var.get():
                        api_df['cumulative'] = api_df['consumption'].cumsum()
                        ax.plot(api_df['datetime'], api_df['cumulative'], 
                               label='Scaphandre API Server (Java) - Cumulative', color='red', linewidth=2, marker=None)
                    
                    if not db_df.empty and self.show_scaphandre_db_var.get():
                        db_df['cumulative'] = db_df['consumption'].cumsum()
                        ax.plot(db_df['datetime'], db_df['cumulative'], 
                               label='Scaphandre DB Server (Postgres) - Cumulative', color='blue', linewidth=2, marker=None)
                    
                    if not host_api_df.empty and self.show_host_api_var.get():
                        host_api_df['cumulative'] = host_api_df['consumption'].cumsum()
                        ax.plot(host_api_df['datetime'], host_api_df['cumulative'], 
                               label='Host Machine (API Server) - Cumulative', color='purple', linestyle='--', linewidth=1.5, marker=None)
                    
                    if not host_db_df.empty and self.show_host_db_var.get():
                        host_db_df['cumulative'] = host_db_df['consumption'].cumsum()
                        ax.plot(host_db_df['datetime'], host_db_df['cumulative'], 
                               label='Host Machine (DB Server) - Cumulative', color='grey', linestyle=':', linewidth=1.5, marker=None)
                    
                    if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                        api_target = self.mongodb_api_target_var.get()
                        if api_target:
                            mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                            if mongo_api_df is not None:
                                processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                                if not processed_mongo_api_df.empty:
                                    processed_mongo_api_df['cumulative'] = processed_mongo_api_df['power'].cumsum()
                                    ax.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['cumulative'],
                                           label='PowerAPI API - Cumulative', color='green', linewidth=2, marker=None)
                    
                    if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                        db_target = self.mongodb_db_target_var.get()
                        if db_target:
                            mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                            if mongo_db_df is not None:
                                processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                if not processed_mongo_db_df.empty:
                                    processed_mongo_db_df['cumulative'] = processed_mongo_db_df['power'].cumsum()
                                    ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['cumulative'],
                                           label='PowerAPI DB - Cumulative', color='orange', linewidth=2, marker=None)
                
                ax.legend(loc='best', fontsize='x-small', markerscale=0.8)
                
                ax.grid(True, linestyle='--', alpha=0.7)
                
                # Add run boundaries visualization for each experiment
                experiment_colors = ['lightgreen', 'lightblue', 'lightyellow', 'lightpink', 'lightcoral', 'lightskyblue']
                
                for idx, exp_id in enumerate(experiment_ids):
                    run_boundaries = self._get_run_time_boundaries(exp_id)
                    if run_boundaries:
                        color_index = idx % len(experiment_colors)
                        color = experiment_colors[color_index]
                        
                        for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                            run_start_dt = self._convert_to_eet(run_start)
                            run_end_dt = self._convert_to_eet(run_end)
                            
                            # Add shaded region for this run
                            ax.axvspan(run_start_dt, run_end_dt, 
                                      alpha=0.3, 
                                      color=color, 
                                      label=f'{exp_id} - Run {run_num}' if i == 0 else "")
                            
                            # Add vertical lines at run boundaries
                            ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.7)
                            ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.7)
                            
                            if experiment_id != "All Experiments":
                                midpoint = run_start_dt + (run_end_dt - run_start_dt) / 2
                                y_pos = ax.get_ylim()[1] * 0.95
                                ax.text(midpoint, y_pos, f'Run {run_num}', 
                                       ha='center', va='top', 
                                       bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'))
                
                self.fig.tight_layout()
                
                if experiment_id == "All Experiments":
                    chronology = self._get_experiment_chronology()
                    
                    if len(chronology) > 1:
                        for i in range(len(chronology) - 1):
                            current_exp_id, _, current_end = chronology[i]
                            next_exp_id, next_start, _ = chronology[i + 1]
                            
                            # Ensure next_start is not before current_end to avoid negative durations
                            if next_start > current_end: 
                                current_end_dt = self._convert_to_eet(current_end)
                                next_start_dt = self._convert_to_eet(next_start)
                                
                                ax.axvspan(current_end_dt, next_start_dt, 
                                          alpha=0.2, 
                                          color='gray', 
                                          hatch='///' if i % 2 == 0 else '\\\\\\',
                                          label='Inter-experiment pause' if i == 0 else "")
                                
                                # Add vertical lines at experiment boundaries
                                ax.axvline(x=current_end_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                ax.axvline(x=next_start_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                
                                # Calculate the duration of the pause in seconds
                                pause_duration = (next_start - current_end) / 1000
                                
                                # Add a text label for the pause duration
                                if pause_duration > 5: # Only label pauses longer than 5 seconds
                                    midpoint = current_end_dt + (next_start_dt - current_end_dt) / 2
                                    y_pos = ax.get_ylim()[1] * 0.75 

                                    
                                    # Format the duration
                                    if pause_duration < 60:
                                        duration_text = f"{pause_duration:.1f}s"
                                    elif pause_duration < 3600:
                                        duration_text = f"{pause_duration/60:.1f}min"
                                    else:
                                        duration_text = f"{pause_duration/3600:.1f}h"
                                    
                                    ax.text(midpoint, y_pos, 
                                           f"Pause: {duration_text}\n{current_exp_id} → {next_exp_id}", 
                                           ha='center', va='center', 
                                           bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'),
                                           fontsize=9)
                
        elif data_source == "Energy Consumed":
            ax = self.fig.add_subplot(111)
            
            ax.set_xlabel('Time (EET)', fontsize=8)
            ax.set_ylabel('Total Energy Consumed (Watts)', fontsize=8)
            
            if experiment_id == "All data":
                ax.set_title(f'Total Energy Consumed - All Data (No Time Filtering)', fontsize=10)
            elif experiment_id == "All Experiments":
                ax.set_title(f'Total Energy Consumed for All Experiments', fontsize=10)
            elif experiment_id == "All Experiments without warmup":
                ax.set_title(f'Total Energy Consumed for All Experiments (excluding warmup)', fontsize=10)
            else:
                ax.set_title(f'Total Energy Consumed for Experiment: {experiment_id}', fontsize=10)
            
            ax.xaxis.set_major_formatter(self._ms_time_formatter())
            ax.tick_params(axis='x', rotation=45)
            
            # Process API server data (java processes)
            api_df = self._process_energy_data(filtered_api_data, "java", window_size_ms)
            
            # Process DB server data (postgres processes)
            db_df = self._process_energy_data(filtered_db_data, "postgres", window_size_ms)
            
            # Convert boundaries to datetime objects for final filtering
            start_dt = None
            end_dt = None
            if start_time_ms is not None:
                start_dt = self._convert_to_eet(start_time_ms) 
            if end_time_ms is not None:
                end_dt = self._convert_to_eet(end_time_ms)
                
            if start_dt is not None and not api_df.empty:
                api_df = api_df[api_df['datetime'] >= start_dt]
            if end_dt is not None and not api_df.empty:
                api_df = api_df[api_df['datetime'] <= end_dt]
                
            if start_dt is not None and not db_df.empty:
                db_df = db_df[db_df['datetime'] >= start_dt]
            if end_dt is not None and not db_df.empty:
                db_df = db_df[db_df['datetime'] <= end_dt]

            processed_mongo_api_df = pd.DataFrame() # Initialize to empty
            processed_mongo_db_df = pd.DataFrame() # Initialize to empty

            if not api_df.empty and self.show_scaphandre_api_var.get():
                api_df['cumulative'] = api_df['consumption'].cumsum()
                ax.plot(api_df['datetime'], api_df['cumulative'], 
                       label='Scaphandre API Server (Java)', color='red', linewidth=2, marker=None)
            
            if not db_df.empty and self.show_scaphandre_db_var.get():
                db_df['cumulative'] = db_df['consumption'].cumsum()
                ax.plot(db_df['datetime'], db_df['cumulative'], 
                       label='Scaphandre DB Server (Postgres)', color='blue', linewidth=2, marker=None)
            
            if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                api_target = self.mongodb_api_target_var.get()
                if api_target:
                    mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                    if mongo_api_df is not None:
                        processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                        if not processed_mongo_api_df.empty:
                            if start_dt is not None:
                                processed_mongo_api_df = processed_mongo_api_df[processed_mongo_api_df['datetime'] >= start_dt]
                            if end_dt is not None:
                                processed_mongo_api_df = processed_mongo_api_df[processed_mongo_api_df['datetime'] <= end_dt]
                                
                            if not processed_mongo_api_df.empty:
                                processed_mongo_api_df['cumulative'] = processed_mongo_api_df['power'].cumsum()
                                ax.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['cumulative'],
                                       label='PowerAPI API', color='green', linewidth=2, marker=None)
            
            if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                db_target = self.mongodb_db_target_var.get()
                if db_target:
                    mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                    if mongo_db_df is not None:
                        processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                        if not processed_mongo_db_df.empty:
                            if start_dt is not None:
                                processed_mongo_db_df = processed_mongo_db_df[processed_mongo_db_df['datetime'] >= start_dt]
                            if end_dt is not None:
                                processed_mongo_db_df = processed_mongo_db_df[processed_mongo_db_df['datetime'] <= end_dt]
                                
                            if not processed_mongo_db_df.empty:
                                processed_mongo_db_df['cumulative'] = processed_mongo_db_df['power'].cumsum()
                                ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['cumulative'],
                                       label='PowerAPI DB', color='orange', linewidth=2, marker=None)
            
            ax.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
            
            ax.grid(True, linestyle='--', alpha=0.7)
            
            experiment_colors = ['lightgreen', 'lightblue', 'lightyellow', 'lightpink', 'lightcoral', 'lightskyblue']
            
            for idx, exp_id in enumerate(experiment_ids):
                run_boundaries = self._get_run_time_boundaries(exp_id)
                if run_boundaries:
                    color_index = idx % len(experiment_colors)
                    color = experiment_colors[color_index]
                    
                    for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                        run_start_dt = self._convert_to_eet(run_start)
                        run_end_dt = self._convert_to_eet(run_end)
                        
                        ax.axvspan(run_start_dt, run_end_dt, 
                                  alpha=0.3, 
                                  color=color, 
                                  label=f'{exp_id} - Run {run_num}' if i == 0 else "")
                        
                        ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.7)
                        ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.7)
            
            # Calculate and display total energy consumed
            total_api = api_df['consumption'].sum() if not api_df.empty else 0
            total_db = db_df['consumption'].sum() if not db_df.empty else 0
            total_mongo_api = processed_mongo_api_df['power'].sum() if not processed_mongo_api_df.empty else 0
            total_mongo_db = processed_mongo_db_df['power'].sum() if not processed_mongo_db_df.empty else 0
            
            total_text = f"Total Energy Consumed:\n"
            total_text += f"Scaphandre API Server: {total_api:.2f} Watts\n"
            total_text += f"Scaphandre DB Server: {total_db:.2f} Watts\n"
            if total_mongo_api > 0:
                total_text += f"PowerAPI API: {total_mongo_api:.2f} Watts\n"
            if total_mongo_db > 0:
                total_text += f"PowerAPI DB: {total_mongo_db:.2f} Watts\n"
            total_text += f"Total: {total_api + total_db + total_mongo_api + total_mongo_db:.2f} Watts"
            
            ax.text(0.98, 0.02, total_text,
                   transform=ax.transAxes,
                   verticalalignment='bottom',
                   horizontalalignment='right',
                   bbox=dict(boxstyle='round', facecolor='white', alpha=0.8),
                   fontsize=8)
            
            self.fig.tight_layout()
            
            self.status_var.set(f"Total energy consumed: {total_api + total_db + total_mongo_api + total_mongo_db:.2f} Watts")
            
        elif data_source == "Energy Comparative":
            # Create two subplots for comparative energy visualization
            # Process API server data (target container by ID)
            api_df = self._process_energy_data(filtered_api_data, "api", window_size_ms)
            
            # Process DB server data (target container by name)
            db_df = self._process_energy_data(filtered_db_data, "db", window_size_ms)
            
            # Create a figure with two subplots (2 rows, 1 column)
            ax1 = self.fig.add_subplot(211)
            ax2 = self.fig.add_subplot(212)
            
            # Configure the axes
            if experiment_id == "All data":
                ax1.set_title(f'Energy Consumption - Scaphandre API Server (Java) - All Data (No Filtering)', fontsize=8)
                ax2.set_title(f'Energy Consumption - Scaphandre DB Server (Postgres) - All Data (No Filtering)', fontsize=8)
            else:
                ax1.set_title(f'Energy Consumption - Scaphandre API Server (Java) - {experiment_id}', fontsize=8)
                ax2.set_title(f'Energy Consumption - Scaphandre DB Server (Postgres) - {experiment_id}', fontsize=8)
            ax1.set_ylabel('Energy Consumption', fontsize=7)
            ax2.set_xlabel('Time (EET)', fontsize=7)
            ax2.set_ylabel('Energy Consumption', fontsize=7)
            
            # Format time axes
            ax1.xaxis.set_major_formatter(self._ms_time_formatter())
            ax1.tick_params(axis='x', rotation=45)
            ax2.xaxis.set_major_formatter(self._ms_time_formatter())
            ax2.tick_params(axis='x', rotation=45)
            
            has_scaphandre_api = not api_df.empty and self.show_scaphandre_api_var.get()
            has_powerapi_api = (self.mongodb_api_var.get() and self.mongodb_api_data is not None and 
                               self.mongodb_api_target_var.get())
            
            if not has_scaphandre_api and not has_powerapi_api:
                ax1.text(0.5, 0.5, "No API server energy data available", 
                        horizontalalignment='center', verticalalignment='center',
                        transform=ax1.transAxes, fontsize=8)
            else:
                if accumulation_mode == "Simple":
                    if has_scaphandre_api:
                        if plot_type == "Line":
                            ax1.plot(api_df['datetime'], api_df['consumption'], 
                                    color='red', linewidth=2, marker=None, label='Scaphandre API Server Energy')
                        elif plot_type == "Scatter":
                            ax1.scatter(api_df['datetime'], api_df['consumption'], 
                                       color='blue', marker='o', s=20, label='Scaphandre API Server Energy')
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax1.bar(api_df['datetime'], api_df['consumption'], 
                                   width=bar_width, color='blue', alpha=0.7, label='Scaphandre API Server Energy')
                    
                    if has_powerapi_api:
                        api_target = self.mongodb_api_target_var.get()
                        mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_api_df is not None:
                            processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                            if not processed_mongo_api_df.empty:
                                bar_width = 0.0002 # Ensure bar_width is defined
                                if plot_type == "Line":
                                    ax1.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                           color='green', linewidth=2, marker=None, 
                                           label='PowerAPI API')
                                elif plot_type == "Scatter":
                                    ax1.scatter(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                                  color='green', marker='o', s=20, 
                                                  label='PowerAPI API')
                                elif plot_type == "Bar":
                                    ax1.bar(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                              width=bar_width, color='green', alpha=0.7, 
                                              label='PowerAPI API')
                else: # Accumulated mode
                    if has_scaphandre_api:
                        api_df['accumulated'] = api_df['consumption'].cumsum()
                        if plot_type == "Line":
                            ax1.plot(api_df['datetime'], api_df['accumulated'], 
                                    color='red', linewidth=2, marker=None, label='Scaphandre Accumulated')
                            ax1_twin = ax1.twinx()
                            ax1_twin.plot(api_df['datetime'], api_df['consumption'], 
                                         color='lightcoral', linewidth=1.5, marker=None, alpha=0.7, label='Per Interval')
                            ax1_twin.set_ylabel('Interval Energy', color='lightcoral', fontsize=8)
                            ax1_twin.tick_params(axis='y', labelcolor='lightcoral')
                        elif plot_type == "Bar": # special handling for bar in accumulated
                            bar_width = 0.0002
                            ax1.fill_between(api_df['datetime'], api_df['accumulated'], color='red', alpha=0.3, label='Accumulated')
                            ax1.plot(api_df['datetime'], api_df['accumulated'], color='red', linewidth=1.5)
                            ax1_twin = ax1.twinx()
                            ax1_twin.bar(api_df['datetime'], api_df['consumption'], width=bar_width, color='lightcoral', alpha=0.7, label='Per Interval')
                            ax1_twin.set_ylabel('Interval Energy', color='lightcoral', fontsize=8)
                            ax1_twin.tick_params(axis='y', labelcolor='lightcoral')
                        elif plot_type == "Scatter":
                            ax1.scatter(api_df['datetime'], api_df['accumulated'], color='red', marker='o', s=15, label='Accumulated')
                            ax1.plot(api_df['datetime'], api_df['accumulated'], color='red', linewidth=1, alpha=0.5)
                    
                    if has_powerapi_api:
                        api_target = self.mongodb_api_target_var.get()
                        mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_api_df is not None:
                            processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                            if not processed_mongo_api_df.empty:
                                processed_mongo_api_df['accumulated'] = processed_mongo_api_df['power'].cumsum()
                                if plot_type == "Line":
                                    ax1.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'],
                                           color='green', linewidth=2, marker=None, 
                                           label='PowerAPI API Accumulated')
                                elif plot_type == "Bar":
                                    bar_width = 0.0002
                                    ax1.fill_between(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'], color='lightgreen', alpha=0.3, label='PowerAPI Accumulated')
                                    ax1.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'], color='green', linewidth=1.5)
                                elif plot_type == "Scatter":
                                    ax1.scatter(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'], color='green', marker='o', s=15, label='PowerAPI Accumulated')
                                    ax1.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'], color='green', linewidth=1, alpha=0.5)

            if (not api_df.empty) or (self.mongodb_api_var.get() and self.mongodb_api_data is not None):
                ax1.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
            
            has_scaphandre_db = not db_df.empty and self.show_scaphandre_db_var.get()
            has_powerapi_db = (self.mongodb_db_var.get() and self.mongodb_db_data is not None and 
                              self.mongodb_db_target_var.get())
            
            if not has_scaphandre_db and not has_powerapi_db:
                ax2.text(0.5, 0.5, "No DB server energy data available", 
                        horizontalalignment='center', verticalalignment='center',
                        transform=ax2.transAxes, fontsize=8)
            else:
                if accumulation_mode == "Simple":
                    if has_scaphandre_db:
                        if plot_type == "Line":
                            ax2.plot(db_df['datetime'], db_df['consumption'], 
                                    color='blue', linewidth=2, marker=None, label='Scaphandre DB Server Energy')
                        elif plot_type == "Scatter":
                            ax2.scatter(db_df['datetime'], db_df['consumption'], 
                                       color='green', marker='s', s=20, label='Scaphandre DB Server Energy')
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax2.bar(db_df['datetime'], db_df['consumption'], 
                                   width=bar_width, color='green', alpha=0.7, label='Scaphandre DB Server Energy')
                    
                    if has_powerapi_db:
                        db_target = self.mongodb_db_target_var.get()
                        mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_db_df is not None:
                            processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                            if not processed_mongo_db_df.empty:
                                bar_width = 0.0002 # Ensure bar_width is defined
                                if plot_type == "Line":
                                    ax2.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                           color='orange', linewidth=2, marker=None, 
                                           label=f'PowerAPI DB ({db_target})')
                                elif plot_type == "Scatter":
                                    ax2.scatter(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                              color='orange', marker='s', s=20, 
                                              label=f'PowerAPI DB ({db_target})')
                                elif plot_type == "Bar":
                                    ax2.bar(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                          width=bar_width, color='orange', alpha=0.7, 
                                          label=f'PowerAPI DB ({db_target})')
                else: # Accumulated mode
                    if has_scaphandre_db:
                        db_df['accumulated'] = db_df['consumption'].cumsum()
                        
                        if plot_type == "Line":
                            ax2.plot(db_df['datetime'], db_df['accumulated'], 
                                    color='blue', linewidth=2, marker=None, label='Scaphandre Accumulated')
                            ax2_twin = ax2.twinx()
                            ax2_twin.plot(db_df['datetime'], db_df['consumption'], 
                                         color='lightblue', linewidth=1.5, marker=None, alpha=0.7, label='Per Interval')
                            ax2_twin.set_ylabel('Interval Energy', color='lightblue', fontsize=8)
                            ax2_twin.tick_params(axis='y', labelcolor='lightblue')
                        
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax2.fill_between(db_df['datetime'], db_df['accumulated'], color='blue', alpha=0.3, label='Accumulated')
                            ax2.plot(db_df['datetime'], db_df['accumulated'], color='blue', linewidth=1.5)
                            ax2_twin = ax2.twinx()
                            ax2_twin.bar(db_df['datetime'], db_df['consumption'], width=bar_width, color='lightblue', alpha=0.7, label='Per Interval')
                            ax2_twin.set_ylabel('Interval Energy', color='lightblue', fontsize=8)
                            ax2_twin.tick_params(axis='y', labelcolor='lightblue')
                        elif plot_type == "Scatter":
                            ax2.scatter(db_df['datetime'], db_df['accumulated'], color='blue', marker='s', s=15, label='Accumulated')
                            ax2.plot(db_df['datetime'], db_df['accumulated'], color='blue', linewidth=1, alpha=0.5)
                    
                    if has_powerapi_db:
                        db_target = self.mongodb_db_target_var.get()
                        mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_db_df is not None:
                            processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                            if not processed_mongo_db_df.empty:
                                processed_mongo_db_df['accumulated'] = processed_mongo_db_df['power'].cumsum()
                                if plot_type == "Line":
                                    ax2.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'],
                                           color='orange', linewidth=2, marker=None, 
                                           label=f'PowerAPI DB ({db_target}) Accumulated')
                                elif plot_type == "Bar":
                                    bar_width = 0.0002
                                    ax2.fill_between(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'], color='orange', alpha=0.3, label='PowerAPI Accumulated')
                                    ax2.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'], color='orange', linewidth=1.5)
                                elif plot_type == "Scatter":
                                    ax2.scatter(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'], color='orange', marker='s', s=15, label='PowerAPI Accumulated')
                                    ax2.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'], color='orange', linewidth=1, alpha=0.5)
            
            if has_scaphandre_db or has_powerapi_db:
                ax2.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
            
            ax1.grid(True, linestyle='--', alpha=0.7)
            ax2.grid(True, linestyle='--', alpha=0.7)
            
            if not api_df.empty and (api_df['consumption'].max() > 10000 or 
                                    (accumulation_mode == "Accumulated" and 'accumulated' in api_df.columns and api_df['accumulated'].max() > 10000)):
                ax1.ticklabel_format(axis='y', style='sci', scilimits=(0,0))
            
            if not db_df.empty and (db_df['consumption'].max() > 10000 or 
                                   (accumulation_mode == "Accumulated" and 'accumulated' in db_df.columns and db_df['accumulated'].max() > 10000)):
                ax2.ticklabel_format(axis='y', style='sci', scilimits=(0,0))
            
            if accumulation_mode == "Accumulated" and not api_df.empty and 'accumulated' in api_df.columns:
                try:
                    lines, labels = ax1.get_legend_handles_labels()
                    if hasattr(ax1, 'twinx_axes'): # Check if twinx was created
                        lines2, labels2 = ax1.twinx_axes[0].get_legend_handles_labels()
                        ax1.legend(lines + lines2, labels + labels2, loc='upper left', fontsize='x-small', markerscale=0.8)
                except Exception as ex:
                    print(f"Error updating legend for ax1: {ex}")
                    if not api_df.empty: ax1.legend(loc='upper left', fontsize='x-small', markerscale=0.8) # Fallback
            
            if accumulation_mode == "Accumulated" and not db_df.empty and 'accumulated' in db_df.columns:
                try:
                    lines, labels = ax2.get_legend_handles_labels()
                    if hasattr(ax2, 'twinx_axes'): # Check if twinx was created
                        lines2, labels2 = ax2.twinx_axes[0].get_legend_handles_labels()
                        ax2.legend(lines + lines2, labels + labels2, loc='upper left', fontsize='x-small', markerscale=0.8)
                except Exception as ex:
                    print(f"Error updating legend for ax2: {ex}")
                    if not db_df.empty: ax2.legend(loc='upper left', fontsize='x-small', markerscale=0.8) # Fallback
            
            self.fig.tight_layout()
            
            api_count = len(api_df) if not api_df.empty else 0
            db_count = len(db_df) if not db_df.empty else 0
            total_api = api_df['consumption'].sum() if not api_df.empty else 0
            total_db = db_df['consumption'].sum() if not db_df.empty else 0
            
            powerapi_api_count = 0
            powerapi_db_count = 0
            
            if has_powerapi_api:
                api_target = self.mongodb_api_target_var.get()
                mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                if mongo_api_df is not None:
                    processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                    if not processed_mongo_api_df.empty:
                        powerapi_api_count = len(processed_mongo_api_df)
            
            if has_powerapi_db:
                db_target = self.mongodb_db_target_var.get()
                mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                if mongo_db_df is not None:
                    processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                    if not processed_mongo_db_df.empty:
                        powerapi_db_count = len(processed_mongo_db_df)
            
            if accumulation_mode == "Accumulated":
                self.status_var.set(
                    f"Comparative view: API data: {api_count} points, total: {total_api:.2e} units | "
                    f"DB data: {db_count} points, total: {total_db:.2e} units"
                )
            else:
                status_parts = []
                if api_count > 0 or powerapi_api_count > 0:
                    status_parts.append(f"API data points: Scaphandre {api_count}, PowerAPI {powerapi_api_count}")
                if db_count > 0 or powerapi_db_count > 0:
                    status_parts.append(f"DB data points: Scaphandre {db_count}, PowerAPI {powerapi_db_count}")
                    
                if status_parts:
                    self.status_var.set(f"Comparative view: {' | '.join(status_parts)}")
                else:
                    self.status_var.set("Comparative view: No data available")
                
        elif data_source == "Latency":
            if experiment_id == "All data" or experiment_id == "All Experiments" or experiment_id == "All Experiments without warmup":
                ax = self.fig.add_subplot(111)
                
                ax.set_xlabel('Time (EET)', fontsize=8)
                ax.set_ylabel('Latency (ms)', fontsize=8)
                if experiment_id == "All data":
                    ax.set_title(f'Request Latency - All Data (No Time Filtering)', fontsize=10)
                elif experiment_id == "All Experiments":
                    ax.set_title(f'Request Latency for All Experiments', fontsize=10)
                elif experiment_id == "All Experiments without warmup":
                    ax.set_title(f'Request Latency for All Experiments (excluding warmup)', fontsize=10)
                else:
                    ax.set_title(f'Request Latency for Experiment: {experiment_id}', fontsize=10)

                ax.xaxis.set_major_formatter(self._ms_time_formatter())
                ax.tick_params(axis='x', rotation=45)

                experiment_colors = ['purple', 'green', 'orange', 'brown', 'magenta', 'cyan']
                has_data = False
                
                for idx, exp_id in enumerate(experiment_ids):
                    experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                    runs = experiment.get("runs", [])
                    
                    latency_data = []
                    for run in runs:
                        if "latencies" in run:
                            for entry in run["latencies"]:
                                if "latency_ns" in entry and "timestamp" in entry:
                                    timestamp_ms = entry["timestamp"]
                                    latency_ms = entry["latency_ns"] / 1_000_000  # Convert ns to ms
                                    dt = self._convert_to_eet(timestamp_ms)
                                    latency_data.append((dt, latency_ms))
                    
                    if latency_data:
                        has_data = True
                        latency_df = pd.DataFrame(latency_data, columns=['datetime', 'latency'])
                        
                        color = experiment_colors[idx % len(experiment_colors)]
                        
                        if plot_type == "Line":
                            ax.plot(latency_df['datetime'], latency_df['latency'], 
                                   label=f'{exp_id}', color=color, linewidth=2, marker=None, alpha=0.7)
                        elif plot_type == "Scatter":
                            ax.scatter(latency_df['datetime'], latency_df['latency'], 
                                     label=f'{exp_id}', color=color, marker='o', s=30, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax.bar(latency_df['datetime'], latency_df['latency'], 
                                  width=bar_width, label=f'{exp_id}', color=color, alpha=0.7)
                
                if not has_data:
                    ax.text(0.5, 0.5, "No latency data available", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=10)
                else:
                    ax.grid(True, linestyle='--', alpha=0.7)
                    
                    ax.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
                    
                    experiment_colors = ['lightgreen', 'lightblue', 'lightyellow', 'lightpink', 'lightcoral', 'lightskyblue']
                    
                    for idx, exp_id in enumerate(experiment_ids):
                        run_boundaries = self._get_run_time_boundaries(exp_id)
                        if run_boundaries:
                            color_index = idx % len(experiment_colors)
                            color = experiment_colors[color_index]
                            
                            for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                                run_start_dt = self._convert_to_eet(run_start)
                                run_end_dt = self._convert_to_eet(run_end)
                                
                                ax.axvspan(run_start_dt, run_end_dt, 
                                          alpha=0.15,
                                          color=color, 
                                          label="" if i > 0 else f'{exp_id} runs')
                                
                                ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.3)
                                ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.3)
                    
                    self.fig.tight_layout()
                    
                    chronology = self._get_experiment_chronology()
                    
                    if len(chronology) > 1:
                        for i in range(len(chronology) - 1):
                            current_exp_id, _, current_end = chronology[i]
                            next_exp_id, next_start, _ = chronology[i + 1]
                            
                            if next_start > current_end:
                                current_end_dt = self._convert_to_eet(current_end)
                                next_start_dt = self._convert_to_eet(next_start)
                                
                                ax.axvspan(current_end_dt, next_start_dt, 
                                          alpha=0.2, 
                                          color='gray', 
                                          hatch='///' if i % 2 == 0 else '\\\\\\',
                                          label='Inter-experiment pause' if i == 0 else "")
                                
                                ax.axvline(x=current_end_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                ax.axvline(x=next_start_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                
                                pause_duration = (next_start - current_end) / 1000
                                
                                if pause_duration > 5:
                                    midpoint = current_end_dt + (next_start_dt - current_end_dt) / 2
                                    y_pos = ax.get_ylim()[1] * 0.75
                                    
                                    if pause_duration < 60:
                                        duration_text = f"{pause_duration:.1f}s"
                                    elif pause_duration < 3600:
                                        duration_text = f"{pause_duration/60:.1f}min"
                                    else:
                                        duration_text = f"{pause_duration/3600:.1f}h"
                                    
                                    ax.text(midpoint, y_pos, 
                                           f"Pause: {duration_text}\n{current_exp_id} → {next_exp_id}", 
                                           ha='center', va='center', 
                                           bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'),
                                           fontsize=9)
            else:
                experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
                runs = experiment.get("runs", [])
                
                if not runs:
                    ax = self.fig.add_subplot(111)
                    ax.text(0.5, 0.5, "No latency data available for this experiment", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=10)
                else:
                    ax = self.fig.add_subplot(111)
                    
                    ax.set_xlabel('Time (EET)', fontsize=8)
                    ax.set_ylabel('Latency (ms)', fontsize=8)
                    ax.set_title(f'Request Latency for Experiment: {experiment_id}', fontsize=10)

                    ax.xaxis.set_major_formatter(self._ms_time_formatter())
                    ax.tick_params(axis='x', rotation=45)

                    latency_data = []
                    for run in runs:
                        if "latencies" in run:
                            for entry in run["latencies"]:
                                if "latency_ns" in entry and "timestamp" in entry:
                                    timestamp_ms = entry["timestamp"]
                                    latency_ms = entry["latency_ns"] / 1_000_000  # Convert ns to ms
                                    dt = self._convert_to_eet(timestamp_ms)
                                    latency_data.append((dt, latency_ms))
                    
                    if not latency_data:
                        ax.text(0.5, 0.5, "No detailed latency data available", 
                               horizontalalignment='center', verticalalignment='center',
                               transform=ax.transAxes, fontsize=10)
                    else:
                        latency_df = pd.DataFrame(latency_data, columns=['datetime', 'latency'])
                        
                        if plot_type == "Line":
                            ax.plot(latency_df['datetime'], latency_df['latency'], 
                                   color='purple', linewidth=2, marker=None, alpha=0.7)
                        elif plot_type == "Scatter":
                            ax.scatter(latency_df['datetime'], latency_df['latency'], 
                                     color='purple', marker='o', s=30, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax.bar(latency_df['datetime'], latency_df['latency'], 
                                  width=bar_width, color='purple', alpha=0.7)
                        
                        ax.grid(True, linestyle='--', alpha=0.7)
                        
                        if run_boundaries := self._get_run_time_boundaries(experiment_id):
                            run_colors = ['lightgreen', 'lightblue']
                            
                            for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                                run_start_dt = self._convert_to_eet(run_start)
                                run_end_dt = self._convert_to_eet(run_end)
                                
                                ax.axvspan(run_start_dt, run_end_dt, 
                                          alpha=0.3, 
                                          color=run_colors[i % len(run_colors)], 
                                          label=f'Run {run_num}' if i == 0 else "")
                                
                                ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.7)
                                ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.7)
                                
                                midpoint = run_start_dt + (run_end_dt - run_start_dt) / 2
                                y_pos = ax.get_ylim()[1] * 0.95
                                ax.text(midpoint, y_pos, f'Run {run_num}', 
                                       ha='center', va='top', 
                                       bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'))
                        
                        self.fig.tight_layout()

                        ax.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
        
        elif data_source == "Throughput":
            if experiment_id == "All data" or experiment_id == "All Experiments" or experiment_id == "All Experiments without warmup":
                ax = self.fig.add_subplot(111)
                
                ax.set_xlabel('Time (EET)', fontsize=8)
                ax.set_ylabel('Throughput (requests/second)', fontsize=8)
                if experiment_id == "All data":
                    ax.set_title(f'Request Throughput - All Data (No Time Filtering)', fontsize=10)
                elif experiment_id == "All Experiments":
                    ax.set_title(f'Request Throughput for All Experiments', fontsize=10)
                elif experiment_id == "All Experiments without warmup":
                    ax.set_title(f'Request Throughput for All Experiments (excluding warmup)', fontsize=10)
                else:
                    ax.set_title(f'Request Throughput for Experiment: {experiment_id}', fontsize=10)

                ax.xaxis.set_major_formatter(self._ms_time_formatter())
                ax.tick_params(axis='x', rotation=45)

                ax.yaxis.set_major_formatter(plt.FormatStrFormatter('%.5f'))
                
                experiment_colors = ['purple', 'green', 'orange', 'brown', 'magenta', 'cyan']
                has_data = False
                
                for idx, exp_id in enumerate(experiment_ids):
                    experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                    runs = experiment.get("runs", [])
                    
                    throughput_data = []
                    goodput_data = []
                    for run in runs:
                        if "throughput" in run and "start_timestamp" in run:
                            timestamp_ms = run["start_timestamp"]
                            throughput = run["throughput"]
                            dt = self._convert_to_eet(timestamp_ms)
                            throughput_data.append((dt, throughput))
                            
                            if "goodput" in run:
                                goodput = run["goodput"]
                                goodput_data.append((dt, goodput))
                    
                    if throughput_data:
                        has_data = True
                        throughput_df = pd.DataFrame(throughput_data, columns=['datetime', 'throughput'])
                        
                        throughput_df = throughput_df.sort_values('datetime')
                        
                        color = experiment_colors[idx % len(experiment_colors)]
                        if plot_type == "Line":
                            ax.plot(throughput_df['datetime'], throughput_df['throughput'], 
                                   marker='o', linestyle='-', color=color, 
                                   label=f"{exp_id} (Total)", linewidth=2)
                            
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                ax.plot(goodput_df['datetime'], goodput_df['goodput'], 
                                       marker='x', linestyle='--', color=color, 
                                       label=f"{exp_id} (Successful)", linewidth=1.5, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002
                            ax.bar(throughput_df['datetime'], throughput_df['throughput'], 
                                  width=bar_width, color=color, alpha=0.7, label=f"{exp_id} (Total)")
                            
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                ax.bar(goodput_df['datetime'], goodput_df['goodput'], 
                                      width=bar_width*0.8, color=color, alpha=0.4, 
                                      label=f"{exp_id} (Successful)")
                        elif plot_type == "Scatter":
                            ax.scatter(throughput_df['datetime'], throughput_df['throughput'], 
                                      color=color, marker='o', s=50, label=f"{exp_id} (Total)")
                            
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                ax.scatter(goodput_df['datetime'], goodput_df['goodput'], 
                                         color=color, marker='x', s=40, alpha=0.7,
                                         label=f"{exp_id} (Successful)")
                
                if has_data:
                    ax.legend(loc='upper left', fontsize='x-small', markerscale=0.8)
                    
                    ax.xaxis.set_major_locator(mticker.MaxNLocator(integer=True))
                    
                    if ax.get_ylim()[0] < 0:
                        ax.set_ylim(bottom=0)
                    
                    if plot_type in ["Line", "Scatter"]:
                        for idx, exp_id in enumerate(experiment_ids):
                            experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                            runs = experiment.get("runs", [])
                            
                            throughput_data = []
                            for run in runs:
                                if "throughput" in run and "start_timestamp" in run:
                                    timestamp_ms = run["start_timestamp"]
                                    throughput = run["throughput"]
                                    dt = self._convert_to_eet(timestamp_ms)
                                    throughput_data.append((dt, throughput))
                            
                            if throughput_data:
                                throughput_df = pd.DataFrame(throughput_data, columns=['datetime', 'throughput'])
                                throughput_df = throughput_df.sort_values('datetime')
                                
                                for i, row in throughput_df.iterrows():
                                    ax.text(row['datetime'], row['throughput'], f"{row['throughput']:.5f}", 
                                           ha='center', va='bottom', fontsize=7)
                else:
                    ax.text(0.5, 0.5, "No throughput data available for the selected experiments", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=8)
            else:
                experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
                runs = experiment.get("runs", [])
                
                ax = self.fig.add_subplot(111)
                
                ax.set_xlabel('Run Number', fontsize=8)
                ax.set_ylabel('Throughput (requests/second)', fontsize=8)
                ax.set_title(f'Request Throughput for {experiment_id}', fontsize=10)
                
                ax.yaxis.set_major_formatter(plt.FormatStrFormatter('%.5f'))
                
                throughput_data = []
                goodput_data = []
                for i, run in enumerate(runs):
                    run_number = i + 1
                    if "throughput" in run:
                        throughput = run["throughput"]
                        throughput_data.append((run_number, throughput))
                    if "goodput" in run:
                        goodput = run["goodput"]
                        goodput_data.append((run_number, goodput))
                
                if throughput_data or goodput_data:
                    if throughput_data:
                        throughput_df = pd.DataFrame(throughput_data, columns=['run', 'throughput'])
                        
                        if plot_type == "Line":
                            ax.plot(throughput_df['run'], throughput_df['throughput'], 
                                  marker='o', linestyle='-', color='blue', 
                                  linewidth=2, label='Total Throughput')
                        elif plot_type == "Bar":
                            bar_width = 0.35
                            ax.bar(throughput_df['run'] - bar_width/2, throughput_df['throughput'], 
                                  width=bar_width, color='blue', alpha=0.7, label='Total Throughput')
                        elif plot_type == "Scatter":
                            ax.scatter(throughput_df['run'], throughput_df['throughput'], 
                                     color='blue', marker='o', s=50, label='Total Throughput')
                    
                    if goodput_data:
                        goodput_df = pd.DataFrame(goodput_data, columns=['run', 'goodput'])
                        
                        if plot_type == "Line":
                            ax.plot(goodput_df['run'], goodput_df['goodput'], 
                                  marker='x', linestyle='--', color='green', 
                                  linewidth=2, label='Successful Throughput')
                        elif plot_type == "Bar":
                            bar_width = 0.35
                            ax.bar(goodput_df['run'] + bar_width/2, goodput_df['goodput'], 
                                  width=bar_width, color='green', alpha=0.7, label='Successful Throughput')
                        elif plot_type == "Scatter":
                            ax.scatter(goodput_df['run'], goodput_df['goodput'], 
                                     color='green', marker='x', s=50, label='Successful Throughput')
                    
                    ax.legend(loc='best', fontsize='x-small', markerscale=0.8)
                    
                    ax.xaxis.set_major_locator(mticker.MaxNLocator(integer=True))
                    
                    if ax.get_ylim()[0] < 0:
                        ax.set_ylim(bottom=0)
                    
                    if throughput_data and plot_type != "Bar":
                        for i, row in throughput_df.iterrows():
                            ax.text(row['run'], row['throughput'], f"{row['throughput']:.5f}", 
                                   ha='center', va='bottom', fontsize=8)
                    
                    if goodput_data and plot_type != "Bar":
                        for i, row in goodput_df.iterrows():
                            ax.text(row['run'], row['goodput'], f"{row['goodput']:.5f}", 
                                   ha='center', va='bottom', fontsize=8)
                else:
                    ax.text(0.5, 0.5, "No throughput data available for this experiment", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=10)
        
        self.canvas.draw()
    def update_accumulation_options(self):
        """Update accumulation dropdown options based on current plot settings"""
        plot_type = self.plot_type_var.get()
        data_source = self.data_source_var.get()
        
        supports_accumulated = True
        
        if data_source in ["Latency", "Throughput"]:
            supports_accumulated = False
        
        elif plot_type == "Scatter" and data_source in ["Energy", "Energy Consumed", "Energy Comparative"]:
            supports_accumulated = False
        
        elif plot_type == "Bar" and data_source in ["Energy", "Energy Consumed", "Energy Comparative"]:
            supports_accumulated = False
        
        if supports_accumulated:
            self.accumulation_combo['values'] = ["Simple", "Accumulated"]
            self.accumulation_combo['state'] = "readonly"
        else:
            self.accumulation_combo['values'] = ["Simple"]
            self.accumulation_combo['state'] = "readonly"
            if self.accumulation_var.get() == "Accumulated":
                self.accumulation_var.set("Simple")
    
    def force_plot_update(self):
        """Force update the plot with the current experiment selection"""
        if not self.data or not self.experiment_var.get():
            self.status_var.set("No data loaded or no experiment selected")
            return
        
        self.update_accumulation_options()
            
        self.update_plot()
    
    def get_experiment_time_boundaries(self, experiment_id):
        """
        Extract time boundaries for the selected experiment.
        Uses the start timestamp of the first run and end timestamp of the last run.
        Returns: (start_time_ms, end_time_ms) in milliseconds since epoch, or None if not available
        """
        if not self.data or not experiment_id:
            return None, None
            
        if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
            experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
            
            if not experiment or "runs" not in experiment or not experiment["runs"]:
                return None, None
            
            runs = experiment["runs"]
            if not runs:
                return None, None
                
            first_run = runs[0]
            start_time_ms = first_run.get("start_timestamp")
            
            last_run = runs[-1]
            end_time_ms = last_run.get("end_timestamp")
            
            # If end_timestamp is not available, try to calculate it from start_timestamp + elapsed_time_ms
            if end_time_ms is None and "start_timestamp" in last_run and "elapsed_time_ms" in last_run:
                end_time_ms = last_run["start_timestamp"] + last_run["elapsed_time_ms"]
            
            # If we still don't have valid timestamps, fall back to timestamp field
            if start_time_ms is None and "timestamp" in first_run:
                # If timestamp exists, assume it's the end time and try to calculate start time
                if "elapsed_time_ms" in first_run:
                    start_time_ms = first_run["timestamp"] - first_run["elapsed_time_ms"]
                else:
                    # No way to determine start time, just use timestamp
                    start_time_ms = first_run["timestamp"]
            
            if end_time_ms is None and "timestamp" in last_run:
                end_time_ms = last_run["timestamp"]
            
            # If we have valid timestamps, return them without any buffer
            if start_time_ms is not None and end_time_ms is not None:
                return start_time_ms, end_time_ms
            
            # If we can't determine the time boundaries, fall back to using energy data
            if start_time_ms is None or end_time_ms is None:
                self.status_var.set(f"No explicit time boundaries found for {experiment_id}, trying energy data range.")
                
                # Look for any timestamps in the energy data
                api_energy_data = self.data.get("api_server_energy", [])
                db_energy_data = self.data.get("db_server_energy", [])
                
                energy_timestamps = []
                for entry in api_energy_data + db_energy_data:
                    # Scaphandre timestamps are in seconds, convert to milliseconds
                    if "host" in entry and "timestamp" in entry["host"]:
                        energy_timestamps.append(entry["host"]["timestamp"] * 1000)
                    elif "consumers" in entry:
                        for consumer in entry["consumers"]:
                            if "timestamp" in consumer:
                                energy_timestamps.append(consumer["timestamp"] * 1000)

                if energy_timestamps:
                    # Use the full range of energy data
                    return min(energy_timestamps), max(energy_timestamps)
        
        return None, None
    
    def _filter_energy_data_by_time(self, energy_data, start_time_ms=None, end_time_ms=None, strict_filtering=True):
        """
        Filter energy data to only include entries within the specified time range.
        
        Args:
            energy_data: List of energy data entries
            start_time_ms: Start time in milliseconds (inclusive)
            end_time_ms: End time in milliseconds (inclusive)
            strict_filtering: If True, strictly filter by time boundaries. If False, use fallbacks for better data availability.
            
        Returns:
            Filtered list of energy data entries
        """
        if not energy_data:
            return []
            
        # If no time boundaries provided, return all data (but only if not strict filtering)
        if start_time_ms is None or end_time_ms is None:
            if not strict_filtering:
                self.status_var.set(f"No time boundaries provided - showing all {len(energy_data)} energy data points")
                return energy_data
            else:
                self.status_var.set("No time boundaries available for selected experiment")
                return []
            
        # Convert start/end times to seconds for comparison with energy timestamp
        start_time_sec = start_time_ms / 1000
        end_time_sec = end_time_ms / 1000
        
        # Convert to human-readable format for debugging
        start_time_str = self._convert_to_eet(start_time_ms).strftime('%Y-%m-%d %H:%M:%S')
        end_time_str = self._convert_to_eet(end_time_ms).strftime('%Y-%m-%d %H:%M:%S')
        
        # Get min/max timestamps from the data for debugging
        energy_timestamps = []
        for host_interval in energy_data:
            # Look for timestamps in host info
            if "host" in host_interval and "timestamp" in host_interval["host"]:
                ts = host_interval["host"]["timestamp"]
                if isinstance(ts, (int, float)):
                    # Scaphandre timestamps are typically in seconds, but could be ms if malformed. Normalize to seconds.
                    if ts > 1e10: # If timestamp is suspiciously large, assume milliseconds
                        ts = ts / 1000
                    energy_timestamps.append(ts)
            
            # Also look for timestamps in consumers
            if "consumers" in host_interval:
                for consumer in host_interval["consumers"]:
                    if "timestamp" in consumer:
                        ts = consumer["timestamp"]
                        if isinstance(ts, (int, float)):
                            if ts > 1e10:
                                ts = ts / 1000
                            energy_timestamps.append(ts)
        
        if not energy_timestamps:
            if not strict_filtering:
                self.status_var.set("Warning: No timestamp fields found in energy data")
                return energy_data  # Return all data if no timestamps
            else:
                self.status_var.set("No timestamp fields found in energy data")
                return []
        
        min_timestamp = min(energy_timestamps)
        max_timestamp = max(energy_timestamps)
        
        # Convert energy timestamps to human-readable format
        min_time_str = self._convert_to_eet(min_timestamp * 1000).strftime('%Y-%m-%d %H:%M:%S')
        max_time_str = self._convert_to_eet(max_timestamp * 1000).strftime('%Y-%m-%d %H:%M:%S')
        
        self.status_var.set(
            f"Experiment time: {start_time_str} to {end_time_str} | "
            f"Energy data: {min_time_str} to {max_time_str} | "
            f"Total entries: {len(energy_data)}"
        )
        
        # Check if there's any overlap at all
        if max_timestamp < start_time_sec or min_timestamp > end_time_sec:
            if not strict_filtering:
                self.status_var.set(
                    f"WARNING: No overlap between experiment time ({start_time_str} - {end_time_str}) "
                    f"and energy data ({min_time_str} - {max_time_str}). "
                    f"Showing ALL data instead."
                )
                return energy_data
            else:
                self.status_var.set(
                    f"No overlap between experiment time ({start_time_str} - {end_time_str}) "
                    f"and energy data ({min_time_str} - {max_time_str}). "
                    f"No energy data to display for this experiment."
                )
                return []
        
        filtered_data = []
        for host_interval in energy_data:
            interval_timestamp_sec = None
            
            if "host" in host_interval and "timestamp" in host_interval["host"]:
                ts = host_interval["host"]["timestamp"]
                if isinstance(ts, (int, float)):
                    if ts > 1e10:
                        ts = ts / 1000
                    interval_timestamp_sec = ts
            
            # If host timestamp isn't reliable, check consumer timestamps
            if interval_timestamp_sec is None and "consumers" in host_interval:
                for consumer in host_interval["consumers"]:
                    if "timestamp" in consumer:
                        ts = consumer["timestamp"]
                        if isinstance(ts, (int, float)):
                            if ts > 1e10:
                                ts = ts / 1000
                            interval_timestamp_sec = ts
                            break # Found a timestamp for the interval, use it.
            
            if interval_timestamp_sec is not None and \
               interval_timestamp_sec >= start_time_sec and interval_timestamp_sec <= end_time_sec:
                filtered_data.append(host_interval)
        
        if not filtered_data:
            if not strict_filtering:
                self.status_var.set(
                    f"WARNING: No energy data found within the exact experiment timeframe. "
                    f"Showing ALL data instead."
                )
                return energy_data
            else:
                self.status_var.set(
                    f"No energy data found within the experiment timeframe "
                    f"({start_time_str} - {end_time_str}). "
                    f"No energy data to display for this experiment."
                )
                return []
        
        self.status_var.set(
            f"Filtered energy data: kept {len(filtered_data)}/{len(energy_data)} entries "
            f"({(len(filtered_data)/len(energy_data)*100):.1f}%)"
        )
        
        return filtered_data
    
    def _process_energy_data(self, data_source, target_type, window_size_ms):
        """Process energy data with time-based windowing from the new JSON structure.
        
        Args:
            data_source: List of host interval energy data entries (each containing host info and a consumers list)
            target_type: Type of target to filter by ("api" for container ID, "db" for container name, "host", "java", "postgres")
            window_size_ms: Size of accumulation window in milliseconds
            
        Returns:
            DataFrame with processed data
        """
        # Get target container IDs from instance variables
        target_api_id = self.api_container_id
        target_db_id = self.db_container_id

        # Initialize variables for accumulation
        current_window_start = None
        accumulated_consumption = 0.0
        processed_data = []

        for host_interval in data_source:
            consumption_in_interval = 0.0
            interval_timestamp_sec = None

            # Get timestamp for the host interval (prefer host timestamp if available)
            host_info = host_interval.get("host")
            if host_info and "timestamp" in host_info:
                interval_timestamp_sec = host_info["timestamp"]
                if interval_timestamp_sec > 1e10: # Normalize to seconds if it's in milliseconds
                    interval_timestamp_sec /= 1000

            if target_type == "host":
                if host_info and "consumption" in host_info and interval_timestamp_sec is not None:
                    # Host consumption is directly in microWatts
                    consumption_in_interval = host_info["consumption"]
                else:
                    continue # Skip if host info or consumption/timestamp is missing
            else: # For specific containers/processes, iterate through consumers
                consumers = host_interval.get("consumers", [])
                target_consumer_found_in_interval = False
                for consumer in consumers:
                    container_info = consumer.get("container")
                    consumption = consumer.get("consumption", 0.0)
                    consumer_timestamp = consumer.get("timestamp") # Use consumer's own timestamp if available
                    
                    if consumer_timestamp is not None:
                         # Normalize to seconds if it's in milliseconds
                        if consumer_timestamp > 1e10:
                            consumer_timestamp /= 1000
                        interval_timestamp_sec = consumer_timestamp # Use consumer timestamp as interval timestamp

                    # Skip entries without necessary data (consumption/timestamp)
                    if consumption is None or interval_timestamp_sec is None:
                        continue
                        
                    is_target = False
                    if target_type == "api":
                        if container_info and target_api_id and target_api_id in container_info.get("id", ""): # Partial match
                            is_target = True
                    elif target_type == "db":
                        if container_info and target_db_id and target_db_id in container_info.get("id", ""): # Partial match
                            is_target = True
                    elif target_type == "java":
                        exe_path = consumer.get("exe", "")
                        if exe_path and "java" in exe_path.lower():
                            is_target = True
                    elif target_type == "postgres":
                        exe_path = consumer.get("exe", "")
                        if exe_path and "postgres" in exe_path.lower():
                            is_target = True

                    if is_target:
                        target_consumer_found_in_interval = True
                        consumption_in_interval += consumption # Sum consumption for matching consumers in this interval

            if interval_timestamp_sec is not None and \
               (target_type == "host" or target_consumer_found_in_interval):
                
                consumption_watts = consumption_in_interval / 1_000_000.0 # Convert from microWatts to Watts
                
                # Convert timestamp from seconds to milliseconds for windowing logic
                milliseconds = int(interval_timestamp_sec * 1000)
                
                # Apply windowing logic
                if current_window_start is None:
                    current_window_start = milliseconds - (milliseconds % window_size_ms)
                    accumulated_consumption = consumption_watts
                elif milliseconds >= current_window_start + window_size_ms:
                    # If current data point is past the current window, save the current window's accumulated data
                    dt = self._convert_to_eet(current_window_start)
                    processed_data.append((current_window_start, accumulated_consumption, dt))
                    # Start a new window
                    current_window_start = milliseconds - (milliseconds % window_size_ms)
                    accumulated_consumption = consumption_watts
                else:
                    accumulated_consumption += consumption_watts
                
        # Add the final accumulated point after the loop
        if current_window_start is not None: # check if any data was processed
            dt = self._convert_to_eet(current_window_start)
            processed_data.append((current_window_start, accumulated_consumption, dt))
                
        if not processed_data:
            print(f"WARNING: No processed data generated for target_type='{target_type}'. Returning empty DataFrame.") # DEBUG
            df = pd.DataFrame(columns=['timestamp', 'consumption', 'datetime'])
        else:
            df = pd.DataFrame(processed_data, columns=['timestamp', 'consumption', 'datetime'])
            print(f"DEBUG: Generated DataFrame for '{target_type}' with {len(df)} rows.") # DEBUG

        return df
    
    def _convert_to_eet(self, timestamp, is_milliseconds=True):
        """Convert timestamp to EET datetime object
        
        Args:
            timestamp: Timestamp (in milliseconds by default)
            is_milliseconds: Whether the timestamp is in milliseconds (True) or seconds (False)
        
        Returns:
            Datetime object in EET timezone
        """
        if is_milliseconds:
            timestamp = timestamp / 1000
            
        # Create UTC datetime object
        utc_dt = datetime.fromtimestamp(timestamp, tz=timezone.utc)
        
        # Convert to EET using pytz for proper timezone handling
        eet_dt = utc_dt.astimezone(self.display_timezone)
        
        return eet_dt

    def _get_run_time_boundaries(self, experiment_id):
        """
        Extract the time boundaries for each run in the experiment.
        
        Args:
            experiment_id: ID of the experiment
            
        Returns:
            List of tuples (run_number, start_time_ms, end_time_ms)
        """
        if not self.data or "benchmark_results" not in self.data or "experiments" not in self.data["benchmark_results"]:
            return []
            
        experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
        if not experiment or "runs" not in experiment or not experiment["runs"]:
            return []
            
        run_boundaries = []
        for i, run in enumerate(experiment["runs"]):
            # Get start time
            start_time_ms = run.get("start_timestamp")
            
            # Get end time (either directly or calculated from start + elapsed)
            end_time_ms = run.get("end_timestamp")
            if end_time_ms is None and "start_timestamp" in run and "elapsed_time_ms" in run:
                end_time_ms = run["start_timestamp"] + run["elapsed_time_ms"]
            elif end_time_ms is None and "timestamp" in run:
                # If only timestamp exists (likely the end time), try to estimate start
                end_time_ms = run["timestamp"]
                if "elapsed_time_ms" in run:
                    start_time_ms = end_time_ms - run["elapsed_time_ms"]
            
            # Only include if we have both start and end times
            if start_time_ms is not None and end_time_ms is not None:
                run_boundaries.append((i+1, start_time_ms, end_time_ms))
        
        return run_boundaries

    def get_all_experiments_time_boundaries(self):
        """
        Get the time boundaries that encompass all experiments in the data.
        Returns: (start_time_ms, end_time_ms) in milliseconds since epoch, or None if not available
        """
        if not self.data or "benchmark_results" not in self.data or "experiments" not in self.data["benchmark_results"]:
            return None, None
            
        experiments = self.data["benchmark_results"]["experiments"]
        if not experiments:
            return None, None
            
        all_start_times = []
        all_end_times = []
        
        # Collect all timestamps from all experiments
        for experiment_id, experiment_data in experiments.items():
            start_time, end_time = self.get_experiment_time_boundaries(experiment_id)
            if start_time is not None:
                all_start_times.append(start_time)
            if end_time is not None:
                all_end_times.append(end_time)
        
        # Return the broadest range
        if all_start_times and all_end_times:
            return min(all_start_times), max(all_end_times)
        
        # Fallback to energy data if no experiment timestamps are available
        api_energy_data = self.data.get("api_server_energy", [])
        db_energy_data = self.data.get("db_server_energy", [])
        
        energy_timestamps = []
        for entry in api_energy_data + db_energy_data:
            # Scaphandre timestamps are in seconds, convert to milliseconds
            if "host" in entry and "timestamp" in entry["host"]:
                energy_timestamps.append(entry["host"]["timestamp"] * 1000)
            elif "consumers" in entry:
                for consumer in entry["consumers"]:
                    if "timestamp" in consumer:
                        energy_timestamps.append(consumer["timestamp"] * 1000)
        
        if energy_timestamps:
            # Use the full range of energy data
            return min(energy_timestamps), max(energy_timestamps)
            
        return None, None

    def get_experiments_without_warmup_time_boundaries(self):
        """
        Get the time boundaries for experiments excluding warmup periods.
        Finds the last warmup experiment's end time and uses that as the start time.
        Returns: (start_time_ms, end_time_ms) in milliseconds since epoch, or None if not available
        """
        if not self.data or "benchmark_results" not in self.data or "experiments" not in self.data["benchmark_results"]:
            return None, None
            
        experiments = self.data["benchmark_results"]["experiments"]
        if not experiments:
            return None, None
        
        # Separate warmup and non-warmup experiments
        warmup_experiments = []
        non_warmup_experiments = []
        
        for experiment_id, experiment_data in experiments.items():
            if "warmup" in experiment_id.lower():
                warmup_experiments.append((experiment_id, experiment_data))
            else:
                non_warmup_experiments.append((experiment_id, experiment_data))
        
        # If no warmup experiments, return the full range
        if not warmup_experiments:
            return self.get_all_experiments_time_boundaries()
        
        # Find the latest end time of all warmup experiments
        latest_warmup_end = None
        for experiment_id, experiment_data in warmup_experiments:
            start_time, end_time = self.get_experiment_time_boundaries(experiment_id)
            if end_time is not None:
                if latest_warmup_end is None or end_time > latest_warmup_end:
                    latest_warmup_end = end_time
        
        # If no non-warmup experiments, return None
        if not non_warmup_experiments:
            return None, None
        
        # Get the time boundaries for non-warmup experiments
        non_warmup_start_times = []
        non_warmup_end_times = []
        
        for experiment_id, experiment_data in non_warmup_experiments:
            start_time, end_time = self.get_experiment_time_boundaries(experiment_id)
            if start_time is not None:
                non_warmup_start_times.append(start_time)
            if end_time is not None:
                non_warmup_end_times.append(end_time)
        
        # Calculate the start time: use the latest warmup end time if available, otherwise use the earliest non-warmup start time
        if latest_warmup_end is not None and non_warmup_start_times:
            start_time_ms = max(latest_warmup_end, min(non_warmup_start_times))
        elif non_warmup_start_times:
            start_time_ms = min(non_warmup_start_times)
        else:
            start_time_ms = None
        
        # Calculate the end time
        if non_warmup_end_times:
            end_time_ms = max(non_warmup_end_times)
        else:
            end_time_ms = None
        
        return start_time_ms, end_time_ms

    def _get_experiment_chronology(self):
        """
        Get the chronological ordering of experiments and identify the pauses between them.
        
        Returns:
            List of tuples (experiment_id, start_time_ms, end_time_ms) sorted by start time
        """
        if not self.data or "benchmark_results" not in self.data or "experiments" not in self.data["benchmark_results"]:
            return []
            
        experiments = self.data["benchmark_results"]["experiments"]
        if not experiments:
            return []
        
        # Collect time boundaries for each experiment
        experiment_times = []
        for experiment_id, experiment_data in experiments.items():
            start_time, end_time = self.get_experiment_time_boundaries(experiment_id)
            if start_time is not None and end_time is not None:
                experiment_times.append((experiment_id, start_time, end_time))
        
        # Sort experiments by start time
        experiment_times.sort(key=lambda x: x[1])
        
        return experiment_times
    
    def _sort_experiments_by_start_time(self, experiments):
        """
        Sort experiments by their start timestamp.
        
        Args:
            experiments: Dictionary of experiments
            
        Returns:
            List of experiment IDs sorted by start timestamp (earliest first)
        """
        experiment_timestamps = []
        
        for experiment_id, experiment_data in experiments.items():
            # Get the start timestamp of the first run
            start_timestamp = None
            if "runs" in experiment_data and experiment_data["runs"]:
                first_run = experiment_data["runs"][0]
                start_timestamp = first_run.get("start_timestamp")
            
            # If no start timestamp found, use a very large timestamp to put it at the end
            if start_timestamp is None:
                start_timestamp = float('inf')
            
            experiment_timestamps.append((experiment_id, start_timestamp))
        
        # Sort by timestamp (earliest first)
        experiment_timestamps.sort(key=lambda x: x[1])
        
        # Return just the experiment IDs in sorted order
        return [exp_id for exp_id, _ in experiment_timestamps]
    
    # _browse_mongodb_file is removed as load_folder handles it
    
    def _load_mongodb_data(self, file_path, data_type):
        """
        Loads PowerAPI energy data from a specified JSON file.
        Attempts to auto-select the target based on container IDs from experiment_info.
        """
        try:
            if not os.path.exists(file_path):
                messagebox.showwarning("File Not Found", f"PowerAPI {data_type.upper()} file not found: {os.path.basename(file_path)}")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
                
            if os.path.getsize(file_path) == 0:
                messagebox.showwarning("Empty File", f"PowerAPI {data_type.upper()} file is empty: {os.path.basename(file_path)}")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
            
            with open(file_path, 'r') as file:
                try:
                    data = json.load(file)
                except json.JSONDecodeError as e:
                    messagebox.showerror("Error", f"Invalid JSON in PowerAPI {data_type.upper()} file ({os.path.basename(file_path)}): {str(e)}")
                    if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                    else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                    return False
            
            if not isinstance(data, list):
                messagebox.showerror("Error", f"Invalid data format in PowerAPI {data_type.upper()} file ({os.path.basename(file_path)}): Expected a list of energy measurements")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
            
            if not data:
                messagebox.showwarning("No Data", f"No data entries found in PowerAPI {data_type.upper()} file ({os.path.basename(file_path)})")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
            
            required_fields = ["timestamp", "power", "target"]
            if not all(all(field in entry for field in required_fields) for entry in data):
                messagebox.showerror("Error", 
                    f"Invalid data format in PowerAPI {data_type.upper()} file ({os.path.basename(file_path)}): Some entries are missing required fields ({', '.join(required_fields)}).")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
            
            targets = {entry.get("target", "") for entry in data if entry.get("target")}
            distinct_targets = sorted(list(targets)) # Sort for consistent display

            if not distinct_targets:
                messagebox.showwarning("No Targets", f"No target services found in PowerAPI {data_type.upper()} file ({os.path.basename(file_path)})")
                if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
                else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
                return False
            
            selected_target = None
            container_id_to_match = None
            if data_type == "api":
                self.mongodb_api_target_combo['values'] = distinct_targets
                container_id_to_match = self.api_container_id
            else: # data_type == "db"
                self.mongodb_db_target_combo['values'] = distinct_targets
                container_id_to_match = self.db_container_id

            if container_id_to_match:
                found_match = False
                for target in distinct_targets:
                    if container_id_to_match in target: # Partial match for container ID
                        selected_target = target
                        found_match = True
                        print(f"DEBUG: Auto-selected PowerAPI {data_type.upper()} target '{selected_target}' based on partial ID '{container_id_to_match}'") # DEBUG
                        break
                if not found_match:
                    print(f"WARNING: No PowerAPI {data_type.upper()} target found containing '{container_id_to_match}'. Defaulting to first target if available.") # DEBUG
            
            if not selected_target and distinct_targets: # Fallback to first target if no match or ID not available
                selected_target = distinct_targets[0]
                
            if data_type == "api":
                self.mongodb_api_target_var.set(selected_target if selected_target else "")
                self.mongodb_api_data = data
            else:
                self.mongodb_db_target_var.set(selected_target if selected_target else "")
                self.mongodb_db_data = data
            
            self.status_var.set(f"Loaded PowerAPI {data_type.upper()} Server energy data with {len(distinct_targets)} target services")
            
            return True
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load PowerAPI {data_type.upper()} data from {os.path.basename(file_path)}: {str(e)}")
            self.status_var.set(f"Error loading PowerAPI {data_type.upper()} Server data")
            if data_type == "api": self.mongodb_api_data = None; self.mongodb_api_target_combo['values'] = []; self.mongodb_api_target_var.set("")
            else: self.mongodb_db_data = None; self.mongodb_db_target_combo['values'] = []; self.mongodb_db_target_var.set("")
            return False
    
    def _get_filtered_mongodb_data(self, data, target_service, start_time_ms=None, end_time_ms=None, strict_filtering=True):
        """
        Filters PowerAPI energy data for a specific target service and returns a pandas DataFrame
        
        Args:
            data: PowerAPI energy data
            target_service: Service name to filter by
            start_time_ms: Start time in milliseconds (inclusive)
            end_time_ms: End time in milliseconds (inclusive)
            strict_filtering: If True, strictly filter by time boundaries. If False, use fallbacks for better data availability.
            
        Returns:
            Filtered pandas DataFrame or None if no data available
        """
        if not data or not target_service:
            return None
            
        # Extract relevant data for the target service
        filtered_data_list = [
            {
                "timestamp": entry["timestamp"]["$date"], # PowerAPI timestamps are ISO8601 strings within "$date"
                "power": entry["power"]  # Already in Watts
            }
            for entry in data if entry.get("target") == target_service
        ]
        
        if not filtered_data_list:
            return None
            
        df = pd.DataFrame(filtered_data_list)
        # PowerAPI timestamps are in ISO8601 format strings, which pandas can parse directly
        df['timestamp'] = pd.to_datetime(df['timestamp'], format='ISO8601', utc=True)
        df.sort_values('timestamp', inplace=True)
        
        # Filter by time boundaries if provided
        if start_time_ms is not None and end_time_ms is not None:
            # Convert millisecond timestamps to pandas datetime objects
            start_time_dt = pd.to_datetime(start_time_ms, unit='ms', utc=True)
            end_time_dt = pd.to_datetime(end_time_ms, unit='ms', utc=True)
            
            original_len = len(df)
            df = df[(df['timestamp'] >= start_time_dt) & (df['timestamp'] <= end_time_dt)]
            
            if df.empty:
                if strict_filtering:
                    self.status_var.set(f"No PowerAPI data for '{target_service}' found within the experiment time range.")
                    return None
                else:
                    self.status_var.set(f"Warning: No PowerAPI data for '{target_service}' found within time range, showing all data.")
                    # Revert to original data if strict filtering is off and no data was found in range
                    df = pd.DataFrame(filtered_data_list)
                    df['timestamp'] = pd.to_datetime(df['timestamp'], format='ISO8601', utc=True)
                    df.sort_values('timestamp', inplace=True)
        
        return df

    # Add a new function to process MongoDB data with windowing
    def _ms_time_formatter(self):
        """Create a time formatter that includes milliseconds"""
        def _format(x, pos=None):
            dt = mdates.num2date(x, tz=self.display_timezone)
            return dt.strftime('%H:%M:%S.%f')[:-3]  # Remove last 3 digits to show milliseconds
        return mticker.FuncFormatter(_format)

    def _process_mongodb_data(self, df, window_size_ms):
        """Process PowerAPI energy data with time-based windowing

        Args:
            df: DataFrame with PowerAPI energy data (must have 'timestamp' and 'power' columns)
            window_size_ms: Size of accumulation window in milliseconds

        Returns:
            DataFrame with processed data using windowing
        """
        if df is None or df.empty:
            return pd.DataFrame(columns=['timestamp', 'power', 'datetime'])

        # Initialize variables for accumulation
        processed_data = []
        # Convert timestamps to milliseconds since epoch for consistent handling
        df['timestamp_ms'] = (df['timestamp'].astype(np.int64) // 10**6)  # Convert nanoseconds to milliseconds

        # Sort by timestamp
        df = df.sort_values('timestamp_ms')

        # Apply windowing
        current_window_start = None
        accumulated_power = 0.0

        for _, row in df.iterrows():
            milliseconds = row['timestamp_ms']
            power = row['power']

            if current_window_start is None:
                # Align to window boundary: start of the window that contains 'milliseconds'
                current_window_start = milliseconds - (milliseconds % window_size_ms)
                accumulated_power = power
            elif milliseconds >= current_window_start + window_size_ms:
                # Data point falls into a new window, or past the current one.
                # Save the current window's accumulated data (average power for the window)
                dt = self._convert_to_eet(current_window_start)
                processed_data.append((current_window_start, accumulated_power, dt))

                # Start a new window, aligning its start to the window boundary
                current_window_start = milliseconds - (milliseconds % window_size_ms)
                accumulated_power = power
            else:
                accumulated_power += power

        # Add the final point after the loop
        if current_window_start is not None:
            dt = self._convert_to_eet(current_window_start)
            processed_data.append((current_window_start, accumulated_power, dt))

        # Convert processed data to DataFrame
        result_df = pd.DataFrame(processed_data, columns=['timestamp_ms', 'power', 'datetime'])

        return result_df


if __name__ == "__main__":
    root = tk.Tk()
    app = ExperimentVisualizer(root)

    root.mainloop()
