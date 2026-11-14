#!/usr/bin/env python3
"""
Creates the Asana task of a comparison that regressed, or updates it when the run is re-run. Used by the compare
workflow; the weekly report goes through asana_sync.py.

The task goes to Triage, like the weekly regressions task. Every Asana ID comes from the GH_API_QUALITY_* environment
variables; see asana_sync.py.

Usage:
  asana_task.py --name TEXT --notes-md FILE

Requires ASANA_ACCESS_TOKEN, and GITHUB_RUN_ID, GITHUB_SERVER_URL and GITHUB_REPOSITORY as set by GitHub Actions.
"""

import argparse

from asana_sync import asana_apis, github_run, load_config, upsert_run_task


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--name", required=True)
    parser.add_argument("--notes-md", required=True)
    args = parser.parse_args()
    config = load_config()

    run_id, run_url = github_run("regression")
    tasks_api, stories_api = asana_apis()
    with open(args.notes_md) as f:
        notes = f.read()
    upsert_run_task(tasks_api, stories_api, config["project"], config["sections"]["triage"], args.name, notes,
                    config["fields"]["github_run"], run_id, run_url)


if __name__ == "__main__":
    main()
