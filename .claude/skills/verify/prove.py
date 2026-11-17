#!/usr/bin/env python3
"""Runs a proof against the app on the selected device and gives a verdict.

  prove.py proofs/<name>.py

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
    def __init__(self, serial, evidence):
        self.serial, self.evidence = serial, evidence
        self.checks, self.notes, self.sections, self._mark = [], [], [], None

    # Driving: a step that fails raises Error, because nothing after it would mean anything.
    def tap(self, selector):
        verify("tap", selector)

    def type(self, selector, text):
        verify("type", selector, text)

    def key(self, name):
        verify("key", name)

    def scroll(self, direction):
        verify("scroll", direction)

    def open(self, url):
        verify("open", url)

    def launch(self):
        verify("launch")

    def reset(self):
        verify("reset")

    def shot(self, name):
        verify("shot", f"{name}.png")

    # Reading: these return what they saw, so a proof can check it and report the actual value.
    def where(self):
        return verify("where", check=False).stdout.strip()

    def text(self, resource_id=None):
        p = verify("text", *([resource_id] if resource_id else []), check=False)
        return p.stdout.rstrip("\n") if p.returncode == 0 else None

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
        p = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True)
        if p.returncode != 0:
            raise Error(f"adb {' '.join(args)} failed: {p.stderr.strip()}")
        return p.stdout


def load(path):
    spec = importlib.util.spec_from_file_location(Path(path).stem, path)
    module = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(Path(path).resolve().parent))
    spec.loader.exec_module(module)
    if not (module.__doc__ or "").strip() or not hasattr(module, "run"):
        raise Error(f"{path} needs a docstring stating its claim and a run(android) function")
    return module


def report(m):
    lines = [f"# {m['proof']['name']}", "", m["proof"]["doc"], "",
             f"**{m['verdict']}**" + (f": {m['error'].splitlines()[0]}" if m["error"] else ""), "",
             f"Device {m['device']}, build {m['build'].get('sha256', '?')[:16]} of {m['build'].get('head', '?')[:10]}"
             + (" + uncommitted changes" if m["build"].get("dirty") else ""), "",
             "| Check | Result | Detail |", "|---|---|---|"]
    lines += [f"| {c['check']} | {'pass' if c['ok'] else 'FAIL'} | {c['detail']} |" for c in m["checks"]]
    if m["notes"]:
        lines += ["", "Notes:", *[f"- {n}" for n in m["notes"]]]
    for title, body in m["sections"]:
        lines += ["", f"## {title}", "", body]
    return "\n".join(lines) + "\n"


def prove(path):
    name = Path(path).stem
    devices = STATE / "devices"
    selected = devices.read_text().split() if devices.exists() else []
    serial = os.environ.get("VERIFY_SERIAL") or (selected[0] if selected else None)
    error, evidence, build, proof, android = None, None, {}, None, None
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
        proof = load(path)
        android = Android(serial, evidence)
        verify("record", "start", f"proof-{name}.mp4")
        try:
            proof.run(android)
        finally:
            verify("record", "stop", check=False)
    except Error as e:
        error = str(e)
    except Exception:
        error = "the proof raised:\n" + traceback.format_exc()

    checks = android.checks if android else []
    verdict = "ERROR" if error else "PASS" if checks and all(c["ok"] for c in checks) else "FAIL"
    m = {"verdict": verdict, "error": error,
         "proof": {"name": name, "path": str(Path(path).resolve()), "doc": (proof.__doc__ or "").strip() if proof else ""},
         "device": serial, "build": build, "checks": checks,
         "notes": android.notes if android else [], "sections": android.sections if android else [],
         "finished": datetime.datetime.now().isoformat(timespec="seconds")}
    if evidence:
        evidence.mkdir(parents=True, exist_ok=True)
        (evidence / f"proof-{name}.json").write_text(json.dumps({**m, "sections": [list(s) for s in m["sections"]]}, indent=2) + "\n")
        (evidence / f"proof-{name}.md").write_text(report(m))
    passed = sum(c["ok"] for c in checks)
    print(f"VERDICT {verdict}  checks {passed}/{len(checks)}" + (f"\nERROR {error}" if error else "")
          + (f"\nevidence {evidence}" if evidence else ""))
    return {"PASS": 0, "FAIL": 1}.get(verdict, 2)


if __name__ == "__main__":
    # Proofs import Error from here; let them share this module's class instead of loading a second copy.
    sys.modules.setdefault("prove", sys.modules[__name__])
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    sys.exit(prove(sys.argv[1]))
