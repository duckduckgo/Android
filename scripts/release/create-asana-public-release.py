#!/usr/bin/env python3

import os
import argparse
import asana

from asana_release_utils import (
    log,
    get_commits_between,
    extract_asana_task_links,
    get_public_release_tag_before,
    create_asana_release_task,
    tag_tasks,
    remove_tasks_from_project,
)


def main():
    parser = argparse.ArgumentParser(description='Create an Asana task for public releases with links to tasks from git commits')
    parser.add_argument('--tag', required=True, help='Tag to use as end commit (e.g., 5.264.0)')
    parser.add_argument('--android-repo-path', default='.', help='Path to Android git repository (default: current directory)')
    parser.add_argument('--trigger-phrase', required=True, help='Prefix for Asana task URLs in commit messages')
    parser.add_argument('--asana-project-id', required=True, help='Asana project ID')
    parser.add_argument('--asana-section-id', required=True, help='Asana section ID to place the task in')
    parser.add_argument('--asana-workspace-id', required=True, help='Asana workspace ID')
    parser.add_argument('--asana-api-key-env-var', required=True, help='Environment variable name containing the API key')
    parser.add_argument('--template-task-id', required=True, help='Asana template task ID to duplicate')

    args = parser.parse_args()

    try:
        # Get environment variables
        asana_api_key = os.getenv(args.asana_api_key_env_var)

        # Validate environment variables
        if not asana_api_key:
            log("Error: Missing required environment variable")
            log(f"Please set {args.asana_api_key_env_var}")
            return 1

        configuration = asana.Configuration()
        configuration.access_token = asana_api_key
        client = asana.ApiClient(configuration)

        start_tag = get_public_release_tag_before(args.android_repo_path, args.tag)
        if not start_tag:
            log(f"Error: No previous public release tag found before {args.tag}")
            return 1

        log(f"Using tag {start_tag} as start commit")
        log(f"Using tag {args.tag} as end commit")

        # Get commits between the tags
        commits = get_commits_between(args.android_repo_path, start_tag, args.tag)

        log(f"Extracting task links from {len(commits)} commits")
        # Extract Asana task links from commit messages
        task_links = extract_asana_task_links(commits, args.trigger_phrase)
        # Filter to only include commits with task links
        task_links = [link for link in task_links if link.url]

        log(f"Extracted {len(task_links)} tasks from {len(commits)} commits")
        for link in task_links:
            log(f"  - {link.commit_hash[:9]}: {link.url or 'no task'}")

        # Create the Asana release task
        task_id = create_asana_release_task(
            client,
            args.asana_workspace_id,
            args.tag,
            args.template_task_id,
            args.asana_section_id,
            args.asana_project_id,
            task_links
        )

        # Tag all linked tasks with the release version
        tag_tasks(client, args.asana_workspace_id, task_links, f"android-release-{args.tag}")

        # Remove tasks from the release board project
        remove_tasks_from_project(client, task_links, args.asana_project_id)

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