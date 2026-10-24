#!/usr/bin/env python3
"""
Compares two scorer runs and reports what changed, declaration by declaration.

Used by the weekly report (this week's develop against last week's) and the PR check (PR head against its base).
Both runs must come from the same Scorer.java, so every difference is a code change rather than a method change.

Usage:
  compare.py --before DIR --after DIR --mode weekly|pr --out-md FILE [--out-json FILE] [--title TEXT]
      [--weekly-report [--baseline DIR]] [--rules-url URL] [--link-base URL]

The JSON has `changed` (any finding or score differs), `regressed` (a new finding or a lower module score), the after-run
fleet numbers, and new findings grouped by module and rule for asana_sync.py.
"""

import argparse
import csv
import glob
import json
import os
from collections import defaultdict
from dataclasses import dataclass, field

FLOOR_EXCLUDED_MODULE = "browser-api"
MAX_DETAILED_FINDINGS = 200
PR_COMMENT_MARKER = "<!-- api-quality-check -->"
# Filled in by asana_sync.py, which can count the Asana tasks; a dash where nothing posts.
AWAITING_TRIAGE_CELL = "— <!-- awaiting-triage -->"
# O1's proposed goals for 2027-06-30, from the objective; shown next to the weekly numbers.
GOALS = {"weighted": "75%", "unweighted": "90%", "floor": "5/8", "awaiting_triage": "0"}


@dataclass(frozen=True)
class Finding:
    module: str
    rule: str
    file: str
    line: str
    declaration: str
    detail: str

    def key(self):
        # M6 detail carries the declaration count, which changes whenever the module grows; the finding itself doesn't.
        # The file stands in for the package: declarations are simple names, so two packages can both have an `A.f()`.
        return (self.module, self.rule, self.file, self.declaration, "" if self.rule == "M6" else self.detail)

    def location(self):
        return (self.module, self.file, self.declaration)

    def moved_key(self):
        return (self.rule, self.declaration, "" if self.rule == "M6" else self.detail)


@dataclass
class ModuleScore:
    module: str
    score: int
    declarations: int
    failing: set = field(default_factory=set)


@dataclass
class Run:
    scores: dict
    findings: list
    # Every (module, file, declaration) that exists in the run, with or without a finding.
    locations: set = field(default_factory=set)

    def fleet(self):
        modules = list(self.scores.values())
        total_decls = sum(m.declarations for m in modules)
        floor_candidates = [m for m in modules if m.module != FLOOR_EXCLUDED_MODULE]
        floor = min((m.score for m in floor_candidates), default=0)
        return {
            "modules": len(modules),
            "points": sum(m.score for m in modules),
            "max_points": len(modules) * 8,
            "weighted": sum(m.score / 8 * m.declarations for m in modules) / total_decls if total_decls else 0.0,
            "unweighted": sum(m.score / 8 for m in modules) / len(modules) if modules else 0.0,
            "floor": floor,
            "floor_modules": sorted(m.module for m in floor_candidates if m.score == floor),
            "declarations": total_decls,
        }


def _single(directory, pattern):
    matches = sorted(glob.glob(os.path.join(directory, pattern)))
    if len(matches) != 1:
        raise SystemExit(f"Expected exactly one {pattern} in {directory}, found {len(matches)}")
    return matches[0]


def load_run(directory):
    scores = {}
    with open(_single(directory, "api-quality-scores-*.csv"), newline="") as f:
        for row in csv.DictReader(f):
            scores[row["module"]] = ModuleScore(
                module=row["module"],
                score=int(row["score"]),
                declarations=int(row["declarations"]),
                failing=set(row["failing"].split()) if row["failing"] else set(),
            )
    with open(_single(directory, "api-quality-findings-*.csv"), newline="") as f:
        findings = [Finding(**row) for row in csv.DictReader(f)]
    locations = {f.location() for f in findings}
    # Constructors aren't counted as declarations, so they have their own inventory, private ones included.
    for pattern in ("api-quality-declarations-*.csv", "api-quality-constructors-*.csv"):
        for path in glob.glob(os.path.join(directory, pattern)):
            with open(path, newline="") as f:
                locations |= {(row["module"], row["file"], row["declaration"]) for row in csv.DictReader(f)}
    return Run(scores=scores, findings=findings, locations=locations)


