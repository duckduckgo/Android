#!/usr/bin/env python3
"""Tests for compare.py."""

import csv
import json

from compare import diff_runs, load_run, main, regressed, regression_reasons, render_pr, render_weekly

SCORES_HEADER = ["module", "score", "declarations", "top_kdoc", "member_kdoc", "failing"]
FINDINGS_HEADER = ["module", "rule", "file", "line", "declaration", "detail"]


def write_run(directory, scores, findings, declarations=(), constructors=()):
    directory.mkdir()
    with open(directory / "api-quality-constructors-2026-01-01.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["module", "public", "file", "line", "declaration"])
        w.writerows([module, "true", file, 1, declaration] for module, file, declaration in constructors)
    with open(directory / "api-quality-declarations-2026-01-01.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["module", "kind", "documented", "file", "line", "declaration"])
        w.writerows([module, "MEMBER_FUNCTION", "true", file, 1, declaration] for module, file, declaration in declarations)
    with open(directory / "api-quality-scores-2026-01-01.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(SCORES_HEADER)
        w.writerows(scores)
    with open(directory / "api-quality-findings-2026-01-01.csv", "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(FINDINGS_HEADER)
        w.writerows(findings)
    return directory


def test_new_and_resolved_findings_are_compared_by_declaration(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "100%", "50%", "M4"]],
                       [["a-api", "M4", "A.kt", 3, "A.old()", "no KDoc"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "100%", "50%", "M4"]],
                      [["a-api", "M4", "A.kt", 9, "A.new()", "no KDoc"]])

    diff = diff_runs(load_run(before), load_run(after))

    assert [f.declaration for f in diff.new] == ["A.new()"]
    assert [f.declaration for f in diff.resolved] == ["A.old()"]
    assert diff.score_changes == []


def test_line_changes_alone_are_not_findings(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "A.kt", 3, "A.f()", "no KDoc"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "A.kt", 40, "A.f()", "no KDoc"]])

    diff = diff_runs(load_run(before), load_run(after))

    assert diff.new == [] and diff.resolved == []


def test_declaration_moving_between_modules_is_reported_as_moved(tmp_path):
    before = write_run(tmp_path / "before",
                       [["a-api", 1, 100, "", "", "M1"], ["b-api", 7, 3, "", "", "M3"]],
                       [["a-api", "M1", "a/Entity.kt", 1, "Entity", "type name ends in Entity"]])
    after = write_run(tmp_path / "after",
                      [["a-api", 2, 99, "", "", ""], ["b-api", 6, 4, "", "", "M1 M3"]],
                      [["b-api", "M1", "b/Entity.kt", 1, "Entity", "type name ends in Entity"]])

    diff = diff_runs(load_run(before), load_run(after))

    assert diff.new == [] and diff.resolved == []
    assert [(s.module, t.module) for s, t in diff.moved] == [("a-api", "b-api")]


def test_size_finding_survives_a_change_in_declaration_count(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 7, 90, "", "", "M6"]],
                       [["a-api", "M6", "", 0, "a-api", "90 public declarations (ceiling 80)"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 95, "", "", "M6"]],
                      [["a-api", "M6", "", 0, "a-api", "95 public declarations (ceiling 80)"]])

    diff = diff_runs(load_run(before), load_run(after))

    assert diff.new == [] and diff.resolved == []


def test_pr_comment_says_so_when_nothing_was_added(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 8, 1, "", "", ""]], [])
    b, a = load_run(before), load_run(after)

    comment = render_pr(b, a, diff_runs(b, a))

    assert "✅ No new API quality findings." in comment


def test_weekly_json_groups_new_findings_by_module_and_rule(tmp_path, monkeypatch):
    before = write_run(tmp_path / "before", [["a-api", 8, 2, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 4, "", "", "M3"]], [
        ["a-api", "M3", "A.kt", 1, "A", "no KDoc"],
        ["a-api", "M3", "B.kt", 1, "B", "no KDoc"],
    ])
    out_md, out_json = tmp_path / "report.md", tmp_path / "new.json"
    monkeypatch.setattr("sys.argv", ["compare.py", "--before", str(before), "--after", str(after), "--mode", "weekly",
                                     "--out-md", str(out_md), "--out-json", str(out_json), "--link-base", "https://gh/blob/sha",
                                     "--weekly-report"])

    main()

    groups = json.loads(out_json.read_text())["new"]
    assert [(g["module"], g["rule"], len(g["findings"])) for g in groups] == [("a-api", "M3", 2)]
    assert groups[0]["findings"][0]["url"] == "https://gh/blob/sha/A.kt#L1"
    assert "| `a-api` | M3: Top-level declarations documented | <!-- task:a-api:M3 --> |" in out_md.read_text()


