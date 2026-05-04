#!/usr/bin/env python3
"""Tests for asana_release_utils and lgc-asana-tasks, extract_asana_task_links and _build_flexible_prefix_pattern."""

from unittest.mock import MagicMock, patch
import pytest
from asana_release_utils import (
    get_public_release_tags,
    get_latest_public_release_tag,
    get_public_release_tag_before,
    extract_asana_task_links,
    _build_flexible_prefix_pattern,
    AsanaTaskLink,
)

def _mock_git_tags_result(tags: list[str]):
    """Create a mock subprocess result with the given tag list."""
    result = MagicMock()
    result.stdout = "\n".join(tags)
    return result



# --- get_public_release_tags / get_latest_public_release_tag / get_public_release_tag_before ---


class TestGetPublicReleaseTags:
    @patch("asana_release_utils.subprocess.run")
    def test_returns_only_release_tags(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.262.0",
            "5.263.0-internal",
            "LGC-2025-01-01T000000",
            "5.264.0",
        ])
        assert get_public_release_tags(".") == ["5.262.0", "5.264.0"]

    @patch("asana_release_utils.subprocess.run")
    def test_returns_empty_when_no_release_tags(self, mock_run):
        mock_run.return_value = _mock_git_tags_result(["LGC-2025-01-01T000000"])
        assert get_public_release_tags(".") == []

    @patch("asana_release_utils.subprocess.run")
    def test_returns_empty_on_empty_repo(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([])
        assert get_public_release_tags(".") == []


class TestGetLatestPublicReleaseTag:
    @patch("asana_release_utils.subprocess.run")
    def test_returns_latest_release_tag(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.262.0",
            "5.263.0",
            "5.264.0",
        ])
        assert get_latest_public_release_tag(".") == "5.264.0"

    @patch("asana_release_utils.subprocess.run")
    def test_returns_none_when_no_release_tags(self, mock_run):
        mock_run.return_value = _mock_git_tags_result(["5.263.0-internal"])
        assert get_latest_public_release_tag(".") is None


class TestGetPublicReleaseTagBefore:
    @patch("asana_release_utils.subprocess.run")
    def test_returns_previous_tag(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.262.0",
            "5.263.0",
            "5.264.0",
        ])
        assert get_public_release_tag_before(".", "5.264.0") == "5.263.0"

    @patch("asana_release_utils.subprocess.run")
    def test_returns_none_when_first_tag(self, mock_run):
        mock_run.return_value = _mock_git_tags_result(["5.262.0", "5.263.0"])
        assert get_public_release_tag_before(".", "5.262.0") is None

    @patch("asana_release_utils.subprocess.run")
    def test_returns_none_when_tag_not_found(self, mock_run):
        mock_run.return_value = _mock_git_tags_result(["5.262.0", "5.263.0"])
        assert get_public_release_tag_before(".", "5.999.0") is None

    @patch("asana_release_utils.subprocess.run")
    def test_ignores_non_release_tags(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.262.0",
            "5.263.0-internal",
            "LGC-2025-01-01T000000",
            "5.264.0",
        ])
        assert get_public_release_tag_before(".", "5.264.0") == "5.262.0"

# --- extract_asana_task_links ---

STANDARD_PREFIX = "Task/Issue URL:"

def _fake_commit(hexsha: str, message: str):
    commit = MagicMock()
    commit.hexsha = hexsha
    commit.message = message
    return commit

class TestExtractAsanaTaskLinks:
    def test_standard_prefix(self):
        commits = [_fake_commit("aaa", "Task/Issue URL: https://app.asana.com/0/123/456")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result == [AsanaTaskLink(url="https://app.asana.com/0/123/456", commit_hash="aaa")]

    def test_no_url_returns_none(self):
        commits = [_fake_commit("bbb", "Fix a bug\n\nNo link here")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result == [AsanaTaskLink(url=None, commit_hash="bbb")]

    def test_multiple_commits(self):
        commits = [
            _fake_commit("c1", "Task/Issue URL: https://app.asana.com/0/111/222"),
            _fake_commit("c2", "No link"),
            _fake_commit("c3", "Task/Issue URL: https://app.asana.com/0/333/444"),
        ]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result == [
            AsanaTaskLink(url="https://app.asana.com/0/111/222", commit_hash="c1"),
            AsanaTaskLink(url=None, commit_hash="c2"),
            AsanaTaskLink(url="https://app.asana.com/0/333/444", commit_hash="c3"),
        ]

    def test_url_with_query_params(self):
        url = "https://app.asana.com/1/137249556945/project/12345/task/67890?focus=true"
        commits = [_fake_commit("ddd", f"Task/Issue URL: {url}")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == url

    def test_url_with_trailing_f(self):
        url = "https://app.asana.com/0/123/456/f"
        commits = [_fake_commit("eee", f"Task/Issue URL: {url}")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == url

    def test_new_style_url(self):
        url = "https://app.asana.com/1/137249556945/project/1209107918776641/task/1210066941136479"
        commits = [_fake_commit("fff", f"Task/Issue URL: {url}")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == url

    def test_prefix_on_separate_line_from_url(self):
        message = "Some title\n\nTask/Issue URL:\nhttps://app.asana.com/0/123/456"
        commits = [_fake_commit("ggg", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"

    def test_flexible_whitespace_between_prefix_words(self):
        message = "Task/Issue  URL:  https://app.asana.com/0/123/456"
        commits = [_fake_commit("hhh", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"

    def test_flexible_whitespace_around_slash(self):
        message = "Task / Issue URL: https://app.asana.com/0/123/456"
        commits = [_fake_commit("iii", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"

    def test_spaces_around_slash_and_extra_whitespace(self):
        message = "Task  /  Issue   URL: https://app.asana.com/0/123/456"
        commits = [_fake_commit("jjj", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"

    def test_prefix_in_multiline_commit_message(self):
        message = (
            "feat: Add new feature\n"
            "\n"
            "This adds a great feature.\n"
            "\n"
            "Task/Issue URL: https://app.asana.com/0/999/888"
        )
        commits = [_fake_commit("kkk", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/999/888"

    def test_wrong_domain_not_matched(self):
        commits = [_fake_commit("nnn", "Task/Issue URL: https://app.example.com/0/1/2")]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url is None

    def test_empty_commit_list(self):
        result = extract_asana_task_links([], STANDARD_PREFIX)
        assert result == []

    def test_url_stops_at_whitespace(self):
        message = "Task/Issue URL: https://app.asana.com/0/123/456 some trailing text"
        commits = [_fake_commit("ooo", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"

    def test_prefix_newline_between_words(self):
        message = "Task/Issue\nURL: https://app.asana.com/0/123/456"
        commits = [_fake_commit("ppp", message)]
        result = extract_asana_task_links(commits, STANDARD_PREFIX)
        assert result[0].url == "https://app.asana.com/0/123/456"


# --- _build_flexible_prefix_pattern ---


class TestBuildFlexiblePrefixPattern:
    def test_single_word(self):
        pattern = _build_flexible_prefix_pattern("URL:")
        assert pattern == "URL:"

    def test_words_joined_with_flexible_whitespace(self):
        pattern = _build_flexible_prefix_pattern("Task URL:")
        assert r"\s+" in pattern

    def test_slash_gets_flexible_whitespace(self):
        pattern = _build_flexible_prefix_pattern("Task/Issue URL:")
        assert r"\s*/\s*" in pattern