@dataclass
class Diff:
    new: list
    resolved: list
    moved: list
    score_changes: list


def by_key(findings):
    keyed = defaultdict(list)
    for f in findings:
        keyed[f.key()].append(f)
    return keyed


def relocated(source: Finding, target: Finding, before: Run, after: Run):
    """The declaration left the source's file and wasn't in the target's file before: it moved."""
    return source.location() not in after.locations and target.location() not in before.locations


def diff_runs(before: Run, after: Run) -> Diff:
    before_keys, after_keys = by_key(before.findings), by_key(after.findings)
    new = [f for k, items in after_keys.items() for f in items[len(before_keys.get(k, [])):]]
    resolved = [f for k, items in before_keys.items() for f in items[len(after_keys.get(k, [])):]]

    # A finding that changed file with its declaration was renamed within its module, or moved to another one: neither
    # fixed nor created. A rename isn't reported; a move is, since it changes which module carries the debt.
    resolved_by_moved_key = defaultdict(list)
    for f in resolved:
        resolved_by_moved_key[f.moved_key()].append(f)
    moved = []
    renamed_sources = set()
    genuinely_new = []
    for f in new:
        candidates = [r for r in resolved_by_moved_key.get(f.moved_key(), []) if relocated(r, f, before, after)]
        if candidates:
            source = candidates[0]
            resolved_by_moved_key[f.moved_key()].remove(source)
            if source.module == f.module:
                renamed_sources.add(id(source))
            else:
                moved.append((source, f))
        else:
            genuinely_new.append(f)
    paired_sources = renamed_sources | {id(source) for source, _ in moved}
    genuinely_resolved = [f for f in resolved if id(f) not in paired_sources]

    score_changes = []
    for module in sorted(set(before.scores) | set(after.scores)):
        b, a = before.scores.get(module), after.scores.get(module)
        if b is None or a is None or b.score != a.score:
            score_changes.append((module, b, a))

    order = lambda f: (f.module, f.rule, f.file, f.declaration)
    return Diff(
        new=sorted(genuinely_new, key=order),
        resolved=sorted(genuinely_resolved, key=order),
        moved=sorted(moved, key=lambda pair: order(pair[1])),
        score_changes=score_changes,
    )


RULES = {
    "M1": ("No implementation-detail types",
           "A public type is named `*Repository`, `*Dao`, `*Entity`, `*ViewModel` or `*Impl`. The name describes how something "
           "is stored today, not what a caller can do.",
           "Expose a capability interface named for what callers need."),
    "M2": ("No `Toggle` in the public surface",
           "A public declaration exposes `Toggle`. Every consumer can then read raw flag state, and the flag can't be removed "
           "without a breaking change.",
           "Move the `@ContributesRemoteFeature` interface to `-impl`, and expose the decision, or nothing, from `-api`."),
    "M3": ("Top-level declarations documented",
           "A public type or top-level function has no KDoc, so consumers have to read `-impl` to learn what it's for.",
           "Add a sentence or two of KDoc: what it's for and who should use it."),
    "M4": ("Members documented",
           "A public function or property has no KDoc; its signature alone doesn't say how to use it.",
           "Add KDoc saying what the member does."),
    "M5": ("No nullable factory returns",
           "A public function returns `Intent?`, `Fragment?`, `View?` or `Bundle?`, which hands a failure the API already "
           "detected to the caller without saying why.",
           "Take every argument needed so it can't fail, or return a sealed result type."),
    "M6": ("Within the size ceiling",
           "The module has more than 80 public declarations: it covers several responsibilities, and every consumer depends "
           "on more than it uses.",
           "Split it by who calls it, grouped by shared callers."),
    "M7": ("No untyped or positional types",
           "`Any`, `Pair` or `Triple` is in a public signature. `Any` turns type errors into runtime crashes; `Pair` and "
           "`Triple` drop the names of what's returned.",
           "Name the thing: a data class or a sealed hierarchy."),
    "M8": ("No exposed write access",
           "A public `var` or a `Mutable*` type lets any consumer change state its owning module is responsible for.",
           "Use a `val` plus a function named for the intent; narrow `MutableStateFlow` to `StateFlow` at the boundary."),
}