def test_new_finding_is_a_regression(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 2, "", "", "M3"]], [["a-api", "M3", "A.kt", 1, "A", "no KDoc"]])
    b, a = load_run(before), load_run(after)

    assert regressed(b, a, diff_runs(b, a))


def test_resolved_and_moved_findings_are_not_regressions_when_no_score_drops(tmp_path):
    before = write_run(tmp_path / "before",
                       [["a-api", 6, 100, "", "", "M1 M3"], ["b-api", 6, 3, "", "", "M1 M3"]],
                       [["a-api", "M1", "a/Entity.kt", 1, "Entity", "type name ends in Entity"],
                        ["a-api", "M3", "a/Old.kt", 1, "Old", "no KDoc"]])
    after = write_run(tmp_path / "after",
                      [["a-api", 8, 98, "", "", ""], ["b-api", 6, 4, "", "", "M1 M3"]],
                      [["b-api", "M1", "b/Entity.kt", 1, "Entity", "type name ends in Entity"]])
    b, a = load_run(before), load_run(after)

    assert not regressed(b, a, diff_runs(b, a))


def test_weekly_title_is_configurable(tmp_path, monkeypatch):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 8, 1, "", "", ""]], [])
    out_md = tmp_path / "report.md"
    monkeypatch.setattr("sys.argv", ["compare.py", "--before", str(before), "--after", str(after), "--mode", "weekly",
                                     "--out-md", str(out_md), "--title", "Since the baseline"])

    main()

    assert out_md.read_text().startswith("## Since the baseline")


def test_same_name_in_two_packages_of_a_module_is_still_new(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "a/one/A.kt", 3, "A.f()", "no KDoc"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 11, "", "", "M4"]], [
        ["a-api", "M4", "a/one/A.kt", 3, "A.f()", "no KDoc"],
        ["a-api", "M4", "a/two/A.kt", 3, "A.f()", "no KDoc"],
    ])

    diff = diff_runs(load_run(before), load_run(after))

    assert [f.file for f in diff.new] == ["a/two/A.kt"]


def test_score_drop_from_a_move_is_a_regression_that_names_the_move(tmp_path):
    before = write_run(tmp_path / "before",
                       [["a-api", 7, 100, "", "", "M1"], ["b-api", 8, 3, "", "", ""]],
                       [["a-api", "M1", "a/Entity.kt", 1, "Entity", "type name ends in Entity"]])
    after = write_run(tmp_path / "after",
                      [["a-api", 8, 99, "", "", ""], ["b-api", 7, 4, "", "", "M1"]],
                      [["b-api", "M1", "b/Entity.kt", 1, "Entity", "type name ends in Entity"]])
    b, a = load_run(before), load_run(after)

    reasons = regression_reasons(b, a, diff_runs(b, a))

    assert len(reasons) == 1
    assert "`b-api`: Score 8/8 → 7/8" in reasons[0] and "moved in from `a-api`" in reasons[0]


def test_pr_comment_lists_every_new_finding_with_a_link_to_its_line(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 7, "", "", "M3"]],
                      [["a-api", "M3", f"src/F{i}.kt", i, f"F{i}", "no KDoc"] for i in range(6)])
    b, a = load_run(before), load_run(after)

    comment = render_pr(b, a, diff_runs(b, a), rules_url="https://rules", link_base="https://gh/blob/sha")

    assert all(f"| `a-api` | [F{i}.kt:{i}](https://gh/blob/sha/src/F{i}.kt#L{i}) | `F{i}`: no KDoc<br>**Fix:** Add a KDoc line saying what it's for. |"
               in comment for i in range(6))
    assert "[API quality rules and scores](https://rules)" in comment


def test_weekly_report_lists_each_regression_once_and_resolved_by_module(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 2, "", "", ""], ["b-api", 7, 2, "", "", "M8"]],
                       [["b-api", "M8", "B.kt", 1, "B.x", "public var"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 4, "", "", "M3"], ["b-api", 8, 2, "", "", ""]], [
        ["a-api", "M3", "A.kt", 1, "A", "no KDoc"],
        ["a-api", "M3", "C.kt", 1, "C", "no KDoc"],
    ])
    b, a = load_run(before), load_run(after)

    report = render_weekly(b, a, diff_runs(b, a), weekly_report=True)

    assert report.count("| `a-api` | M3") == 1
    assert "| `b-api` | M8 |" in report
    assert "| `a-api` | 8/8 | 7/8 |" in report
    assert "score effect" not in report.lower()


