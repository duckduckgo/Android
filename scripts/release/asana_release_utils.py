#!/usr/bin/env python3
"""
Shared utilities for Asana release scripts.
"""

import re
import sys
from typing import List
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
    
    return task_links


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
