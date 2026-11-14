#!/usr/bin/env python3
"""
Posts the weekly API quality report to Asana, and one regressions task listing every new finding of the week.

A week creates at most two tasks, however many findings it has: the triage owner assigns the regressions or splits them
into their own tasks. A re-run of the workflow updates the tasks it created instead of adding more; see upsert_run_task.
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
from asana.rest import ApiException

from compare import (AWAITING_TRIAGE_CELL, MAX_DETAILED_FINDINGS, REGRESSIONS_TASK_MARKER, SHORT_FIXES, detail_md,
                     rule_label)

TASK_PREFIX = "[API quality]"
CONFIG_VARIABLES = {
    "project": "GH_API_QUALITY_PROJECT_ID",
    "triage": "GH_API_QUALITY_TRIAGE_SECTION_ID",
    "weekly_reports": "GH_API_QUALITY_WEEKLY_REPORTS_SECTION_ID",
    "github_run": "GH_API_QUALITY_GITHUB_RUN_FIELD_KEY_ID",
    "status": "GH_API_QUALITY_STATUS_FIELD_KEY_ID",
    "status_needs_explanation": "GH_API_QUALITY_STATUS_FIELD_NEEDS_EXPLANATION_VALUE_ID",
}
UNTRIAGED_AFTER_DAYS = 14
# Asana gives every column the same default width, which wraps these long cells; widths are in pixels.
COLUMN_WIDTHS = {"Metric": 280, "API": 220, "Rule": 280, "Details": 400, "Fix": 320}


def log(message):
    print(message, flush=True)


def inline(text):
    text = html.escape(text, quote=False)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", text)
    return re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r'<a href="\2">\1</a>', text)


def markdown_to_asana_html(markdown, tables=True):
    """
    Converts the report's small markdown subset into Asana rich text.

    Asana's documentation allows tables in project briefs only, but task descriptions accept them in practice. With
    `tables=False`, each table row after the header becomes a list item with its cells joined: the fallback if Asana
    starts rejecting them.
    """
    out, bullets, table_header, rows = [], [], None, []
    gap = False

    def flush_bullets():
        if bullets:
            out.append("<ul>" + "".join(f"<li>{b}</li>" for b in bullets) + "</ul>")
            bullets.clear()

    def td(column, content):
        width = COLUMN_WIDTHS.get(table_header[column]) if column < len(table_header) else None
        return f'<td data-cell-widths="{width}">{content}</td>' if width else f"<td>{content}</td>"

    def flush_table():
        if table_header is not None and tables:
            header = "<tr>" + "".join(td(i, f"<strong>{inline(h)}</strong>") for i, h in enumerate(table_header)) + "</tr>"
            body = "".join("<tr>" + "".join(td(i, inline(c)) for i, c in enumerate(row)) + "</tr>" for row in rows)
            out.append(f"<table>{header}{body}</table>")
        rows.clear()

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
            if tables:
                rows.append(cells)
                continue
            parts = [f"{inline(h)}: {inline(c)}" if h and i > 0 else inline(c) for i, (h, c) in enumerate(zip(table_header, cells)) if c]
            bullets.append(", ".join(parts))
            continue
        flush_table()
        table_header = None
        flush_bullets()
        if not line:
            gap = True
            continue
        heading = re.match(r"(#+)\s+(.*)", line)
        if heading:
            tag = "h1" if len(heading.group(1)) == 1 else "h2"
            out.append(f"<{tag}>{inline(heading.group(2))}</{tag}>")
        elif line.startswith("- "):
            bullets.append(inline(line[2:]))
        else:
            # Asana drops blank lines between blocks, so a paragraph after a table, list or paragraph gets an empty
            # line of its own; one straight after a heading doesn't need it.
            spacer = "\n" if gap and out and not out[-1].startswith("<h") else ""
            out.append(f"{spacer}{inline(line)}\n")
        gap = False
    flush_table()
    flush_bullets()
    return "<body>" + "".join(out) + "</body>"


def with_rich_text(call, markdown):
    """Calls `call(html_notes)` with tables, retrying with lists if Asana rejects the description."""
    try:
        return call(markdown_to_asana_html(markdown))
    except ApiException as e:
        if e.status != 400:
            raise
        log("Asana rejected the description; retrying with tables as lists")
        return call(markdown_to_asana_html(markdown, tables=False))


def regressions_notes(groups, date, report_url, rules_url):
    """Every new finding of the week with its line, details and fix, so the task can be triaged without the report."""
    findings = [(g, f) for g in groups for f in g["findings"]]
    rows = []
    for g, f in findings[:MAX_DETAILED_FINDINGS]:
        where = f"{os.path.basename(f['file'])}:{f['line']}" if f["file"] else "module"
        where = f"[{where}]({f['url']})" if f.get("url") else f"`{where}`"
        rows.append(f"| `{g['module']}` | {rule_label(g['rule'])} | {where} | `{f['declaration']}`: {detail_md(f['detail'])} "
                    f"| {SHORT_FIXES.get(g['rule'], '')} |")
    if len(findings) > MAX_DETAILED_FINDINGS:
        rows.append(f"| | | | …and {len(findings) - MAX_DETAILED_FINDINGS} more in the run's artifact | |")
    md = [f"Detected in the [{date} weekly report]({report_url}).", "",
          "| API | Rule | Line | Details | Fix |", "|---|---|---|---|---|"] + rows
    md += ["", "Please assign each finding to the author of the change, or split it into its own task, then mark this task "
           "complete."]
    if rules_url:
        md += ["", f"**Ref:** [API quality rules and scores]({rules_url})"]
    return "\n".join(md) + "\n"


def link_regressions_task(report, url):
    """Swaps the report's marker for a link to the regressions task, or drops it when there's no task."""
    link = f"Lines, details and fixes for every new finding: [regressions task]({url})" if url else ""
    return report.replace(REGRESSIONS_TASK_MARKER, link)


def load_config(env=os.environ):
    missing = [name for name in CONFIG_VARIABLES.values() if not env.get(name)]
    if missing:
        raise SystemExit(f"Set the Asana repository env variables first: {', '.join(missing)}")
    ids = {key: env[name] for key, name in CONFIG_VARIABLES.items()}
    return {
        "project": ids["project"],
        "sections": {key: ids[key] for key in ("triage", "weekly_reports")},
        "fields": {key: ids[key] for key in ("github_run", "status", "status_needs_explanation")},
    }


def status_of(task, config):
    for f in task.get("custom_fields") or []:
        if f.get("gid") == config["fields"]["status"]:
            return (f.get("enum_value") or {}).get("gid")
    return None


def count_untriaged(tasks_api, config, now):
    """
    Open tasks still at Needs explanation after the deadline, in whichever section they are. The triager sets that status
    on each finding split from a regressions task, so manually created tasks count as well.
    """
    cutoff = now - timedelta(days=UNTRIAGED_AFTER_DAYS)
    tasks = tasks_api.get_tasks_for_project(
        config["project"], {"completed_since": "now", "opt_fields": "created_at,custom_fields.gid,custom_fields.enum_value.gid"})
    return sum(1 for t in tasks if status_of(t, config) == config["fields"]["status_needs_explanation"]
               and datetime.fromisoformat(t["created_at"].replace("Z", "+00:00")) < cutoff)


def github_run(kind):
    """
    The tag stored on the task, and the run's link. The tag is the run ID plus the kind of task, because one run can post
    several kinds: a workflow calling the compare workflow while posting its own report shares one run ID with it.
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


