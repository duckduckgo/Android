#!/usr/bin/env python3
"""
Posts the weekly API quality report to Asana and files one backlog task per new finding group.

A group is one module and one rule, so a PR that leaves ten members undocumented opens one M4 task, not ten.
When an open task for the same module and rule already exists, the new declarations are added to it as a comment.
A re-run of the workflow updates its own report task instead of creating a second one; see upsert_run_task.
Every Asana ID comes from the GH_API_QUALITY_* environment variables, which the workflows fill from repository
variables of the same names.

Usage:
  asana_sync.py --report-md FILE --new-json FILE --date YYYY-MM-DD

Requires ASANA_ACCESS_TOKEN, and GITHUB_RUN_ID, GITHUB_SERVER_URL and GITHUB_REPOSITORY as set by GitHub Actions.
"""

import argparse
import html
import json
import os
import re
from datetime import datetime, timedelta, timezone

import asana

TASK_PREFIX = "[API quality]"
CONFIG_VARIABLES = {
    "project": "GH_API_QUALITY_PROJECT_ID",
    "triage": "GH_API_QUALITY_TRIAGE_SECTION_ID",
    "scorecards": "GH_API_QUALITY_SCORECARDS_SECTION_ID",
    "weekly_reports": "GH_API_QUALITY_WEEKLY_REPORTS_SECTION_ID",
    "github_run": "GH_API_QUALITY_GITHUB_RUN_FIELD_KEY_ID",
    "status": "GH_API_QUALITY_STATUS_FIELD_KEY_ID",
    "status_needs_explanation": "GH_API_QUALITY_STATUS_FIELD_NEEDS_EXPLANATION_VALUE_ID",
}
UNEXPLAINED_AFTER_DAYS = 14


def log(message):
    print(message, flush=True)


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)


def markdown_to_asana_html(markdown):
    """
    Converts the report's small markdown subset into Asana rich text.

    Asana rich text has no tables, so each table row after the header becomes a list item with its cells joined.
    """
    out, bullets, table_header = [], [], None

    def flush_bullets():
        if bullets:
            out.append("<ul>" + "".join(f"<li>{b}</li>" for b in bullets) + "</ul>")
            bullets.clear()

    for raw in markdown.splitlines():
        line = raw.strip()
        if line.startswith("<!--"):
            continue
        if line.startswith("|"):
            cells = [c.strip() for c in line.strip("|").split("|")]
            if all(re.fullmatch(r":?-+:?", c) for c in cells if c):
                continue
            if table_header is None:
                table_header = cells
                continue
            parts = [f"{inline(h)}: {inline(c)}" if h and i > 0 else inline(c) for i, (h, c) in enumerate(zip(table_header, cells)) if c]
            bullets.append(" · ".join(parts))
            continue
        table_header = None
        flush_bullets()
        if not line:
            continue
        heading = re.match(r"(#+)\s+(.*)", line)
        if heading:
            tag = "h1" if len(heading.group(1)) == 1 else "h2"
            out.append(f"<{tag}>{inline(heading.group(2))}</{tag}>")
        elif line.startswith("- "):
            bullets.append(inline(line[2:]))
        else:
            out.append(f"{inline(line)}\n")
    flush_bullets()
    return "<body>" + "".join(out) + "</body>"


def finding_task_name(group):
    return f"{TASK_PREFIX} {group['module']} {group['rule']}"


def finding_lines(group):
    return [f"<li><code>{html.escape(f['declaration'])}</code> ({html.escape(f['file'])}:{f['line']}) {html.escape(f['detail'])}</li>"
            for f in group["findings"]]


def open_tasks_by_name(tasks_api, project_id):
    tasks = tasks_api.get_tasks_for_project(project_id, {"completed_since": "now", "opt_fields": "name,permalink_url"})
    return {t["name"]: t for t in tasks}


def load_config(env=os.environ):
    missing = [name for name in CONFIG_VARIABLES.values() if not env.get(name)]
    if missing:
        raise SystemExit(f"Set the Asana repository env variables first: {', '.join(missing)}")
    ids = {key: env[name] for key, name in CONFIG_VARIABLES.items()}
    return {
        "project": ids["project"],
        "sections": {key: ids[key] for key in ("triage", "scorecards", "weekly_reports")},
        "fields": {key: ids[key] for key in ("github_run", "status", "status_needs_explanation")},
    }


def needs_explanation(config):
    return {config["fields"]["status"]: config["fields"]["status_needs_explanation"]}


def status_of(task, config):
    for f in task.get("custom_fields") or []:
        if f.get("gid") == config["fields"]["status"]:
            return (f.get("enum_value") or {}).get("gid")
    return None


def count_unexplained(tasks_api, config, now):
    """Open finding tasks still at the default status after the deadline, in whichever section they are."""
    cutoff = now - timedelta(days=UNEXPLAINED_AFTER_DAYS)
    tasks = tasks_api.get_tasks_for_project(
        config["project"], {"completed_since": "now", "opt_fields": "name,created_at,custom_fields.gid,custom_fields.enum_value.gid"})
    stale = 0
    for t in tasks:
        created = datetime.fromisoformat(t["created_at"].replace("Z", "+00:00"))
        if (t["name"].startswith(TASK_PREFIX) and created < cutoff
                and status_of(t, config) == config["fields"]["status_needs_explanation"]):
            stale += 1
    return stale


