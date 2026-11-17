#!/usr/bin/env python3
"""Runs a proof against the app on the selected device and gives a verdict.

  prove.py proofs/<name>.py
  prove.py compare proofs/<name>.py   runs it on the base build (verify.sh build --base <ref>), then on this
                                      checkout's build, and lines up the checks: before and after
  prove.py selftest            checks the harness itself (run it after changing prove.py); see tests/

A proof is a module with a docstring (the claim) and run(android). It acts through verify.sh and records
checks with android.check(). Exit 0 PASS (at least one check, all passed), 1 FAIL (a check failed),
2 ERROR (setup, a driving step or the installed build went wrong). An error is never a pass, and the run
stops at the first one instead of failing every check after it.

Evidence (video, screenshots, manifest.json, report.md) goes to verify.sh's evidence folder for the run.
"""
import datetime
import importlib.util
import json
import os
import re
import subprocess
import sys
import threading
import time
import traceback
from pathlib import Path

SKILL = Path(__file__).resolve().parent
REPO = Path(subprocess.run(["git", "-C", str(SKILL), "rev-parse", "--show-toplevel"],
                           capture_output=True, text=True, check=True).stdout.strip())
V = str(SKILL / "verify.sh")
STATE = REPO / "build" / "verify" / "state"
PIXEL_LINE = re.compile(r"Pixel (?:sent|enqueued): (?:\S+ )?(\S+) with params")


class Error(Exception):
    """Setup or driving problem: exit 2, never a pass."""


def verify(*args, check=True):
    p = subprocess.run([V, *map(str, args)], capture_output=True, text=True, cwd=REPO)
    if check and p.returncode != 0:
        raise Error(f"verify.sh {' '.join(map(str, args))} failed ({p.returncode}): {(p.stderr or p.stdout).strip()}")
    return p


