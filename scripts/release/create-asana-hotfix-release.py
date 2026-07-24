#!/usr/bin/env python3

import os
import re
import argparse
from typing import List
import asana

from asana_release_utils import (
    AsanaTaskLink,
    log,
    resolve_task_id,
    get_commits_between,
    get_commits_by_hashes,
    extract_asana_task_links,
    get_public_release_tag_before,
    get_latest_release_tag_in_line,
    build_release_includes_html,
    create_asana_release_task,
    tag_tasks,
    remove_tasks_from_project,
)


def parse_commit_hashes(raw: str) -> List[str]:
    """Split a space- or comma-separated list of commit SHAs into a clean list."""
    return [sha for sha in re.split(r'[,\s]+', raw.strip()) if sha]


def base_normal_release(tag: str) -> str | None:
    """Return the base normal release X.Y.0 for a hotfix tag X.Y.Z."""
    m = re.match(r'^(\d+)\.(\d+)\.\d+$', tag)
    if not m:
        return None
    return f"{m.group(1)}.{m.group(2)}.0"


def dedupe_links_by_task_id(task_links: List[AsanaTaskLink]) -> List[AsanaTaskLink]:
    """
    Drop links that resolve to a task ID already seen (keeping the first
    occurrence) and links with no resolvable task ID.
    """
    seen = set()
    deduped = []
    for link in task_links:
        task_id = resolve_task_id(link)
        if not task_id or task_id in seen:
            continue
        seen.add(task_id)
        deduped.append(link)
    return deduped


def collect_shipped_links(repo_path: str, tag: str, trigger_phrase: str) -> List[AsanaTaskLink]:
    """
    Collect Asana task links already shipped in this minor line: the base normal
    release X.Y.0 plus any prior hotfixes, i.e. commits between the public release
    before X.Y.0 and the latest existing X.Y.* tag.
    """
    base_normal = base_normal_release(tag)
    if not base_normal:
        log(f"Could not derive base normal release from tag '{tag}'; skipping shipped commits")
        return []

    latest_line_tag = get_latest_release_tag_in_line(repo_path, tag)
    if not latest_line_tag:
        log(f"No existing tag found in the {base_normal} line before {tag}; skipping shipped commits")
        return []

    start_tag = get_public_release_tag_before(repo_path, base_normal)
    if not start_tag:
        log(f"No public release tag before base normal release {base_normal}; skipping shipped commits")
        return []

    log(f"Collecting shipped commits between {start_tag} and {latest_line_tag}")
    commits = get_commits_between(repo_path, start_tag, latest_line_tag)
    links = extract_asana_task_links(commits, trigger_phrase)
    return [link for link in links if link.url]


def collect_cherry_links(repo_path: str, commit_hashes: List[str], trigger_phrase: str) -> List[AsanaTaskLink]:
    """Collect Asana task links from the explicitly cherry-picked commits."""
    log(f"Collecting cherry-picked commits: {', '.join(commit_hashes)}")
    commits = get_commits_by_hashes(repo_path, commit_hashes)
    links = extract_asana_task_links(commits, trigger_phrase)
    return [link for link in links if link.url]