# One line each, for the PR comment; the Asana finding tasks use the fuller RULES text.
SHORT_FIXES = {
    "M1": "Name it for what callers can do, or hide it behind a capability interface.",
    "M2": "Move the toggle interface to `-impl`; expose only the decision.",
    "M3": "Add a KDoc line saying what it's for.",
    "M4": "Add a KDoc line saying what it does.",
    "M5": "Make it unable to fail, or return a sealed result.",
    "M6": "Split the module by who calls it.",
    "M7": "Use a named data class or sealed type.",
    "M8": "Expose a `val` or a read-only type; add a function for writes.",
}


def detail_md(detail):
    """`Any in Pair<Int, Any>?` keeps its type in code: GitHub would read `<Int, Any>` in plain text as an HTML tag."""
    what, sep, type_text = detail.partition(" in ")
    return f"{what} in `{type_text}`" if sep else detail


def rule_label(rule):
    return f"{rule}: {RULES[rule][0]}" if rule in RULES else rule


def task_marker(module, rule):
    """Invisible in GitHub's markdown; asana_sync.py swaps it for a link to the group's Asana task."""
    return f"<!-- task:{module}:{rule} -->"


def location_url(f, link_base):
    return f"{link_base}/{f.file}#L{f.line}" if link_base and f.file else None


def location(f, link_base):
    if not f.file:
        return "—"
    text = f"{os.path.basename(f.file)}:{f.line}"
    url = location_url(f, link_base)
    return f"[{text}]({url})" if url else f"`{text}`"


def score_drops(diff: Diff):
    return [(module, b, a) for module, b, a in diff.score_changes if b and a and a.score < b.score]


def moved_in(diff: Diff):
    sources = defaultdict(set)
    for source, target in diff.moved:
        sources[target.module].add(source.module)
    return sources


def score_drop_text(module, b, a, diff: Diff):
    text = f"Score {b.score}/8 → {a.score}/8"
    sources = moved_in(diff)[module]
    if sources:
        text += (f", from findings moved in from {', '.join(f'`{m}`' for m in sorted(sources))}: possibly existing API "
                 "moving between modules, or two unrelated declarations sharing a name")
    return text


def score_drop_line(module, b, a, diff: Diff):
    return f"`{module}`: {score_drop_text(module, b, a, diff)}"


def regression_reasons(before: Run, after: Run, diff: Diff):
    """New finding groups, and score drops in modules without new findings: a moved finding isn't new, but a score it
    lowers still counts."""
    reasons = [f"`{module}` {rule_label(rule)}" for (module, rule), _ in group(diff.new)]
    modules_with_new = {f.module for f in diff.new}
    reasons += [score_drop_line(m, b, a, diff) for m, b, a in score_drops(diff) if m not in modules_with_new]
    return reasons


def regressed(before: Run, after: Run, diff: Diff):
    return bool(diff.new or score_drops(diff))


def group(findings):
    groups = defaultdict(list)
    for f in findings:
        groups[(f.module, f.rule)].append(f)
    return sorted(groups.items())


def pct(value):
    return f"{value * 100:.1f}%"


def score_change_row(module, b, a):
    return f"| `{module}` | {f'{b.score}/8' if b else 'new'} | {f'{a.score}/8' if a else 'removed'} |"


def rules_line(rules_url):
    return ["", f"For more details on fixing the issues, rules, and how scores are calculated: [API quality rules and scores]({rules_url})"] if rules_url else []


