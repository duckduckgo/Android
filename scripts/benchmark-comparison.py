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


def generate_terminal_summary(results: Dict[str, BenchmarkResult]) -> str:
    """Generate terminal-friendly summary with better formatting."""
    
    if not results:
        return "No benchmark results found."
    
    output = []
    output.append("=" * 80)
    output.append("BUILD PERFORMANCE BENCHMARK RESULTS")
    output.append("=" * 80)
    output.append("")
    
    # Group results by scenario (without phase/sample details for cleaner grouping)
    scenario_groups = {}
    for result in results.values():
        # Extract base scenario name (before the first ' - ')
        base_scenario = result.scenario.split(' - ')[0]
        if base_scenario not in scenario_groups:
            scenario_groups[base_scenario] = []
        scenario_groups[base_scenario].append(result)
    
    for base_scenario, scenario_results in scenario_groups.items():
        output.append(f"📊 {base_scenario}")
        output.append("-" * (len(base_scenario) + 4))
        
        for result in scenario_results:
            # Extract the metric type and phase from the scenario name
            parts = result.scenario.split(' - ')
            if len(parts) >= 2:
                metric_type = parts[1].split(' (')[0]  # Remove phase info
                phase = parts[1].split(' (')[1].rstrip(')') if '(' in parts[1] else 'Unknown'
                phase_icon = "🔥 (WARM_UP)" if phase == "WARM_UP" else "📈 (MEASURE)"
            else:
                metric_type = "Unknown"
                phase_icon = "❓"
            
            output.append(f"  {phase_icon} {metric_type}:")
            output.append(f"    Mean:   {format_time(result.mean):>8}")
            output.append(f"    Median: {format_time(result.median):>8}")
            output.append(f"    StdDev: ±{format_time(result.std_dev):>7}")
            output.append(f"    Range:  {format_time(result.min):>8} - {format_time(result.max)}")
            output.append("")
        
        output.append("")
    
    return "\n".join(output)


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
    print(generate_terminal_summary(results))

if __name__ == '__main__':
    main()
