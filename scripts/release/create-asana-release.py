#!/usr/bin/env python3

import os
import re
import argparse
from typing import List
import git
from git import Repo
import asana
from dataclasses import dataclass
import sys
import subprocess
import time

@dataclass
class AsanaTaskLink:
    url: str | None
    commit_hash: str

def log(message: str) -> None:
    print(message, file=sys.stderr)

def get_commits_between(repo_path: str, start_commit: str, end_commit: str) -> List[git.Commit]:
    """
    Get a list of commits between two commit hashes in a git repository.
    """
    repo = Repo(repo_path)
    
    # Get all commits between start (exclusive) and end (inclusive)
    commits = list(repo.iter_commits(f"{start_commit}..{end_commit}"))
    
    return commits

def extract_asana_task_links(commits: List[git.Commit], url_prefix: str) -> List[AsanaTaskLink]:
    """
    Extract Asana task links from commit messages.
    """
    task_links = []
    url_pattern = re.compile(rf"{re.escape(url_prefix)}\s*(https://app\.asana\.com/\S*)")
    
    for commit in commits:
        message = commit.message
        match = url_pattern.search(message)
        full_url = None
        if match:
            full_url = match.group(1)

        task_links.append(AsanaTaskLink(
            url=full_url,
            commit_hash=commit.hexsha,
        ))
    
    return [link for link in task_links if link.url]

def extract_task_id_from_url(url: str) -> str:
    """
    Extract task ID from various Asana URL formats.
    Examples:
    - https://app.asana.com/1/137249556945/project/1209107918776641/task/1210066941136479?focus=true
    - https://app.asana.com/1/137249556945/project/1209077031784564/task/1210067100873189
    - https://app.asana.com/0/1208400340757517/1208902659709099
    - https://app.asana.com/0/1208400340757517/1208902659709099/f
    """
    # Remove any query parameters
    url = url.split('?')[0]
    
    # Split by slashes and get the last non-empty part
    parts = [p for p in url.split('/') if p]
    
    # The task ID is either:
    # - The last part before any query parameters (for /task/ format)
    # - The last part (for short format)
    task_id = parts[-1]
    
    # If the last part is 'f', get the previous part
    if task_id == 'f':
        task_id = parts[-2]
    
    return task_id

def create_asana_release_task(client: asana.ApiClient,
                              workspace_id: str,
                              release_tag: str,
                              template_task_id: str,
                              section_id: str,
                              project_id: str,
                              task_links: List[AsanaTaskLink]) -> str:
    """
    Create a new Asana release task by duplicating a template task.
    """
    tasks_api = asana.TasksApi(client)

    # Duplicate the template task
    log(f"Duplicating template task {template_task_id}")
    job_response = tasks_api.duplicate_task(
        {
            "data": {
                "name": f"Android Release {release_tag}",
                "include": "subtasks,projects,notes"
            }
        },
        template_task_id,
        {}
    )

    if job_response['status'] == 'succeeded':
        new_task_id = job_response['new_task']['gid']
    elif job_response['status'] == 'failed':
        raise Exception(f"Task duplication failed: {job_response}")
    else:
        completed_job = wait_for_job(client, job_response['gid'])
        new_task_id = completed_job['new_task']['gid']

    log(f"Created new task {new_task_id}")

    # Get the current task to retrieve its notes
    task = tasks_api.get_task(new_task_id, {"opt_fields": "html_notes"})
    current_notes = task.get('html_notes', '')

    # Build the task links bullet points
    task_links_html = ""
    for link in task_links:
        if link.url:
            task_links_html += f'<li><a href="{link.url}"/></li>'

    # Replace the placeholder section with actual task links using regex to handle newlines
    placeholder_pattern = r"<strong>This release includes:</strong>\s*<ul><li>Add Asana tasks in release here and tag them with release number e\.g android-release-5\.9\.0</li></ul>"
    replacement = f"<strong>This release includes:</strong><ul>{task_links_html}</ul>"
    updated_notes = re.sub(placeholder_pattern, replacement, current_notes)
    if updated_notes == current_notes:
        # Fallback if pattern not found
        updated_notes = current_notes + replacement

    # Update the task with the new notes
    log(f"Updating task with notes: {updated_notes}")
    tasks_api.update_task({"data": {"html_notes": updated_notes}}, new_task_id, {})
    log(f"Updated task description with {len(task_links)} task links")

    # Add the task to the project section
    section_api = asana.SectionsApi(client)
    section_api.add_task_for_section(
        section_id,
        {
            "body": {
                "data": {
                    "task": new_task_id,
                }
            }
        }
    )

    return new_task_id

