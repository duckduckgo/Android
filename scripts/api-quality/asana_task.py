#!/usr/bin/env python3
"""
Creates the Asana task of one workflow run, or updates it when the run is re-run. Used by the scorecard and compare
workflows; the weekly report goes through asana_sync.py.

A scorecard task goes to the Scorecards section. A regression task goes to Triage with the Needs explanation status,
like a finding task. Every Asana ID comes from the GH_API_QUALITY_* environment variables; see asana_sync.py.

Usage:
  asana_task.py --kind scorecard|regression --name TEXT --notes-md FILE

Requires ASANA_ACCESS_TOKEN, and GITHUB_RUN_ID, GITHUB_SERVER_URL and GITHUB_REPOSITORY as set by GitHub Actions.
"""

import argparse

from asana_sync import asana_apis, github_run, load_config, markdown_to_asana_html, needs_explanation, upsert_run_task


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--kind", choices=["scorecard", "regression"], required=True)
    parser.add_argument("--name", required=True)
    parser.add_argument("--notes-md", required=True)
    args = parser.parse_args()
    config = load_config()

    if args.kind == "scorecard":
        section, custom_fields = config["sections"]["scorecards"], None
    else:
        section, custom_fields = config["sections"]["triage"], needs_explanation(config)

    run_id, run_url = github_run(args.kind)
    tasks_api, stories_api = asana_apis()
    with open(args.notes_md) as f:
        notes = markdown_to_asana_html(f.read())
    upsert_run_task(tasks_api, stories_api, config["project"], section, args.name, notes, config["fields"]["github_run"], run_id,
                    run_url, custom_fields)


if __name__ == "__main__":
    main()
