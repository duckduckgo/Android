#!/usr/bin/env python3
"""Tests for the AI-docs drift checker. stdlib unittest only."""
import os
import tempfile
import unittest

import check


PATTERN = r"(?i)\b(kotlin|gradle|ktlint|google java format|agp)\b\s*\(?\s*v?\d+\.\d+(?:\.\d+)?\)?"


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
        self.assertEqual(check.run_guard(text, PATTERN, []), [])

    def test_flags_reintroduced_kotlin_version(self):
        findings = check.run_guard("**Language:** Kotlin (1.9.24)\n", PATTERN, [])
        self.assertEqual(len(findings), 1)
        self.assertEqual(findings[0]["id"], "volatile-fact-guard")
        self.assertEqual(findings[0]["line"], 1)
        self.assertEqual(findings[0]["match"], "Kotlin (1.9.24)")

    def test_flags_reintroduced_gradle_version(self):
        findings = check.run_guard("Build: Gradle 7.6, AGP via refreshVersions\n", PATTERN, [])
        self.assertTrue(any("7.6" in f["match"] for f in findings))

    def test_single_integers_are_allowed(self):
        # "JDK 21" and "JVM target 17" must NOT be flagged (kept on purpose).
        findings = check.run_guard("Kotlin JVM target 17; building requires JDK 21.\n", PATTERN, [])
        self.assertEqual(findings, [])

    def test_allowlist_suppresses_a_match(self):
        findings = check.run_guard("- Spotless with ktlint (0.50.0)\n", PATTERN, ["ktlint (0.50.0)"])
        self.assertEqual(findings, [])

    def test_non_guarded_tool_names_are_not_flagged(self):
        # The example bump table in dependency-updates.mdc uses tools that are
        # NOT in the guard list (turbine, harmony) — they must not be flagged.
        text = "| `app.cash.turbine` | 1.1.0 | 1.2.1 |\n| `com.frybits.harmony` | 1.2.6 | 1.2.7 |\n"
        self.assertEqual(check.run_guard(text, PATTERN, []), [])


class ClaimTests(unittest.TestCase):
    def test_source_signal_present_passes(self):
        claim = {"id": "di", "type": "source_signal", "extract": "echo present", "expected": "present", "on_mismatch": "x"}
        self.assertIsNone(check.run_claim(claim, "", "."))

    def test_source_signal_absent_flags_review(self):
        claim = {"id": "di", "type": "source_signal", "extract": "echo absent", "expected": "present", "on_mismatch": "review"}
        finding = check.run_claim(claim, "", ".")
        self.assertEqual(finding["status"], "review")
        self.assertEqual(finding["actual"], "absent")

    def test_equals_match_passes(self):
        claim = {"id": "e", "type": "equals", "doc_pattern": r"value is (\S+)", "extract": "echo 1.2.3"}
        self.assertIsNone(check.run_claim(claim, "the value is 1.2.3 here", "."))

    def test_equals_mismatch_flags_drift(self):
        claim = {"id": "e", "type": "equals", "doc_pattern": r"value is (\S+)", "extract": "echo 9.9.9", "on_mismatch": "stale"}
        finding = check.run_claim(claim, "the value is 1.2.3 here", ".")
        self.assertEqual(finding["status"], "drift")
        self.assertEqual(finding["doc_value"], "1.2.3")
        self.assertEqual(finding["actual"], "9.9.9")

    def test_path_exists_passes_for_present_file(self):
        with tempfile.TemporaryDirectory() as root:
            os.makedirs(os.path.join(root, "src"))
            open(os.path.join(root, "src", "Foo.kt"), "w").close()
            claim = {"id": "p", "type": "path-exists", "target": "src/Foo.kt"}
            self.assertIsNone(check.run_claim(claim, "", root))

    def test_path_exists_flags_missing_file(self):
        with tempfile.TemporaryDirectory() as root:
            claim = {"id": "p", "type": "path-exists", "target": "src/Gone.kt", "on_missing": "moved"}
            finding = check.run_claim(claim, "", root)
            self.assertEqual(finding["status"], "drift")
            self.assertEqual(finding["target"], "src/Gone.kt")

    def test_unknown_type_is_error(self):
        finding = check.run_claim({"id": "x", "type": "bogus"}, "", ".")
        self.assertEqual(finding["status"], "error")


class AuditTests(unittest.TestCase):
    def _registry(self, docs):
        return {"guard_pattern": PATTERN, "docs": docs}

    def test_audit_tags_findings_with_file(self):
        with tempfile.TemporaryDirectory() as root:
            with open(os.path.join(root, "DOC.md"), "w") as fh:
                fh.write("Kotlin (1.9.24)\n")
            registry = self._registry([{"path": "DOC.md", "guard": True, "guard_allow": [], "claims": []}])
            findings = check.audit(registry, root)
            self.assertEqual(len(findings), 1)
            self.assertEqual(findings[0]["file"], "DOC.md")

    def test_audit_runs_multiple_docs(self):
        with tempfile.TemporaryDirectory() as root:
            with open(os.path.join(root, "A.md"), "w") as fh:
                fh.write("Versions live in the build files.\n")
            with open(os.path.join(root, "B.md"), "w") as fh:
                fh.write("Gradle 7.6\n")
            registry = self._registry([
                {"path": "A.md", "guard": True, "guard_allow": [], "claims": []},
                {"path": "B.md", "guard": True, "guard_allow": [], "claims": []},
            ])
            findings = check.audit(registry, root)
            self.assertEqual([f["file"] for f in findings], ["B.md"])

    def test_audit_path_exists_claim_on_doc(self):
        with tempfile.TemporaryDirectory() as root:
            with open(os.path.join(root, "C.md"), "w") as fh:
                fh.write("see Ref.kt\n")
            registry = self._registry([{
                "path": "C.md", "guard": False, "guard_allow": [],
                "claims": [{"id": "ref", "type": "path-exists", "target": "Ref.kt", "on_missing": "gone"}],
            }])
            findings = check.audit(registry, root)
            self.assertEqual(findings[0]["id"], "ref")
            self.assertEqual(findings[0]["file"], "C.md")


if __name__ == "__main__":
    unittest.main()
