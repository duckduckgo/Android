#!/usr/bin/env python3
"""Deterministic drift checker for AGENTS.md.

Reads `.github/drift-audit/registry.json` and verifies two things:

  1. AGENTS.md has not re-introduced volatile version numbers (the inverse
     guard — see the spec). A trimmed AGENTS.md points to the build files for
     versions; if a tool name reappears followed by a semver, that's drift.
  2. Each registered claim still agrees with the codebase.

Prints a JSON report to stdout. Exit code 0 = clean, 1 = findings found.

stdlib only — no PyYAML/yq is available in CI, so the registry is JSON and the
checker shells out for source signals.
"""
import argparse
import json
import os
import re
import subprocess
import sys

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def load_registry(path):
    with open(path) as fh:
        return json.load(fh)


def run_guard(agents_text, guard):
    """Flag a known tool name immediately followed by a semver-like version."""
    findings = []
    if not guard:
        return findings
    pattern = re.compile(guard["pattern"])
    allow = set(guard.get("allow", []))
    for line_no, line in enumerate(agents_text.splitlines(), start=1):
        for match in pattern.finditer(line):
            snippet = match.group(0)
            if snippet in allow:
                continue
            findings.append({
                "id": "volatile-fact-guard",
                "status": "drift",
                "line": line_no,
                "match": snippet,
                "message": "AGENTS.md restates a tool version number; remove it and point to the source of truth.",
            })
    return findings


def _shell(cmd, root):
    return subprocess.run(
        cmd, shell=True, cwd=root, capture_output=True, text=True
    ).stdout.strip()


def run_claim(claim, agents_text, root):
    ctype = claim.get("type")
    if ctype == "source_signal":
        actual = _shell(claim["extract"], root)
        if actual != claim["expected"]:
            return {
                "id": claim["id"],
                "status": "review",
                "expected": claim["expected"],
                "actual": actual,
                "message": claim["on_mismatch"],
            }
        return None
    if ctype == "equals":
        actual = _shell(claim["extract"], root)
        match = re.search(claim["doc_pattern"], agents_text)
        doc_value = match.group(1) if match else None
        if doc_value != actual:
            return {
                "id": claim["id"],
                "status": "drift",
                "doc_value": doc_value,
                "actual": actual,
                "message": claim.get("on_mismatch", "AGENTS.md value disagrees with the codebase."),
            }
        return None
    return {
        "id": claim.get("id", "?"),
        "status": "error",
        "message": "unknown claim type: {!r}".format(ctype),
    }


def audit(registry, root):
    agents_path = os.path.join(root, registry["agents_md"])
    with open(agents_path) as fh:
        agents_text = fh.read()
    findings = list(run_guard(agents_text, registry.get("volatile_fact_guard")))
    for claim in registry.get("claims", []):
        finding = run_claim(claim, agents_text, root)
        if finding:
            findings.append(finding)
    return findings


def main(argv=None):
    parser = argparse.ArgumentParser(description="AGENTS.md drift checker")
    parser.add_argument(
        "--registry",
        default=os.path.join(os.path.dirname(__file__), "registry.json"),
    )
    parser.add_argument("--root", default=REPO_ROOT)
    args = parser.parse_args(argv)

    registry = load_registry(args.registry)
    findings = audit(registry, args.root)
    print(json.dumps({"clean": not findings, "findings": findings}, indent=2))
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
