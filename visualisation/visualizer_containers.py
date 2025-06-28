import tkinter as tk
from tkinter import ttk, filedialog, messagebox
import json
import pandas as pd
import numpy as np
import os
from datetime import datetime, timedelta, timezone
import pytz  # For timezone handling
import matplotlib.pyplot as plt
from matplotlib.figure import Figure
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg, NavigationToolbar2Tk
from matplotlib.dates import DateFormatter
import matplotlib.dates as mdates
import matplotlib.ticker as mticker
import plotly.graph_objects as go
from plotly.subplots import make_subplots

class ExperimentVisualizer:
    def __init__(self, root):
        self.root = root
        self.root.title("Experiment Visualizer")
        self.data = None
        self.mongodb_data = None
        self.mongodb_api_data = None
        self.mongodb_db_data = None
        self.api_container_id = None # Add instance variable for API container ID
        self.db_container_id = None  # Add instance variable for DB container ID
        # Use pytz for proper timezone handling
        self.display_timezone = pytz.timezone('Europe/Helsinki')  # EET timezone
        
        # Variables to control host energy visibility
        self.show_host_api_var = tk.BooleanVar(value=True)
        self.show_host_db_var = tk.BooleanVar(value=True)
        
        # Variables to control scaphandre energy visibility
        self.show_scaphandre_api_var = tk.BooleanVar(value=True)
        self.show_scaphandre_db_var = tk.BooleanVar(value=True)
        
        # Current tab state
        self.current_tab = "plot"  # "plot" or "info"
        
        # Set up main frame
        self.main_frame = ttk.Frame(root)
        self.main_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        # Set up the UI components - pack bottom controls first to ensure they're visible
        self.setup_menu()
        self.setup_top_controls()
        self.setup_bottom_controls()  # Pack bottom controls BEFORE content area
        self.setup_main_content_area()
        
        # Status bar
        self.status_var = tk.StringVar()
        self.status_var.set("Ready. Please load a data file.")
        self.status_bar = ttk.Label(root, textvariable=self.status_var, relief=tk.SUNKEN, anchor=tk.W)
        self.status_bar.pack(side=tk.BOTTOM, fill=tk.X)
    
    def setup_menu(self):
        menu_bar = tk.Menu(self.root)
        file_menu = tk.Menu(menu_bar, tearoff=0)
        file_menu.add_command(label="Load Data", command=self.load_data)
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
        
        # Tab buttons row
        tab_frame = ttk.Frame(top_frame)
        tab_frame.pack(fill=tk.X, pady=(0, 10))
        
        # Create tab buttons
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
        
        # Load Data button
        style.configure('Big.TButton', font=('Arial', 11, 'bold'))
        load_button = ttk.Button(tab_frame, text="Load Data", command=self.load_data, style='Big.TButton')
        load_button.pack(side=tk.RIGHT)
        
        # Experiment selection row (always visible)
        exp_frame = ttk.Frame(top_frame)
        exp_frame.pack(fill=tk.X, pady=(0, 5))
        
        ttk.Label(exp_frame, text="Select Experiment:", font=("Arial", 10, "bold")).pack(side=tk.LEFT, padx=5)
        self.experiment_var = tk.StringVar()
        self.experiment_selector = ttk.Combobox(exp_frame, textvariable=self.experiment_var, state="readonly", width=30)
        self.experiment_selector.pack(side=tk.LEFT, padx=5)
        self.experiment_selector.bind("<<ComboboxSelected>>", self.on_experiment_selected)
        
        # Refresh button
        refresh_button = ttk.Button(exp_frame, text="Refresh", command=self.force_plot_update)
        refresh_button.pack(side=tk.LEFT, padx=5)
        
        # Update tab button appearance
        self.update_tab_buttons()
    
    def setup_main_content_area(self):
        """Setup the main content area that switches between plot and experiment info"""
        self.content_frame = ttk.Frame(self.main_frame)
        # Don't expand fully - leave room for bottom controls
        self.content_frame.pack(fill=tk.BOTH, expand=True, pady=(0, 0))
        
        # Create plot frame
        self.setup_plot_frame()
        
        # Create experiment info frame
        self.setup_experiment_info_frame()
        
        # Show the default tab
        self.switch_tab("plot")
    
    def setup_plot_frame(self):
        """Setup the plot visualization frame"""
        self.plot_frame = ttk.Frame(self.content_frame)
        
        # MongoDB data selection frame - more compact
        mongodb_frame = ttk.LabelFrame(self.plot_frame, text="PowerAPI Energy Data")
        mongodb_frame.pack(fill=tk.X, padx=5, pady=2)
        
        # MongoDB API server data
        api_frame = ttk.Frame(mongodb_frame)
        api_frame.pack(fill=tk.X, padx=3, pady=2)
        
        self.mongodb_api_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(api_frame, text="API Energy",
                       variable=self.mongodb_api_var).pack(side=tk.LEFT, padx=3)
        self.mongodb_api_file_var = tk.StringVar()
        ttk.Entry(api_frame, textvariable=self.mongodb_api_file_var, width=20).pack(side=tk.LEFT, padx=3)
        ttk.Button(api_frame, text="Browse", 
                  command=lambda: self._browse_mongodb_file("api")).pack(side=tk.LEFT, padx=3)
        
        # MongoDB API target service selection
        self.mongodb_api_target_var = tk.StringVar()
        self.mongodb_api_target_combo = ttk.Combobox(api_frame, textvariable=self.mongodb_api_target_var, 
                                                    state="readonly", width=25)
        self.mongodb_api_target_combo.pack(side=tk.LEFT, padx=3)
        
        # MongoDB DB server data
        db_frame = ttk.Frame(mongodb_frame)
        db_frame.pack(fill=tk.X, padx=3, pady=2)
        
        self.mongodb_db_var = tk.BooleanVar(value=False)
        ttk.Checkbutton(db_frame, text="DB Energy",
                       variable=self.mongodb_db_var).pack(side=tk.LEFT, padx=3)
        self.mongodb_db_file_var = tk.StringVar()
        ttk.Entry(db_frame, textvariable=self.mongodb_db_file_var, width=20).pack(side=tk.LEFT, padx=3)
        ttk.Button(db_frame, text="Browse", 
                  command=lambda: self._browse_mongodb_file("db")).pack(side=tk.LEFT, padx=3)
        
        # MongoDB DB target service selection
        self.mongodb_db_target_var = tk.StringVar()
        self.mongodb_db_target_combo = ttk.Combobox(db_frame, textvariable=self.mongodb_db_target_var, 
                                                   state="readonly", width=25)
        self.mongodb_db_target_combo.pack(side=tk.LEFT, padx=3)
        
        # Plot area - reduced padding to save space
        plot_area_frame = ttk.Frame(self.plot_frame)
        plot_area_frame.pack(fill=tk.BOTH, expand=True, padx=5, pady=5)
        
        # Create the matplotlib figure and canvas - reduce size to leave room for controls
        self.fig = Figure(figsize=(8, 4), dpi=100)
        self.canvas = FigureCanvasTkAgg(self.fig, master=plot_area_frame)
        self.canvas.get_tk_widget().pack(fill=tk.BOTH, expand=True)
        
        # Add matplotlib toolbar for zoom, pan, save, etc.
        toolbar_frame = ttk.Frame(plot_area_frame)
        toolbar_frame.pack(fill=tk.X)
        self.toolbar = NavigationToolbar2Tk(self.canvas, toolbar_frame)
        self.toolbar.update()
    
    def setup_experiment_info_frame(self):
        """Setup the experiment information frame"""
        self.info_frame = ttk.Frame(self.content_frame)
        
        # Title
        ttk.Label(self.info_frame, text="Experiment Details", font=("Arial", 14, "bold")).pack(pady=10)
        
        # Scrollable frame for details
        frame_canvas = ttk.Frame(self.info_frame)
        frame_canvas.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)
        
        canvas = tk.Canvas(frame_canvas)
        self.details_frame = ttk.Frame(canvas) # Frame that holds the details
        
        # Vertical Scrollbar
        v_scrollbar = ttk.Scrollbar(frame_canvas, orient="vertical", command=canvas.yview)
        canvas.configure(yscrollcommand=v_scrollbar.set)
        
        # Horizontal Scrollbar
        h_scrollbar = ttk.Scrollbar(frame_canvas, orient="horizontal", command=canvas.xview)
        canvas.configure(xscrollcommand=h_scrollbar.set)
        
        # Packing order:
        v_scrollbar.pack(side=tk.RIGHT, fill=tk.Y)
        h_scrollbar.pack(side=tk.BOTTOM, fill=tk.X)
        canvas.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        
        # Create window in canvas for details_frame
        canvas_frame = canvas.create_window((0, 0), window=self.details_frame, anchor="nw")
        
        # Update scrollregion when details_frame (content) size changes
        def configure_canvas(event):
            canvas.configure(scrollregion=canvas.bbox("all"))
        
        self.details_frame.bind("<Configure>", configure_canvas)
    
    def setup_bottom_controls(self):
        """Setup the bottom controls that are always visible"""
        # Create a frame for bottom controls - use pack side=BOTTOM to ensure it's always visible
        bottom_frame = ttk.LabelFrame(self.main_frame, text="Plot Controls", relief=tk.RAISED, borderwidth=2)
        bottom_frame.pack(side=tk.BOTTOM, fill=tk.X, pady=(5, 5))
        bottom_frame.configure(height=100)  # Force minimum height
        
        # Create a simple frame for controls 
        controls_frame = ttk.Frame(bottom_frame)
        controls_frame.pack(fill=tk.X, padx=5, pady=5)
        
        # If we need scrolling later, we can wrap this in a canvas, but for now keep it simple
        scrollable_control_frame = controls_frame
        
        # Create two rows of controls for better layout
        # First row - main controls
        row1_frame = ttk.Frame(scrollable_control_frame)
        row1_frame.pack(fill=tk.X, pady=2)
        
        # Second row - additional controls
        row2_frame = ttk.Frame(scrollable_control_frame)
        row2_frame.pack(fill=tk.X, pady=2)
        
        # Row 1 controls
        ttk.Button(row1_frame, text="Refresh Plot", command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        
        # Plot type selection
        ttk.Label(row1_frame, text="Plot Type:").pack(side=tk.LEFT, padx=(10, 3))
        self.plot_type_var = tk.StringVar(value="Line")
        plot_type_combo = ttk.Combobox(row1_frame, textvariable=self.plot_type_var, 
                                      values=["Line", "Bar", "Scatter"], state="readonly", width=8)
        plot_type_combo.pack(side=tk.LEFT, padx=3)
        plot_type_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        # Plot data selection
        ttk.Label(row1_frame, text="Data Source:").pack(side=tk.LEFT, padx=(10, 3))
        self.data_source_var = tk.StringVar(value="Energy")
        data_source_combo = ttk.Combobox(row1_frame, textvariable=self.data_source_var, 
                                      values=["Energy", "Energy Comparative", "Energy Consumed", "Latency", "Throughput"], state="readonly", width=12)
        data_source_combo.pack(side=tk.LEFT, padx=3)
        data_source_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        # Row 2 controls
        # Accumulation mode selection
        ttk.Label(row2_frame, text="Accumulation:").pack(side=tk.LEFT, padx=3)
        self.accumulation_var = tk.StringVar(value="Simple")
        self.accumulation_combo = ttk.Combobox(row2_frame, textvariable=self.accumulation_var, 
                                      values=["Simple", "Accumulated"], state="readonly", width=8)
        self.accumulation_combo.pack(side=tk.LEFT, padx=3)
        self.accumulation_combo.bind("<<ComboboxSelected>>", lambda e: self.force_plot_update())
        
        # Initialize accumulation options based on default settings
        self.update_accumulation_options()
        
        # Window size for accumulation (ms)
        ttk.Label(row2_frame, text="Window Size (ms):").pack(side=tk.LEFT, padx=(10, 3))
        self.window_size_var = tk.StringVar(value="100")
        window_size_entry = ttk.Entry(row2_frame, textvariable=self.window_size_var, width=6)
        window_size_entry.pack(side=tk.LEFT, padx=3)
        window_size_entry.bind("<Return>", lambda e: self.force_plot_update())
        
        # Checkboxes for energy visibility
        ttk.Checkbutton(row2_frame, text="Show Scaphandre API", 
                       variable=self.show_scaphandre_api_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=(10, 3))
        ttk.Checkbutton(row2_frame, text="Show Scaphandre DB", 
                       variable=self.show_scaphandre_db_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        ttk.Checkbutton(row2_frame, text="Show Host API", 
                       variable=self.show_host_api_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
        ttk.Checkbutton(row2_frame, text="Show Host DB", 
                       variable=self.show_host_db_var, command=self.force_plot_update).pack(side=tk.LEFT, padx=3)
    
    def switch_tab(self, tab_name):
        """Switch between plot and experiment info tabs"""
        self.current_tab = tab_name
        
        # Hide all frames first
        self.plot_frame.pack_forget()
        self.info_frame.pack_forget()
        
        # Show the selected frame
        if tab_name == "plot":
            self.plot_frame.pack(fill=tk.BOTH, expand=True)
        elif tab_name == "info":
            self.info_frame.pack(fill=tk.BOTH, expand=True)
            # Refresh experiment info when switching to info tab
            self.refresh_experiment_info()
        
        # Update tab button appearance
        self.update_tab_buttons()
    
    def update_tab_buttons(self):
        """Update the appearance of tab buttons to show which one is active"""
        # Create styles for active and inactive tabs
        style = ttk.Style()
        
        # Configure styles for tab buttons
        style.configure('TabActive.TButton', font=('Arial', 10, 'bold'), foreground='blue')
        style.configure('TabInactive.TButton', font=('Arial', 10, 'normal'), foreground='black')
        
        # Reset both buttons to inactive style
        self.plot_tab_button.configure(style='TabInactive.TButton')
        self.info_tab_button.configure(style='TabInactive.TButton')
        
        # Highlight the active tab
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

    def setup_left_panel(self):
        # This method is no longer needed with the new tab structure
        pass

    def setup_right_panel(self):
        # This method is no longer needed with the new tab structure
        pass

    def load_data(self):
        file_path = filedialog.askopenfilename(
            title="Select Data File",
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")]
        )
        
        if not file_path:
            return
        
        try:
            self.status_var.set(f"Loading data from {os.path.basename(file_path)}...")
            self.root.update_idletasks()
            
            print(f"Attempting to load JSON from: {file_path}") # DEBUG
            with open(file_path, 'r') as file:
                self.data = json.load(file)
            
            print(f"Successfully loaded JSON. Data type: {type(self.data)}") # DEBUG
            if isinstance(self.data, dict):
                 print(f"Top-level keys found: {list(self.data.keys())}") # DEBUG
            else:
                 print("Loaded data is not a dictionary.") # DEBUG
                 
            # Define the expected keys
            # We absolutely need benchmark_results, but energy data is optional -> REVERTING: Making energy keys required again
            # required_keys = ["benchmark_results"]
            # optional_keys = ["api_server_energy", "db_server_energy"]
            expected_keys = ["api_server_energy", "db_server_energy", "benchmark_results"]
            
            # Check for required keys
            # missing_required = [key for key in required_keys if key not in self.data]
            # if missing_required:
            #     print(f"ERROR: The file is missing the following required top-level keys: {missing_required}") # DEBUG
            #     messagebox.showerror("Error", f"The file is missing required data: {', '.join(missing_required)}")
            #     self.data = None # Clear invalid data
            #     self.status_var.set("Error: Missing required data.")
            #     return
            #     
            # # Check for optional keys and warn if missing
            # missing_optional = [key for key in optional_keys if key not in self.data]
            # if missing_optional:
            #      print(f"WARNING: The file is missing the following optional top-level keys: {missing_optional}. Energy plots may be empty.") # DEBUG
            #      self.status_var.set(f"Warning: Missing optional energy keys: {', '.join(missing_optional)}")
            # else:
            #     print("File structure check passed. Found all expected keys.") # DEBUG
            
            # Check if the data has the expected structure (Restoring strict check)
            missing_keys = [key for key in expected_keys if key not in self.data or self.data[key] is None]
            
            if missing_keys:
                 print(f"ERROR: The file is missing the following expected top-level keys: {missing_keys}") # DEBUG
                 messagebox.showerror("Error", f"The file does not have the expected structure or is missing required keys: {', '.join(missing_keys)}")
                 self.data = None # Clear invalid data
                 self.status_var.set("Error: Invalid file structure.")
                 return
            
            print("File structure check passed. Found all expected keys.") # DEBUG
            
            # Extract container IDs if available
            if "container_info" in self.data and isinstance(self.data["container_info"], dict):
                self.api_container_id = self.data["container_info"].get("api_container_id")
                self.db_container_id = self.data["container_info"].get("db_container_id")
                print(f"Found container IDs: API={self.api_container_id}, DB={self.db_container_id}") # DEBUG
                if not self.api_container_id or not self.db_container_id:
                    print("WARNING: Missing 'api_container_id' or 'db_container_id' within 'container_info'. Energy filtering might fail.") # DEBUG
                    self.status_var.set("Warning: Missing container IDs in data.")
            else:
                 print("WARNING: 'container_info' key not found or not a dictionary. Cannot extract container IDs.") # DEBUG
                 self.status_var.set("Warning: Container info missing, cannot link energy data.")
                 self.api_container_id = None # Ensure IDs are None if container_info is missing
                 self.db_container_id = None

            # Extract experiment names from benchmark_results.experiments
            # (Check benchmark_results structure as well)
            if "benchmark_results" in self.data and isinstance(self.data["benchmark_results"], dict) and "experiments" in self.data["benchmark_results"] and isinstance(self.data["benchmark_results"]["experiments"], dict):
                experiments = self.data["benchmark_results"]["experiments"]
                experiment_ids = list(experiments.keys())
                print(f"Found {len(experiment_ids)} experiments in benchmark_results.") # DEBUG
                
                # Add "All data" and "All Experiments" as the first options
                all_data_option = "All data"
                all_experiments_option = "All Experiments"
                self.experiment_selector['values'] = [all_data_option, all_experiments_option] + experiment_ids
                
                # Select the first option (All data)
                self.experiment_selector.current(0)
                self.experiment_var.set(all_data_option)
                self.on_experiment_selected(None) # Trigger update
                
                self.status_var.set(f"Loaded {len(experiment_ids)} experiments from {os.path.basename(file_path)}")
            else:
                print("ERROR: 'benchmark_results' key found, but it does not contain a valid 'experiments' dictionary.") # DEBUG
                if "benchmark_results" not in self.data:
                    err_msg = "'benchmark_results' key is missing."
                elif not isinstance(self.data["benchmark_results"], dict):
                    err_msg = "'benchmark_results' is not a dictionary."
                elif "experiments" not in self.data["benchmark_results"]:
                    err_msg = "'experiments' key is missing within 'benchmark_results'."
                elif not isinstance(self.data["benchmark_results"]["experiments"], dict):
                    err_msg = "'experiments' within 'benchmark_results' is not a dictionary."
                else:
                    err_msg = "Unknown structure issue with 'benchmark_results'."
                    
                messagebox.showerror("Error", f"No valid experiments found in the data file.\nReason: {err_msg}")
                self.status_var.set("No experiments found in the data file.")
                # Optionally clear data or handle differently
                # self.data = None 
                # Clear experiment selector
                self.experiment_selector['values'] = []
                self.experiment_var.set("")
                # Clear details and plot
                for widget in self.details_frame.winfo_children():
                    widget.destroy()
                self.fig.clear()
                self.canvas.draw()
                
        except json.JSONDecodeError as e:
            print(f"ERROR: Failed to decode JSON from file: {file_path}\nError: {e}") # DEBUG
            messagebox.showerror("Error", f"Failed to parse JSON file: {str(e)}\nPlease check the file content.")
            self.status_var.set("Error loading data file: Invalid JSON")
            self.data = None
        except Exception as e:
            print(f"ERROR: An unexpected error occurred during data loading: {e}") # DEBUG
            import traceback
            traceback.print_exc() # Print full traceback for unexpected errors
            messagebox.showerror("Error", f"Failed to load data: {str(e)}")
            self.status_var.set("Error loading data file")
            self.data = None
    
    def on_experiment_selected(self, event):
        if not self.data or not self.experiment_var.get():
            return
            
        selected_experiment = self.experiment_var.get()
        
        # Clear previous details
        for widget in self.details_frame.winfo_children():
            widget.destroy()
            
        # Handle "All data" selection
        if selected_experiment == "All data":
            if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
                experiments = self.data["benchmark_results"]["experiments"]
                
                # Display a summary for all data
                row = 0
                ttk.Label(self.details_frame, text="All Data Summary", font=("Arial", 12, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
                row += 1
                
                ttk.Label(self.details_frame, text="Displaying all available energy data without time filtering", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Total Experiments: {len(experiments)}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                # Show energy data statistics
                api_energy_count = len(self.data.get("api_server_energy", []))
                db_energy_count = len(self.data.get("db_server_energy", []))
                
                ttk.Label(self.details_frame, text=f"API Server Energy Entries: {api_energy_count}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                ttk.Label(self.details_frame, text=f"DB Server Energy Entries: {db_energy_count}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                # Update the plot with all data
                self.update_plot()
                return
        
        # Handle "All Experiments" selection
        elif selected_experiment == "All Experiments":
            if "benchmark_results" in self.data and "experiments" in self.data["benchmark_results"]:
                experiments = self.data["benchmark_results"]["experiments"]
                
                # Display a summary of all experiments
                row = 0
                ttk.Label(self.details_frame, text="All Experiments Summary", font=("Arial", 12, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=5)
                row += 1
                
                ttk.Label(self.details_frame, text=f"Total Experiments: {len(experiments)}", font=("Arial", 10)).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
                row += 1
                
                # Add a brief overview of each experiment
                for exp_id, exp_data in experiments.items():
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
                    
                    # Add a separator
                    ttk.Separator(self.details_frame, orient='horizontal').grid(row=row, column=0, columnspan=2, sticky='ew', padx=5, pady=5)
                    row += 1
                
                # Update the plot with all experiments
                self.update_plot()
                return
        
        # Regular single experiment selection
        experiment = self.data["benchmark_results"]["experiments"].get(selected_experiment, {})
        if not experiment:
            return
            
        row = 0
        
        # Show experiment details
        ttk.Label(self.details_frame, text="Experiment Details:", font=("Arial", 10, "bold")).grid(row=row, column=0, columnspan=2, sticky=tk.W, padx=5, pady=2)
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
                
                # Show start time in human readable format with EET timezone
                if "start_timestamp" in run:
                    # Convert milliseconds UTC to datetime with EET timezone
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
        
        # Update the plot for this experiment
        self.update_plot()
        
        self.status_var.set(f"Displaying data for experiment: {selected_experiment}")
    
    def update_plot(self):
        """Update the plot with selected experiment data"""
        if not self.data or not self.experiment_var.get():
            return
        
        experiment_id = self.experiment_var.get()
        self.status_var.set(f"Updating plot for: {experiment_id}")
        self.root.update_idletasks()  # Force UI update
        
        # Clear the figure
        self.fig.clear()
        
        # Get the experiment time boundaries
        if experiment_id == "All data":
            # No time filtering for "All data" option
            start_time_ms, end_time_ms = None, None
            experiment_ids = list(self.data["benchmark_results"]["experiments"].keys())
            # print(f"DEBUG: 'All data' selected - no time filtering will be applied")
        elif experiment_id == "All Experiments":
            # Get the combined time boundaries for all experiments
            start_time_ms, end_time_ms = self.get_all_experiments_time_boundaries()
            
            # Get the list of all experiment IDs
            experiment_ids = list(self.data["benchmark_results"]["experiments"].keys())
        else:
            # Get boundaries for the single selected experiment
            start_time_ms, end_time_ms = self.get_experiment_time_boundaries(experiment_id)
            experiment_ids = [experiment_id]
            
            # Debug output for experiment boundaries (optional - can be removed in production)
            # print(f"DEBUG: Experiment '{experiment_id}' boundaries: start={start_time_ms}, end={end_time_ms}")
            # if start_time_ms and end_time_ms:
            #     start_str = self._convert_to_eet(start_time_ms).strftime('%Y-%m-%d %H:%M:%S')
            #     end_str = self._convert_to_eet(end_time_ms).strftime('%Y-%m-%d %H:%M:%S')
            #     print(f"DEBUG: Experiment time range: {start_str} to {end_str} (EET)")
            # else:
            #     print(f"DEBUG: Failed to get time boundaries for experiment '{experiment_id}'")
        
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
                # print(f"DEBUG: Time boundaries are None - start_time_ms: {start_time_ms}, end_time_ms: {end_time_ms}")
                self.status_var.set("No time boundaries available for selected experiment")
            self.root.update_idletasks()
        
        # Filter energy data to only include entries within the time range
        api_energy_data = self.data.get("api_server_energy", [])
        db_energy_data = self.data.get("db_server_energy", [])
        
        # Determine if we should use strict filtering (for specific experiments) or lenient (for all experiments/all data)
        use_strict_filtering = (experiment_id != "All Experiments" and experiment_id != "All data")
        
        filtered_api_data = self._filter_energy_data_by_time(api_energy_data, start_time_ms, end_time_ms, use_strict_filtering)
        filtered_db_data = self._filter_energy_data_by_time(db_energy_data, start_time_ms, end_time_ms, use_strict_filtering)
        
        # Debug output (optional - can be removed in production)
        # print(f"DEBUG: Original API data: {len(api_energy_data)} entries, Filtered: {len(filtered_api_data)} entries")
        # print(f"DEBUG: Original DB data: {len(db_energy_data)} entries, Filtered: {len(filtered_db_data)} entries")
        # print(f"DEBUG: Strict filtering: {use_strict_filtering}, Experiment: {experiment_id}")
        # if start_time_ms and end_time_ms:
        #     print(f"DEBUG: Time range: {start_time_ms} to {end_time_ms} ms")
        
        # Get the selected plot type and data source
        plot_type = self.plot_type_var.get()
        data_source = self.data_source_var.get()
        accumulation_mode = self.accumulation_var.get()
        
        try:
            window_size_ms = int(self.window_size_var.get())
        except ValueError:
            window_size_ms = 100  # Default if parsing fails
        
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
            ax.set_xlabel('Time (EET)', fontsize=10)
            ax.set_ylabel('Energy Consumption (Watts)', fontsize=10)  # Updated unit label
            
            # Set plot title based on selected experiments
            if experiment_id == "All data":
                ax.set_title(f'Energy Consumption - All Data (No Time Filtering)', fontsize=12)
            elif experiment_id == "All Experiments":
                ax.set_title(f'Energy Consumption for All Experiments', fontsize=12)
            else:
                ax.set_title(f'Energy Consumption for Experiment: {experiment_id}', fontsize=12)
            
            # Format the time axis
            ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
            ax.tick_params(axis='x', rotation=45)
            
            # If either dataframe is empty, show a message
            if api_df.empty and db_df.empty and host_api_df.empty and host_db_df.empty and not self.mongodb_api_var.get() and not self.mongodb_db_var.get():
                self.status_var.set("No energy data available for the selected time period")
                ax.text(0.5, 0.5, "No energy data available", 
                       horizontalalignment='center', verticalalignment='center',
                       transform=ax.transAxes, fontsize=14)
            else:
                # Plot the data based on the selected plot type and accumulation mode
                if accumulation_mode == "Simple":
                    # Simple mode - show instantaneous consumption
                    if plot_type == "Line":
                        if not api_df.empty and self.show_scaphandre_api_var.get():
                            ax.plot(api_df['datetime'], api_df['consumption'], 
                                   label='Scaphandre API Server (Java)', color='red', linewidth=2, marker=None)
                        
                        if not db_df.empty and self.show_scaphandre_db_var.get():
                            ax.plot(db_df['datetime'], db_df['consumption'], 
                                   label='Scaphandre DB Server (Postgres)', color='blue', linewidth=2, marker=None)
                        
                        if not host_api_df.empty and self.show_host_api_var.get():
                             ax.plot(host_api_df['datetime'], host_api_df['consumption'], 
                                    label='Host (API Server)', color='purple', linestyle='--', linewidth=1.5, marker=None)
                        
                        if not host_db_df.empty and self.show_host_db_var.get():
                             ax.plot(host_db_df['datetime'], host_db_df['consumption'], 
                                    label='Host (DB Server)', color='grey', linestyle=':', linewidth=1.5, marker=None)
                        
                        # Plot MongoDB data if available
                        if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                            api_target = self.mongodb_api_target_var.get()
                            if api_target:
                                mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_api_df is not None:
                                    # Apply windowing to MongoDB data
                                    processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                                    if not processed_mongo_api_df.empty:
                                        ax.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                               label='PowerAPI API', color='green', linewidth=2, marker=None)
                        
                        if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                            db_target = self.mongodb_db_target_var.get()
                            if db_target:
                                mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_db_df is not None:
                                    # Apply windowing to MongoDB data
                                    processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                    if not processed_mongo_db_df.empty:
                                        ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                               label='PowerAPI DB', color='orange', linewidth=2, marker=None)
                    
                    elif plot_type == "Bar":
                        # For bar plots, we'll use bar width based on data density
                        bar_width = 0.0002  # Adjust as needed for visibility
                        
                        if not api_df.empty and self.show_scaphandre_api_var.get():
                            ax.bar(api_df['datetime'], api_df['consumption'], 
                                   width=bar_width, label='Scaphandre API Server (Java)', color='red', alpha=0.7)
                        
                        if not db_df.empty and self.show_scaphandre_db_var.get():
                            ax.bar(db_df['datetime'], db_df['consumption'], 
                                   width=bar_width, label='Scaphandre DB Server (Postgres)', color='blue', alpha=0.7)
                        
                        if not host_api_df.empty and self.show_host_api_var.get():
                            ax.bar(host_api_df['datetime'], host_api_df['consumption'], 
                                   width=bar_width * 0.8, label='Host (API Server)', color='purple', alpha=0.5)
                        
                        if not host_db_df.empty and self.show_host_db_var.get():
                            ax.bar(host_db_df['datetime'], host_db_df['consumption'], 
                                   width=bar_width * 0.6, label='Host (DB Server)', color='grey', alpha=0.5)
                        
                        # Plot MongoDB data if available
                        if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                            api_target = self.mongodb_api_target_var.get()
                            if api_target:
                                mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_api_df is not None:
                                    # Apply windowing to MongoDB data
                                    processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                                    if not processed_mongo_api_df.empty:
                                        ax.bar(processed_mongo_api_df['datetime'], processed_mongo_api_df['power'],
                                               width=bar_width, label='PowerAPI API', color='green', alpha=0.7)
                        
                        if self.mongodb_db_var.get() and self.mongodb_db_data is not None:
                            db_target = self.mongodb_db_target_var.get()
                            if db_target:
                                mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                                if mongo_db_df is not None:
                                    # Apply windowing to MongoDB data
                                    processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                    if not processed_mongo_db_df.empty:
                                        ax.bar(processed_mongo_db_df['datetime'], processed_mongo_db_df['power'],
                                               width=bar_width, label='PowerAPI DB', color='orange', alpha=0.7)
                
                elif accumulation_mode == "Accumulated":
                    # Accumulated mode - show cumulative energy consumption
                    ax.set_ylabel('Cumulative Energy Consumption (Watts)', fontsize=10)
                    
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
                               label='Host (API Server) - Cumulative', color='purple', linestyle='--', linewidth=1.5, marker=None)
                    
                    if not host_db_df.empty and self.show_host_db_var.get():
                        host_db_df['cumulative'] = host_db_df['consumption'].cumsum()
                        ax.plot(host_db_df['datetime'], host_db_df['cumulative'], 
                               label='Host (DB Server) - Cumulative', color='grey', linestyle=':', linewidth=1.5, marker=None)
                    
                    # Plot MongoDB data if available
                    if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                        api_target = self.mongodb_api_target_var.get()
                        if api_target:
                            mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                            if mongo_api_df is not None:
                                # Apply windowing to MongoDB data
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
                                # Apply windowing to MongoDB data
                                processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                                if not processed_mongo_db_df.empty:
                                    processed_mongo_db_df['cumulative'] = processed_mongo_db_df['power'].cumsum()
                                    ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['cumulative'],
                                           label='PowerAPI DB - Cumulative', color='orange', linewidth=2, marker=None)
                
                # Add legend
                ax.legend(loc='best')
                
                # Add grid
                ax.grid(True, linestyle='--', alpha=0.7)
                
                # Add run boundaries visualization for each experiment
                experiment_colors = ['lightgreen', 'lightblue', 'lightyellow', 'lightpink', 'lightcoral', 'lightskyblue']
                
                for idx, exp_id in enumerate(experiment_ids):
                    run_boundaries = self._get_run_time_boundaries(exp_id)
                    if run_boundaries:
                        color_index = idx % len(experiment_colors)
                        color = experiment_colors[color_index]
                        
                        for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                            # Convert timestamps to datetime objects for plotting
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
                            
                            # Only add text labels if we're showing a single experiment
                            # Otherwise, it gets too cluttered
                            if experiment_id != "All Experiments":
                                midpoint = run_start_dt + (run_end_dt - run_start_dt) / 2
                                y_pos = ax.get_ylim()[1] * 0.95  # Position near the top
                                ax.text(midpoint, y_pos, f'Run {run_num}', 
                                       ha='center', va='top', 
                                       bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'))
                
                # Apply tight layout to maximize plot area
                self.fig.tight_layout()
                
                # Add experiment boundary visualization when showing all experiments
                if experiment_id == "All Experiments":
                    chronology = self._get_experiment_chronology()
                    
                    if len(chronology) > 1:  # Only need to show boundaries if there's more than one experiment
                        # Visualize the pause between experiments
                        for i in range(len(chronology) - 1):
                            current_exp_id, _, current_end = chronology[i]
                            next_exp_id, next_start, _ = chronology[i + 1]
                            
                            # Only show pause if there is a gap between experiments
                            if next_start > current_end:
                                # Convert to datetime for plotting
                                current_end_dt = self._convert_to_eet(current_end)
                                next_start_dt = self._convert_to_eet(next_start)
                                
                                # Add a gray shaded region for the pause
                                ax.axvspan(current_end_dt, next_start_dt, 
                                          alpha=0.2, 
                                          color='gray', 
                                          hatch='///' if i % 2 == 0 else '\\\\\\',
                                          label='Inter-experiment pause' if i == 0 else "")
                                
                                # Add vertical lines at experiment boundaries
                                ax.axvline(x=current_end_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                ax.axvline(x=next_start_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                
                                # Calculate the duration of the pause in seconds
                                pause_duration = (next_start - current_end) / 1000  # Convert ms to seconds
                                
                                # Add a text label for the pause duration
                                if pause_duration > 5:  # Only label if pause is significant (> 5 seconds)
                                    midpoint = current_end_dt + (next_start_dt - current_end_dt) / 2
                                    y_pos = ax.get_ylim()[1] * 0.75  # Position at 75% of the height
                                    
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
            # Create a new subplot
            ax = self.fig.add_subplot(111)
            
            # Configure the axis
            ax.set_xlabel('Time (EET)', fontsize=10)
            ax.set_ylabel('Total Energy Consumed (Watts)', fontsize=10)
            
            # Set plot title based on selected experiments
            if experiment_id == "All data":
                ax.set_title(f'Total Energy Consumed - All Data (No Time Filtering)', fontsize=12)
            elif experiment_id == "All Experiments":
                ax.set_title(f'Total Energy Consumed for All Experiments', fontsize=12)
            else:
                ax.set_title(f'Total Energy Consumed for Experiment: {experiment_id}', fontsize=12)
            
            # Format the time axis
            ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
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
                
            # --- Final Filtering of Processed Data --- 
            if start_dt is not None and not api_df.empty:
                api_df = api_df[api_df['datetime'] >= start_dt]
            if end_dt is not None and not api_df.empty:
                api_df = api_df[api_df['datetime'] <= end_dt]
                
            if start_dt is not None and not db_df.empty:
                db_df = db_df[db_df['datetime'] >= start_dt]
            if end_dt is not None and not db_df.empty:
                db_df = db_df[db_df['datetime'] <= end_dt]
            # --- End Final Filtering ---

            # Calculate cumulative energy consumption
            if not api_df.empty and self.show_scaphandre_api_var.get():
                api_df['cumulative'] = api_df['consumption'].cumsum()
                ax.plot(api_df['datetime'], api_df['cumulative'], 
                       label='Scaphandre API Server (Java)', color='red', linewidth=2, marker=None)
            
            if not db_df.empty and self.show_scaphandre_db_var.get():
                db_df['cumulative'] = db_df['consumption'].cumsum()
                ax.plot(db_df['datetime'], db_df['cumulative'], 
                       label='Scaphandre DB Server (Postgres)', color='blue', linewidth=2, marker=None)
            
            # Add MongoDB data if available
            if self.mongodb_api_var.get() and self.mongodb_api_data is not None:
                api_target = self.mongodb_api_target_var.get()
                if api_target:
                    mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                    if mongo_api_df is not None:
                        processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                        if not processed_mongo_api_df.empty:
                            # Filter processed MongoDB data as well
                            if start_dt is not None:
                                processed_mongo_api_df = processed_mongo_api_df[processed_mongo_api_df['datetime'] >= start_dt]
                            if end_dt is not None:
                                processed_mongo_api_df = processed_mongo_api_df[processed_mongo_api_df['datetime'] <= end_dt]
                                
                            if not processed_mongo_api_df.empty: # Check if still not empty after filtering
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
                            # Filter processed MongoDB data as well
                            if start_dt is not None:
                                processed_mongo_db_df = processed_mongo_db_df[processed_mongo_db_df['datetime'] >= start_dt]
                            if end_dt is not None:
                                processed_mongo_db_df = processed_mongo_db_df[processed_mongo_db_df['datetime'] <= end_dt]
                                
                            if not processed_mongo_db_df.empty: # Check if still not empty after filtering
                                processed_mongo_db_df['cumulative'] = processed_mongo_db_df['power'].cumsum()
                                ax.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['cumulative'],
                                       label='PowerAPI DB', color='orange', linewidth=2, marker=None)
            
            # Add legend
            ax.legend(loc='upper left', fontsize='small')
            
            # Add grid
            ax.grid(True, linestyle='--', alpha=0.7)
            
            # Add run boundaries visualization
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
            total_mongo_api = processed_mongo_api_df['power'].sum() if 'processed_mongo_api_df' in locals() and not processed_mongo_api_df.empty else 0
            total_mongo_db = processed_mongo_db_df['power'].sum() if 'processed_mongo_db_df' in locals() and not processed_mongo_db_df.empty else 0
            
            # Add text box with total energy consumption
            total_text = f"Total Energy Consumed:\n"
            total_text += f"API Server: {total_api:.2f} Watts\n"
            total_text += f"DB Server: {total_db:.2f} Watts\n"
            if total_mongo_api > 0:
                total_text += f"PowerAPI API: {total_mongo_api:.2f} Watts\n"
            if total_mongo_db > 0:
                total_text += f"PowerAPI DB: {total_mongo_db:.2f} Watts\n"
            total_text += f"Total: {total_api + total_db + total_mongo_api + total_mongo_db:.2f} Watts"
            
            ax.text(0.98, 0.98, total_text,
                   transform=ax.transAxes,
                   verticalalignment='top',
                   horizontalalignment='right',
                   bbox=dict(boxstyle='round', facecolor='white', alpha=0.8),
                   fontsize=10)
            
            # Apply tight layout
            self.fig.tight_layout()
            
            # Update status with total energy consumption
            self.status_var.set(f"Total energy consumed: {total_api + total_db + total_mongo_api + total_mongo_db:.2f} Watts")
            
        elif data_source == "Energy Comparative":
            # Create two subplots for comparative energy visualization
            # Process API server data (target container by ID)
            api_df = self._process_energy_data(filtered_api_data, "api", window_size_ms)
            
            # Process DB server data (target container by name)
            db_df = self._process_energy_data(filtered_db_data, "db", window_size_ms)
            
            # Create a figure with two subplots (2 rows, 1 column)
            ax1 = self.fig.add_subplot(211)  # Top subplot
            ax2 = self.fig.add_subplot(212)  # Bottom subplot
            
            # Configure the axes
            if experiment_id == "All data":
                ax1.set_title(f'Energy Consumption - Scaphandre API Server (Java) - All Data (No Filtering)', fontsize=10)
                ax2.set_title(f'Energy Consumption - Scaphandre DB Server (Postgres) - All Data (No Filtering)', fontsize=10)
            else:
                ax1.set_title(f'Energy Consumption - Scaphandre API Server (Java) - {experiment_id}', fontsize=10)
                ax2.set_title(f'Energy Consumption - Scaphandre DB Server (Postgres) - {experiment_id}', fontsize=10)
            ax1.set_ylabel('Energy Consumption', fontsize=9)
            ax2.set_xlabel('Time (EET)', fontsize=9)
            ax2.set_ylabel('Energy Consumption', fontsize=9)
            
            # Format time axes
            ax1.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
            ax1.tick_params(axis='x', rotation=45)
            ax2.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
            ax2.tick_params(axis='x', rotation=45)
            
            # Plot API server data in top subplot
            has_scaphandre_api = not api_df.empty and self.show_scaphandre_api_var.get()
            has_powerapi_api = (self.mongodb_api_var.get() and self.mongodb_api_data is not None and 
                               self.mongodb_api_target_var.get())
            
            if not has_scaphandre_api and not has_powerapi_api:
                ax1.text(0.5, 0.5, "No API server energy data available", 
                        horizontalalignment='center', verticalalignment='center',
                        transform=ax1.transAxes, fontsize=10)
            else:
                if accumulation_mode == "Simple":
                    # Simple plot - show available data points
                    if has_scaphandre_api:
                        if plot_type == "Line":
                            ax1.plot(api_df['datetime'], api_df['consumption'], 
                                    color='red', linewidth=2, marker=None, label='Scaphandre API Server Energy')
                        elif plot_type == "Scatter":
                            ax1.scatter(api_df['datetime'], api_df['consumption'], 
                                       color='blue', marker='o', s=20, label='Scaphandre API Server Energy')
                        elif plot_type == "Bar":
                            bar_width = 0.0002  # Adjust as needed
                            ax1.bar(api_df['datetime'], api_df['consumption'], 
                                   width=bar_width, color='blue', alpha=0.7, label='Scaphandre API Server Energy')
                    
                    # Add MongoDB API data if available
                    if has_powerapi_api:
                        api_target = self.mongodb_api_target_var.get()
                        mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_api_df is not None:
                            # Apply windowing to MongoDB data
                            processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                            if not processed_mongo_api_df.empty:
                                bar_width = 0.0002 if 'bar_width' not in locals() else bar_width
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
                else:  # Accumulated mode
                    # Handle accumulated mode for available data
                    if has_scaphandre_api:
                        # Add a cumulative sum column for accumulated energy over time
                        api_df['accumulated'] = api_df['consumption'].cumsum()
                        if plot_type == "Line":
                            ax1.plot(api_df['datetime'], api_df['accumulated'], 
                                    color='red', linewidth=2, marker=None, label='Scaphandre Accumulated')
                            # Add a second y-axis for non-accumulated values
                            ax1_twin = ax1.twinx()
                            ax1_twin.plot(api_df['datetime'], api_df['consumption'], 
                                         color='lightcoral', linewidth=1.5, marker=None, alpha=0.7, label='Per Interval')
                            ax1_twin.set_ylabel('Interval Energy', color='lightcoral', fontsize=8)
                            ax1_twin.tick_params(axis='y', labelcolor='lightcoral')
                    
                    # Add MongoDB API data if available
                    if has_powerapi_api:
                        api_target = self.mongodb_api_target_var.get()
                        mongo_api_df = self._get_filtered_mongodb_data(self.mongodb_api_data, api_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_api_df is not None:
                            # Apply windowing to MongoDB data
                            processed_mongo_api_df = self._process_mongodb_data(mongo_api_df, window_size_ms)
                            if not processed_mongo_api_df.empty:
                                # Plot MongoDB data on the main y-axis
                                processed_mongo_api_df['accumulated'] = processed_mongo_api_df['power'].cumsum()
                                ax1.plot(processed_mongo_api_df['datetime'], processed_mongo_api_df['accumulated'],
                                       color='green', linewidth=2, marker=None, 
                                       label='PowerAPI API Accumulated')
                    elif plot_type == "Bar":
                        bar_width = 0.0002  # Adjust as needed
                        ax1.fill_between(api_df['datetime'], api_df['accumulated'], color='blue', alpha=0.3, label='Accumulated')
                        ax1.plot(api_df['datetime'], api_df['accumulated'], color='blue', linewidth=1.5)
                        # Add bars for non-accumulated values
                        ax1_twin = ax1.twinx()
                        ax1_twin.bar(api_df['datetime'], api_df['consumption'], width=bar_width, color='lightblue', alpha=0.7, label='Per Interval')
                        ax1_twin.set_ylabel('Interval Energy', color='lightblue', fontsize=8)
                        ax1_twin.tick_params(axis='y', labelcolor='lightblue')
                    elif plot_type == "Scatter":
                        ax1.scatter(api_df['datetime'], api_df['accumulated'], color='blue', marker='o', s=15, label='Accumulated')
                        ax1.plot(api_df['datetime'], api_df['accumulated'], color='blue', linewidth=1, alpha=0.5)
            
            # Add legend for API server subplot if we have data
            if (not api_df.empty) or (self.mongodb_api_var.get() and self.mongodb_api_data is not None):
                ax1.legend(loc='upper left', fontsize='small')
            
            # Plot DB server data in bottom subplot
            has_scaphandre_db = not db_df.empty and self.show_scaphandre_db_var.get()
            has_powerapi_db = (self.mongodb_db_var.get() and self.mongodb_db_data is not None and 
                              self.mongodb_db_target_var.get())
            
            if not has_scaphandre_db and not has_powerapi_db:
                ax2.text(0.5, 0.5, "No DB server energy data available", 
                        horizontalalignment='center', verticalalignment='center',
                        transform=ax2.transAxes, fontsize=10)
            else:
                if accumulation_mode == "Simple":
                    # Simple plot - show available data points
                    if has_scaphandre_db:
                        if plot_type == "Line":
                            ax2.plot(db_df['datetime'], db_df['consumption'], 
                                    color='blue', linewidth=2, marker=None, label='Scaphandre DB Server Energy')
                        elif plot_type == "Scatter":
                            ax2.scatter(db_df['datetime'], db_df['consumption'], 
                                       color='green', marker='s', s=20, label='Scaphandre DB Server Energy')
                        elif plot_type == "Bar":
                            bar_width = 0.0002  # Adjust as needed
                            ax2.bar(db_df['datetime'], db_df['consumption'], 
                                   width=bar_width, color='green', alpha=0.7, label='Scaphandre DB Server Energy')
                    
                    # Add MongoDB DB data if available
                    if has_powerapi_db:
                        db_target = self.mongodb_db_target_var.get()
                        mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_db_df is not None:
                            # Apply windowing to MongoDB data
                            processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                            if not processed_mongo_db_df.empty:
                                bar_width = 0.0002 if 'bar_width' not in locals() else bar_width
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
                else:  # Accumulated mode
                    # Handle accumulated mode for available data
                    if has_scaphandre_db:
                        # Add a cumulative sum column for accumulated energy over time
                        db_df['accumulated'] = db_df['consumption'].cumsum()
                        
                        if plot_type == "Line":
                            ax2.plot(db_df['datetime'], db_df['accumulated'], 
                                    color='blue', linewidth=2, marker=None, label='Scaphandre Accumulated')
                            # Add a second y-axis for non-accumulated values
                            ax2_twin = ax2.twinx()
                            ax2_twin.plot(db_df['datetime'], db_df['consumption'], 
                                         color='lightblue', linewidth=1.5, marker=None, alpha=0.7, label='Per Interval')
                            ax2_twin.set_ylabel('Interval Energy', color='lightblue', fontsize=8)
                            ax2_twin.tick_params(axis='y', labelcolor='lightblue')
                        
                    # Add MongoDB DB data if available
                    if has_powerapi_db:
                        db_target = self.mongodb_db_target_var.get()
                        mongo_db_df = self._get_filtered_mongodb_data(self.mongodb_db_data, db_target, start_time_ms, end_time_ms, use_strict_filtering)
                        if mongo_db_df is not None:
                            # Apply windowing to MongoDB data
                            processed_mongo_db_df = self._process_mongodb_data(mongo_db_df, window_size_ms)
                            if not processed_mongo_db_df.empty:
                                # Plot MongoDB data on the main y-axis
                                processed_mongo_db_df['accumulated'] = processed_mongo_db_df['power'].cumsum()
                                ax2.plot(processed_mongo_db_df['datetime'], processed_mongo_db_df['accumulated'],
                                       color='orange', linewidth=2, marker=None, 
                                       label=f'PowerAPI DB ({db_target}) Accumulated')
                    elif plot_type == "Bar":
                        bar_width = 0.0002  # Adjust as needed
                        ax2.fill_between(db_df['datetime'], db_df['accumulated'], color='green', alpha=0.3, label='Accumulated')
                        ax2.plot(db_df['datetime'], db_df['accumulated'], color='green', linewidth=1.5)
                        # Add bars for non-accumulated values
                        ax2_twin = ax2.twinx()
                        ax2_twin.bar(db_df['datetime'], db_df['consumption'], width=bar_width, color='lightblue', alpha=0.7, label='Per Interval')
                        ax2_twin.set_ylabel('Interval Energy', color='lightblue', fontsize=8)
                        ax2_twin.tick_params(axis='y', labelcolor='lightblue')
                    elif plot_type == "Scatter":
                        ax2.scatter(db_df['datetime'], db_df['accumulated'], color='green', marker='s', s=15, label='Accumulated')
                        ax2.plot(db_df['datetime'], db_df['accumulated'], color='green', linewidth=1, alpha=0.5)
            
            # Add legend for DB server subplot if we have data
            if has_scaphandre_db or has_powerapi_db:
                ax2.legend(loc='upper left', fontsize='small')
            
            # Add grid to both subplots
            ax1.grid(True, linestyle='--', alpha=0.7)
            ax2.grid(True, linestyle='--', alpha=0.7)
            
            # Use scientific notation for y-axis if values are large
            if not api_df.empty and (api_df['consumption'].max() > 10000 or 
                                    (accumulation_mode == "Accumulated" and 'accumulated' in api_df.columns and api_df['accumulated'].max() > 10000)):
                ax1.ticklabel_format(axis='y', style='sci', scilimits=(0,0))
            
            if not db_df.empty and (db_df['consumption'].max() > 10000 or 
                                   (accumulation_mode == "Accumulated" and 'accumulated' in db_df.columns and db_df['accumulated'].max() > 10000)):
                ax2.ticklabel_format(axis='y', style='sci', scilimits=(0,0))
            
            # Add legends if needed
            if accumulation_mode == "Accumulated" and not api_df.empty and 'accumulated' in api_df.columns:
                try:
                    lines, labels = ax1.get_legend_handles_labels()
                    lines2, labels2 = ax1.twinx().get_legend_handles_labels()
                    ax1.legend(lines + lines2, labels + labels2, loc='upper left', fontsize='small')
                except:
                    pass  # Fallback if twin axis doesn't exist
            
            if accumulation_mode == "Accumulated" and not db_df.empty and 'accumulated' in db_df.columns:
                try:
                    lines, labels = ax2.get_legend_handles_labels()
                    lines2, labels2 = ax2.twinx().get_legend_handles_labels()
                    ax2.legend(lines + lines2, labels + labels2, loc='upper left', fontsize='small')
                except:
                    pass  # Fallback if twin axis doesn't exist
            
            # Adjust the layout to fit the subplots
            self.fig.tight_layout()
            
            # Update status with data summary
            api_count = len(api_df) if not api_df.empty else 0
            db_count = len(db_df) if not db_df.empty else 0
            total_api = api_df['consumption'].sum() if not api_df.empty else 0
            total_db = db_df['consumption'].sum() if not db_df.empty else 0
            
            # Count PowerAPI data points
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
            
            # Format the summary message
            if accumulation_mode == "Accumulated":
                self.status_var.set(
                    f"Comparative view: API data: {api_count} points, total: {total_api:.2e} units | "
                    f"DB data: {db_count} points, total: {total_db:.2e} units"
                )
            else:
                status_parts = []
                if api_count > 0 or powerapi_api_count > 0:
                    status_parts.append(f"API data points: {api_count + powerapi_api_count}")
                if db_count > 0 or powerapi_db_count > 0:
                    status_parts.append(f"DB data points: {db_count + powerapi_db_count}")
                    
                if status_parts:
                    self.status_var.set(f"Comparative view: {' | '.join(status_parts)}")
                else:
                    self.status_var.set("Comparative view: No data available")
                
        elif data_source == "Latency":
            # Handle the "All data" and "All Experiments" cases
            if experiment_id == "All data" or experiment_id == "All Experiments":
                # Create a new subplot
                ax = self.fig.add_subplot(111)
                
                # Configure the axis
                ax.set_xlabel('Time (EET)', fontsize=10)
                ax.set_ylabel('Latency (ms)', fontsize=10)
                if experiment_id == "All data":
                    ax.set_title(f'Request Latency - All Data (No Time Filtering)', fontsize=12)
                else:
                    ax.set_title(f'Request Latency for All Experiments', fontsize=12)
                
                # Format the time axis
                ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
                ax.tick_params(axis='x', rotation=45)
                
                # Plot latency data for each experiment with different colors
                experiment_colors = ['purple', 'green', 'orange', 'brown', 'magenta', 'cyan']
                has_data = False
                
                for idx, exp_id in enumerate(experiment_ids):
                    experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                    runs = experiment.get("runs", [])
                    
                    # Collect latency data from all runs of this experiment
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
                        # Convert to DataFrame for easier plotting
                        latency_df = pd.DataFrame(latency_data, columns=['datetime', 'latency'])
                        
                        # Plot based on plot type with experiment-specific color
                        color = experiment_colors[idx % len(experiment_colors)]
                        
                        if plot_type == "Line":
                            ax.plot(latency_df['datetime'], latency_df['latency'], 
                                   label=f'{exp_id}', color=color, linewidth=2, marker=None, alpha=0.7)
                        elif plot_type == "Scatter":
                            ax.scatter(latency_df['datetime'], latency_df['latency'], 
                                     label=f'{exp_id}', color=color, marker='o', s=30, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002  # Adjust as needed
                            ax.bar(latency_df['datetime'], latency_df['latency'], 
                                  width=bar_width, label=f'{exp_id}', color=color, alpha=0.7)
                
                if not has_data:
                    ax.text(0.5, 0.5, "No latency data available", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=14)
                else:
                    # Add grid
                    ax.grid(True, linestyle='--', alpha=0.7)
                    
                    # Add legend for the experiments
                    ax.legend(loc='upper left', fontsize='small')
                    
                    # Add run boundaries visualization for each experiment
                    experiment_colors = ['lightgreen', 'lightblue', 'lightyellow', 'lightpink', 'lightcoral', 'lightskyblue']
                    
                    for idx, exp_id in enumerate(experiment_ids):
                        run_boundaries = self._get_run_time_boundaries(exp_id)
                        if run_boundaries:
                            color_index = idx % len(experiment_colors)
                            color = experiment_colors[color_index]
                            
                            for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                                # Convert timestamps to datetime objects for plotting
                                run_start_dt = self._convert_to_eet(run_start)
                                run_end_dt = self._convert_to_eet(run_end)
                                
                                # Add shaded region for this run
                                ax.axvspan(run_start_dt, run_end_dt, 
                                          alpha=0.15,  # Lower alpha for multiple experiments
                                          color=color, 
                                          label="" if i > 0 else f'{exp_id} runs')
                                
                                # Add vertical lines at run boundaries (less visible)
                                ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.3)
                                ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.3)
                    
                    # Apply tight layout to maximize plot area
                    self.fig.tight_layout()
                    
                    # Add experiment boundary visualization when showing all experiments
                    chronology = self._get_experiment_chronology()
                    
                    if len(chronology) > 1:  # Only need to show boundaries if there's more than one experiment
                        # Visualize the pause between experiments
                        for i in range(len(chronology) - 1):
                            current_exp_id, _, current_end = chronology[i]
                            next_exp_id, next_start, _ = chronology[i + 1]
                            
                            # Only show pause if there is a gap between experiments
                            if next_start > current_end:
                                # Convert to datetime for plotting
                                current_end_dt = self._convert_to_eet(current_end)
                                next_start_dt = self._convert_to_eet(next_start)
                                
                                # Add a gray shaded region for the pause
                                ax.axvspan(current_end_dt, next_start_dt, 
                                          alpha=0.2, 
                                          color='gray', 
                                          hatch='///' if i % 2 == 0 else '\\\\\\',
                                          label='Inter-experiment pause' if i == 0 else "")
                                
                                # Add vertical lines at experiment boundaries
                                ax.axvline(x=current_end_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                ax.axvline(x=next_start_dt, color='black', linestyle='-', alpha=0.7, linewidth=2)
                                
                                # Calculate the duration of the pause in seconds
                                pause_duration = (next_start - current_end) / 1000  # Convert ms to seconds
                                
                                # Add a text label for the pause duration
                                if pause_duration > 5:  # Only label if pause is significant (> 5 seconds)
                                    midpoint = current_end_dt + (next_start_dt - current_end_dt) / 2
                                    y_pos = ax.get_ylim()[1] * 0.75  # Position at 75% of the height
                                    
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
            else:
                # Single experiment latency view - use existing code
                experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
                runs = experiment.get("runs", [])
                
                if not runs:
                    ax = self.fig.add_subplot(111)
                    ax.text(0.5, 0.5, "No latency data available for this experiment", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=14)
                else:
                    # Create a new subplot
                    ax = self.fig.add_subplot(111)
                    
                    # Configure the axis
                    ax.set_xlabel('Time (EET)', fontsize=10)
                    ax.set_ylabel('Latency (ms)', fontsize=10)
                    ax.set_title(f'Request Latency for Experiment: {experiment_id}', fontsize=12)
                    
                    # Format the time axis
                    ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
                    ax.tick_params(axis='x', rotation=45)
                    
                    # Collect latency data from all runs
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
                               transform=ax.transAxes, fontsize=14)
                    else:
                        # Convert to DataFrame for easier plotting
                        latency_df = pd.DataFrame(latency_data, columns=['datetime', 'latency'])
                        
                        # Plot based on plot type
                        if plot_type == "Line":
                            ax.plot(latency_df['datetime'], latency_df['latency'], 
                                   color='purple', linewidth=2, marker=None, alpha=0.7)
                        elif plot_type == "Scatter":
                            ax.scatter(latency_df['datetime'], latency_df['latency'], 
                                     color='purple', marker='o', s=30, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002  # Adjust as needed
                            ax.bar(latency_df['datetime'], latency_df['latency'], 
                                  width=bar_width, color='purple', alpha=0.7)
                        
                        # Add grid
                        ax.grid(True, linestyle='--', alpha=0.7)
                        
                        # Add run boundaries visualization
                        if run_boundaries := self._get_run_time_boundaries(experiment_id):
                            # Define colors for alternating run shading
                            run_colors = ['lightgreen', 'lightblue']
                            
                            for i, (run_num, run_start, run_end) in enumerate(run_boundaries):
                                # Convert timestamps to datetime objects for plotting
                                run_start_dt = self._convert_to_eet(run_start)
                                run_end_dt = self._convert_to_eet(run_end)
                                
                                # Add shaded region for this run
                                ax.axvspan(run_start_dt, run_end_dt, 
                                          alpha=0.3, 
                                          color=run_colors[i % len(run_colors)], 
                                          label=f'Run {run_num}' if i == 0 else "")
                                
                                # Add vertical lines at run boundaries
                                ax.axvline(x=run_start_dt, color='green', linestyle='--', alpha=0.7)
                                ax.axvline(x=run_end_dt, color='red', linestyle='--', alpha=0.7)
                                
                                # Add text label for the run
                                midpoint = run_start_dt + (run_end_dt - run_start_dt) / 2
                                y_pos = ax.get_ylim()[1] * 0.95  # Position near the top
                                ax.text(midpoint, y_pos, f'Run {run_num}', 
                                       ha='center', va='top', 
                                       bbox=dict(facecolor='white', alpha=0.8, boxstyle='round'))
                        
                        # Apply tight layout to maximize plot area
                        self.fig.tight_layout()

                        # Add legend
                        ax.legend(loc='upper left', fontsize='small')
        
        elif data_source == "Throughput":
            # Handle the "All data" and "All Experiments" cases
            if experiment_id == "All data" or experiment_id == "All Experiments":
                # Create a new subplot
                ax = self.fig.add_subplot(111)
                
                # Configure the axis
                ax.set_xlabel('Time (EET)', fontsize=10)
                ax.set_ylabel('Throughput (requests/second)', fontsize=10)
                if experiment_id == "All data":
                    ax.set_title(f'Request Throughput - All Data (No Time Filtering)', fontsize=12)
                else:
                    ax.set_title(f'Request Throughput for All Experiments', fontsize=12)
                
                # Format the time axis
                ax.xaxis.set_major_formatter(mdates.DateFormatter('%H:%M:%S', tz=self.display_timezone))
                ax.tick_params(axis='x', rotation=45)
                
                # Format y-axis to show more decimal places
                ax.yaxis.set_major_formatter(plt.FormatStrFormatter('%.5f'))
                
                # Plot throughput data for each experiment with different colors
                experiment_colors = ['purple', 'green', 'orange', 'brown', 'magenta', 'cyan']
                has_data = False
                
                for idx, exp_id in enumerate(experiment_ids):
                    experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                    runs = experiment.get("runs", [])
                    
                    # Collect throughput data from all runs of this experiment
                    throughput_data = []
                    goodput_data = []
                    for run in runs:
                        if "throughput" in run and "start_timestamp" in run:
                            # Use the start timestamp for the throughput point
                            timestamp_ms = run["start_timestamp"]
                            throughput = run["throughput"]
                            dt = self._convert_to_eet(timestamp_ms)
                            throughput_data.append((dt, throughput))
                            
                            # Also collect goodput if available
                            if "goodput" in run:
                                goodput = run["goodput"]
                                goodput_data.append((dt, goodput))
                    
                    if throughput_data:
                        has_data = True
                        # Convert to DataFrame for easier plotting
                        throughput_df = pd.DataFrame(throughput_data, columns=['datetime', 'throughput'])
                        
                        # Sort by datetime to ensure chronological order
                        throughput_df = throughput_df.sort_values('datetime')
                        
                        # Plot with appropriate color and label
                        color = experiment_colors[idx % len(experiment_colors)]
                        if plot_type == "Line":
                            ax.plot(throughput_df['datetime'], throughput_df['throughput'], 
                                   marker='o', linestyle='-', color=color, 
                                   label=f"{exp_id} (Total)", linewidth=2)
                            
                            # Plot goodput if available
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                ax.plot(goodput_df['datetime'], goodput_df['goodput'], 
                                       marker='x', linestyle='--', color=color, 
                                       label=f"{exp_id} (Successful)", linewidth=1.5, alpha=0.7)
                        elif plot_type == "Bar":
                            bar_width = 0.0002  # Adjust as needed
                            ax.bar(throughput_df['datetime'], throughput_df['throughput'], 
                                  width=bar_width, color=color, alpha=0.7, label=f"{exp_id} (Total)")
                            
                            # Plot goodput as bars if available
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                # Use a lighter shade of the same color for goodput with slight offset
                                ax.bar(goodput_df['datetime'], goodput_df['goodput'], 
                                      width=bar_width*0.8, color=color, alpha=0.4, 
                                      label=f"{exp_id} (Successful)")
                        elif plot_type == "Scatter":
                            ax.scatter(throughput_df['datetime'], throughput_df['throughput'], 
                                      color=color, marker='o', s=50, label=f"{exp_id} (Total)")
                            
                            # Plot goodput if available
                            if goodput_data:
                                goodput_df = pd.DataFrame(goodput_data, columns=['datetime', 'goodput'])
                                goodput_df = goodput_df.sort_values('datetime')
                                ax.scatter(goodput_df['datetime'], goodput_df['goodput'], 
                                         color=color, marker='x', s=40, alpha=0.7,
                                         label=f"{exp_id} (Successful)")
                
                if has_data:
                    # Add a legend
                    ax.legend(loc='upper left', fontsize='small')
                    
                    # Set x-axis to show integer run numbers
                    ax.xaxis.set_major_locator(mticker.MaxNLocator(integer=True))
                    
                    # Set reasonable y-axis limits
                    if ax.get_ylim()[0] < 0:
                        ax.set_ylim(bottom=0)  # Start from 0 for throughput
                    
                    # Add data labels for Line and Scatter plots
                    if plot_type in ["Line", "Scatter"]:
                        for idx, exp_id in enumerate(experiment_ids):
                            experiment = self.data["benchmark_results"]["experiments"].get(exp_id, {})
                            runs = experiment.get("runs", [])
                            
                            # Process throughput data
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
                                
                                # Add data labels
                                for i, row in throughput_df.iterrows():
                                    ax.text(row['datetime'], row['throughput'], f"{row['throughput']:.5f}", 
                                           ha='center', va='bottom', fontsize=7)
                else:
                    # No data available message
                    ax.text(0.5, 0.5, "No throughput data available for the selected experiments", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=10)
            else:
                # Single experiment view
                experiment = self.data["benchmark_results"]["experiments"].get(experiment_id, {})
                runs = experiment.get("runs", [])
                
                # Create a new subplot
                ax = self.fig.add_subplot(111)
                
                # Configure the axis
                ax.set_xlabel('Run Number', fontsize=10)
                ax.set_ylabel('Throughput (requests/second)', fontsize=10)
                ax.set_title(f'Request Throughput for {experiment_id}', fontsize=12)
                
                # Format y-axis to show more decimal places
                ax.yaxis.set_major_formatter(plt.FormatStrFormatter('%.5f'))
                
                # Collect throughput data for this experiment
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
                    # Plot throughput data
                    if throughput_data:
                        # Convert to DataFrame for easier plotting
                        throughput_df = pd.DataFrame(throughput_data, columns=['run', 'throughput'])
                        
                        # Plot with appropriate style
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
                    
                    # Plot goodput data
                    if goodput_data:
                        # Convert to DataFrame for easier plotting
                        goodput_df = pd.DataFrame(goodput_data, columns=['run', 'goodput'])
                        
                        # Plot with appropriate style
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
                    
                    # Add legend
                    ax.legend(loc='best')
                    
                    # Set x-axis to show integer run numbers
                    ax.xaxis.set_major_locator(mticker.MaxNLocator(integer=True))
                    
                    # Set reasonable y-axis limits
                    if ax.get_ylim()[0] < 0:
                        ax.set_ylim(bottom=0)  # Start from 0 for throughput
                    
                    # Add data labels
                    if throughput_data and plot_type != "Bar":
                        for i, row in throughput_df.iterrows():
                            ax.text(row['run'], row['throughput'], f"{row['throughput']:.5f}", 
                                   ha='center', va='bottom', fontsize=8)
                    
                    if goodput_data and plot_type != "Bar":
                        for i, row in goodput_df.iterrows():
                            ax.text(row['run'], row['goodput'], f"{row['goodput']:.5f}", 
                                   ha='center', va='bottom', fontsize=8)
                else:
                    # No data available message
                    ax.text(0.5, 0.5, "No throughput data available for this experiment", 
                           horizontalalignment='center', verticalalignment='center',
                           transform=ax.transAxes, fontsize=10)
        
        # Redraw the canvas with the new plot
        self.canvas.draw()
    def update_accumulation_options(self):
        """Update accumulation dropdown options based on current plot settings"""
        plot_type = self.plot_type_var.get()
        data_source = self.data_source_var.get()
        
        # Define which combinations support accumulated mode
        supports_accumulated = True
        
        # Disable accumulated mode for non-energy data sources
        # (Latency and Throughput don't make sense to accumulate)
        if data_source in ["Latency", "Throughput"]:
            supports_accumulated = False
        
        # Disable accumulated mode for scatter plots with energy data
        # (Accumulated scatter plots are visually confusing)
        elif plot_type == "Scatter" and data_source in ["Energy", "Energy Consumed", "Energy Comparative"]:
            supports_accumulated = False
        
        # Bar charts with accumulated mode can be confusing too
        elif plot_type == "Bar" and data_source in ["Energy", "Energy Consumed", "Energy Comparative"]:
            supports_accumulated = False
        
        # Update the dropdown values and state
        if supports_accumulated:
            self.accumulation_combo['values'] = ["Simple", "Accumulated"]
            self.accumulation_combo['state'] = "readonly"
        else:
            self.accumulation_combo['values'] = ["Simple"]
            self.accumulation_combo['state'] = "readonly"
            # If currently set to Accumulated, switch to Simple
            if self.accumulation_var.get() == "Accumulated":
                self.accumulation_var.set("Simple")
    
    def force_plot_update(self):
        """Force update the plot with the current experiment selection"""
        if not self.data or not self.experiment_var.get():
            self.status_var.set("No data loaded or no experiment selected")
            return
        
        # Update accumulation options first
        self.update_accumulation_options()
            
        # Simply call the update_plot method
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
                
            # Get the start timestamp from the first run
            first_run = runs[0]
            start_time_ms = first_run.get("start_timestamp")
            
            # Get the end timestamp from the last run
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
                self.status_var.set(f"No explicit time boundaries found for {experiment_id}, using full energy data range.")
                
                # Look for any timestamps in the energy data
                api_energy_data = self.data.get("api_server_energy", [])
                db_energy_data = self.data.get("db_server_energy", [])
                
                energy_timestamps = []
                for entry in api_energy_data + db_energy_data:
                    if "timestamp" in entry:
                        # Convert seconds to milliseconds
                        energy_timestamps.append(entry.get("timestamp", 0) * 1000)
                
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
                    # Convert to seconds if needed
                    if ts > 1e10:  # If timestamp is in milliseconds
                        ts = ts / 1000
                    energy_timestamps.append(ts)
            
            # Also look for timestamps in consumers
            if "consumers" in host_interval:
                for consumer in host_interval["consumers"]:
                    if "timestamp" in consumer:
                        ts = consumer["timestamp"]
                        if isinstance(ts, (int, float)):
                            # Convert to seconds if needed
                            if ts > 1e10:  # If timestamp is in milliseconds
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
        
        # Print detailed debug information
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
                return energy_data  # Return all data if no overlap
            else:
                self.status_var.set(
                    f"No overlap between experiment time ({start_time_str} - {end_time_str}) "
                    f"and energy data ({min_time_str} - {max_time_str}). "
                    f"No energy data to display for this experiment."
                )
                return []  # Return empty data for strict filtering
        
        # Filter data to only include entries within the exact experiment time range
        filtered_data = []
        for host_interval in energy_data:
            # Check if this host interval has any timestamps within the experiment range
            interval_in_range = False
            
            # Check host timestamp
            if "host" in host_interval and "timestamp" in host_interval["host"]:
                ts = host_interval["host"]["timestamp"]
                if isinstance(ts, (int, float)):
                    if ts > 1e10:  # If timestamp is in milliseconds
                        ts = ts / 1000
                    if ts >= start_time_sec and ts <= end_time_sec:
                        interval_in_range = True
            
            # Check consumer timestamps
            if not interval_in_range and "consumers" in host_interval:
                for consumer in host_interval["consumers"]:
                    if "timestamp" in consumer:
                        ts = consumer["timestamp"]
                        if isinstance(ts, (int, float)):
                            if ts > 1e10:  # If timestamp is in milliseconds
                                ts = ts / 1000
                            if ts >= start_time_sec and ts <= end_time_sec:
                                interval_in_range = True
                                break
            
            if interval_in_range:
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
        
        # Show how many data points were kept
        self.status_var.set(
            f"Filtered energy data: kept {len(filtered_data)}/{len(energy_data)} entries "
            f"({(len(filtered_data)/len(energy_data)*100):.1f}%)"
        )
        
        return filtered_data
    
    def _process_energy_data(self, data_source, target_type, window_size_ms):
        """Process energy data with time-based windowing from the new JSON structure.
        
        Args:
            data_source: List of host interval energy data entries (each containing host info and a consumers list)
            target_type: Type of target to filter by ("api" for container ID, "db" for container name)
            window_size_ms: Size of accumulation window in milliseconds
            
        Returns:
            DataFrame with processed data
        """
        # Get target container IDs from instance variables
        target_api_id = self.api_container_id
        target_db_id = self.db_container_id

        # Check if IDs were loaded successfully
        if target_type == "api" and not target_api_id:
            print("ERROR: API container ID not available. Cannot process API energy data.") # DEBUG
            self.status_var.set("Error: API container ID missing.")
            return pd.DataFrame(columns=['timestamp', 'consumption', 'datetime']) # Return empty DF
        
        if target_type == "db" and not target_db_id:
             print("ERROR: DB container ID not available. Cannot process DB energy data.") # DEBUG
             self.status_var.set("Error: DB container ID missing.")
             return pd.DataFrame(columns=['timestamp', 'consumption', 'datetime']) # Return empty DF

        # Initialize variables for accumulation
        current_window_start = None
        accumulated_consumption = 0.0
        processed_data = []
        
        # Create debug log structure
        debug_log = {
            "target_type": target_type,
            "window_size_ms": window_size_ms,
            "total_host_intervals": len(data_source),
            "processed_consumer_entries": [],
            "intervals_without_target": 0,
            "skipped_consumers": []
        }

        # --- Host Energy Processing Logic --- 
        if target_type == "host":
            for host_interval in data_source:
                host_info = host_interval.get("host")
                if not host_info:
                    continue # Skip if host info is missing
                
                consumption = host_info.get("consumption")
                timestamp = host_info.get("timestamp")
                
                if consumption is None or timestamp is None:
                    continue # Skip if consumption or timestamp is missing for host
                
                # Convert consumption from microWatts to Watts
                consumption_watts = consumption / 1_000_000.0
                
                # Convert timestamp from seconds to milliseconds
                milliseconds = int(timestamp * 1000)
                
                # Apply windowing logic
                if current_window_start is None or milliseconds >= current_window_start + window_size_ms:
                    if current_window_start is not None and accumulated_consumption > 0:
                        dt = self._convert_to_eet(current_window_start)
                        processed_data.append((current_window_start, accumulated_consumption, dt))
                    current_window_start = milliseconds - (milliseconds % window_size_ms)
                    accumulated_consumption = consumption_watts
                else:
                    accumulated_consumption += consumption_watts
                    
            # Add the final host accumulated point
            if current_window_start is not None and accumulated_consumption > 0:
                dt = self._convert_to_eet(current_window_start)
                processed_data.append((current_window_start, accumulated_consumption, dt))
                
            # Convert host processed data to DataFrame and return
            if not processed_data:
                print("WARNING: No processed data generated for host. Returning empty DataFrame.") # DEBUG
                return pd.DataFrame(columns=['timestamp', 'consumption', 'datetime'])
            else:
                df = pd.DataFrame(processed_data, columns=['timestamp', 'consumption', 'datetime'])
                print(f"DEBUG: Generated DataFrame for 'host' with {len(df)} rows.") # DEBUG
                return df
        # --- End Host Energy Processing Logic ---

        # --- Container Energy Processing Logic (existing code) ---
        # Iterate through host intervals (each element in the top-level list)
        for host_interval in data_source:
            consumers = host_interval.get("consumers", [])
            target_consumer_found_in_interval = False
            consumption_in_interval = 0.0 # Accumulate consumption for this interval
            interval_timestamp = None # Timestamp for this interval

            # Iterate through consumers within the interval
            for consumer in consumers:
                container_info = consumer.get("container")
                consumption = consumer.get("consumption", 0.0)
                # Get the timestamp from the consumer entry (in seconds)
                timestamp = consumer.get("timestamp") 
                if timestamp is not None:
                    interval_timestamp = timestamp # Use the consumer timestamp for the interval
                
                # Basic consumer info for debug log
                consumer_debug = {
                    "pid": consumer.get("pid"),
                    "exe": consumer.get("exe"),
                    "cmdline": consumer.get("cmdline"),
                    "consumption": consumption,
                    "timestamp": timestamp,
                    "container": container_info
                }

                # Skip entries without necessary data (consumption/timestamp)
                if consumption is None or timestamp is None:
                    consumer_debug["reason_skipped"] = "Missing consumption or timestamp"
                    debug_log["skipped_consumers"].append(consumer_debug)
                    continue
                    
                # --- Target Filtering Logic --- (Modified)
                is_target = False
                if target_type == "api":
                    # Check if the full container ID starts with the stored short ID
                    if container_info and target_api_id and container_info.get("id", "").startswith(target_api_id):
                        is_target = True
                elif target_type == "db":
                     # Check if the full container ID starts with the stored short ID
                    if container_info and target_db_id and container_info.get("id", "").startswith(target_db_id):
                        is_target = True
                elif target_type == "java":
                    # Check executable name (case-insensitive)
                    exe_path = consumer.get("exe", "")
                    if exe_path and "java" in exe_path.lower():
                        is_target = True
                elif target_type == "postgres":
                    # Check executable name (case-insensitive)
                    exe_path = consumer.get("exe", "")
                    if exe_path and "postgres" in exe_path.lower():
                        is_target = True
                # --- End Target Filtering ---

                if is_target:
                    target_consumer_found_in_interval = True
                    consumption_in_interval += consumption # Accumulate for all matching consumers in interval
                    
                    # Log processed consumer (only need basic info here)
                    consumer_debug["processed"] = True
                    debug_log["processed_consumer_entries"].append(consumer_debug)
                else:
                    # Log skipped consumer if it wasn't the target
                    consumer_debug["reason_skipped"] = f"Not the target ({target_type}) container/process"
                    debug_log["skipped_consumers"].append(consumer_debug)

            # --- Apply Windowing Logic (Moved outside consumer loop) ---
            if target_consumer_found_in_interval and interval_timestamp is not None:
                # Convert accumulated consumption from microWatts to Watts
                consumption_watts = consumption_in_interval / 1_000_000.0
                
                # Convert timestamp from seconds to milliseconds
                milliseconds = int(interval_timestamp * 1000)
                
                if current_window_start is None or milliseconds >= current_window_start + window_size_ms:
                    # If a window exists and has data, add it before starting a new one
                    if current_window_start is not None and accumulated_consumption > 0:
                         dt = self._convert_to_eet(current_window_start)
                         processed_data.append((current_window_start, accumulated_consumption, dt))
                    # Start a new window, aligned to window size
                    current_window_start = milliseconds - (milliseconds % window_size_ms) 
                    accumulated_consumption = consumption_watts
                else:
                    # Add to the current window
                    accumulated_consumption += consumption_watts
            elif not target_consumer_found_in_interval:
                 debug_log["intervals_without_target"] += 1
                 # print(f"DEBUG: Target type '{target_type}' not found in interval with host timestamp {host_interval.get('host',{}).get('timestamp')}")
                
        # Add the final accumulated point if it exists
        if current_window_start is not None and accumulated_consumption > 0:
            dt = self._convert_to_eet(current_window_start)
            processed_data.append((current_window_start, accumulated_consumption, dt))
            
        # Convert processed data to DataFrame
        if not processed_data:
            print(f"WARNING: No processed data generated for target_type='{target_type}'. Returning empty DataFrame.") # DEBUG
            df = pd.DataFrame(columns=['timestamp', 'consumption', 'datetime'])
        else:
            # Create DataFrame from the processed list which now includes datetime
            df = pd.DataFrame(processed_data, columns=['timestamp', 'consumption', 'datetime'])
            print(f"DEBUG: Generated DataFrame for '{target_type}' with {len(df)} rows.") # DEBUG

        # --- Debug Logging --- 
        debug_log["summary"] = {
            "processed_count": len(debug_log["processed_consumer_entries"]),
            "skipped_count": len(debug_log["skipped_consumers"]),
            "intervals_without_target": debug_log["intervals_without_target"],
            "total_windows_created": len(processed_data)
        }
        timestamp_str = datetime.now().strftime("%Y%m%d_%H%M%S")
        debug_log_filename = f"energy_processing_debug_{target_type}_{timestamp_str}.json"
        try:
            with open(debug_log_filename, 'w') as f:
                json.dump(debug_log, f, indent=2, default=str) # Use default=str for non-serializable types if any
            print(f"Debug log saved to {debug_log_filename}") # Changed status_var to print
        except Exception as e:
            print(f"Failed to save debug log: {str(e)}") # Changed status_var to print
        # --- End Debug Logging ---

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
            timestamp = timestamp / 1000  # Convert ms to seconds
            
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
            if "timestamp" in entry:
                # Convert seconds to milliseconds
                energy_timestamps.append(entry.get("timestamp", 0) * 1000)
        
        if energy_timestamps:
            # Use the full range of energy data
            return min(energy_timestamps), max(energy_timestamps)
            
        return None, None

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

    def _browse_mongodb_file(self, file_type):
        file_path = filedialog.askopenfilename(
            title=f"Select PowerAPI {file_type.upper()} Server Energy Data File",
            filetypes=[("JSON files", "*.json"), ("All files", "*.*")]
        )
        if file_path:
            if file_type == "api":
                self.mongodb_api_file_var.set(file_path)
                self._load_mongodb_data(file_path, "api")
            else:
                self.mongodb_db_file_var.set(file_path)
                self._load_mongodb_data(file_path, "db")
    
    def _load_mongodb_data(self, file_path, data_type):
        try:
            # Check if file exists and is not empty
            if not os.path.exists(file_path):
                messagebox.showerror("Error", f"File not found: {file_path}")
                return
                
            if os.path.getsize(file_path) == 0:
                messagebox.showerror("Error", f"File is empty: {file_path}")
                return
            
            # Read and parse JSON file
            with open(file_path, 'r') as file:
                try:
                    data = json.load(file)
                except json.JSONDecodeError as e:
                    messagebox.showerror("Error", f"Invalid JSON file: {str(e)}\nPlease ensure the file contains valid JSON data.")
                    return
            
            # Validate data structure
            if not isinstance(data, list):
                messagebox.showerror("Error", "Invalid data format: Expected a list of energy measurements")
                return
            
            if not data:
                messagebox.showerror("Error", "No data found in the file")
                return
            
            # Validate required fields in each entry
            required_fields = ["timestamp", "power", "target"]
            invalid_entries = []
            for i, entry in enumerate(data):
                if not all(field in entry for field in required_fields):
                    invalid_entries.append(i)
            
            if invalid_entries:
                messagebox.showerror("Error", 
                    f"Invalid data format: Missing required fields in entries {invalid_entries[:5]}\n"
                    f"Each entry must contain: {', '.join(required_fields)}")
                return
            
            # Get distinct targets
            targets = {entry.get("target", "") for entry in data if entry.get("target")}
            distinct_targets = list(targets)
            
            if not distinct_targets:
                messagebox.showwarning("Warning", "No target services found in the data file")
                return
            
            # Update target service combobox
            selected_target = None
            if data_type == "api":
                self.mongodb_api_target_combo['values'] = distinct_targets
                if distinct_targets:
                    # Try to auto-select based on container ID
                    if self.api_container_id:
                        for target in distinct_targets:
                            if self.api_container_id in target:
                                selected_target = target
                                print(f"DEBUG: Auto-selected PowerAPI API target '{selected_target}' based on ID '{self.api_container_id}'") # DEBUG
                                break
                    # Fallback to first target if no match or ID not available
                    if not selected_target:
                        selected_target = distinct_targets[0]
                    self.mongodb_api_target_combo.set(selected_target)
            else: # data_type == "db"
                self.mongodb_db_target_combo['values'] = distinct_targets
                if distinct_targets:
                    # Try to auto-select based on container ID
                    if self.db_container_id:
                        for target in distinct_targets:
                            if self.db_container_id in target:
                                selected_target = target
                                print(f"DEBUG: Auto-selected PowerAPI DB target '{selected_target}' based on ID '{self.db_container_id}'") # DEBUG
                                break
                    # Fallback to first target if no match or ID not available
                    if not selected_target:
                        selected_target = distinct_targets[0]
                    self.mongodb_db_target_combo.set(selected_target)
            
            # Store the raw data for later filtering
            if data_type == "api":
                self.mongodb_api_data = data
            else:
                self.mongodb_db_data = data
            
            self.status_var.set(f"Loaded PowerAPI {data_type.upper()} Server energy data with {len(distinct_targets)} target services")
            self.force_plot_update()
            
        except Exception as e:
            messagebox.showerror("Error", f"Failed to load PowerAPI data: {str(e)}")
            self.status_var.set(f"Error loading PowerAPI {data_type.upper()} Server data")
    
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
        filtered_data = [
            {
                "timestamp": entry["timestamp"]["$date"],
                "power": entry["power"]  # Already in Watts, no conversion needed
            }
            for entry in data if entry.get("target") == target_service
        ]
        
        if not filtered_data:
            return None
            
        # Convert to DataFrame and parse timestamps
        df = pd.DataFrame(filtered_data)
        # PowerAPI timestamps are in ISO8601 format strings, not seconds
        df['timestamp'] = pd.to_datetime(df['timestamp'], format='ISO8601', utc=True)
        df.sort_values('timestamp', inplace=True)
        
        # Filter by time boundaries if provided
        if start_time_ms is not None and end_time_ms is not None:
            # Convert millisecond timestamps to pandas datetime objects
            start_time = pd.to_datetime(start_time_ms, unit='ms', utc=True)
            end_time = pd.to_datetime(end_time_ms, unit='ms', utc=True)
            
            # Apply the time filter
            df = df[(df['timestamp'] >= start_time) & (df['timestamp'] <= end_time)]
            
            # If no data remains after filtering, handle based on strict filtering mode
            if df.empty:
                if strict_filtering:
                    self.status_var.set(f"No PowerAPI data found within the experiment time range")
                    return None
                else:
                    # In lenient mode, return the original data
                    self.status_var.set(f"No PowerAPI data found within time range, showing all data")
                    df = pd.DataFrame(filtered_data)
                    df['timestamp'] = pd.to_datetime(df['timestamp'], format='ISO8601', utc=True)
                    df.sort_values('timestamp', inplace=True)
                
        return df

    # Add a new function to process MongoDB data with windowing
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
        # Convert timestamps to milliseconds for consistent handling
        df['timestamp_ms'] = df['timestamp'].astype(int) // 10**6  # Convert nanoseconds to milliseconds
        
        # Sort by timestamp
        df = df.sort_values('timestamp_ms')
        
        # Apply windowing
        current_window_start = None
        accumulated_power = 0.0
        
        for _, row in df.iterrows():
            milliseconds = row['timestamp_ms']
            power = row['power']
            
            if current_window_start is None or milliseconds >= current_window_start + window_size_ms:
                if current_window_start is not None:
                    # Create datetime from milliseconds
                    dt = pd.to_datetime(current_window_start, unit='ms', utc=True)
                    processed_data.append((current_window_start, accumulated_power, dt))
                current_window_start = milliseconds
                accumulated_power = power
            else:
                accumulated_power += power
        
        # Add final point
        if current_window_start is not None:
            dt = pd.to_datetime(current_window_start, unit='ms', utc=True)
            processed_data.append((current_window_start, accumulated_power, dt))
        
        # Convert processed data to DataFrame
        result_df = pd.DataFrame(processed_data, columns=['timestamp_ms', 'power', 'datetime'])
        
        return result_df

if __name__ == "__main__":
    root = tk.Tk()
    app = ExperimentVisualizer(root)
    root.mainloop() 