def summary_table(before: Run, after: Run, diff: Diff, baseline: Run = None, weekly_report=False):
    runs = ([baseline] if baseline else []) + [before, after]
    fleets = [r.fleet() for r in runs]
    if weekly_report:
        header = ["Metric"] + (["Baseline"] if baseline else []) + ["Last week", "This week", "Goal"]
    else:
        header = ["Metric", "Before", "After"]
    blanks = [""] * (len(runs) - 1)
    rows = [
        ["Score, weighted by declarations"] + [pct(f["weighted"]) for f in fleets] + [GOALS["weighted"]],
        ["Score, average per module"] + [pct(f["unweighted"]) for f in fleets] + [GOALS["unweighted"]],
        [f"Lowest module score, excluding `{FLOOR_EXCLUDED_MODULE}`"] + [f"{f['floor']}/8" for f in fleets] + [GOALS["floor"]],
        ["New findings"] + blanks + [str(len(diff.new)), ""],
        ["Resolved findings"] + blanks + [str(len(diff.resolved)), ""],
    ]
    if weekly_report:
        rows.append(["Finding tasks untriaged after 14 days"] + blanks + [AWAITING_TRIAGE_CELL, GOALS["awaiting_triage"]])
    else:
        rows = [row[:-1] for row in rows]
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    return lines + ["| " + " | ".join(row) + " |" for row in rows]


def regressions_table(diff: Diff, link_base=None, detailed=False):
    """The weekly report lists one row per API and rule, linked to its finding task; a comparison has no tasks, so it
    lists every finding with its line, like the PR comment."""
    modules_with_new = {f.module for f in diff.new}
    drops = [(m, score_drop_text(m, x, y, diff)) for m, x, y in score_drops(diff) if m not in modules_with_new]
    if detailed:
        rows = [f"| `{f.module}` | {location(f, link_base)} | `{f.declaration}`: {detail_md(f.detail)} | {SHORT_FIXES.get(f.rule, '')} |"
                for f in diff.new[:MAX_DETAILED_FINDINGS]]
        if len(diff.new) > MAX_DETAILED_FINDINGS:
            rows.append(f"| | | …and {len(diff.new) - MAX_DETAILED_FINDINGS} more in the run's artifact | |")
        rows += [f"| `{m}` | | {text} | |" for m, text in drops]
        return (["| API | Line | Details | Fix |", "|---|---|---|---|"] + rows) if rows else ["None."]
    rows = [f"| `{module}` | {rule_label(rule)} | {task_marker(module, rule)} |" for (module, rule), _ in group(diff.new)]
    rows += [f"| `{m}` | {text} | |" for m, text in drops]
    return (["| API | Rule | Task |", "|---|---|---|"] + rows) if rows else ["None."]


def findings_sections(diff: Diff, link_base=None, detailed=False):
    md = ["", "### Regressions", ""] + regressions_table(diff, link_base, detailed)
    if diff.resolved:
        md += ["", "### Resolved", "", "| API | Rules |", "|---|---|"]
        for module, items in sorted(group_by_module(diff.resolved).items()):
            md.append(f"| `{module}` | {', '.join(sorted({f.rule for f in items}))} |")
    if diff.moved:
        pairs = defaultdict(set)
        for source, target in diff.moved:
            pairs[(source.module, target.module)].add(target.rule)
        md += ["", "### Moved between modules", "", "| From | To | Rules |", "|---|---|---|"]
        md += [f"| `{s}` | `{t}` | {', '.join(sorted(rules))} |" for (s, t), rules in sorted(pairs.items())]
    if diff.score_changes:
        md += ["", "### Score changes", "", "| API | Before | After |", "|---|---|---|"]
        md += [score_change_row(m, x, y) for m, x, y in diff.score_changes]
    return md