def github_run(kind):
    """
    The tag stored on the task, and the run's link. The tag is the run ID plus the kind of task, because one run can post
    several kinds: a workflow calling both the scorecard and the compare workflow shares one run ID between them.
    """
    run_id = os.environ.get("GITHUB_RUN_ID")
    if not run_id:
        raise SystemExit("GITHUB_RUN_ID is not set: this posts on behalf of a GitHub Actions run")
    return f"{run_id}/{kind}", f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{run_id}"


def task_for_run(tasks_api, project_id, run_field, run_id):
    # Only the custom fields are fetched: descriptions hold whole reports and aren't needed to match.
    tasks = tasks_api.get_tasks_for_project(
        project_id, {"completed_since": "now", "opt_fields": "permalink_url,custom_fields.gid,custom_fields.text_value"})
    for t in tasks:
        if any(f.get("gid") == run_field and f.get("text_value") == run_id for f in t.get("custom_fields") or []):
            return t
    return None


def upsert_run_task(tasks_api, stories_api, project, section, name, notes_html, run_field, run_id, run_url, custom_fields=None):
    """
    Creates the task of one workflow run, tagged in the `run_field` text custom field with `run_id`, the tag from
    github_run().

    A re-run finds the task its earlier attempt created, refreshes it and leaves a comment,
    rather than creating a duplicate. A completed task is left alone and a new one is created. `custom_fields` only
    apply on creation, so a re-run never resets a status someone has already changed.
    """
    existing = task_for_run(tasks_api, project, run_field, run_id)
    if existing:
        tasks_api.update_task({"data": {"name": name, "html_notes": notes_html}}, existing["gid"], {})
        stories_api.create_story_for_task(
            {"data": {"html_text": f"<body>Updated by a <a href=\"{run_url}\">re-run</a> of the workflow.</body>"}},
            existing["gid"], {})
        log(f"Updated task {existing['permalink_url']}")
        return existing
    membership = {"project": project}
    if section:
        membership["section"] = section
    task = tasks_api.create_task(
        # `projects` sets the task's workspace; `memberships` alone may not, and only adds the section.
        {"data": {"name": name, "html_notes": notes_html, "projects": [project], "memberships": [membership],
                  "custom_fields": {run_field: run_id, **(custom_fields or {})}}},
        {"opt_fields": "permalink_url"},
    )
    log(f"Created task {task['permalink_url']}")
    return task


def asana_apis():
    configuration = asana.Configuration()
    configuration.access_token = os.environ["ASANA_ACCESS_TOKEN"]
    client = asana.ApiClient(configuration)
    return asana.TasksApi(client), asana.StoriesApi(client)


def file_finding_groups(tasks_api, stories_api, config, date, groups, report_url):
    project = config["project"]
    existing = open_tasks_by_name(tasks_api, project)
    for group in groups:
        name = finding_task_name(group)
        items = "".join(finding_lines(group))
        if name in existing:
            stories_api.create_story_for_task(
                {"data": {"html_text": f"<body>New in the <a href=\"{report_url}\">{date} report</a>:<ul>{items}</ul></body>"}},
                existing[name]["gid"],
                {},
            )
            log(f"Commented on {existing[name]['permalink_url']}")
            continue
        notes = (
            f"<body>New finding in the <a href=\"{report_url}\">{date} weekly report</a>."
            f"\n<strong>Score effect:</strong> {html.escape(group['score_effect'])}"
            f"<ul>{items}</ul>"
            f"Explain it within {UNEXPLAINED_AFTER_DAYS} days by changing its status from Needs explanation: to fix, fix "
            "planned, false positive or exception accepted. The rule and its fix are in the API quality team guide.</body>"
        )
        task = tasks_api.create_task(
            {"data": {"name": name, "html_notes": notes, "projects": [project],
                      "memberships": [{"project": project, "section": config["sections"]["triage"]}],
                      "custom_fields": needs_explanation(config)}},
            {"opt_fields": "permalink_url"},
        )
        log(f"Created finding task {task['permalink_url']}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--report-md", required=True)
    parser.add_argument("--new-json", required=True)
    parser.add_argument("--date", required=True)
    args = parser.parse_args()
    config = load_config()
    run_id, run_url = github_run("weekly")
    tasks_api, stories_api = asana_apis()

    with open(args.report_md) as f:
        report = f.read()
    with open(args.new_json) as f:
        groups = json.load(f)["new"]

    unexplained = count_unexplained(tasks_api, config, datetime.now(timezone.utc))
    report += f"\n- Finding tasks unexplained for more than {UNEXPLAINED_AFTER_DAYS} days: **{unexplained}**\n"
    report_task = upsert_run_task(tasks_api, stories_api, config["project"], config["sections"]["weekly_reports"],
                                  f"API Weekly Report, {args.date}", markdown_to_asana_html(report),
                                  config["fields"]["github_run"], run_id, run_url)
    file_finding_groups(tasks_api, stories_api, config, args.date, groups, report_task["permalink_url"])


if __name__ == "__main__":
    main()
