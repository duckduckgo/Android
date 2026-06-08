#!/usr/bin/env python3
"""Tests for the AGENTS.md drift checker. stdlib unittest only."""
import json
import os
import tempfile
import unittest

import check


GUARD = {
    "pattern": r"(?i)\b(kotlin|gradle|ktlint|google java format|agp)\b\s*\(?\s*v?\d+\.\d+(?:\.\d+)?\)?",
    "allow": [],
}


class GuardTests(unittest.TestCase):
    def test_clean_trimmed_text_has_no_findings(self):
        text = (
            "**Versions** live in the build files: version.kotlin in versions.properties,\n"
            "the Gradle version in gradle/wrapper/gradle-wrapper.properties.\n"
            "- Spotless with ktlint for Kotlin\n"
            "- Google Java Format in AOSP style for Java\n"
            "- Max line length: 150 characters\n"
            "**Toolchain:** Kotlin JVM target 17; building requires JDK 21.\n"
        )
        self.assertEqual(check.run_guard(text, GUARD), [])

    def test_flags_reintroduced_kotlin_version(self):
        findings = check.run_guard("**Language:** Kotlin (1.9.24)\n", GUARD)
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0]["id"], "volatile-fact-guard")
        self.assertEqual(findings[0]["line"], 1)
        self.assertIn("1.9.24", findings[0]["match"])

    def test_flags_reintroduced_gradle_version(self):
        findings = check.run_guard("Build: Gradle 7.6, AGP via refreshVersions\n", GUARD)
        self.assertTrue(any("7.6" in f["match"] for f in findings))

    def test_single_integers_are_allowed(self):
        # "JDK 21" and "JVM target 17" must NOT be flagged (kept on purpose).
        findings = check.run_guard("Kotlin JVM target 17; building requires JDK 21.\n", GUARD)
        self.assertEqual(findings, [])

    def test_allowlist_suppresses_a_match(self):
        guard = dict(GUARD, allow=["ktlint (0.50.0)"])
        findings = check.run_guard("- Spotless with ktlint (0.50.0)\n", guard)
        self.assertEqual(findings, [])


class ClaimTests(unittest.TestCase):
    def test_source_signal_present_passes(self):
        claim = {
            "id": "di-framework",
            "type": "source_signal",
            "extract": "echo present",
            "expected": "present",
            "on_mismatch": "review the DI wording",
        }
        self.assertIsNone(check.run_claim(claim, "", "."))

    def test_source_signal_absent_flags_review(self):
        claim = {
            "id": "di-framework",
            "type": "source_signal",
            "extract": "echo absent",
            "expected": "present",
            "on_mismatch": "review the DI wording",
        }
        finding = check.run_claim(claim, "", ".")
        self.assertIsNotNone(finding)
        self.assertEqual(finding["status"], "review")
        self.assertEqual(finding["actual"], "absent")

    def test_equals_match_passes(self):
        claim = {
            "id": "example",
            "type": "equals",
            "doc_pattern": r"value is (\S+)",
            "extract": "echo 1.2.3",
        }
        self.assertIsNone(check.run_claim(claim, "the value is 1.2.3 here", "."))

    def test_equals_mismatch_flags_drift(self):
        claim = {
            "id": "example",
            "type": "equals",
            "doc_pattern": r"value is (\S+)",
            "extract": "echo 9.9.9",
            "on_mismatch": "stale",
        }
        finding = check.run_claim(claim, "the value is 1.2.3 here", ".")
        self.assertEqual(finding["status"], "drift")
        self.assertEqual(finding["doc_value"], "1.2.3")
        self.assertEqual(finding["actual"], "9.9.9")

    def test_unknown_type_is_error(self):
        finding = check.run_claim({"id": "x", "type": "bogus"}, "", ".")
        self.assertEqual(finding["status"], "error")


class AuditTests(unittest.TestCase):
    def test_audit_clean_then_dirty(self):
        with tempfile.TemporaryDirectory() as root:
            agents = os.path.join(root, "AGENTS.md")
            with open(agents, "w") as fh:
                fh.write("Versions live in the build files.\n")
            registry = {"agents_md": "AGENTS.md", "volatile_fact_guard": GUARD, "claims": []}
            self.assertEqual(check.audit(registry, root), [])

            with open(agents, "w") as fh:
                fh.write("Kotlin (1.9.24) and Gradle 7.6\n")
            findings = check.audit(registry, root)
            self.assertEqual(len(findings), 2)


if __name__ == "__main__":
    unittest.main()