class Android:
    def __init__(self, serial, evidence, prefix=""):
        # prefix keeps a base run's screenshots apart from the after run's in the same evidence folder
        self.serial, self.evidence, self.prefix = serial, evidence, prefix
        self.checks, self.notes, self.sections, self._mark = [], [], [], None

    # Driving: a step that fails raises Error, because nothing after it would mean anything.
    def tap(self, selector, index=None):
        verify("tap", selector, *(["--index", index] if index else []))

    def seed(self, *args):
        """Puts state the UI can't reach in place through trapdoor (verify.sh seeds lists them); returns its reply."""
        return verify("seed", *args).stdout.strip()

    def subscription(self, *args):
        """Seeds a subscription state (or `none`) and restarts the app; see features/subscription.md."""
        return verify("subscription", *args).stdout.strip()

    def type(self, selector, text):
        verify("type", selector, text)

    def key(self, name):
        verify("key", name)

    def scroll(self, direction, times=1):
        verify("scroll", direction, times)

    def open(self, url):
        verify("open", url)

    def launch(self):
        verify("launch")

    def reset(self):
        verify("reset")

    def shot(self, name):
        verify("shot", f"{self.prefix}{name}.png")

    # Reading: these return what they saw, so a proof can check it and report the actual value.
    def where(self):
        return verify("where", check=False).stdout.strip()

    def text(self, resource_id=None):
        p = verify("text", *([resource_id] if resource_id else []), check=False)
        return p.stdout.rstrip("\n") if p.returncode == 0 else None

    def lines(self):
        """Every labelled element as [id, text, flags], in layout order; [] when the layout is empty."""
        # A label with a line break spills onto lines without tabs; those continuation lines are skipped.
        rows = [line.split("\t") for line in (self.text() or "").splitlines() if "\t" in line]
        return [row + [""] * (3 - len(row)) for row in rows]

    def checked(self, resource_id, index=1):
        p = verify("checked", resource_id, index, check=False)
        return p.stdout.strip() == "true" if p.returncode == 0 else None

    def windows(self):
        """Text in popups and system windows that android layout doesn't list, as one string."""
        return verify("windows", check=False).stdout

    def ui(self):
        return verify("ui", check=False).stdout

    def web_targets(self):
        return verify("web", "targets", check=False).stdout

    def visible(self, selector):
        return verify("assert", "visible", selector, check=False).returncode == 0

    def wait(self, selector, timeout=10):
        return verify("wait", selector, timeout, check=False).returncode == 0

    def web_eval(self, js):
        p = verify("web", "eval", js, check=False)
        if p.returncode != 0:
            return None
        try:
            return json.loads(p.stdout)
        except ValueError:
            return p.stdout.strip()

    def poll(self, read, until, timeout=20, interval=1.0):
        """Reads until `until(value)` holds or the timeout passes; returns the last value read."""
        deadline = time.monotonic() + timeout
        while True:
            value = read()
            if until(value) or time.monotonic() >= deadline:
                return value
            time.sleep(interval)

    # Checks and independent reads.
    def check(self, what, ok, detail=""):
        self.checks.append({"check": what, "ok": bool(ok), "detail": str(detail)})
        print(f"{'PASS' if ok else 'FAIL'} {what}" + (f"  ({detail})" if detail and not ok else ""), flush=True)
        return bool(ok)

    def mark(self):
        """Logs and pixels count from here: call it right before the action under test."""
        self._mark = self._adb("shell", "date", "+%s.%N").strip()[:14]
        return self._mark

    def logs(self, since=None):
        since = since or self._mark
        if not since:
            raise Error("logs() needs a mark: call android.mark() before the action")
        return self._adb("logcat", "-d", "-T", since)

    def pixels(self, since=None):
        return PIXEL_LINE.findall(self.logs(since))

    def expect_pixel(self, name, since=None):
        fired = self.pixels(since)
        return self.check(f"pixel {name} fired", name in fired, "" if name in fired else f"fired: {sorted(set(fired))}")

    def expect_no_pixel(self, name, since=None):
        fired = self.pixels(since)
        return self.check(f"pixel {name} not fired", name not in fired, "fired" if name in fired else "")

    def note(self, text):
        self.notes.append(str(text))
        print(f"NOTE {text}", flush=True)

    def section(self, title, markdown):
        self.sections.append((title, markdown))

    def _adb(self, *args):
        # Logcat carries raw bytes from other apps; decode leniently so one bad byte isn't an error.
        p = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True, errors="replace")
        if p.returncode != 0:
            raise Error(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
        return p.stdout


class Recording:
    """Films the whole run: screenrecord stops at 170 s, so a new segment starts every SEGMENT seconds."""
    SEGMENT = 160

    def __init__(self, name):
        self.name, self.videos, self._done = name, [], threading.Event()
        self._start()
        self._thread = threading.Thread(target=self._rotate, daemon=True)
        self._thread.start()

    def _start(self):
        video = f"proof-{self.name}-{len(self.videos) + 1}.mp4"
        verify("record", "start", video)
        self.videos.append(video)

    def _rotate(self):
        while not self._done.wait(self.SEGMENT):
            verify("record", "stop", check=False)
            self._start()

    def stop(self):
        self._done.set()
        self._thread.join()
        verify("record", "stop", check=False)
        return self.videos


def load(path):
    spec = importlib.util.spec_from_file_location(Path(path).stem, path)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(Path(path).resolve().parent))
    spec.loader.exec_module(module)
    if not (module.__doc__ or "").strip() or not hasattr(module, "run"):
        raise Error(f"{path} needs a docstring stating its claim and a run(android) function")
    return module


def build_label(build):
    label = f"build {build.get('sha256', '?')[:16]} of {build.get('head', '?')[:10]}"
    if build.get("base"):
        return f"base {label} ({build['base']})"
    return label + (" + uncommitted changes" if build.get("dirty") else "")


def report(m):
    lines = [f"# {m['proof']['name']}", "", m["proof"]["doc"], "",
             f"**{m['verdict']}**" + (f": {m['error'].splitlines()[0]}" if m["error"] else ""), "",
             f"Device {m['device']}, {build_label(m['build'])}", "",
             "| Check | Result | Detail |", "|---|---|---|"]
    lines += [f"| {c['check']} | {'pass' if c['ok'] else 'FAIL'} | {c['detail']} |" for c in m["checks"]]
    if m["videos"]:
        lines += ["", "Video, in order: " + ", ".join(m["videos"])]
    if m["notes"]:
        lines += ["", "Notes:", *[f"- {n}" for n in m["notes"]]]
    for title, body in m["sections"]:
        lines += ["", f"## {title}", "", body]
    return "\n".join(lines) + "\n"


