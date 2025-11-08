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
            scenario = row.get('Scenario', row.get('scenario', 'unknown'))
            execution_time = row.get('Total execution time', row.get('total execution time'))

            if execution_time:
                # Convert time to seconds (gradle-profiler outputs in ms)
                time_seconds = float(execution_time) / 1000.0

                if scenario not in scenario_data:
                    scenario_data[scenario] = []
                scenario_data[scenario].append(time_seconds)

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


def calculate_percentage_change(base: float, head: float) -> float:
    """Calculate percentage change from base to head."""
    if base == 0:
        return 0
    return ((head - base) / base) * 100


def generate_comparison_markdown(base_results: Dict[str, BenchmarkResult],
                                   head_results: Dict[str, BenchmarkResult]) -> str:
    """Generate markdown comparison table."""

    if not base_results and not head_results:
        return "No benchmark results found."

    # Determine all scenarios
    all_scenarios = set(base_results.keys()) | set(head_results.keys())
    scenario_order = ['clean-build', 'abi-change', 'non-abi-change']
    ordered_scenarios = [s for s in scenario_order if s in all_scenarios]
    ordered_scenarios.extend([s for s in all_scenarios if s not in scenario_order])

    md = "# Build Performance Benchmark Results\n\n"

    if not base_results:
        md += "## Head Branch Results\n\n"
        md += "| Scenario | Mean | Median | Std Dev | Min | Max |\n"
        md += "|----------|------|--------|---------|-----|-----|\n"

        for scenario in ordered_scenarios:
            if scenario in head_results:
                result = head_results[scenario]
                md += f"| {result.scenario} | {format_time(result.mean)} | {format_time(result.median)} | "
                md += f"±{format_time(result.std_dev)} | {format_time(result.min)} | {format_time(result.max)} |\n"

    elif not head_results:
        md += "## Base Branch Results\n\n"
        md += "| Scenario | Mean | Median | Std Dev | Min | Max |\n"
        md += "|----------|------|--------|---------|-----|-----|\n"

        for scenario in ordered_scenarios:
            if scenario in base_results:
                result = base_results[scenario]
                md += f"| {result.scenario} | {format_time(result.mean)} | {format_time(result.median)} | "
                md += f"±{format_time(result.std_dev)} | {format_time(result.min)} | {format_time(result.max)} |\n"

    else:
        # Full comparison
        md += "## Comparison: Head vs Base\n\n"
        md += "| Scenario | Base Mean | Head Mean | Difference | Change | Status |\n"
        md += "|----------|-----------|-----------|------------|--------|--------|\n"

        total_regression = 0
        regression_count = 0

        for scenario in ordered_scenarios:
            base = base_results.get(scenario)
            head = head_results.get(scenario)

            if base and head:
                diff = head.mean - base.mean
                pct_change = calculate_percentage_change(base.mean, head.mean)

                # Determine status emoji
                if pct_change > 10:
                    status = "🔴 Regression"
                    total_regression += pct_change
                    regression_count += 1
                elif pct_change > 5:
                    status = "⚠️ Warning"
                elif pct_change < -5:
                    status = "✅ Improvement"
                else:
                    status = "➖ Neutral"

                sign = "+" if diff > 0 else ""
                md += f"| {scenario} | {format_time(base.mean)} | {format_time(head.mean)} | "
                md += f"{sign}{format_time(diff)} | {pct_change:+.1f}% | {status} |\n"

        # Summary
        md += "\n## Summary\n\n"
        if regression_count > 0:
            avg_regression = total_regression / regression_count
            md += f"⚠️ **{regression_count} scenario(s) show significant regression (>10%)**\n"
            md += f"Average regression: {avg_regression:.1f}%\n\n"
        else:
            md += "✅ No significant regressions detected\n\n"

        # Detailed statistics
        md += "### Detailed Statistics\n\n"
        for scenario in ordered_scenarios:
            base = base_results.get(scenario)
            head = head_results.get(scenario)

            if base and head:
                md += f"#### {scenario}\n\n"
                md += f"- **Base**: Mean={format_time(base.mean)}, Median={format_time(base.median)}, "
                md += f"StdDev=±{format_time(base.std_dev)}\n"
                md += f"- **Head**: Mean={format_time(head.mean)}, Median={format_time(head.median)}, "
                md += f"StdDev=±{format_time(head.std_dev)}\n"
                md += f"- **Iterations**: {len(head.data)} measured runs\n\n"

    return md


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: benchmark-comparison.py <head_csv> [base_csv]")
        sys.exit(1)

    head_csv = Path(sys.argv[1])
    base_csv = Path(sys.argv[2]) if len(sys.argv) > 2 else None

    print(f"Parsing head results from: {head_csv}")
    head_results = parse_csv(head_csv)

    base_results = {}
    if base_csv:
        print(f"Parsing base results from: {base_csv}")
        base_results = parse_csv(base_csv)

    # Generate comparison
    markdown = generate_comparison_markdown(base_results, head_results)

    # Write to output file
    output_file = Path("benchmark-result.md")
    with open(output_file, 'w') as f:
        f.write(markdown)

    print(f"\nResults written to: {output_file}")

    # Also print to stdout for GitHub Actions
    print("\n" + markdown)

    # Write to GitHub Step Summary if available
    github_step_summary = Path(os.environ.get('GITHUB_STEP_SUMMARY', ''))
    if github_step_summary and github_step_summary.parent.exists():
        with open(github_step_summary, 'a') as f:
            f.write('\n' + markdown + '\n')
        print(f"Results added to GitHub Step Summary")

    # Exit with error code if there are regressions
    if base_results and head_results:
        for scenario in head_results.keys():
            if scenario in base_results:
                pct_change = calculate_percentage_change(
                    base_results[scenario].mean,
                    head_results[scenario].mean
                )
                if pct_change > 10:
                    print(f"\n⚠️ Regression detected in {scenario}: {pct_change:+.1f}%")
                    sys.exit(1)


if __name__ == '__main__':
    import os
    main()
