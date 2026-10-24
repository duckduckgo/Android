#!/usr/bin/env python3
"""Tests for compare.py."""

import csv
import json

from compare import diff_runs, load_run, main, regressed, regression_reasons, render_pr

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


def test_pr_comment_says_so_when_nothing_changed(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 8, 1, "", "", ""]], [])
    b, a = load_run(before), load_run(after)

    comment = render_pr(b, a, diff_runs(b, a))

    assert "doesn't change any API quality finding" in comment


def test_weekly_json_groups_new_findings_by_module_and_rule(tmp_path, monkeypatch):
    before = write_run(tmp_path / "before", [["a-api", 8, 2, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 4, "", "", "M3"]], [
        ["a-api", "M3", "A.kt", 1, "A", "no KDoc"],
        ["a-api", "M3", "B.kt", 1, "B", "no KDoc"],
    ])
    out_md, out_json = tmp_path / "report.md", tmp_path / "new.json"
    monkeypatch.setattr("sys.argv", ["compare.py", "--before", str(before), "--after", str(after), "--mode", "weekly",
                                     "--out-md", str(out_md), "--out-json", str(out_json)])

    main()

    groups = json.loads(out_json.read_text())["new"]
    assert [(g["module"], g["rule"], len(g["findings"]), g["score_effect"]) for g in groups] == [("a-api", "M3", 2, "8/8 → 7/8")]
    assert "### New findings" in out_md.read_text()


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
    assert "`b-api`: score 8/8 → 7/8" in reasons[0] and "moved in from `a-api`" in reasons[0]


def test_pr_comment_lists_every_new_finding_with_its_location(tmp_path):
    before = write_run(tmp_path / "before", [["a-api", 8, 1, "", "", ""]], [])
    after = write_run(tmp_path / "after", [["a-api", 7, 7, "", "", "M3"]],
                      [["a-api", "M3", f"F{i}.kt", i, f"F{i}", "no KDoc"] for i in range(6)])
    b, a = load_run(before), load_run(after)

    comment = render_pr(b, a, diff_runs(b, a))

    assert "and 1 more" in comment
    assert all(f"`F{i}.kt:{i}`" in comment for i in range(6))


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