def wait_for_job(client: asana.ApiClient, job_gid: str, timeout: int = 30, interval: float = 0.5):
    """Wait for an Asana job to complete."""
    jobs_api = asana.JobsApi(client)
    elapsed = 0
    while elapsed < timeout:
        job = jobs_api.get_job(job_gid, {})
        if job['status'] == 'succeeded':
            return job
        if job['status'] == 'failed':
            raise Exception(f"Job failed: {job}")
        time.sleep(interval)
        elapsed += interval
    raise TimeoutError(f"Job {job_gid} did not complete within {timeout}s")

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

def find_or_create_tag(client: asana.ApiClient, workspace_id: str, tag_name: str) -> str:
    """
    Find an existing tag by name or create a new one.
    Returns the tag GID.
    """
    tags_api = asana.TagsApi(client)

    # Search for existing tag
    try:
        tags = tags_api.get_tags_for_workspace(workspace_id, {"opt_fields": "name"})
        for tag in tags:
            if tag['name'] == tag_name:
                log(f"Found existing tag '{tag_name}' with ID {tag['gid']}")
                return tag['gid']
    except Exception as e:
        log(f"Error searching for tag: {e}")

    # Create new tag if not found
    log(f"Creating new tag '{tag_name}'")
    new_tag = tags_api.create_tag({
        "data": {
            "name": tag_name,
            "workspace": workspace_id
        }
    }, {})
    return new_tag['gid']


def tag_tasks(client: asana.ApiClient, workspace_id: str, task_links: List[AsanaTaskLink], tag_name: str) -> None:
    """
    Tag all tasks from the task links with the specified tag.
    """
    log(f"Tagging tasks with '{tag_name}'")

    tag_id = find_or_create_tag(client, workspace_id, tag_name)
    tasks_api = asana.TasksApi(client)

    tagged_count = 0
    for link in task_links:
        if link.url:
            task_id = extract_task_id_from_url(link.url)
            try:
                tasks_api.add_tag_for_task({"data": {"tag": tag_id}}, task_id)
                tagged_count += 1
            except Exception as e:
                log(f"Error tagging task {task_id}: {e}")

    log(f"Tagged {tagged_count} tasks with '{tag_name}'")


def remove_tasks_from_project(client: asana.ApiClient, task_links: List[AsanaTaskLink], project_id: str) -> None:
    """
    Remove all tasks from the task links from the specified project.
    """
    log(f"Removing tasks from project {project_id}")

    tasks_api = asana.TasksApi(client)

    removed_count = 0
    for link in task_links:
        if link.url:
            task_id = extract_task_id_from_url(link.url)
            try:
                tasks_api.remove_project_for_task({"data": {"project": project_id}}, task_id)
                removed_count += 1
            except Exception as e:
                log(f"Error removing task {task_id} from project: {e}")

    log(f"Removed {removed_count} tasks from project")


def get_latest_public_release_tag_before_commit(repo_path: str, current_tag: str) -> str | None:
    """
    Return the previous public release tag before `current_tag`, sorted by semantic version.
    Public release tags match the pattern: X.Y.Z (e.g., 5.264.0)
    """
    public_pattern = re.compile(r'^\d+\.\d+\.\d+$')

    try:
        result = subprocess.run(
            ["git", "-C", repo_path, "for-each-ref", "--sort=version:refname", "--format=%(refname:short)", "refs/tags"],
            capture_output=True,
            text=True,
            check=True
        )
        tags = result.stdout.strip().splitlines()

        # Filter to only public release tags (X.Y.Z format)
        public_tags = [tag for tag in tags if public_pattern.match(tag)]

        if current_tag not in public_tags:
            return None

        idx = public_tags.index(current_tag)
        return public_tags[idx - 1] if idx > 0 else None
    except subprocess.CalledProcessError:
        return None


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
    parser.add_argument('--public-release', action='store_true', help='Whether the release is a public one or internal')
    parser.add_argument('--template-task-id', help='Asana template task ID to duplicate (required for public releases)')
    
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

        is_public_release = args.public_release

        if is_public_release and not args.template_task_id:
            log("Error: --template-task-id is required for public releases")
            return 1

        # Get the start tag (latest tag before the specified tag)
        if is_public_release:
            start_tag = get_latest_public_release_tag_before_commit(args.android_repo_path, args.tag)
        else:
            start_tag = get_latest_internal_tag_before_commit(args.android_repo_path, args.tag)
        if not start_tag:
            log(f"Error: No previous version tag found before {args.tag}")
            return 1
        
        log(f"Using tag {start_tag} as start commit")

        # Get commits between the tags
        commits = get_commits_between(args.android_repo_path, start_tag, args.tag)
        
        log(f"Extracting task links from {len(commits)} commits")
        # Extract Asana task links from commit messages
        task_links = extract_asana_task_links(commits, args.trigger_phrase)


        log(f"Extracted {len(task_links)} tasks from {len(commits)} commits")
        for link in task_links:
            log(f"  - {link.commit_hash[:9]}: {link.url or 'no task'}")

        # Create the Asana task
        if is_public_release:
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
        else:
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
