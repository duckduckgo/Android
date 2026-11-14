#!/usr/bin/env python3
"""Tests for asana_sync.py."""

from datetime import datetime, timezone
from unittest.mock import MagicMock

import pytest
from asana.rest import ApiException

from asana_sync import (CONFIG_VARIABLES, count_untriaged, github_run, link_regressions_task, load_config,
                        markdown_to_asana_html, regressions_notes, upsert_run_task, with_rich_text)
from compare import REGRESSIONS_TASK_MARKER

GROUP = {
    "module": "a-api",
    "rule": "M4",
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


def test_untriaged_counts_old_open_tasks_still_needing_explanation_whatever_their_name():
    def task(created, status):
        return {"created_at": created, "custom_fields": [{"gid": "STATUS", "enum_value": {"gid": status} if status else None}]}

    tasks_api = MagicMock()
    tasks_api.get_tasks_for_project.return_value = [
        task("2026-09-01T00:00:00Z", "NEEDS"),
        task("2026-09-01T00:00:00Z", "FIX_PLANNED"),
        task("2026-09-01T00:00:00Z", None),
        task("2026-10-10T00:00:00Z", "NEEDS"),
    ]

    assert count_untriaged(tasks_api, CONFIG, datetime(2026, 10, 12, tzinfo=timezone.utc)) == 1

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


def test_a_paragraph_after_a_table_is_separated_by_an_empty_line():
    notes = markdown_to_asana_html("## T\n\nIntro\n\n| A |\n|---|\n| 1 |\n\nFor more details: x\n")

    assert "<h2>T</h2>Intro\n<table>" in notes
    assert "</table>\nFor more details: x\n" in notes


def test_regressions_task_lists_every_finding_with_its_line_details_and_fix():
    other = {"module": "b-api", "rule": "M7", "findings": [
        {"file": "B.kt", "line": "9", "declaration": "B.items", "detail": "MutableList in MutableList<String>", "url": "https://gh/B.kt#L9"}]}

    notes = regressions_notes([GROUP, other], "2026-10-05", "https://report", "https://rules")

    assert notes.startswith("Detected in the [2026-10-05 weekly report](https://report).")
    assert "| `a-api` | M4: Members documented | `A.kt:3` | `A.f(String)`: no KDoc | Add a KDoc line saying what it does. |" in notes
    assert "| `b-api` | M7: No exposed write access | [B.kt:9](https://gh/B.kt#L9) | `B.items`: MutableList in `MutableList<String>` |" in notes
    assert "split it into its own task" in notes
    assert notes.endswith("**Ref:** [API quality rules and scores](https://rules)\n")


def test_regressions_task_caps_the_listed_findings():
    many = {**GROUP, "findings": [{**GROUP["findings"][0], "declaration": f"A.f{i}()"} for i in range(250)]}

    notes = regressions_notes([many], "2026-10-05", "https://report", None)

    assert notes.count("| `a-api` |") == 200
    assert "…and 50 more in the run's artifact" in notes


def test_report_links_to_the_regressions_task_or_drops_the_marker():
    report = f"### Regressions\n\n{REGRESSIONS_TASK_MARKER}\n\n| API | Rule | Findings |\n"

    assert "[regressions task](https://task)" in link_regressions_task(report, "https://task")
    assert "<!--" not in link_regressions_task(report, None)


def test_weekly_run_posts_the_report_and_one_regressions_task_linked_both_ways(tmp_path, monkeypatch):
    import asana_sync

    for key, name in CONFIG_VARIABLES.items():
        monkeypatch.setenv(name, CONFIG["fields"].get(key) or CONFIG["sections"].get(key) or CONFIG[key])
    monkeypatch.setenv("GITHUB_RUN_ID", "123")
    monkeypatch.setenv("GITHUB_SERVER_URL", "https://github.com")
    monkeypatch.setenv("GITHUB_REPOSITORY", "o/r")
    report = tmp_path / "report.md"
    report.write_text(f"### Regressions\n\n{REGRESSIONS_TASK_MARKER}\n\n| API | Rule | Findings |\n|---|---|---|\n| `a-api` | M4 | 1 |\n")
    new = tmp_path / "new.json"
    new.write_text('{"new": [' + __import__("json").dumps(GROUP) + ', ' + __import__("json").dumps({**GROUP, "module": "b-api"}) + ']}')
    tasks_api, stories_api = MagicMock(), MagicMock()
    tasks_api.get_tasks_for_project.return_value = []
    tasks_api.create_task.side_effect = [{"gid": "1", "permalink_url": "https://report"}, {"gid": "2", "permalink_url": "https://regressions"}]
    monkeypatch.setattr(asana_sync, "asana_apis", lambda: (tasks_api, stories_api))
    monkeypatch.setattr("sys.argv", ["asana_sync.py", "--report-md", str(report), "--new-json", str(new), "--date", "2026-10-05"])

    asana_sync.main()

    report_data, regressions_data = (c[0][0]["data"] for c in tasks_api.create_task.call_args_list)
    assert tasks_api.create_task.call_count == 2
    assert report_data["memberships"] == [{"project": "P", "section": "W"}]
    assert regressions_data["name"] == "[API quality] Regressions, 2026-10-05"
    assert regressions_data["memberships"] == [{"project": "P", "section": "T"}]
    assert regressions_data["custom_fields"] == {"RUN": "123/weekly-regressions"}
    assert "https://report" in regressions_data["html_notes"] and "b-api" in regressions_data["html_notes"]
    assert 'href="https://regressions"' in tasks_api.update_task.call_args[0][0]["data"]["html_notes"]


def test_long_columns_get_wider_cells_and_others_keep_the_default():
    notes = markdown_to_asana_html("| API | Line | Details |\n|---|---|---|\n| `a-api` | A.kt:3 | no KDoc |\n")

    assert '<td data-cell-widths="220"><strong>API</strong></td><td><strong>Line</strong></td>' in notes
    assert '<td data-cell-widths="400">no KDoc</td>' in notes
