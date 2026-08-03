#!/usr/bin/env python3
"""
Shared utilities for Asana release scripts.
"""

import re
import subprocess
import sys
import time
from typing import List
import asana
import git
from git import Repo
from dataclasses import dataclass


@dataclass
class AsanaTaskLink:
    url: str | None
    commit_hash: str


def log(message: str) -> None:
    """Log a message to stderr."""
    print(message, file=sys.stderr)


def get_commits_between(repo_path: str, start_commit: str, end_commit: str) -> List[git.Commit]:
    """
    Get a list of commits between two commit hashes in a git repository.
    """
    repo = Repo(repo_path)
    
    # Get all commits between start (exclusive) and end (inclusive)
    commits = list(repo.iter_commits(f"{start_commit}..{end_commit}"))
    
    return commits


def _build_flexible_prefix_pattern(prefix: str) -> str:
    """
    Build a flexible regex pattern from a prefix string.
    Allows flexible whitespace (including newlines) between words and around "/".
    """
    # Split prefix by whitespace, escape each part, join with \s+ to allow flexible whitespace
    prefix_parts = prefix.split()
    flexible = r"\s+".join(re.escape(part) for part in prefix_parts)
    # Allow optional whitespace around "/" (e.g., "Task / Issue" or "Task/ Issue")
    flexible = flexible.replace("/", r"\s*/\s*")
    return flexible


def extract_asana_task_links(commits: List[git.Commit], url_prefix: str) -> List[AsanaTaskLink]:
    """
    Extract Asana task links from commit messages.
    """
    task_links = []
    prefix_pattern = _build_flexible_prefix_pattern(url_prefix)
    url_pattern = re.compile(rf"(?:{prefix_pattern})\s*(https://app\.asana\.com/\S*)")
    
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
    
    return task_links


def get_public_release_tags(repo_path: str) -> List[str]:
    """
    Return all public release tags sorted by semantic version (ascending).
    Public release tags match the pattern: X.Y.Z (e.g., 5.264.0)
    """
    public_pattern = re.compile(r'^\d+\.\d+\.\d+$')

    try:
        result = subprocess.run(
            ["git", "-C", repo_path, "for-each-ref", "--sort=version:refname",
             "--format=%(refname:short)", "refs/tags"],
            capture_output=True,
            text=True,
            check=True,
        )
        tags = result.stdout.strip().splitlines()
        return [tag for tag in tags if public_pattern.match(tag)]
    except subprocess.CalledProcessError:
        return []


def get_latest_public_release_tag(repo_path: str) -> str | None:
    """Return the most recent public release tag, or None."""
    tags = get_public_release_tags(repo_path)
    return tags[-1] if tags else None


def is_ancestor(repo_path: str, ancestor: str, descendant: str) -> bool:
    """
    Return True if `ancestor` is reachable from `descendant` (i.e. all commits
    reachable from `ancestor` are already in `descendant`'s history).
    """
    result = subprocess.run(
        ["git", "-C", repo_path, "merge-base", "--is-ancestor", ancestor, descendant],
    )
    return result.returncode == 0


def get_public_release_tag_before(repo_path: str, current_tag: str) -> str | None:
    """
    Return the public release tag immediately before `current_tag` by semantic version.
    Returns None if `current_tag` is not found or is the earliest tag.
    """
    tags = get_public_release_tags(repo_path)

    if current_tag not in tags:
        return None

    idx = tags.index(current_tag)
    return tags[idx - 1] if idx > 0 else None


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


def resolve_task_id(link: AsanaTaskLink) -> str | None:
    """
    Safely resolve an Asana task ID from a link.

    Returns None (and logs) for a missing or malformed URL, so a single bad
    link never aborts the workflow.
    """
    if not link.url:
        return None

    try:
        task_id = extract_task_id_from_url(link.url)
    except Exception as e:
        log(f"Skipping malformed Asana URL '{link.url}': {e}")
        return None

    if not task_id:
        log(f"Skipping Asana URL with no task ID: {link.url}")
        return None

    return task_id


def get_commits_by_hashes(repo_path: str, hashes: List[str]) -> List[git.Commit]:
    """
    Resolve an explicit list of commit SHAs to Commit objects.
    Unresolvable SHAs are logged and skipped instead of raising.
    """
    repo = Repo(repo_path)

    commits = []
    for sha in hashes:
        sha = sha.strip()
        if not sha:
            continue
        try:
            commits.append(repo.commit(sha))
        except Exception as e:
            log(f"Skipping unresolvable commit '{sha}': {e}")

    return commits


