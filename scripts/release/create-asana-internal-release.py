#!/usr/bin/env python3

import os
import re
import argparse
from typing import List
import asana
import subprocess

from asana_release_utils import (
    AsanaTaskLink,
    log,
    get_commits_between,
    extract_asana_task_links,
    extract_task_id_from_url,
)


def create_asana_task(client: asana.ApiClient,
                      workspace_id: str,
                      release_tag: str,
                      task_links: List[AsanaTaskLink],
                      section_id: str) -> str:
    """
    Create a new Asana task with the list of task links in its description.
    """
    # Format the description with all task links
    description = "<body>"
    description += "<h2>Included Tasks</h2>"
    for link in task_links:
        if link.url:
            task_link = f"<a data-asana-gid=\"{extract_task_id_from_url(link.url)}\"/>"
        else:
            task_link = "no task"
        commit_url = f"https://github.com/duckduckgo/Android/commit/{link.commit_hash}"
        description += f"- {task_link} - <a href=\"{commit_url}\">{link.commit_hash[:9]}</a>\n"
    
    description += "</body>"
    
    tasks_api = asana.TasksApi(client)
    section_api = asana.SectionsApi(client)

    # Create the task
    task = tasks_api.create_task(
        {
            "data": {
                "name": f"Android Internal Release {release_tag}",
                "html_notes": description,
                "workspace": workspace_id
            }
        },
        {}
    )
    
    # Add the task to the project and optionally to a section
    section_api.add_task_for_section(
        section_id,
        {
            "body": {
                "data": {
                    "task": task['gid'],
                }
            }
        }
    )
    
    return task['gid']


def get_latest_internal_tag_before_commit(repo_path: str, current_tag: str) -> str | None:
    """
    Return the previous *internal* tag before `current_tag`, sorted by creation date (not version number).
    """
    internal_pattern = re.compile(r'^\d+\.\d+\.\d+(?:\.\d+)?-internal$')

    try:
        result = subprocess.run(
            ["git", "-C", repo_path, "tag", "--sort=creatordate"],
            capture_output=True,
            text=True,
            check=True
        )
        tags = result.stdout.strip().splitlines()

        # Filter to only internal tags
        internal_tags = [tag for tag in tags if internal_pattern.match(tag)]

        if current_tag not in internal_tags:
            return None

        idx = internal_tags.index(current_tag)
        return internal_tags[idx - 1] if idx > 0 else None
    except subprocess.CalledProcessError:
        return None


def main():
    parser = argparse.ArgumentParser(description='Create an Asana task with links to tasks from git commits')
    parser.add_argument('--tag', required=True, help='Tag to use as end commit') # Example: v0.44.0
    parser.add_argument('--android-repo-path', default='.', help='Path to Android git repository (default: current directory)')
    parser.add_argument('--trigger-phrase', required=True, help='Prefix for Asana task URLs in commit messages')
    parser.add_argument('--asana-project-id', required=True, help='Asana project ID')
    parser.add_argument('--asana-section-id', required=True, help='Asana section ID to place the task in')
    parser.add_argument('--asana-workspace-id', required=True, help='Asana workspace ID')
    parser.add_argument('--asana-api-key-env-var', required=True, help='Environment variable name containing the API key')
    
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
        
        # Get the start tag (latest tag before the specified tag)
        # start_tag = get_latest_tag_before_commit(args.android_repo_path, args.tag)
        start_tag = get_latest_internal_tag_before_commit(args.android_repo_path, args.tag)
        if not start_tag:
            log(f"Error: No previous version tag found before {args.tag}")
            return 1
        
        log(f"Using tag {start_tag} as start commit")
        log(f"Using tag {args.tag} as end commit")
        
        # Get commits between the tags
        commits = get_commits_between(args.android_repo_path, start_tag, args.tag)
        
        log(f"Extracting task links from {len(commits)} commits")
        # Extract Asana task links from commit messages
        task_links = extract_asana_task_links(commits, args.trigger_phrase)

        # Create the Asana task with the tag name
        task_id = create_asana_task(client, args.asana_workspace_id, args.tag, task_links, args.asana_section_id)
        task_url = f"https://app.asana.com/1/{args.asana_workspace_id}/project/{args.asana_project_id}/task/{task_id}"
        print(task_url) # Only the URL is ever printed to stdout

        return 0
        
    except Exception as e:
        import traceback
        log(f"Unexpected error: {e}")
        log(f"Stack trace:\n{traceback.format_exc()}")
        return 1


if __name__ == "__main__":
    exit(main())