def test_weekly_report_opens_with_a_summary_and_has_an_o2_placeholder(tmp_path):
    base = write_run(tmp_path / "base", [["a-api", 6, 2, "", "", "M3 M4"]], [])
    before = write_run(tmp_path / "before", [["a-api", 8, 2, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 4, "", "", "M3"]], [["a-api", "M3", "A.kt", 1, "A", "no KDoc"]])
    b, a = load_run(before), load_run(after)

    report = render_weekly(b, a, diff_runs(b, a), baseline=load_run(base), weekly_report=True)

    assert report.startswith("## Summary\n\n| Metric | Baseline | Last week | This week | Goal |")
    assert "| Score, weighted by declarations | 75.0% | 100.0% | 87.5% | 75% |" in report
    assert "| New findings |  |  | 1 |  |" in report
    assert "| Finding tasks untriaged after 14 days |  |  | — <!-- awaiting-triage --> | 0 |" in report
    assert report.index("## O1: API code quality") < report.index("## O2: API review process\n\n[Under construction]")


def test_moving_the_debt_to_another_copy_of_a_name_in_one_module_is_new(tmp_path):
    copies = [("a-api", "a/one/A.kt", "A.f()"), ("a-api", "a/two/A.kt", "A.f()")]
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "a/one/A.kt", 3, "A.f()", "no KDoc"]], copies)
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "a/two/A.kt", 3, "A.f()", "no KDoc"]], copies)
    b, a = load_run(before), load_run(after)

    diff = diff_runs(b, a)

    assert [f.file for f in diff.new] == ["a/two/A.kt"] and [f.file for f in diff.resolved] == ["a/one/A.kt"]
    assert regressed(b, a, diff)


def test_renamed_file_is_not_a_change(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "a/Old.kt", 3, "A.f()", "no KDoc"]])
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "", "", "M4"]], [["a-api", "M4", "a/New.kt", 3, "A.f()", "no KDoc"]])

    diff = diff_runs(load_run(before), load_run(after))

    assert diff.new == [] and diff.resolved == [] and diff.moved == []


def test_same_name_getting_worse_in_another_module_is_not_a_move(tmp_path):
    copies = [("a-api", "a/A.kt", "A.f()"), ("b-api", "b/A.kt", "A.f()")]
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M4"], ["b-api", 7, 10, "", "", "M4"]],
                       [["a-api", "M4", "a/A.kt", 3, "A.f()", "no KDoc"]], copies)
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "", "", "M4"], ["b-api", 7, 10, "", "", "M4"]],
                      [["b-api", "M4", "b/A.kt", 3, "A.f()", "no KDoc"]], copies)
    b, a = load_run(before), load_run(after)

    diff = diff_runs(b, a)

    assert diff.moved == [] and [f.module for f in diff.new] == ["b-api"]
    assert regressed(b, a, diff)


def test_hiding_one_constructor_and_exposing_a_same_named_one_is_new(tmp_path):
    constructors = [("a-api", "a/one/A.kt", "A.constructor(Any)"), ("a-api", "a/two/A.kt", "A.constructor(Any)")]
    before = write_run(tmp_path / "before", [["a-api", 7, 10, "", "", "M7"]],
                       [["a-api", "M7", "a/one/A.kt", 3, "A.constructor(Any)", "Any in Any"]], constructors=constructors)
    after = write_run(tmp_path / "after", [["a-api", 7, 10, "", "", "M7"]],
                      [["a-api", "M7", "a/two/A.kt", 3, "A.constructor(Any)", "Any in Any"]], constructors=constructors)
    b, a = load_run(before), load_run(after)

    diff = diff_runs(b, a)

    assert [f.file for f in diff.new] == ["a/two/A.kt"]
    assert regressed(b, a, diff)


def test_pr_comment_keeps_generic_types_in_code(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 1, "", "", "M8"]],
                      [["a-api", "M8", "A.kt", 1, "A.items", "MutableList in MutableList<String>"]])
    b, a = load_run(before), load_run(after)

    assert "`A.items`: MutableList in `MutableList<String>`" in render_pr(b, a, diff_runs(b, a))


def test_comparison_lists_each_regression_with_its_line_details_and_fix(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 2, "", "", "M8"]], [["a-api", "M8", "src/A.kt", 4, "A.x", "public var"]])
    b, a = load_run(before), load_run(after)

    report = render_weekly(b, a, diff_runs(b, a), "API quality: x compared with y", link_base="https://gh/blob/sha")

    assert "| API | Line | Details | Fix |" in report and "<!-- task:" not in report
    assert "| `a-api` | [A.kt:4](https://gh/blob/sha/src/A.kt#L4) | `A.x`: public var | Expose a `val`" in report
