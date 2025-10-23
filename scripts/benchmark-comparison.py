#!/usr/bin/env python3
"""
Build Benchmark Comparison Script

Compares build performance metrics from gradle-profiler CSV outputs.
"""

import csv
import sys
import statistics
import requests
import argparse
from pathlib import Path
from typing import Dict, List, Optional
from urllib.parse import urlencode


class BenchmarkResult:
    """Represents benchmark results for a single scenario."""

    def __init__(self, scenario: str, sample: str, mean: float, median: float, std_dev: float, data: List[float]):
        self.scenario = scenario
        self.sample = sample
        self.mean = mean
        self.median = median
        self.std_dev = std_dev
        self.data = data
        self.min = min(data) if data else 0
        self.max = max(data) if data else 0


def parse_csv(csv_path: Path) -> List[BenchmarkResult]:
    """Parse gradle-profiler CSV output and extract metrics per scenario."""
    results = []

    if not csv_path.exists():
        print(f"Warning: CSV file not found: {csv_path}")
        return results

    with open(csv_path, 'r') as f:
        reader = csv.DictReader(f)
        measurements = {}

        for row in reader:
            scenario = row.get('Scenario')
            phase = row.get('Phase')
            sample = row.get('Sample')
            duration = row.get('Duration')
            # Process only 'total execution time' and 'task start' samples from MEASURE phase
            if (sample not in ['total execution time', 'task start'] or 
                not duration or 
                phase != 'MEASURE'):
                continue

            try:
                # Convert time to seconds (gradle-profiler outputs in ms)
                time_seconds = float(duration) / 1000.0

                key = (scenario, sample)
                if key not in measurements:
                    measurements[key] = []
                measurements[key].append(time_seconds)
            except (ValueError, TypeError) as e:
                print(f"Warning: Could not parse duration '{duration}' for scenario '{scenario}': {e}")
                continue

    # Create BenchmarkResult for each group of measurements
    for (scenario, sample), data in measurements.items():
        if data:
            results.append(BenchmarkResult(
                scenario=scenario,
                sample=sample,
                mean=statistics.mean(data),
                median=statistics.median(data),
                std_dev=statistics.stdev(data) if len(data) > 1 else 0,
                data=data
            ))

    return results