def prove(path):
    """Runs one proof; returns its manifest, whose "exit" is 0 PASS, 1 FAIL or 2 ERROR."""
    name = label = Path(path).stem
    devices = STATE / "devices"
    selected = devices.read_text().split() if devices.exists() else []
    serial = os.environ.get("VERIFY_SERIAL") or (selected[0] if selected else None)
    error, evidence, build, proof, android, videos = None, None, {}, None, None, []
    try:
        if not serial:
            raise Error("no device selected: run verify.sh use <serial>")
        os.environ["VERIFY_SERIAL"] = serial
        # The gate: the app on the device must be verify.sh's build of the current source.
        doctor = verify("doctor", check=False)
        evidence = Path(re.search(r"evidence -> (\S+)", doctor.stdout).group(1)) if "evidence ->" in doctor.stdout else None
        if doctor.returncode != 0:
            raise Error("verify.sh doctor failed:\n" + "\n".join(l for l in doctor.stdout.splitlines() if l.startswith("FAIL")))
        build = json.loads((STATE / "dev" / serial / "installed.json").read_text())
        base = bool(build.get("base"))
        label = f"{name}-base" if base else name
        proof = load(path)
        android = Android(serial, evidence, "base-" if base else "")
        recording = Recording(label)
        try:
            proof.run(android)
        finally:
            videos = recording.stop()
    except Error as e:
        error = str(e)
    except Exception:
        error = "the proof raised:\n" + traceback.format_exc()

    checks = android.checks if android else []
    verdict = "ERROR" if error else "PASS" if checks and all(c["ok"] for c in checks) else "FAIL"
    m = {"verdict": verdict, "error": error,
         "proof": {"name": name, "path": str(Path(path).resolve()), "doc": (proof.__doc__ or "").strip() if proof else ""},
         "device": serial, "build": build, "checks": checks,
         "videos": videos, "notes": android.notes if android else [], "sections": android.sections if android else [],
         "finished": datetime.datetime.now().isoformat(timespec="seconds"),
         "exit": {"PASS": 0, "FAIL": 1}.get(verdict, 2), "evidence": str(evidence) if evidence else None}
    if evidence:
        evidence.mkdir(parents=True, exist_ok=True)
        (evidence / f"proof-{label}.json").write_text(json.dumps({**m, "sections": [list(s) for s in m["sections"]]}, indent=2) + "\n")
        (evidence / f"proof-{label}.md").write_text(report(m))
    passed = sum(c["ok"] for c in checks)
    print(f"VERDICT {verdict}  checks {passed}/{len(checks)}" + (f"\nERROR {error}" if error else "")
          + (f"\nevidence {evidence}" if evidence else ""))
    return m


def paired(checks):
    """Checks keyed by (name, occurrence), so a name checked twice pairs up in order."""
    seen, out = {}, {}
    for c in checks:
        n = seen[c["check"]] = seen.get(c["check"], 0) + 1
        out[(c["check"], n)] = c
    return out


