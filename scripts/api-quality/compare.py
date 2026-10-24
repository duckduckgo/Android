#!/usr/bin/env python3
"""
Compares two scorer runs and reports what changed, declaration by declaration.

Used by the weekly report (this week's develop against last week's) and the PR check (PR head against its base).
Both runs must come from the same Scorer.java, so every difference is a code change rather than a method change.

Usage:
  compare.py --before DIR --after DIR --mode weekly|pr --out-md FILE [--out-json FILE] [--title TEXT]

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
MAX_NAMES_PER_GROUP = 5
MAX_DETAILED_FINDINGS = 200
PR_COMMENT_MARKER = "<!-- api-quality-check -->"


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


def regression_reasons(before: Run, after: Run, diff: Diff):
    """New findings and lower module scores. A moved finding isn't new, but a score it lowers still counts."""
    reasons = [f"`{module}`: {len(items)} new {rule} finding(s)" for (module, rule), items in group(diff.new)]
    moved_in = defaultdict(set)
    for source, target in diff.moved:
        moved_in[target.module].add(source.module)
    for module, b, a in diff.score_changes:
        if not (b and a and a.score < b.score):
            continue
        reason = f"`{module}`: score {b.score}/8 → {a.score}/8"
        if moved_in[module]:
            sources = ", ".join(f"`{m}`" for m in sorted(moved_in[module]))
            reason += (f". Findings were moved in from {sources}: this may be existing API moving between modules rather "
                       "than new debt, or two unrelated declarations sharing a name")
        reasons.append(reason)
    return reasons


def regressed(before: Run, after: Run, diff: Diff):
    return bool(regression_reasons(before, after, diff))


def regressions_section(before: Run, after: Run, diff: Diff, heading):
    reasons = regression_reasons(before, after, diff)
    if not reasons:
        return []
    return ["", heading, ""] + [f"- {r}" for r in reasons]


def all_findings_details(findings):
    shown = findings[:MAX_DETAILED_FINDINGS]
    lines = ["", "<details><summary>All new findings, with file and line</summary>", ""]
    lines += [f"- `{f.module}` {f.rule} `{f.declaration}`: {f.detail} (`{f.file}:{f.line}`)" for f in shown]
    if len(findings) > len(shown):
        lines.append(f"- …and {len(findings) - len(shown)} more: see the run's artifact")
    return lines + ["", "</details>"]


def group(findings):
    groups = defaultdict(list)
    for f in findings:
        groups[(f.module, f.rule)].append(f)
    return sorted(groups.items())


def score_effect(module, rule, before: Run, after: Run):
    b, a = before.scores.get(module), after.scores.get(module)
    if a is None:
        return "Module removed"
    if b is None:
        return f"New module, {a.score}/8"
    if rule not in a.failing:
        return "None, module still passes"
    if rule in b.failing:
        return f"None, already failing {rule}"
    return f"{b.score}/8 → {a.score}/8"


def names(findings):
    shown = [f"`{f.declaration}`" for f in findings[:MAX_NAMES_PER_GROUP]]
    rest = len(findings) - len(shown)
    return ", ".join(shown) + (f" and {rest} more" if rest else "")


def pct(value):
    return f"{value * 100:.1f}%"


def arrow(before, after):
    if after > before:
        return " ▲"
    if after < before:
        return " ▼"
    return ""


def fleet_table(before: Run, after: Run, diff: Diff):
    b, a = before.fleet(), after.fleet()
    rows = [
        ("Weighted average (by declaration count)", pct(b["weighted"]), pct(a["weighted"]) + arrow(b["weighted"], a["weighted"])),
        ("Unweighted average", pct(b["unweighted"]), pct(a["unweighted"]) + arrow(b["unweighted"], a["unweighted"])),
        (f"Minimum, excluding `{FLOOR_EXCLUDED_MODULE}`", f"{b['floor']}/8", f"{a['floor']}/8" + arrow(b["floor"], a["floor"])),
        ("Points", f"{b['points']} / {b['max_points']}", f"{a['points']} / {a['max_points']}"),
        ("Public declarations", f"{b['declarations']:,}", f"{a['declarations']:,}"),
    ]
    lines = ["| | Before | After |", "|---|---|---|"]
    lines += [f"| {label} | {x} | **{y}** |" for label, x, y in rows]
    lines.append(f"| New / resolved / moved findings | | **{len(diff.new)} / {len(diff.resolved)} / {len(diff.moved)}** |")
    return lines


def findings_table(findings, before: Run, after: Run, with_effect=True):
    header = "| Module | Rule | Declarations | Score effect |" if with_effect else "| Module | Rule | Declarations |"
    lines = [header, "|---|---|---|---|" if with_effect else "|---|---|---|"]
    for (module, rule), items in group(findings):
        row = f"| `{module}` | {rule} | {names(items)} |"
        if with_effect:
            row += f" {score_effect(module, rule, before, after)} |"
        lines.append(row)
    return lines


