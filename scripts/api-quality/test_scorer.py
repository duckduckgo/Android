#!/usr/bin/env python3
"""Tests for Scorer.java, run through score.sh on a one-module fixture. Needs Java 21; the first run fetches the jars."""

import csv
import os
import shutil
import subprocess
from pathlib import Path

import pytest

SCORE_SH = Path(__file__).parent / "score.sh"

pytestmark = pytest.mark.skipif(shutil.which("java") is None, reason="needs Java 21")


def score_run(tmp_path, source):
    module = tmp_path / "repo" / "foo" / "foo-api"
    (module / "src" / "main" / "java" / "x").mkdir(parents=True)
    (module / "build.gradle").touch()
    (module / "src" / "main" / "java" / "x" / "A.kt").write_text("package x\n\n" + source)
    out = tmp_path / "out"
    subprocess.run([str(SCORE_SH), str(out), "t"], env={**os.environ, "REPO_ROOT": str(tmp_path / "repo")}, check=True,
                   capture_output=True)
    return out


def score(tmp_path, source):
    with open(score_run(tmp_path, source) / "api-quality-findings-t.csv", newline="") as f:
        return {(row["rule"], row["declaration"], row["detail"]) for row in csv.DictReader(f)}


def test_public_property_behind_a_private_constructor_is_checked(tmp_path):
    findings = score(tmp_path, "/** Doc. */\nclass A private constructor(var items: MutableList<String>)\n")

    assert ("M8", "A.items", "public var") in findings
    assert ("M8", "A.items", "MutableList in MutableList<String>") in findings


def test_public_constructor_parameters_are_checked_whatever_they_are_stored_in(tmp_path):
    findings = score(tmp_path, (
        "/** Doc. */\n"
        "class A(plain: MutableList<String>, private val hidden: Any) {\n"
        "    /** Doc. */\n"
        "    constructor(pair: Pair<Int, Int>) : this(mutableListOf(), 1)\n"
        "}\n"
    ))

    assert findings == {
        ("M8", "A.constructor(MutableList<String>, Any)", "MutableList in MutableList<String>"),
        ("M7", "A.constructor(MutableList<String>, Any)", "Any in Any"),
        ("M7", "A.constructor(Pair<Int, Int>)", "Pair in Pair<Int, Int>"),
    }


def test_public_val_parameter_is_reported_once_as_its_property(tmp_path):
    findings = score(tmp_path, "/** Doc.\n * @property value the value\n */\nclass A(val value: Any)\n")

    assert findings == {("M7", "A.value", "Any in Any")}


def test_constructors_outside_callers_reach_are_skipped(tmp_path):
    findings = score(tmp_path, (
        "/** Doc. */\nclass A internal constructor(items: MutableList<String>)\n\n"
        "/** Doc. */\nenum class E(items: List<Any>) { ONE(emptyList()) }\n\n"
        "/** Doc. */\nsealed class S(items: MutableList<String>)\n"
    ))

    assert findings == set()


def test_extension_properties_are_named_with_their_receiver(tmp_path):
    findings = score(tmp_path, "val String.value: Int get() = 1\n\nval Int.value: Int get() = 2\n")

    assert {("M4", "String.value", "no KDoc"), ("M4", "Int.value", "no KDoc")} <= findings


def test_every_constructor_is_listed_whatever_its_visibility(tmp_path):
    out = score_run(tmp_path, (
        "/** Doc. */\nclass A internal constructor(any: Any) {\n"
        "    /** Doc. */\n    constructor(pair: Pair<Int, Int>) : this(1)\n}\n"
    ))

    with open(out / "api-quality-constructors-t.csv", newline="") as f:
        rows = {(row["public"], row["declaration"]) for row in csv.DictReader(f)}
    assert rows == {("false", "A.constructor(Any)"), ("true", "A.constructor(Pair<Int, Int>)")}


def test_multiline_types_give_one_single_line_finding(tmp_path):
    out = score_run(tmp_path, (
        "/** Doc. */\ninterface A {\n"
        "    /** Doc. */\n    fun f(items: List<\n        Any\n    >)\n}\n"
    ))

    with open(out / "api-quality-findings-t.csv", newline="") as f:
        rows = list(csv.DictReader(f))
    assert [(row["rule"], row["declaration"], row["detail"]) for row in rows] == [("M7", "A.f(List<Any>)", "Any in List<Any>")]
    assert len((out / "api-quality-findings-t.csv").read_text().splitlines()) == 2


def test_reformatting_a_type_keeps_the_same_finding(tmp_path):
    one_line = score(tmp_path / "one", "/** Doc. */\ninterface A {\n    /** Doc. */\n    fun f(m: Map<String, Any>)\n}\n")
    spread = score(tmp_path / "spread", "/** Doc. */\ninterface A {\n    /** Doc. */\n    fun f(m: Map<\n        String ,\n        Any\n    >)\n}\n")

    assert one_line == spread
