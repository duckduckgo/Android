#!/usr/bin/env python3
"""
Collect Asana task IDs from commits since the last public release.

Finds all Asana task URLs in commit messages between the latest public release
tag and a given end commit, extracts task IDs, and prints them as a JSON array
to stdout. This output is intended to be consumed by GitHub Actions workflows.
"""

import json
import argparse

from asana_release_utils import (
    log,
    get_commits_between,
    extract_asana_task_links,
    extract_task_id_from_url,
    get_latest_public_release_tag,
    collect_shipped_task_ids,
)


def main():
    parser = argparse.ArgumentParser(
        description="Collect Asana task IDs from commits since the last release",
    )
    parser.add_argument(
        "--end-commit",
        required=True,
        help="End commit SHA or tag (e.g. the LGC tag or HEAD)",
    )
    parser.add_argument(
        "--android-repo-path",
        default=".",
        help="Path to Android git repository (default: current directory)",
    )
    parser.add_argument(
        "--trigger-phrase",
        required=True,
        help="Prefix for Asana task URLs in commit messages",
    )
    parser.add_argument(
        "--start-tag",
        default=None,
        help="Override the start tag (defaults to the latest public release)",
    )

    args = parser.parse_args()

    try:
        start_tag = args.start_tag or get_latest_public_release_tag(args.android_repo_path)
        if not start_tag:
            log("Error: No public release tag found")
            return 1

        log(f"Using tag {start_tag} as start commit (latest release)")
        log(f"Using {args.end_commit} as end commit")

        commits = get_commits_between(args.android_repo_path, start_tag, args.end_commit)
        log(f"Found {len(commits)} commits between {start_tag} and {args.end_commit}")

        task_links = extract_asana_task_links(commits, args.trigger_phrase)
        task_links_with_url = [link for link in task_links if link.url]

        # A task can already have shipped in a release tag even though its
        # commit is also reachable from develop (e.g. a hotfix merged back).
        # Exclude by task ID, not commit SHA, so it stays excluded either way.
        already_released_task_ids = collect_shipped_task_ids(
            args.android_repo_path, start_tag, args.trigger_phrase
        )

        filtered_links = [
            link for link in task_links_with_url
            if extract_task_id_from_url(link.url) not in already_released_task_ids
        ]
        task_ids = [extract_task_id_from_url(link.url) for link in filtered_links]

        log(f"Found {len(task_ids)} Asana tasks")
        for link in filtered_links:
            log(f"  - {link.commit_hash[:9]}: {link.url}")

        print(json.dumps(task_ids))

        return 0

    except Exception as e:
        import traceback
        log(f"Unexpected error: {e}")
        log(f"Stack trace:\n{traceback.format_exc()}")
        return 1


if __name__ == "__main__":
    exit(main())
