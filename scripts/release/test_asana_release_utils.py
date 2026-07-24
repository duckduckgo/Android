#!/usr/bin/env python3
"""Tests for asana_release_utils and lgc-asana-tasks, extract_asana_task_links and _build_flexible_prefix_pattern."""

from unittest.mock import MagicMock, patch
import pytest
from asana_release_utils import (
    get_public_release_tags,
    get_latest_public_release_tag,
    get_public_release_tag_before,
    get_latest_release_tag_in_line,
    get_commits_by_hashes,
    is_ancestor,
    extract_asana_task_links,
    resolve_task_id,
    _build_flexible_prefix_pattern,
    build_release_includes_html,
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


# --- get_latest_release_tag_in_line ---


class TestGetLatestReleaseTagInLine:
    @patch("asana_release_utils.subprocess.run")
    def test_returns_base_normal_when_no_prior_hotfix(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.283.0",
            "5.284.0",
        ])
        assert get_latest_release_tag_in_line(".", "5.284.1") == "5.284.0"

    @patch("asana_release_utils.subprocess.run")
    def test_returns_newest_prior_hotfix(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.284.0",
            "5.284.1",
            "5.284.2",
        ])
        assert get_latest_release_tag_in_line(".", "5.284.3") == "5.284.2"

    @patch("asana_release_utils.subprocess.run")
    def test_ignores_patch_greater_or_equal(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.284.0",
            "5.284.1",
            "5.284.5",
        ])
        # Only tags with patch < 2 count.
        assert get_latest_release_tag_in_line(".", "5.284.2") == "5.284.1"

    @patch("asana_release_utils.subprocess.run")
    def test_ignores_other_minor_lines(self, mock_run):
        mock_run.return_value = _mock_git_tags_result([
            "5.283.0",
            "5.283.9",
            "5.285.0",
        ])
        assert get_latest_release_tag_in_line(".", "5.284.1") is None

    @patch("asana_release_utils.subprocess.run")
    def test_returns_none_for_malformed_version(self, mock_run):
        mock_run.return_value = _mock_git_tags_result(["5.284.0"])
        assert get_latest_release_tag_in_line(".", "5.284.0-internal") is None


# --- get_commits_by_hashes ---


class TestGetCommitsByHashes:
    @patch("asana_release_utils.Repo")
    def test_resolves_each_hash(self, mock_repo_cls):
        repo = mock_repo_cls.return_value
        repo.commit.side_effect = lambda sha: _fake_commit(sha, f"msg {sha}")

        commits = get_commits_by_hashes(".", ["aaa", "bbb"])

        assert [c.hexsha for c in commits] == ["aaa", "bbb"]

    @patch("asana_release_utils.Repo")
    def test_skips_blank_hashes(self, mock_repo_cls):
        repo = mock_repo_cls.return_value
        repo.commit.side_effect = lambda sha: _fake_commit(sha, "msg")

        commits = get_commits_by_hashes(".", ["aaa", "", "  "])

        assert [c.hexsha for c in commits] == ["aaa"]

    @patch("asana_release_utils.Repo")
    def test_skips_unresolvable_hashes(self, mock_repo_cls):
        repo = mock_repo_cls.return_value

        def resolve(sha):
            if sha == "bad":
                raise ValueError("no such commit")
            return _fake_commit(sha, "msg")

        repo.commit.side_effect = resolve

        commits = get_commits_by_hashes(".", ["aaa", "bad", "ccc"])

        assert [c.hexsha for c in commits] == ["aaa", "ccc"]


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


# --- create-asana-hotfix-release.py pure helpers ---