def compare(path):
    """Before and after: the proof on the base build, then on this checkout's build. Exit is the after run's."""
    name = Path(path).stem
    runs = {}
    for side, args in (("before", ["--base"]), ("after", [])):
        installed = verify("install", *args, check=False)
        if installed.returncode != 0:
            print(f"ERROR installing the {side} build: {(installed.stderr or installed.stdout).strip()}")
            return 2
        print(f"== {side}: {installed.stdout.strip()}", flush=True)
        runs[side] = prove(path)
    before, after = runs["before"], runs["after"]

    b, a = paired(before["checks"]), paired(after["checks"])
    keys = list(a) + [k for k in b if k not in a]
    mark = lambda c: "-" if c is None else "pass" if c["ok"] else "FAIL"
    rows = [(k[0], mark(b.get(k)), mark(a.get(k))) for k in keys]
    changed = [r for r in rows if r[1] != r[2]]
    if after["verdict"] == "PASS" and before["verdict"] != "PASS":
        meaning = "fails before and passes after: the change makes the proof's claim true"
    elif after["verdict"] == "PASS":
        meaning = ("passes on both: expected for a refactor or a removal; for a fix, the proof doesn't catch the bug "
                   "the change fixes")
    else:
        meaning = f"the after run is {after['verdict']}: the change doesn't hold up"
    lines = [f"# {name}: before and after", "", after["proof"]["doc"], "",
             f"**{before['verdict']} before, {after['verdict']} after**: {meaning}.", "",
             f"Before: {build_label(before['build'])}. After: {build_label(after['build'])}. Device {after['device']}.", "",
             "| Check | Before | After |", "|---|---|---|", *[f"| {n} | {x} | {y} |" for n, x, y in rows]]
    for side in (before, after):
        if side["error"]:
            lines += ["", f"{'Before' if side is before else 'After'} stopped with an error: {side['error'].splitlines()[0]}"]
    if after["evidence"]:
        Path(after["evidence"], f"compare-{name}.md").write_text("\n".join(lines) + "\n")

    print(f"\nCOMPARE {before['verdict']} before, {after['verdict']} after: {meaning}")
    for n, x, y in changed:
        print(f"  {x:>4} -> {y:<4}  {n}")
    if after["evidence"]:
        print(f"report {Path(after['evidence'], f'compare-{name}.md')}")
    return after["exit"]


def selftest():
    """Runs the tests/ proofs and a stale build, and checks each verdict. Exit 0 when all behave."""
    tests = SKILL / "tests"
    stale = REPO / "verify-selftest-stale.txt"

    def case(proof, expected_exit, must_say="", must_not_say=""):
        p = subprocess.run([sys.executable, __file__, str(tests / proof)], capture_output=True, text=True,
                           env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
        out = p.stdout + p.stderr
        problems = [f"exit {p.returncode}, expected {expected_exit}"] if p.returncode != expected_exit else []
        problems += [f"output lacks {must_say!r}"] if must_say and must_say not in out else []
        problems += [f"output has {must_not_say!r}"] if must_not_say and must_not_say in out else []
        return problems, out

    cases = [
        ("harness checks pass", "harness.py", 0, "VERDICT PASS", ""),
        ("a wrong expectation is FAIL", "must_fail.py", 1, "FAIL pixel m_verify_never_fired fired", ""),
        ("a broken step is ERROR and stops the run", "must_error.py", 2, "VERDICT ERROR", "never reached"),
        ("a proof with no checks isn't a pass", "no_checks.py", 1, "VERDICT FAIL", ""),
    ]
    results = []
    for name, proof, code, say, not_say in cases:
        results.append((name, *case(proof, code, say, not_say)))
    # A source change after the build makes the installed app stale: the run must refuse to start.
    try:
        stale.write_text("verify selftest: makes the installed build stale; deleted when the selftest ends\n")
        results.append(("a stale build is ERROR before anything runs", *case("harness.py", 2, "stale build", "PASS")))
    finally:
        stale.unlink(missing_ok=True)

    for name, problems, out in results:
        print(f"{'ok  ' if not problems else 'BAD '} {name}" + (f": {'; '.join(problems)}" if problems else ""))
        if problems:
            print("     " + "\n     ".join(out.strip().splitlines()[-6:]))
    bad = sum(1 for _, problems, _ in results if problems)
    print(f"SELFTEST {'PASS' if not bad else 'FAIL'}  {len(results) - bad}/{len(results)} cases behaved")
    return 1 if bad else 0


if __name__ == "__main__":
    # Proofs import Error from here; let them share this module's class instead of loading a second copy.
    sys.modules.setdefault("prove", sys.modules[__name__])
    if len(sys.argv) not in (2, 3):
        sys.exit(__doc__)
    if sys.argv[1:2] == ["compare"] and len(sys.argv) == 3:
        sys.exit(compare(sys.argv[2]))
    sys.exit(selftest() if sys.argv[1] == "selftest" else prove(sys.argv[1])["exit"])