def upsert_run_task(tasks_api, stories_api, project, section, name, notes_md, run_field, run_id, run_url, custom_fields=None):
    """
    Creates the task of one workflow run, tagged in the `run_field` text custom field with `run_id`, the tag from
    github_run().

    A re-run finds the task its earlier attempt created, refreshes it and leaves a comment,
    rather than creating a duplicate. A completed task is left alone and a new one is created. `custom_fields` only
    apply on creation, so a re-run never resets a status someone has already changed.
    """
    existing = task_for_run(tasks_api, project, run_field, run_id)
    if existing:
        with_rich_text(lambda notes: tasks_api.update_task({"data": {"name": name, "html_notes": notes}}, existing["gid"], {}), notes_md)
        stories_api.create_story_for_task(
            {"data": {"html_text": f"<body>Updated by a <a href=\"{run_url}\">re-run</a> of the workflow.</body>"}},
            existing["gid"], {})
        log(f"Updated task {existing['permalink_url']}")
        return existing
    membership = {"project": project}
    if section:
        membership["section"] = section
    task = with_rich_text(lambda notes: tasks_api.create_task(
        # `projects` sets the task's workspace; `memberships` alone may not, and only adds the section.
        {"data": {"name": name, "html_notes": notes, "projects": [project], "memberships": [membership],
                  "custom_fields": {run_field: run_id, **(custom_fields or {})}}},
        {"opt_fields": "permalink_url"},
    ), notes_md)
    log(f"Created task {task['permalink_url']}")
    return task


def asana_apis():
    configuration = asana.Configuration()
    configuration.access_token = os.environ["ASANA_ACCESS_TOKEN"]
    client = asana.ApiClient(configuration)
    return asana.TasksApi(client), asana.StoriesApi(client)


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

    untriaged = count_untriaged(tasks_api, config, datetime.now(timezone.utc))
    report = report.replace(AWAITING_TRIAGE_CELL, str(untriaged))
    report_task = upsert_run_task(tasks_api, stories_api, config["project"], config["sections"]["weekly_reports"],
                                  f"API Weekly Report, {args.date}", link_regressions_task(report, None),
                                  config["fields"]["github_run"], run_id, run_url)
    if not groups:
        return
    # The regressions task links to the report, which exists first; the report links to it once it exists.
    regressions_run_id, _ = github_run("weekly-regressions")
    regressions_task = upsert_run_task(
        tasks_api, stories_api, config["project"], config["sections"]["triage"], f"{TASK_PREFIX} Regressions, {args.date}",
        regressions_notes(groups, args.date, report_task["permalink_url"], os.environ.get("GH_API_QUALITY_RULES_URL")),
        config["fields"]["github_run"], regressions_run_id, run_url)
    with_rich_text(lambda notes: tasks_api.update_task({"data": {"html_notes": notes}}, report_task["gid"], {}),
                   link_regressions_task(report, regressions_task["permalink_url"]))


if __name__ == "__main__":
    main()
