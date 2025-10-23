#!/usr/bin/env python3
"""
Build Benchmark Comparison Script

Compares build performance metrics from gradle-profiler CSV outputs.
Analyzes clean build, ABI change, and non-ABI change scenarios.
"""

import csv
import sys
import statistics
from pathlib import Path
from typing import Dict, List, Optional


class BenchmarkResult:
    """Represents benchmark results for a single scenario."""

    def __init__(self, scenario: str, mean: float, median: float, std_dev: float, data: List[float]):
        self.scenario = scenario
        self.mean = mean
        self.median = median
        self.std_dev = std_dev
        self.data = data
        self.min = min(data) if data else 0
        self.max = max(data) if data else 0


def parse_csv(csv_path: Path) -> Dict[str, BenchmarkResult]:
    """Parse gradle-profiler CSV output and extract metrics per scenario."""
    results = {}

    if not csv_path.exists():
        print(f"Warning: CSV file not found: {csv_path}")
        return results

    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        scenario_data = {}

        for row in reader:
            scenario = row.get('Scenario')
            phase = row.get('Phase')
            sample = row.get('Sample')
            duration = row.get('Duration')
            # Process both 'total execution time' and 'task start' samples
            if sample not in ['total execution time', 'task start'] or not duration:
                continue

            try:
                # Convert time to seconds (gradle-profiler outputs in ms)
                time_seconds = float(duration) / 1000.0

                # Group builds by phase, scenario, and sample type
                group_scenario = f'{scenario} - {sample} ({phase})'

                if group_scenario not in scenario_data:
                    scenario_data[group_scenario] = []
                scenario_data[group_scenario].append(time_seconds)
            except (ValueError, TypeError) as e:
                print(f"Warning: Could not parse duration '{duration}' for scenario '{scenario}': {e}")
                continue

    # Calculate statistics for each scenario
    for scenario, data in scenario_data.items():
        if data:
            results[scenario] = BenchmarkResult(
                scenario=scenario,
                mean=statistics.mean(data),
                median=statistics.median(data),
                std_dev=statistics.stdev(data) if len(data) > 1 else 0,
                data=data
            )

    return results


def format_time(seconds: float) -> str:
    """Format time in seconds to human-readable format."""
    if seconds >= 60:
        minutes = int(seconds // 60)
        secs = seconds % 60
        return f"{minutes}m {secs:.1f}s"
    else:
        return f"{seconds:.1f}s"


def generate_markdown_summary(results: Dict[str, BenchmarkResult]) -> str:
    """Generate markdown comparison table."""

    md = "# Build Performance Benchmark Results\n\n"
    md += "## Results\n\n"
    md += "| Scenario | Mean | Median | Std Dev | Min | Max |\n"
    md += "|----------|------|--------|---------|-----|-----|\n"

    for result in results.values():
        md += f"| {result.scenario} | {format_time(result.mean)} | {format_time(result.median)} | "
        md += f"±{format_time(result.std_dev)} | {format_time(result.min)} | {format_time(result.max)} |\n"

    return md


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: benchmark-comparison.py <benchmark_results_csv>")
        sys.exit(1)

    results_csv = Path(sys.argv[1])

    print(f"Parsing results from: {results_csv}")
    results = parse_csv(results_csv)
    print(generate_markdown_summary(results))

if __name__ == '__main__':
    main()