def get_latest_release_tag_in_line(repo_path: str, version: str) -> str | None:
    """
    Return the most recent existing public release tag in the same minor line as
    `version` (X.Y.<patch>) with a patch number strictly less than `version`'s.

    For a hotfix X.Y.Z this is the base normal release X.Y.0 or the newest prior
    hotfix X.Y.<Z-1>, whichever exists. Returns None if there is no such tag.
    """
    version_pattern = re.compile(r'^(\d+)\.(\d+)\.(\d+)$')

    m = version_pattern.match(version)
    if not m:
        return None
    major, minor, patch = int(m.group(1)), int(m.group(2)), int(m.group(3))

    candidates = []
    for tag in get_public_release_tags(repo_path):
        tm = version_pattern.match(tag)
        if not tm:
            continue
        if int(tm.group(1)) == major and int(tm.group(2)) == minor and int(tm.group(3)) < patch:
            candidates.append((int(tm.group(3)), tag))

    if not candidates:
        return None

    return max(candidates, key=lambda t: t[0])[1]


# --- Asana task manipulation helpers (shared by release scripts) ---


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


def iter_valid_task_ids(task_links: List[AsanaTaskLink]):
    """
    Yield resolvable Asana task IDs from the given links.

    Links with no URL, a malformed URL, or no extractable task ID are skipped
    (and logged) instead of raising, so a single bad URL never aborts the
    workflow.
    """
    for link in task_links:
        task_id = resolve_task_id(link)
        if task_id:
            yield task_id


def build_release_task_links_html(task_links: List[AsanaTaskLink]) -> str:
    """
    Build HTML list items for release task notes.

    Use explicit href links instead of data-asana-gid anchors so the request
    does not fail if Asana cannot auto-resolve an object ID.
    """
    items = [
        f'<li><a href="{link.url}">{task_id}</a></li>'
        for link in task_links
        if (task_id := resolve_task_id(link))
    ]

    return "".join(items)


def build_release_includes_html(task_links: List[AsanaTaskLink],
                                previous_release_links: List[AsanaTaskLink] = None,
                                previous_release_label: str = None) -> str:
    """
    Build the "This release includes:" notes block. When previous_release_links
    is given, append a second section (e.g. "Includes from 5.287.0:") for tasks
    already shipped in this line.
    """
    html = f"<strong>This release includes:</strong><ul>{build_release_task_links_html(task_links)}</ul>"
    if previous_release_links:
        html += f"<strong>{previous_release_label}</strong><ul>{build_release_task_links_html(previous_release_links)}</ul>"
    return html


def create_asana_release_task(client: asana.ApiClient,
                              workspace_id: str,
                              release_tag: str,
                              template_task_id: str,
                              section_id: str,
                              project_id: str,
                              task_links: List[AsanaTaskLink],
                              previous_release_links: List[AsanaTaskLink] = None,
                              previous_release_label: str = None) -> str:
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

    # Build the task links bullet points (optionally with a second "Includes from ..." section)
    replacement = build_release_includes_html(task_links, previous_release_links, previous_release_label)

    # Replace the placeholder section with actual task links using regex to handle newlines
    placeholder_pattern = r"<strong>This release includes:</strong>\s*<ul><li>Add Asana tasks in release here and tag them with release number e\.g android-release-5\.9\.0</li></ul>"
    updated_notes = re.sub(placeholder_pattern, replacement, current_notes)
    if updated_notes == current_notes:
        # Fallback if pattern not found
        updated_notes = current_notes + replacement

    # Update the task with the new notes
    log(f"Updating task with notes: {updated_notes}")
    tasks_api.update_task({"data": {"html_notes": updated_notes}}, new_task_id, {})
    log(f"Updated task description with {len(task_links) + len(previous_release_links or [])} task links")

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


def find_or_create_tag(client: asana.ApiClient, workspace_id: str, tag_name: str) -> str:
    """
    Find an existing tag by name or create a new one.
    Returns the tag GID.
    """
    tags_api = asana.TagsApi(client)

    # Search for existing tag - let exceptions propagate to avoid creating duplicates
    # if a transient error (network timeout, rate limiting) occurs
    tags = tags_api.get_tags_for_workspace(workspace_id, {"opt_fields": "name"})
    for tag in tags:
        if tag['name'] == tag_name:
            log(f"Found existing tag '{tag_name}' with ID {tag['gid']}")
            return tag['gid']

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
    for task_id in iter_valid_task_ids(task_links):
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
    for task_id in iter_valid_task_ids(task_links):
        try:
            tasks_api.remove_project_for_task({"data": {"project": project_id}}, task_id)
            removed_count += 1
        except Exception as e:
            log(f"Error removing task {task_id} from project: {e}")

    log(f"Removed {removed_count} tasks from project")