def main():
    parser = argparse.ArgumentParser(description='Create an Asana task for a hotfix release with links to cherry-picked tasks and the tasks already shipped in this minor line')
    parser.add_argument('--tag', required=True, help='Hotfix version tag (e.g., 5.284.1)')
    parser.add_argument('--commit-hashes', required=True, help='Space- or comma-separated commit SHAs cherry-picked into the hotfix')
    parser.add_argument('--android-repo-path', default='.', help='Path to Android git repository (default: current directory)')
    parser.add_argument('--trigger-phrase', required=True, help='Prefix for Asana task URLs in commit messages')
    parser.add_argument('--asana-project-id', help='Asana project ID (not required with --dry-run)')
    parser.add_argument('--asana-section-id', help='Asana section ID to place the task in (not required with --dry-run)')
    parser.add_argument('--asana-workspace-id', help='Asana workspace ID (not required with --dry-run)')
    parser.add_argument('--asana-api-key-env-var', help='Environment variable name containing the API key (not required with --dry-run)')
    parser.add_argument('--template-task-id', help='Asana template task ID to duplicate (not required with --dry-run)')
    parser.add_argument('--dry-run', action='store_true', help='Only resolve and print the tasks that would be included; make no Asana API calls')

    args = parser.parse_args()

    try:
        commit_hashes = parse_commit_hashes(args.commit_hashes)
        if not commit_hashes:
            log("Error: --commit-hashes must contain at least one commit SHA")
            return 1

        # Cherry-picked fixes for this hotfix (the new work) ...
        cherry_links = dedupe_links_by_task_id(collect_cherry_links(args.android_repo_path, commit_hashes, args.trigger_phrase))
        # ... plus everything already shipped in this minor line (normal release + prior hotfixes).
        shipped_links = collect_shipped_links(args.android_repo_path, args.tag, args.trigger_phrase)

        # Prior-line tasks get their own section; drop any already listed as cherry-picks.
        cherry_ids = {resolve_task_id(link) for link in cherry_links}
        prior_links = [link for link in dedupe_links_by_task_id(shipped_links) if resolve_task_id(link) not in cherry_ids]

        # Label the prior section with the most recent tag in the line: the previous
        # hotfix if there is one, otherwise the base X.Y.0 release.
        prior_tag = get_latest_release_tag_in_line(args.android_repo_path, args.tag)
        previous_label = f"Includes from {prior_tag}:" if prior_tag else "Includes from previous release:"

        log(f"Cherry-picked tasks: {len(cherry_links)}, prior-line tasks: {len(prior_links)}")
        for link in cherry_links:
            log(f"  [cherry] {link.commit_hash[:9]}: {link.url}")
        for link in prior_links:
            log(f"  [prior]  {link.commit_hash[:9]}: {link.url}")

        if args.dry_run:
            log(f"[dry-run] No Asana calls made. Task would be named 'Android Release {args.tag}' with notes:")
            log(build_release_includes_html(cherry_links, prior_links, previous_label))
            log(f"[dry-run] Only cherry-picked tasks would be tagged 'android-release-{args.tag}' and removed from the board:")
            for link in cherry_links:
                log(f"  - {link.url}")
            return 0

        missing = [
            name for name, value in (
                ('--asana-project-id', args.asana_project_id),
                ('--asana-section-id', args.asana_section_id),
                ('--asana-workspace-id', args.asana_workspace_id),
                ('--asana-api-key-env-var', args.asana_api_key_env_var),
                ('--template-task-id', args.template_task_id),
            ) if not value
        ]
        if missing:
            log(f"Error: Missing required arguments for a real run: {', '.join(missing)}")
            return 1

        asana_api_key = os.getenv(args.asana_api_key_env_var)
        if not asana_api_key:
            log("Error: Missing required environment variable")
            log(f"Please set {args.asana_api_key_env_var}")
            return 1

        configuration = asana.Configuration()
        configuration.access_token = asana_api_key
        client = asana.ApiClient(configuration)

        # Create the Asana hotfix task by duplicating the release template.
        task_id = create_asana_release_task(
            client,
            args.asana_workspace_id,
            args.tag,
            args.template_task_id,
            args.asana_section_id,
            args.asana_project_id,
            cherry_links,
            previous_release_links=prior_links,
            previous_release_label=previous_label,
        )

        # Tag and remove ONLY the newly cherry-picked tasks; shipped tasks were
        # already tagged/removed for their own release.
        tag_tasks(client, args.asana_workspace_id, cherry_links, f"android-release-{args.tag}")
        remove_tasks_from_project(client, cherry_links, args.asana_project_id)

        task_url = f"https://app.asana.com/1/{args.asana_workspace_id}/project/{args.asana_project_id}/task/{task_id}"
        print(task_url)  # Only the URL is ever printed to stdout

        return 0

    except Exception as e:
        import traceback
        log(f"Unexpected error: {e}")
        log(f"Stack trace:\n{traceback.format_exc()}")
        return 1


if __name__ == "__main__":
    exit(main())
