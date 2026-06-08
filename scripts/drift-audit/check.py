#!/usr/bin/env python3
"""Deterministic drift checker for AI-docs files.

Reads `scripts/drift-audit/registry.json` and, for each registered doc
(`AGENTS.md` and selected `.cursor/rules/*.mdc`), verifies:

  1. The doc has not re-introduced volatile version numbers (the inverse
     guard — see the spec). Trimmed docs point to the build files for
     versions; if a tool name reappears followed by a semver, that's drift.
  2. Each registered claim still agrees with the codebase (source signals,
     value equality, referenced file paths).

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


def run_guard(text, pattern, allow):
    """Flag a known tool name immediately followed by a semver-like version."""
    findings = []
    compiled = re.compile(pattern)
    allow = set(allow or [])
    for line_no, line in enumerate(text.splitlines(), start=1):
        for match in compiled.finditer(line):
            snippet = match.group(0)
            if snippet in allow:
                continue
            findings.append({
                "id": "volatile-fact-guard",
                "status": "drift",
                "line": line_no,
                "match": snippet,
                "message": "Restates a tool version number; remove it and point to the source of truth.",
            })
    return findings


def _shell(cmd, root):
    return subprocess.run(
        cmd, shell=True, cwd=root, capture_output=True, text=True
    ).stdout.strip()


def run_claim(claim, doc_text, root):
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
        match = re.search(claim["doc_pattern"], doc_text)
        doc_value = match.group(1) if match else None
        if doc_value != actual:
            return {
                "id": claim["id"],
                "status": "drift",
                "doc_value": doc_value,
                "actual": actual,
                "message": claim.get("on_mismatch", "Doc value disagrees with the codebase."),
            }
        return None
    if ctype == "path-exists":
        target = os.path.join(root, claim["target"])
        if not os.path.exists(target):
            return {
                "id": claim["id"],
                "status": "drift",
                "target": claim["target"],
                "message": claim.get("on_missing", "Referenced file no longer exists."),
            }
        return None
    return {
        "id": claim.get("id", "?"),
        "status": "error",
        "message": "unknown claim type: {!r}".format(ctype),
    }


def audit_doc(doc, registry, root):
    """Run the guard and claims for a single registered doc. Returns findings,
    each tagged with the doc's path."""
    findings = []
    doc_path = doc["path"]
    with open(os.path.join(root, doc_path)) as fh:
        text = fh.read()

    if doc.get("guard"):
        findings += run_guard(text, registry["guard_pattern"], doc.get("guard_allow", []))
    for claim in doc.get("claims", []):
        finding = run_claim(claim, text, root)
        if finding:
            findings.append(finding)

    for finding in findings:
        finding["file"] = doc_path
    return findings


def audit(registry, root):
    findings = []
    for doc in registry.get("docs", []):
        findings += audit_doc(doc, registry, root)
    return findings


def main(argv=None):
    parser = argparse.ArgumentParser(description="AI-docs drift checker")
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
