#!/usr/bin/env python3
"""Tests for asana_release_utils and lgc-asana-tasks, extract_asana_task_links and _build_flexible_prefix_pattern."""

from unittest.mock import MagicMock, patch
import pytest
from asana_release_utils import (
    get_public_release_tags,
    get_latest_public_release_tag,
    get_public_release_tag_before,
    is_ancestor,
    extract_asana_task_links,
    resolve_task_id,
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


# --- resolve_task_id ---


class TestResolveTaskId:
    def test_resolves_id_from_valid_url(self):
        link = AsanaTaskLink(url="https://app.asana.com/0/123/456", commit_hash="aaa")
        assert resolve_task_id(link) == "456"

    def test_resolves_id_from_new_style_url(self):
        url = "https://app.asana.com/1/137249556945/project/1209107918776641/task/1210066941136479?focus=true"
        link = AsanaTaskLink(url=url, commit_hash="bbb")
        assert resolve_task_id(link) == "1210066941136479"

    def test_returns_none_when_no_url(self):
        link = AsanaTaskLink(url=None, commit_hash="ccc")
        assert resolve_task_id(link) is None

    @patch("asana_release_utils.extract_task_id_from_url", side_effect=IndexError("boom"))
    def test_returns_none_on_malformed_url(self, _mock_extract):
        link = AsanaTaskLink(url="https://app.asana.com/", commit_hash="ddd")
        assert resolve_task_id(link) is None

    @patch("asana_release_utils.extract_task_id_from_url", return_value="")
    def test_returns_none_when_extracted_id_is_empty(self, _mock_extract):
        link = AsanaTaskLink(url="https://app.asana.com/0/0", commit_hash="eee")
        assert resolve_task_id(link) is None


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


# --- collect-lgc-asana-tasks main(): filtering tasks already in prior release ---


import importlib.util
import json
import os
import sys
from io import StringIO


def _load_collect_lgc_module():
    """Load collect-lgc-asana-tasks.py as a module (the hyphenated filename
    blocks a plain `import`)."""
    path = os.path.join(os.path.dirname(__file__), "collect-lgc-asana-tasks.py")
    spec = importlib.util.spec_from_file_location("collect_lgc_asana_tasks", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _run_main(module, argv, *, new_commits, prior_release_commits, prior_tag,
              start_is_ancestor=False):
    """Invoke main() with mocked git lookups and capture stdout."""

    def fake_commits_between(_repo, start, _end):
        # `start` disambiguates the two ranges: prior_tag..start_tag vs start_tag..end.
        return prior_release_commits if start == prior_tag else new_commits

    captured = StringIO()
    with patch.object(module, "get_latest_public_release_tag", return_value="5.283.1"), \
         patch.object(module, "get_public_release_tag_before", return_value=prior_tag), \
         patch.object(module, "is_ancestor", return_value=start_is_ancestor), \
         patch.object(module, "get_commits_between", side_effect=fake_commits_between), \
         patch.object(sys, "argv", argv), \
         patch.object(sys, "stdout", captured):
        rc = module.main()
    return rc, captured.getvalue()


class TestCollectLgcMainPriorReleaseFilter:
    BASE_ARGV = [
        "collect-lgc-asana-tasks.py",
        "--end-commit", "HEAD",
        "--android-repo-path", ".",
        "--trigger-phrase", "Task/Issue URL:",
    ]

    def test_filters_out_task_already_in_prior_release(self):
        module = _load_collect_lgc_module()
        new_commits = [
            _fake_commit("n1", "Task/Issue URL: https://app.asana.com/0/p/111"),
            _fake_commit("n2", "Task/Issue URL: https://app.asana.com/0/p/222"),
            _fake_commit("n3", "Task/Issue URL: https://app.asana.com/0/p/333"),
        ]
        prior_release_commits = [
            _fake_commit("p1", "Task/Issue URL: https://app.asana.com/0/p/222"),
        ]

        rc, stdout = _run_main(
            module, self.BASE_ARGV,
            new_commits=new_commits,
            prior_release_commits=prior_release_commits,
            prior_tag="5.283.0",
        )

        assert rc == 0
        assert json.loads(stdout.strip()) == ["111", "333"]

    def test_no_prior_tag_skips_filter(self):
        module = _load_collect_lgc_module()
        new_commits = [
            _fake_commit("n1", "Task/Issue URL: https://app.asana.com/0/p/111"),
            _fake_commit("n2", "Task/Issue URL: https://app.asana.com/0/p/222"),
        ]

        rc, stdout = _run_main(
            module, self.BASE_ARGV,
            new_commits=new_commits,
            prior_release_commits=[],
            prior_tag=None,
        )

        assert rc == 0
        assert json.loads(stdout.strip()) == ["111", "222"]

    def test_no_overlap_keeps_all_new_tasks(self):
        module = _load_collect_lgc_module()
        new_commits = [
            _fake_commit("n1", "Task/Issue URL: https://app.asana.com/0/p/111"),
            _fake_commit("n2", "Task/Issue URL: https://app.asana.com/0/p/222"),
        ]
        prior_release_commits = [
            _fake_commit("p1", "Task/Issue URL: https://app.asana.com/0/p/999"),
        ]

        rc, stdout = _run_main(
            module, self.BASE_ARGV,
            new_commits=new_commits,
            prior_release_commits=prior_release_commits,
            prior_tag="5.283.0",
        )

        assert rc == 0
        assert json.loads(stdout.strip()) == ["111", "222"]

    def test_start_tag_override_used_as_start(self):
        module = _load_collect_lgc_module()
        new_commits = [
            _fake_commit("n1", "Task/Issue URL: https://app.asana.com/0/p/111"),
        ]

        with patch.object(module, "get_latest_public_release_tag") as mock_latest, \
             patch.object(module, "get_public_release_tag_before", return_value=None), \
             patch.object(module, "is_ancestor", return_value=True), \
             patch.object(module, "get_commits_between", return_value=new_commits), \
             patch.object(sys, "argv", self.BASE_ARGV + ["--start-tag", "5.283.1"]), \
             patch.object(sys, "stdout", StringIO()):
            rc = module.main()

        assert rc == 0
        # When --start-tag is provided, the latest-tag lookup should be skipped.
        mock_latest.assert_not_called()

    def test_filter_skipped_when_start_tag_is_ancestor(self):
        """For a normal release, start_tag is an ancestor of end_commit, so
        `start_tag..end` already excludes prior-release commits — the explicit
        filter is unnecessary and should be skipped."""
        module = _load_collect_lgc_module()
        new_commits = [
            _fake_commit("n1", "Task/Issue URL: https://app.asana.com/0/p/111"),
            _fake_commit("n2", "Task/Issue URL: https://app.asana.com/0/p/222"),
        ]

        captured = StringIO()
        with patch.object(module, "get_latest_public_release_tag", return_value="5.283.0"), \
             patch.object(module, "get_public_release_tag_before") as mock_prior, \
             patch.object(module, "is_ancestor", return_value=True), \
             patch.object(module, "get_commits_between", return_value=new_commits), \
             patch.object(sys, "argv", self.BASE_ARGV), \
             patch.object(sys, "stdout", captured):
            rc = module.main()

        assert rc == 0
        assert json.loads(captured.getvalue().strip()) == ["111", "222"]
        # When start_tag IS an ancestor, we don't need to look up the prior tag.
        mock_prior.assert_not_called()


# --- is_ancestor ---


class TestIsAncestor:
    @patch("asana_release_utils.subprocess.run")
    def test_returns_true_when_exit_code_zero(self, mock_run):
        mock_run.return_value = MagicMock(returncode=0)
        assert is_ancestor(".", "5.283.0", "HEAD") is True

    @patch("asana_release_utils.subprocess.run")
    def test_returns_false_when_exit_code_nonzero(self, mock_run):
        mock_run.return_value = MagicMock(returncode=1)
        assert is_ancestor(".", "5.283.1", "HEAD") is False