def render_weekly(before: Run, after: Run, diff: Diff, title="O1: code quality", rules_url=None, baseline: Run = None,
                  weekly_report=False, link_base=None):
    """The comparison of two runs. As the weekly report, it opens with a summary against the baseline and the goals,
    and closes with the O2 section."""
    if weekly_report:
        md = ["## Summary", ""] + summary_table(before, after, diff, baseline, weekly_report=True)
        md += ["", "## O1: API code quality"] + findings_sections(diff)
        md += ["", "## O2: API review process", "", "[Under construction]"]
    else:
        md = [f"## {title}", ""] + summary_table(before, after, diff) + findings_sections(diff, link_base, detailed=True)
    md += rules_line(rules_url)
    return "\n".join(md) + "\n"


def group_by_module(findings):
    modules = defaultdict(list)
    for f in findings:
        modules[f.module].append(f)
    return modules


def render_pr(before: Run, after: Run, diff: Diff, rules_url=None, link_base=None):
    md = [PR_COMMENT_MARKER, "### API quality check", ""]
    if regressed(before, after, diff):
        md.append("❌ This PR adds API quality findings.")
    else:
        md.append("✅ No new API quality findings.")
    if diff.new:
        shown = diff.new[:MAX_DETAILED_FINDINGS]
        md += ["", "| API | Line | Details |", "|---|---|---|"]
        md += [f"| `{f.module}` | {location(f, link_base)} | `{f.declaration}`: {detail_md(f.detail)}<br>**Fix:** {SHORT_FIXES.get(f.rule, '')} |"
               for f in shown]
        if len(diff.new) > len(shown):
            md.append(f"\n…and {len(diff.new) - len(shown)} more in the run's artifact.")
    modules_with_new = {f.module for f in diff.new}
    drops = [score_drop_line(m, b, a, diff) for m, b, a in score_drops(diff) if m not in modules_with_new]
    if drops:
        md += [""] + [f"- {d}" for d in drops]
    if diff.resolved:
        md += ["", f"Resolved {len(diff.resolved)} finding{'' if len(diff.resolved) == 1 else 's'}, thank you!"]
    md += rules_line(rules_url)
    return "\n".join(md) + "\n"


def new_finding_groups(diff: Diff, link_base=None):
    groups = []
    for (module, rule), items in group(diff.new):
        groups.append({
            "module": module,
            "rule": rule,
            "findings": [{"file": f.file, "line": f.line, "declaration": f.declaration, "detail": f.detail,
                          "url": location_url(f, link_base)} for f in items],
        })
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--mode", choices=["weekly", "pr"], required=True)
    parser.add_argument("--out-md", required=True)
    parser.add_argument("--out-json")
    parser.add_argument("--rules-url", help="The rules and scores reference, linked at the end")
    parser.add_argument("--link-base", help="URL that file paths are appended to, e.g. https://github.com/o/r/blob/<sha>")
    parser.add_argument("--title", default="O1: code quality", help="Heading of the weekly report section")
    parser.add_argument("--weekly-report", action="store_true", help="Weekly mode: the full weekly report layout")
    parser.add_argument("--baseline", help="Weekly report: a scorer run for the summary's baseline column")
    args = parser.parse_args()

    before, after = load_run(args.before), load_run(args.after)
    diff = diff_runs(before, after)
    with open(args.out_md, "w") as f:
        if args.mode == "weekly":
            baseline = load_run(args.baseline) if args.baseline else None
            f.write(render_weekly(before, after, diff, args.title, args.rules_url, baseline, args.weekly_report, args.link_base))
        else:
            f.write(render_pr(before, after, diff, args.rules_url, args.link_base))
    if args.out_json:
        with open(args.out_json, "w") as f:
            changed = bool(diff.new or diff.resolved or diff.moved or diff.score_changes)
            json.dump({
                "changed": changed,
                "regressed": regressed(before, after, diff),
                "fleet": after.fleet(),
                "new": new_finding_groups(diff, args.link_base),
            }, f, indent=2)


if __name__ == "__main__":
    main()