def _load_hotfix_module():
    """Load create-asana-hotfix-release.py as a module (the hyphenated filename
    blocks a plain `import`)."""
    path = os.path.join(os.path.dirname(__file__), "create-asana-hotfix-release.py")
    spec = importlib.util.spec_from_file_location("create_asana_hotfix_release", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class TestParseCommitHashes:
    def test_space_separated(self):
        module = _load_hotfix_module()
        assert module.parse_commit_hashes("aaa bbb ccc") == ["aaa", "bbb", "ccc"]

    def test_comma_separated(self):
        module = _load_hotfix_module()
        assert module.parse_commit_hashes("aaa,bbb,ccc") == ["aaa", "bbb", "ccc"]

    def test_mixed_and_extra_whitespace(self):
        module = _load_hotfix_module()
        assert module.parse_commit_hashes("  aaa,  bbb   ccc, ") == ["aaa", "bbb", "ccc"]

    def test_empty(self):
        module = _load_hotfix_module()
        assert module.parse_commit_hashes("   ") == []


class TestBaseNormalRelease:
    def test_derives_base_normal(self):
        module = _load_hotfix_module()
        assert module.base_normal_release("5.284.1") == "5.284.0"

    def test_base_normal_of_zero_patch(self):
        module = _load_hotfix_module()
        assert module.base_normal_release("5.284.0") == "5.284.0"

    def test_returns_none_for_malformed(self):
        module = _load_hotfix_module()
        assert module.base_normal_release("5.284.1-internal") is None


class TestDedupeLinksByTaskId:
    def test_keeps_first_occurrence_and_drops_urlless(self):
        module = _load_hotfix_module()
        links = [
            AsanaTaskLink(url="https://app.asana.com/0/p/111", commit_hash="c1"),
            AsanaTaskLink(url="https://app.asana.com/0/p/222", commit_hash="c2"),
            AsanaTaskLink(url="https://app.asana.com/0/p/111", commit_hash="c3"),  # dup task 111
            AsanaTaskLink(url=None, commit_hash="c4"),  # no url
        ]
        result = module.dedupe_links_by_task_id(links)
        assert [l.commit_hash for l in result] == ["c1", "c2"]


class TestBuildReleaseIncludesHtml:
    def test_single_section_when_no_previous_links(self):
        links = [AsanaTaskLink(url="https://app.asana.com/1/x/task/111", commit_hash="c1")]
        html = build_release_includes_html(links)
        assert html == '<strong>This release includes:</strong><ul><li><a href="https://app.asana.com/1/x/task/111">111</a></li></ul>'
        assert "Includes from" not in html

    def test_two_sections_when_previous_links_given(self):
        cherry = [AsanaTaskLink(url="https://app.asana.com/1/x/task/111", commit_hash="c1")]
        prior = [AsanaTaskLink(url="https://app.asana.com/1/x/task/222", commit_hash="c2")]
        html = build_release_includes_html(cherry, prior, "Includes from 5.287.1:")
        assert html == (
            '<strong>This release includes:</strong><ul><li><a href="https://app.asana.com/1/x/task/111">111</a></li></ul>'
            '<strong>Includes from 5.287.1:</strong><ul><li><a href="https://app.asana.com/1/x/task/222">222</a></li></ul>'
        )

    def test_empty_previous_links_stays_single_section(self):
        cherry = [AsanaTaskLink(url="https://app.asana.com/1/x/task/111", commit_hash="c1")]
        html = build_release_includes_html(cherry, [], "Includes from 5.287.1:")
        assert "Includes from" not in html


class TestDryRun:
    def _run_main(self, module, argv):
        with patch.object(sys, "argv", argv):
            return module.main()

    def test_dry_run_makes_no_asana_calls(self, capsys):
        module = _load_hotfix_module()
        cherry = [AsanaTaskLink(url="https://app.asana.com/0/p/111", commit_hash="c1")]
        shipped = [AsanaTaskLink(url="https://app.asana.com/0/p/222", commit_hash="c2")]

        with patch.object(module, "collect_cherry_links", return_value=cherry), \
             patch.object(module, "collect_shipped_links", return_value=shipped), \
             patch.object(module, "asana") as mock_asana, \
             patch.object(module, "create_asana_release_task") as mock_create, \
             patch.object(module, "tag_tasks") as mock_tag, \
             patch.object(module, "remove_tasks_from_project") as mock_remove:
            rc = self._run_main(module, [
                "prog",
                "--tag", "5.287.2",
                "--commit-hashes", "abc123",
                "--trigger-phrase", "Task/Issue URL:",
                "--dry-run",
            ])

        assert rc == 0
        mock_asana.ApiClient.assert_not_called()
        mock_create.assert_not_called()
        mock_tag.assert_not_called()
        mock_remove.assert_not_called()
        # Dry-run prints nothing to stdout (that channel is reserved for the task URL).
        assert capsys.readouterr().out == ""

    def test_dry_run_does_not_require_asana_args(self, capsys):
        module = _load_hotfix_module()
        with patch.object(module, "collect_cherry_links", return_value=[]), \
             patch.object(module, "collect_shipped_links", return_value=[]), \
             patch.object(module, "asana"):
            rc = self._run_main(module, [
                "prog",
                "--tag", "5.287.2",
                "--commit-hashes", "abc123",
                "--trigger-phrase", "Task/Issue URL:",
                "--dry-run",
            ])
        assert rc == 0

    def test_real_run_errors_when_asana_args_missing(self, capsys):
        module = _load_hotfix_module()
        cherry = [AsanaTaskLink(url="https://app.asana.com/0/p/111", commit_hash="c1")]
        with patch.object(module, "collect_cherry_links", return_value=cherry), \
             patch.object(module, "collect_shipped_links", return_value=[]), \
             patch.object(module, "asana") as mock_asana, \
             patch.object(module, "create_asana_release_task") as mock_create:
            rc = self._run_main(module, [
                "prog",
                "--tag", "5.287.2",
                "--commit-hashes", "abc123",
                "--trigger-phrase", "Task/Issue URL:",
            ])
        assert rc == 1
        mock_asana.ApiClient.assert_not_called()
        mock_create.assert_not_called()
