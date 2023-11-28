# Usage: python3 parser.py input_json_file

import json
import csv
import sys
import os
from datetime import datetime

input_file = sys.argv[1]

with open(input_file) as json_file:
    data = json.load(json_file)

benchmarks = data['benchmarks']

metric_keys = set()
max_runs = 0
for benchmark in benchmarks:
    metric_keys.update(benchmark['metrics'].keys())
    for metric_key in metric_keys:
        max_runs = max(max_runs, len(benchmark['metrics'].get(metric_key, {}).get('runs', [])))

for metric_key in metric_keys:
    output_file = f'{metric_key}.csv'

    if not os.path.exists(output_file):
        with open(output_file, 'w', newline='') as csv_file:
            writer = csv.writer(csv_file)
            writer.writerow(['Name'] + [f'Run {i+1}' for i in range(max_runs)])

    with open(output_file, 'a', newline='') as csv_file:
        writer = csv.writer(csv_file)

        for benchmark in benchmarks:
            name = benchmark['name']
            runs = benchmark['metrics'].get(metric_key, {}).get('runs', [])
            writer.writerow([name] + runs + [''] * (max_runs - len(runs)))
