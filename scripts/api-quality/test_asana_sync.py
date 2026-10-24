#!/usr/bin/env python3
"""Tests for asana_sync.py."""

from datetime import datetime, timezone
from unittest.mock import MagicMock

import pytest
from asana.rest import ApiException

from asana_sync import (CONFIG_VARIABLES, count_unexplained, file_finding_groups, finding_notes, github_run, link_tasks,
                        load_config, markdown_to_asana_html, upsert_run_task, with_rich_text)

GROUP = {
    "module": "a-api",
    "rule": "M4",
    "score_effect": "7/8 → 6/8",
    "findings": [{"file": "A.kt", "line": "3", "declaration": "A.f(String)", "detail": "no KDoc"}],
}
CONFIG = {
    "project": "P",
    "sections": {"triage": "T", "weekly_reports": "W"},
    "fields": {"github_run": "RUN", "status": "STATUS", "status_needs_explanation": "NEEDS"},
}


def test_tables_become_asana_tables_with_a_bold_header():
    notes = markdown_to_asana_html("## Scores\n\n| Module | Score |\n|---|---|\n| `a-api` | 7/8 |\n")

    assert notes == ("<body><h2>Scores</h2><table><tr><td><strong>Module</strong></td><td><strong>Score</strong></td></tr>"
                     "<tr><td><code>a-api</code></td><td>7/8</td></tr></table></body>")


def test_tables_can_fall_back_to_list_items_labelled_by_header():
    notes = markdown_to_asana_html("## Scores\n\n| Module | Score |\n|---|---|\n| `a-api` | 7/8 |\n", tables=False)

    assert notes == "<body><h2>Scores</h2><ul><li><code>a-api</code>, Score: 7/8</li></ul></body>"


def test_a_rejected_table_is_retried_as_a_list():
    call = MagicMock(side_effect=[ApiException(status=400), "ok"])

    assert with_rich_text(call, "| A | B |\n|---|---|\n| 1 | 2 |\n") == "ok"
    assert "<table>" in call.call_args_list[0][0][0] and "<ul>" in call.call_args_list[1][0][0]


def test_html_in_report_text_is_escaped():
    assert "&lt;T&gt;" in markdown_to_asana_html("List<T> is fine")


def test_new_group_creates_a_task_in_triage_needing_explanation():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = []
    tasks_api.create_task.return_value = {"permalink_url": "https://app.asana.com/x"}

    file_finding_groups(tasks_api, stories_api, CONFIG, "2026-10-05", [GROUP], "https://report")

    data = tasks_api.create_task.call_args[0][0]["data"]
    assert data["name"] == "[API quality] a-api: Members documented"
    assert data["projects"] == ["P"]
    assert data["memberships"] == [{"project": "P", "section": "T"}]
    assert data["custom_fields"] == {"STATUS": "NEEDS"}
    assert "A.f(String)" in data["html_notes"]
    stories_api.create_story_for_task.assert_not_called()


def test_existing_open_task_gets_a_comment_instead_of_a_duplicate():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = [{"name": "[API quality] a-api: Members documented", "gid": "42", "permalink_url": "u"}]

    file_finding_groups(tasks_api, stories_api, CONFIG, "2026-10-05", [GROUP], "https://report")

    tasks_api.create_task.assert_not_called()
    assert stories_api.create_story_for_task.call_args[0][1] == "42"


def test_first_run_creates_a_task_tagged_with_its_run_id():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = []
    tasks_api.create_task.return_value = {"permalink_url": "u"}

    upsert_run_task(tasks_api, stories_api, "P", "S", "Report", "<body/>", "F", "123", "https://run")

    data = tasks_api.create_task.call_args[0][0]["data"]
    assert data["custom_fields"] == {"F": "123"}
    assert data["projects"] == ["P"]
    assert data["memberships"] == [{"project": "P", "section": "S"}]
    stories_api.create_story_for_task.assert_not_called()


def test_rerun_updates_its_own_task_and_comments_instead_of_duplicating():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = [
        {"gid": "1", "permalink_url": "other", "custom_fields": [{"gid": "F", "text_value": "999"}]},
        {"gid": "2", "permalink_url": "mine", "custom_fields": [{"gid": "F", "text_value": "123"}]},
    ]

    task = upsert_run_task(tasks_api, stories_api, "P", None, "Report", "<body>new</body>", "F", "123", "https://run")

    assert task["gid"] == "2"
    tasks_api.create_task.assert_not_called()
    assert tasks_api.update_task.call_args[0][1] == "2"
    assert "re-run" in stories_api.create_story_for_task.call_args[0][0]["data"]["html_text"]


def test_custom_fields_apply_on_creation_only():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = []
    tasks_api.create_task.return_value = {"permalink_url": "u"}

    upsert_run_task(tasks_api, stories_api, "P", "T", "Regression", "<body/>", "RUN", "123", "https://run", {"STATUS": "NEEDS"})

    assert tasks_api.create_task.call_args[0][0]["data"]["custom_fields"] == {"RUN": "123", "STATUS": "NEEDS"}

    tasks_api.get_tasks_for_project.return_value = [{"gid": "2", "permalink_url": "u", "custom_fields": [{"gid": "RUN", "text_value": "123"}]}]
    upsert_run_task(tasks_api, stories_api, "P", "T", "Regression", "<body/>", "RUN", "123", "https://run", {"STATUS": "NEEDS"})

    assert "custom_fields" not in tasks_api.update_task.call_args[0][0]["data"]


