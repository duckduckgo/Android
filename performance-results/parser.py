import json
import csv
import sys
import os
from datetime import datetime

# Get the input file name from command-line argument
input_file = sys.argv[1]

# Load the JSON data
with open(input_file) as json_file:
    data = json.load(json_file)

# Extract the required fields for each benchmark
benchmarks = data['benchmarks']

# Get all the metric keys
metric_keys = set()
max_runs = 0
for benchmark in benchmarks:
    metric_keys.update(benchmark['metrics'].keys())
    for metric_key in metric_keys:
        max_runs = max(max_runs, len(benchmark['metrics'].get(metric_key, {}).get('runs', [])))

# Write the data to separate CSV files for each metric
for metric_key in metric_keys:
    # Set the output file name with timestamp
    output_file = f'{metric_key}.csv'

    # Check if the output file exists
    if not os.path.exists(output_file):
        # Create the file if it doesn't exist
        with open(output_file, 'w', newline='') as csv_file:
            writer = csv.writer(csv_file)
            writer.writerow(['Name'] + [f'Run {i+1}' for i in range(max_runs)])

    # Append values to the file if it exists
    with open(output_file, 'a', newline='') as csv_file:
        writer = csv.writer(csv_file)

        for benchmark in benchmarks:
            name = benchmark['name']
            runs = benchmark['metrics'].get(metric_key, {}).get('runs', [])
            writer.writerow([name] + runs + [''] * (max_runs - len(runs)))