def moved_table(moved, before: Run, after: Run):
    grouped = defaultdict(list)
    for source, target in moved:
        grouped[(source.module, target.module)].append(target)
    lines = ["| From | To | Findings | Effect on receiving module |", "|---|---|---|---|"]
    for (source, target), items in sorted(grouped.items()):
        rules = sorted({f.rule for f in items})
        b, a = before.scores.get(target), after.scores.get(target)
        newly_failing = [r for r in rules if a and r in a.failing and not (b and r in b.failing)]
        if newly_failing and b and a:
            effect = f"{b.score}/8 → {a.score}/8, now fails {', '.join(newly_failing)}"
        else:
            effect = "None"
        lines.append(f"| `{source}` | `{target}` | {len(items)} ({', '.join(rules)}) | {effect} |")
    return lines


def score_table(diff: Diff):
    lines = ["| Module | Before | After | Failing now |", "|---|---|---|---|"]
    for module, b, a in diff.score_changes:
        before = f"{b.score}/8" if b else "new"
        after = f"{a.score}/8" if a else "removed"
        failing = ", ".join(sorted(a.failing)) if a and a.failing else "none"
        lines.append(f"| `{module}` | {before} | {after} | {failing} |")
    return lines


def render_weekly(before: Run, after: Run, diff: Diff, title="O1: code quality"):
    md = [f"## {title}", ""]
    md += fleet_table(before, after, diff)
    md += regressions_section(before, after, diff, "### Regressions")
    md += ["", "### New findings", ""]
    md += findings_table(diff.new, before, after) if diff.new else ["None."]
    md += ["", "### Resolved findings", ""]
    md += findings_table(diff.resolved, before, after, with_effect=False) if diff.resolved else ["None."]
    if diff.moved:
        md += ["", "### Moved between modules", "", "Debt that changed owner without being created or fixed.", ""]
        md += moved_table(diff.moved, before, after)
    md += ["", "### Score movement", ""]
    md += score_table(diff) if diff.score_changes else ["No module changed score."]
    return "\n".join(md) + "\n"


def render_pr(before: Run, after: Run, diff: Diff, guide_url=None):
    md = [PR_COMMENT_MARKER, "### API quality check", ""]
    if not (diff.new or diff.resolved or diff.moved or diff.score_changes):
        md.append("This PR changes `-api` code but doesn't change any API quality finding.")
        return "\n".join(md) + "\n"
    md.append("Mechanical checks from the API quality scorecard, comparing this PR with its base. Non-blocking.")
    md += regressions_section(before, after, diff, "**Regressions**")
    if diff.score_changes:
        md += ["", "**Score changes**", ""] + score_table(diff)
    if diff.new:
        md += ["", "**New findings**", ""] + findings_table(diff.new, before, after) + all_findings_details(diff.new)
    if diff.resolved:
        md += ["", f"**Resolved:** {len(diff.resolved)} finding(s). Thank you!"]
    if diff.moved:
        md += ["", f"**Moved between modules:** {len(diff.moved)} finding(s), unchanged."]
    guide = f"[API quality team guide]({guide_url})" if guide_url else "API quality team guide"
    md += ["", f"What each rule means and how to fix it: {guide}."]
    return "\n".join(md) + "\n"


def new_finding_groups(before: Run, after: Run, diff: Diff):
    groups = []
    for (module, rule), items in group(diff.new):
        groups.append({
            "module": module,
            "rule": rule,
            "score_effect": score_effect(module, rule, before, after),
            "findings": [{"file": f.file, "line": f.line, "declaration": f.declaration, "detail": f.detail} for f in items],
        })
    return groups


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--before", required=True)
    parser.add_argument("--after", required=True)
    parser.add_argument("--mode", choices=["weekly", "pr"], required=True)
    parser.add_argument("--out-md", required=True)
    parser.add_argument("--out-json")
    parser.add_argument("--guide-url", help="Linked from the PR comment")
    parser.add_argument("--title", default="O1: code quality", help="Heading of the weekly report section")
    args = parser.parse_args()

    before, after = load_run(args.before), load_run(args.after)
    diff = diff_runs(before, after)
    with open(args.out_md, "w") as f:
        f.write(render_weekly(before, after, diff, args.title) if args.mode == "weekly" else render_pr(before, after, diff, args.guide_url))
    if args.out_json:
        with open(args.out_json, "w") as f:
            changed = bool(diff.new or diff.resolved or diff.moved or diff.score_changes)
            json.dump({
                "changed": changed,
                "regressed": regressed(before, after, diff),
                "fleet": after.fleet(),
                "new": new_finding_groups(before, after, diff),
            }, f, indent=2)


if __name__ == "__main__":
    main()