def format_time(seconds: float) -> str:
    """Format time in seconds to human-readable format."""
    if seconds >= 60:
        minutes = int(seconds // 60)
        secs = seconds % 60
        return f"{minutes}m {secs:.1f}s"
    else:
        return f"{seconds:.1f}s"


def generate_terminal_summary(results: List[BenchmarkResult]) -> str:
    """Generate terminal-friendly summary with better formatting."""
    
    if not results:
        return "No benchmark results found."
    
    output = []
    output.append("=" * 80)
    output.append("BUILD PERFORMANCE BENCHMARK RESULTS")
    output.append("=" * 80)
    output.append("")
    
    # Group results by scenario
    scenario_groups = {}
    for result in results:
        base_scenario = result.scenario
        if base_scenario not in scenario_groups:
            scenario_groups[base_scenario] = []
        scenario_groups[base_scenario].append(result)
    
    for base_scenario, scenario_results in scenario_groups.items():
        output.append(f"📊 {base_scenario}")
        output.append("-" * (len(base_scenario) + 4))
        
        # Separate total execution time and task start results
        total_exec_results = []
        task_start_results = []
        
        for result in scenario_results:
            # Use the task field directly
            if result.sample == "total execution time":
                total_exec_results.append(result)
            elif result.sample == "task start":
                task_start_results.append(result)
        
        # Display total execution time results
        for total_exec_result in total_exec_results:
            output.append(f"  📈 Total Execution Time:")
            output.append(f"    Mean:   {format_time(total_exec_result.mean):>8}")
            output.append(f"    Median: {format_time(total_exec_result.median):>8}")
            output.append(f"    StdDev: ±{format_time(total_exec_result.std_dev):>7}")
            output.append(f"    Range:  {format_time(total_exec_result.min):>8} - {format_time(total_exec_result.max)}")
            output.append("")
            
            # Display task start as sub-metric
            for task_start_result in task_start_results:
                output.append(f"    └─ Gradle Configuration Time:")
                output.append(f"      Mean:   {format_time(task_start_result.mean):>8}")
                output.append(f"      Median: {format_time(task_start_result.median):>8}")
                output.append(f"      StdDev: ±{format_time(task_start_result.std_dev):>7}")
                output.append(f"      Range:  {format_time(task_start_result.min):>8} - {format_time(task_start_result.max)}")
                output.append("")
        
        output.append("")
    
    return "\n".join(output)


def generate_markdown_summary(results: List[BenchmarkResult]) -> str:
    """Generate markdown results table."""

    md = "# Build Performance Benchmark Results\n\n"
    md += "## Results\n\n"
    md += "| Scenario | Mean | Median | Std Dev | Min | Max |\n"
    md += "|----------|------|--------|---------|-----|-----|\n"

    for result in results:
        md += f"| {result.scenario} | {format_time(result.mean)} | {format_time(result.median)} | "
        md += f"±{format_time(result.std_dev)} | {format_time(result.min)} | {format_time(result.max)} |\n"

    return md


def send_results_to_api(results: List[BenchmarkResult], github_action_run_id: Optional[str] = None, github_action_job_id: Optional[str] = None) -> None:
    """Send benchmark results to the API endpoint."""
    base_url = "https://improving.duckduckgo.com/t/m_build_time_android"
    
    for result in results:
        scenario = result.scenario
        sample = result.sample
        
        # Prepare query parameters
        params = {
            'scenario': scenario,
            'sample': sample,
            'mean': f"{result.mean:.3f}",
            'median': f"{result.median:.3f}",
            'std_dev': f"{result.std_dev:.3f}",
            'min': f"{result.min:.3f}",
            'max': f"{result.max:.3f}"
        }

        if github_action_run_id:
            params['github_action_run_id'] = github_action_run_id

        if github_action_job_id:
            params['github_action_job_id'] = github_action_job_id
        
        # Construct URL with query parameters
        url = f"{base_url}?{urlencode(params)}"
        
        try:
            print(f"Sending results for {scenario} - {sample}...")
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            print(f"✅ Successfully sent results for {scenario} - {sample}")
        except requests.exceptions.RequestException as e:
            print(f"❌ Failed to send results for {scenario} - {sample}: {e}")
        except Exception as e:
            print(f"❌ Unexpected error sending results for {scenario} - {sample}: {e}")


def main():
    parser = argparse.ArgumentParser(
        description="Compare build performance metrics from gradle-profiler CSV outputs"
    )
    parser.add_argument(
        "csv_file",
        type=Path,
        help="Path to the benchmark results CSV file"
    )
    parser.add_argument(
        "--github-action-run-id",
        type=str,
        help="GitHub Action run ID for linking results"
    )
    parser.add_argument(
        "--github-action-job-id",
        type=str,
        help="GitHub Action job ID for linking results"
    )
    parser.add_argument(
        "--report-pixel",
        action="store_true",
        help="Send results to the reporting API (requires --github-action-run-id and --github-action-job-id)"
    )
    
    args = parser.parse_args()
    
    print(f"Parsing results from: {args.csv_file}")
    results = parse_csv(args.csv_file)
    print(generate_terminal_summary(results))
    
    # Send results to API only if both conditions are met
    if results and args.report_pixel and args.github_action_run_id and args.github_action_job_id:
        print("\n" + "=" * 80)
        print("SENDING RESULTS TO API")
        print("=" * 80)
        send_results_to_api(results, args.github_action_run_id, args.github_action_job_id)
    elif results and args.report_pixel and (not args.github_action_run_id or not args.github_action_job_id):
        print("\n⚠️  --report-pixel flag set but no --github-action-run-id and --github-action-job-id provided. Skipping API requests.")


if __name__ == '__main__':
    main()