def test_unexplained_counts_old_finding_tasks_still_needing_explanation_in_any_section():
    def task(name, created, status):
        return {"name": name, "created_at": created, "custom_fields": [{"gid": "STATUS", "enum_value": {"gid": status} if status else None}]}

    tasks_api = MagicMock()
    tasks_api.get_tasks_for_project.return_value = [
        task("[API quality] a-api: Members documented", "2026-09-01T00:00:00Z", "NEEDS"),
        task("[API quality] b-api M4", "2026-09-01T00:00:00Z", "FIX_PLANNED"),
        task("[API quality] c-api M4", "2026-10-10T00:00:00Z", "NEEDS"),
        task("Manual ticket", "2026-09-01T00:00:00Z", "NEEDS"),
    ]

    assert count_unexplained(tasks_api, CONFIG, datetime(2026, 10, 12, tzinfo=timezone.utc)) == 1


def test_config_is_read_from_the_repository_variables():
    env = {name: f"id-{key}" for key, name in CONFIG_VARIABLES.items()}

    config = load_config(env)

    assert config["project"] == "id-project"
    assert config["sections"]["triage"] == "id-triage"
    assert config["fields"]["status_needs_explanation"] == "id-status_needs_explanation"


def test_missing_variables_are_named():
    env = {name: "1" for name in CONFIG_VARIABLES.values() if name != "GH_API_QUALITY_TRIAGE_SECTION_ID"}

    with pytest.raises(SystemExit, match="GH_API_QUALITY_TRIAGE_SECTION_ID"):
        load_config(env)


def test_run_tag_keeps_task_kinds_of_one_run_apart(monkeypatch):
    monkeypatch.setenv("GITHUB_RUN_ID", "123")
    monkeypatch.setenv("GITHUB_SERVER_URL", "https://github.com")
    monkeypatch.setenv("GITHUB_REPOSITORY", "o/r")

    weekly, url = github_run("weekly")
    regression, _ = github_run("regression")

    assert (weekly, regression) == ("123/weekly", "123/regression")
    assert url == "https://github.com/o/r/actions/runs/123"


def test_a_job_does_not_update_another_jobs_task_from_the_same_run():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = [
        {"gid": "1", "permalink_url": "weekly", "custom_fields": [{"gid": "RUN", "text_value": "123/weekly"}]},
    ]
    tasks_api.create_task.return_value = {"permalink_url": "regression"}

    upsert_run_task(tasks_api, stories_api, "P", "T", "Regression", "<body/>", "RUN", "123/regression", "https://run")

    tasks_api.update_task.assert_not_called()
    assert tasks_api.create_task.call_args[0][0]["data"]["custom_fields"] == {"RUN": "123/regression"}


def test_report_links_each_regression_to_its_task():
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = [{"name": "[API quality] a-api: Members documented", "gid": "42", "permalink_url": "https://old"}]
    tasks_api.create_task.return_value = {"permalink_url": "https://new"}
    other = {**GROUP, "module": "b-api"}
    report = "| `a-api` | M4 | <!-- task:a-api:M4 --> |\n| `b-api` | M4 | <!-- task:b-api:M4 --> |\n"

    filed = file_finding_groups(tasks_api, stories_api, CONFIG, "2026-10-05", [GROUP, other], "https://report")
    linked = link_tasks(report, filed)

    assert "| `a-api` | M4 | [task](https://old) |" in linked
    assert "| `b-api` | M4 | [task](https://new) |" in linked
    assert "<!--" not in link_tasks(report, [])


def test_finding_task_says_what_broke_and_how_to_fix_it_without_score_or_deadline():
    notes = finding_notes({**GROUP, "findings": [{**GROUP["findings"][0], "url": "https://github.com/f#L3"}]},
                          "2026-10-05", "https://report", "https://rules")

    assert notes.startswith('<body>Detected in the <a href="https://report">2026-10-05 weekly report</a>.')
    assert notes.index("API:") < notes.index("Lines:") < notes.index("Details:") < notes.index("How to fix:")
    assert '<a href="https://github.com/f#L3">A.kt:3</a>' in notes
    assert "\n\nPlease update the API quality status field above and add a comment if needed." in notes
    assert notes.endswith('<strong>Ref:</strong> <a href="https://rules">API quality rules and scores</a></body>')
    assert "7/8" not in notes and "14 days" not in notes



def test_a_paragraph_after_a_table_is_separated_by_an_empty_line():
    notes = markdown_to_asana_html("## T\n\nIntro\n\n| A |\n|---|\n| 1 |\n\nFor more details: x\n")

    assert "<h2>T</h2>Intro\n<table>" in notes
    assert "</table>\nFor more details: x\n" in notes
