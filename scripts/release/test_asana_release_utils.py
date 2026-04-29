#!/usr/bin/env python3
"""Tests for asana_release_utils and lgc-asana-tasks."""

from unittest.mock import MagicMock, patch
from asana_release_utils import (
    get_public_release_tags,
    get_latest_public_release_tag,
    get_public_release_tag_before,
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
