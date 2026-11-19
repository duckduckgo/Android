#!/usr/bin/env python3
"""Runs a proof against the app on the selected device and gives a verdict.

  prove.py proofs/<name>.py
  prove.py compare proofs/<name>.py   runs it on the base build (verify.sh build --base <ref>), then on this
                                      checkout's build, and lines up the checks: before and after
  prove.py pr-summary [<before>] <after>
                               Markdown for a PR's verification section from runs' proof-<name>.json files (or
                               the evidence folder); safe for a public PR: no local paths, logs or screenshots
  prove.py publish <proof-<name>.json | evidence folder> [--into <auto evidence folder>]
                               copies one run (and its base run, after compare) into an auto evidence folder (a
                               new one, or the one auto opened for this change), records each report's served URL
                               in its proof-<name>.json, uploads it, and prints the URLs: the one to put in the PR
                               template's Evidence URL field
  prove.py selftest [--offline]
                               checks the harness itself (run it after changing prove.py); see tests/.
                               --offline runs only the cases that need no device

A proof is a module with a docstring (the claim) and run(android). It acts through verify.sh and records
checks with android.check(). Exit 0 PASS (at least one check, all passed), 1 FAIL (a check failed),
2 ERROR (setup, a driving step or the installed build went wrong). An error is never a pass, and the run
stops at the first one instead of failing every check after it.

Evidence (video, screenshots, proof-<name>.json and .md, ui.log) goes to verify.sh's evidence folder for the run.
"""
import datetime
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
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


def ui_log(line):
    """Appends to the run's ui.log. prove() sets VERIFY_UI_LOG, and verify.sh logs its adb and android calls there too."""
    log = os.environ.get("VERIFY_UI_LOG")
    if log:
        with open(log, "a") as f:
            f.write(line + "\n")


def verify(*args, check=True):
    p = subprocess.run([V, *map(str, args)], capture_output=True, text=True, cwd=REPO)
    ui_log(f"verify.sh {' '.join(map(str, args))} -> exit {p.returncode}")
    if check and p.returncode != 0:
        raise Error(f"verify.sh {' '.join(map(str, args))} failed ({p.returncode}): {(p.stderr or p.stdout).strip()}")
    return p


class Android:
    def __init__(self, serial, evidence, prefix=""):
        # prefix keeps a base run's screenshots apart from the after run's in the same evidence folder
        self.serial, self.evidence, self.prefix = serial, evidence, prefix
        self.checks, self.notes, self.sections, self._mark = [], [], [], None
        self.unverified_items, self.failure_files = [], []

    # Driving: a step that fails raises Error, because nothing after it would mean anything.
    def tap(self, selector, index=None):
        verify("tap", selector, *(["--index", index] if index else []))

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

    def rows(self, label_id, control_id):
        """{label text: (index, checked)} for each list row on screen, pairing the label with the control in its own
        row; (None, None) when that row's control isn't on screen. The index is the one tap(..., index=) takes."""
        p = verify("rows", label_id, control_id, check=False)
        out = {}
        for line in p.stdout.splitlines() if p.returncode == 0 else []:
            text, index, checked = (line.split("\t") + ["-", "-"])[:3]
            out.setdefault(text, (None, None) if index == "-" else (int(index), checked == "true"))
        return out

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

    def expect_log(self, what, pattern, since=None):
        """A check that a logcat line matching the regex appeared since the mark."""
        # The matched line isn't kept as the detail: logcat can carry URLs and other apps' data into a PR.
        found = re.search(pattern, self.logs(since))
        return self.check(what, found, "" if found else f"no log line matched /{pattern}/")

    def unverified(self, what, why):
        """Something the proof couldn't check: reported, never a pass and never a fail."""
        self.unverified_items.append({"what": str(what), "why": str(why)})
        print(f"UNVERIFIED {what}  ({why})", flush=True)

    def note(self, text):
        self.notes.append(str(text))
        print(f"NOTE {text}", flush=True)

    def section(self, title, markdown):
        self.sections.append((title, markdown))

    def at_failure(self):
        """Saves the element tree and a screenshot when a step fails, so the error can be read without a rerun."""
        if not self.evidence:
            return
        layout = verify("layout", check=False)
        if layout.returncode == 0:
            name = f"{self.prefix}layout-at-failure.json"
            (self.evidence / name).write_text(layout.stdout)
            self.failure_files.append(name)
        if verify("shot", f"{self.prefix}at-failure.png", check=False).returncode == 0:
            self.failure_files.append(f"{self.prefix}at-failure.png")

    def _adb(self, *args):
        # Logcat carries raw bytes from other apps; decode leniently so one bad byte isn't an error.
        p = subprocess.run(["adb", "-s", self.serial, *args], capture_output=True, text=True, errors="replace")
        ui_log(f"  adb {' '.join(args)} -> {p.returncode}")
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
    if build.get("external"):
        return f"external APK {build.get('sha256', '?')[:16]}, not built from this source"
    label = f"build {build.get('sha256', '?')[:16]} of {build.get('head', '?')[:10]}"
    if build.get("base"):
        return f"base {label} ({build['base']})"
    return label + (" + uncommitted changes" if build.get("dirty") else "")


def report(m):
    lines = [f"# {m['proof']['name']}", "", m["proof"]["doc"], "",
             f"**{m['verdict']}**" + (f": {m['error'].splitlines()[0]}" if m["error"] else ""), "",
             f"Device {m['device']} ({m.get('model') or '?'}, API {m.get('api') or '?'}), {build_label(m['build'])}", "",
             f"Live services: {', '.join(m.get('live') or []) or 'none'}", "",
             "| Check | Result | Detail |", "|---|---|---|"]
    lines += [f"| {c['check']} | {'pass' if c['ok'] else 'FAIL'} | {c['detail']} |" for c in m["checks"]]
    if m.get("unverified"):
        lines += ["", "Not verified:", *[f"- {u['what']}: {u['why']}" for u in m["unverified"]]]
    # Relative links resolve beside the report, locally and in auto's evidence viewer once published.
    if m["videos"]:
        lines += ["", "Video, in order: " + ", ".join(f"[{v}]({v})" for v in m["videos"])]
    shots = [f for f in m.get("files") or [] if f.endswith(".png")]
    if shots:
        lines += ["", "## Screenshots", "", *[line for f in shots for line in (f"![{f}]({f})", "")]]
    if m["notes"]:
        lines += ["", "Notes:", *[f"- {n}" for n in m["notes"]]]
    for title, body in m["sections"]:
        lines += ["", f"## {title}", "", body]
    return "\n".join(lines) + "\n"


def proof_origin(path):
    """The proof's path in the repo and whether it is committed unchanged; pr-summary includes the source otherwise."""
    path = Path(path).resolve()
    try:
        rel = str(path.relative_to(REPO))
    except ValueError:
        return None, False
    git = ["git", "-C", str(REPO)]
    tracked = subprocess.run([*git, "ls-files", "--error-unmatch", rel], capture_output=True).returncode == 0
    unchanged = subprocess.run([*git, "diff", "--quiet", "HEAD", "--", rel], capture_output=True).returncode == 0
    return rel, tracked and unchanged


def prove(path):
    """Runs one proof; returns its manifest, whose "exit" is 0 PASS, 1 FAIL or 2 ERROR."""
    name = label = Path(path).stem
    devices = STATE / "devices"
    selected = devices.read_text().split() if devices.exists() else []
    serial = os.environ.get("VERIFY_SERIAL") or (selected[0] if selected else None)
    error, evidence, build, proof, android, videos, props = None, None, {}, None, None, [], {}
    existing, ui_header = {}, None
    try:
        if not serial:
            raise Error("no device selected: run verify.sh use <serial>")
        os.environ["VERIFY_SERIAL"] = serial
        # The gate: the app on the device must be verify.sh's build of the current source.
        doctor = verify("doctor", check=False)
        evidence = Path(re.search(r"evidence -> (\S+)", doctor.stdout).group(1)) if "evidence ->" in doctor.stdout else None
        if doctor.returncode != 0:
            raise Error("verify.sh doctor failed: " + "; ".join(l[5:] for l in doctor.stdout.splitlines() if l.startswith("FAIL")))
        build = json.loads((STATE / "dev" / serial / "installed.json").read_text())
        # A base or external build gets its own file names, so a run on this checkout's build doesn't overwrite it.
        side = "base" if build.get("base") else "external" if build.get("external") else ""
        label = f"{name}-{side}" if side else name
        proof = load(path)
        android = Android(serial, evidence, f"{side}-" if side else "")
        for prop, key in (("ro.product.model", "model"), ("ro.build.version.sdk", "api")):
            props[key] = android._adb("shell", "getprop", prop).strip()
        if evidence:
            evidence.mkdir(parents=True, exist_ok=True)
            # The folder holds every run until verify.sh cleanup, so a run's own files are what changed during it.
            existing = {f.name: f.stat().st_mtime_ns for f in evidence.iterdir() if f.is_file()}
            os.environ["VERIFY_UI_LOG"] = str(evidence / "ui.log")
            ui_header = f"== {label} {datetime.datetime.now().isoformat(timespec='seconds')}"
            ui_log(ui_header)
        recording = Recording(label)
        try:
            proof.run(android)
        except Exception:
            android.at_failure()
            raise
        finally:
            videos = recording.stop()
    except Error as e:
        error = str(e)
    except Exception:
        error = "the proof raised:\n" + traceback.format_exc()
    finally:
        os.environ.pop("VERIFY_UI_LOG", None)
    if error and android and android.failure_files:
        error += f"\nscreen at the failure: {', '.join(android.failure_files)}; commands run: ui.log"

    checks = android.checks if android else []
    verdict = "ERROR" if error else "PASS" if checks and all(c["ok"] for c in checks) else "FAIL"
    repo_path, committed = proof_origin(path)
    m = {"verdict": verdict, "error": error,
         "proof": {"name": name, "path": str(Path(path).resolve()), "doc": (proof.__doc__ or "").strip() if proof else "",
                   "repo_path": repo_path, "committed": committed,
                   "source": Path(path).read_text() if Path(path).is_file() else ""},
         "device": serial, **props, "build": build, "checks": checks,
         "live": list(getattr(proof, "LIVE", [])) if proof else [],
         "unverified": android.unverified_items if android else [],
         "videos": videos, "notes": android.notes if android else [], "sections": android.sections if android else [],
         "finished": datetime.datetime.now().isoformat(timespec="seconds"), "ui_log": ui_header,
         "exit": {"PASS": 0, "FAIL": 1}.get(verdict, 2), "evidence": str(evidence) if evidence else None}
    if evidence:
        evidence.mkdir(parents=True, exist_ok=True)
        own = {f"proof-{label}.json", f"proof-{label}.md"}
        m["files"] = sorted(own | {f.name for f in evidence.iterdir() if f.is_file() and f.name != "ui.log"
                                   and existing.get(f.name) != f.stat().st_mtime_ns})
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


# --- pr-summary: the repo is public, so the Markdown carries only what the runs' JSON records about the proof,
# builds, device and checks. Never a path, log, screenshot or user name.

def load_run(arg):
    """A run's proof-<name>.json, given the file or an evidence folder holding one proof's run on this checkout."""
    path = Path(arg)
    if path.is_dir():
        found = sorted(f for f in path.glob("proof-*.json") if not f.stem.endswith(("-base", "-external")))
        if len(found) != 1:
            raise Error(f"{arg} holds {len(found)} runs on this checkout's build; pass the run's proof-<name>.json")
        path = found[0]
    if not path.is_file():
        raise Error(f"no run at {arg}: pass a proof-<name>.json or the evidence folder holding it")
    return json.loads(path.read_text()), path


def public(text):
    for local, shown in ((str(REPO) + "/", ""), (str(REPO), "<repo>"), (str(Path.home()), "~")):
        text = text.replace(local, shown)
    return text


def column(build):
    return "Base" if build.get("base") else "External APK" if build.get("external") else "This branch"


def build_public(build):
    head = f"`{build['head'][:10]}`" if build.get("head") else ""
    if build.get("base"):
        return f"base `{build['base']}`" + (f" ({head})" if head else "")
    if build.get("external"):
        return "an external APK" + (f" (sha256 `{build['sha256'][:12]}`)" if build.get("sha256") else "") + ", not built from this source"
    detail = ", ".join(x for x in (head, "uncommitted changes" if build.get("dirty") else "") if x)
    return "this branch" + (f" ({detail})" if detail else "")


def headline(error):
    lines = [l.strip() for l in (error or "error").splitlines() if l.strip()] or ["error"]
    # A proof that raised carries a traceback, whose last line is the exception itself.
    return lines[-1] if lines[0].startswith("the proof raised") else lines[0]


def outcome(run):
    where = {"Base": "the base", "External APK": "the external APK"}.get(column(run["build"]), "this branch")
    bad = [c for c in run["checks"] if not c["ok"]]
    if run["verdict"] == "PASS":
        return f"passes on {where}"
    if run["verdict"] == "ERROR":
        return f"couldn't run on {where} ({headline(run['error'])})"
    if not run["checks"]:
        return f"fails on {where} (no checks ran)"
    return f"fails on {where} ({len(bad)} of {len(run['checks'])} checks)"


def cell(check):
    if check is None:
        return "not run"
    detail = check["detail"].replace("|", "\\|").replace("\n", " ")
    return ("pass" if check["ok"] else "FAIL") + (f" ({detail})" if detail else "")


def rerun_lines(before, after, proof):
    def setup(build):
        if build.get("base"):
            return f"$V build --base {build.get('head', build['base'])[:10]} && $V install --base"
        if build.get("external"):
            return f"$V install --apk <the APK with sha256 {build.get('sha256', '?')[:12]}>"
        return "$V build && $V install"

    if before and before["build"].get("base") and column(after["build"]) == "This branch":
        return [f"$V build --base {before['build'].get('head', before['build']['base'])[:10]} && $V build && $P compare {proof}"]
    return [f"{setup(run['build'])} && $P {proof}" for run in (before, after) if run]


def auto_evidence_scripts():
    """show-me-your-work's scripts from the newest installed auto: only auto's evidence store serves evidence to a PR."""
    if os.environ.get("VERIFY_AUTO_EVIDENCE_SCRIPTS"):
        candidates = [Path(os.environ["VERIFY_AUTO_EVIDENCE_SCRIPTS"])]
    else:
        roots = [Path.home() / ".claude/plugins/cache/ddg-auto/auto", Path.home() / ".codex/plugins/cache/ddg-auto/auto"]
        versions = [v for r in roots if r.is_dir() for v in r.iterdir()]
        version = lambda v: tuple(int(x) if x.isdigit() else -1 for x in v.name.split("."))
        candidates = [v / "skills/show-me-your-work/scripts" for v in sorted(versions, key=version, reverse=True)]
    for scripts in candidates:
        if (scripts / "evidence-url.ts").is_file():
            return scripts
    raise Error("auto isn't installed: publishing evidence needs its show-me-your-work scripts "
                "(or point VERIFY_AUTO_EVIDENCE_SCRIPTS at them)")

def node(script, *args):
    p = subprocess.run(["node", str(script), *map(str, args)], capture_output=True, text=True)
    if p.returncode:
        detail = ((p.stderr or p.stdout).strip().splitlines() or ["no output"])[-1]
        raise Error(f"{script.name} failed: {detail}")
    return p.stdout.strip().splitlines()[-1]

def ui_log_sections(text, headers):
    """The parts of a shared ui.log that belong to the given runs: each starts at its "== <label> <time>" line."""
    keep, out = False, []
    for line in text.splitlines(keepends=True):
        if line.startswith("== "):
            keep = line.rstrip("\n") in headers
        if keep:
            out.append(line)
    return "".join(out)

def publish(arg, into=None):
    """Copies one run (with its base run and compare report, when compare left them) into an auto evidence folder,
    records each report's served URL in the run's proof-<name>.json (here and in the copy), uploads the auto folder,
    and prints the URLs."""
    try:
        after, path = load_run(arg)
        src = path.parent
        base = path.with_name(f"{path.stem}-base.json")
        runs = [(after, path)] + ([(json.loads(base.read_text()), base)] if base.is_file() else [])
        compared = src / f"compare-{after['proof']['name']}.md"
        scripts = auto_evidence_scripts()
        os.umask(0o022)  # the store serves what it uploads; keep files readable
        root = Path(into).resolve() if into else Path(node(scripts / "new-evidence.ts"))
        dest = root / "verify-android" / f"{src.parent.name}-{src.name}-{path.stem.removeprefix('proof-')}"
        if dest.exists():
            raise Error(f"{dest} exists: the store never replaces a file, so publish a run once")
        dest.mkdir(parents=True)
        # Runs from before files were recorded fall back to their own report, data and videos.
        names = {n for m, p in runs for n in (m.get("files") or [p.name, p.with_suffix(".md").name, *m.get("videos", [])])}
        names |= {compared.name} if base.is_file() and compared.is_file() else set()
        for n in sorted(names):
            if (src / n).is_file():
                shutil.copy2(src / n, dest / n)
        if (src / "ui.log").is_file():
            (dest / "ui.log").write_text(ui_log_sections((src / "ui.log").read_text(), {m.get("ui_log") for m, _ in runs}))
        urls = {}
        for m, p in runs:
            report_md = dest / p.with_suffix(".md").name
            urls[p.name] = node(scripts / "evidence-url.ts", report_md if report_md.is_file() else dest / p.name)
            for copy in (dest / p.name, p):
                data = json.loads(copy.read_text())
                data["evidence_url"] = urls[p.name]
                copy.write_text(json.dumps(data, indent=2) + "\n")
        if (dest / compared.name).is_file():
            urls[compared.name] = node(scripts / "evidence-url.ts", dest / compared.name)
        uploaded = json.loads(node(scripts / "upload-evidence.ts", "--directory", root))
        if uploaded.get("failed"):
            raise Error(f"{uploaded['failed']} files failed to upload; rerun publish with --into {root}")
    except Error as e:
        print(f"ERROR {e}", file=sys.stderr)
        return 2
    for name, url in urls.items():
        print(f"{name}  {url}")
    return 0

def summarize(before, after):
    proof = after["proof"]
    committed = proof.get("repo_path") if proof.get("committed") else None
    claim = (proof.get("doc") or "").split("\n\n")[0].replace("\n", " ")
    sentence = "; ".join(outcome(r) for r in (before, after) if r)
    # A run that stopped at doctor never read the device or the build, so those lines are left out, not guessed.
    device = ", ".join(x for x in (after.get("model"), f"API {after['api']}" if after.get("api") else "") if x)
    lines = [f"**{sentence[0].upper()}{sentence[1:]}.**", "",
             "- Proof: " + (f"`{committed}`" if committed else "written for this change (source below)") + (f": {claim}" if claim else "")]
    lines += [f"- Device: {device}"] if device else []
    lines += ["- Builds: " + (f"{build_public(before['build'])} → " if before else "") + build_public(after["build"]),
              f"- Live services: {', '.join(after.get('live') or []) or 'none'}"]
    lines += [f"- Evidence: {after['evidence_url']}"] if after.get("evidence_url") else []
    if before:
        differs = [k for k in ("model", "api") if before.get(k) != after.get(k)]
        differs += ["proof source"] if before["proof"].get("source") != proof.get("source") else []
        if differs:
            lines.append(f"- Not like for like: the runs differ in {', '.join(differs)}")
    lines += [f"- Not verified: {u['what']} ({u['why']})" for u in after.get("unverified") or []]

    if before:
        b, a = paired(before["checks"]), paired(after["checks"])
        keys = list(a) + [k for k in b if k not in a]
        label = lambda k: k[0] if k[1] == 1 else f"{k[0]} ({k[1]})"
        header = [f"| Check | {column(before['build'])} | {column(after['build'])} |", "|---|---|---|"]
        rows = {k: f"| {label(k)} | {cell(b.get(k))} | {cell(a.get(k))} |" for k in keys}
        changed = [k for k in keys if cell(b.get(k)).split(" ")[0] != cell(a.get(k)).split(" ")[0]]
        lines += ["", *header, *([rows[k] for k in changed] or [f"| all {len(keys)} checks | same result | same result |"])]
        lines += ["", "<details><summary>All checks</summary>", "", *header, *rows.values(), "", "</details>"]
    else:
        bad = [c for c in after["checks"] if not c["ok"]]
        if bad:
            lines += ["", "| Check | Result |", "|---|---|", *[f"| {c['check']} | {cell(c)} |" for c in bad]]
        elif after["verdict"] == "PASS":
            lines += ["", f"All {len(after['checks'])} checks passed."]
        elif not after["checks"]:
            lines += ["", "No checks ran."]
        lines += ["", "<details><summary>All checks</summary>", "", "| Check | Result |", "|---|---|",
                  *[f"| {c['check']} | {cell(c)} |" for c in after["checks"]], "", "</details>"]

    for run in (r for r in (before, after) if r):
        for title, markdown in run.get("sections") or []:
            lines += ["", f"<details><summary>{title} ({column(run['build'])})</summary>", "", markdown, "", "</details>"]
    if not committed and proof.get("source"):
        lines += ["", "<details><summary>Proof source</summary>", "", "```python", proof["source"].rstrip(), "```", "",
                  "</details>"]
    # proofs/ is where a proof finds steps.py, and .gitignore keeps it out of the repo there.
    saved = f"{SKILL.relative_to(REPO)}/proofs/{proof.get('name') or 'proof'}.py"
    lines += ["", "<details><summary>Rerun</summary>", "", "```",
              *([] if committed else [f"# save the proof source above as {saved}"]),
              f"V={SKILL.relative_to(REPO)}/verify.sh", f"P={SKILL.relative_to(REPO)}/prove.py",
              *rerun_lines(before, after, committed or saved), "```", "", "</details>"]
    return public("\n".join(lines) + "\n")


def pr_summary(*args):
    """Prints the summary of one run, or of a before and an after run. With one run and a -base run of the same proof
    beside it (as compare leaves them), that is the before."""
    try:
        runs = [load_run(a) for a in args]
    except Error as e:
        print(f"ERROR {e}", file=sys.stderr)
        return 2
    if len(runs) == 2:
        before, after = runs[0][0], runs[1][0]
    else:
        after, path = runs[0]
        base = path.with_name(f"{path.stem}-base.json")
        before = json.loads(base.read_text()) if base.is_file() and not after["build"].get("base") else None
    print(summarize(before, after), end="")
    return 0


def summary_fixtures(folder):
    """Runs as prove() writes them, with this machine's paths in every field that has one, for the offline cases."""
    home, apk = str(Path.home()), str(REPO / "build" / "verify" / "builds")
    branch = {"head": "4f2a9c1e7b3d5a6f", "dirty": True, "fingerprint": "f" * 64, "apk": f"{apk}/4f2a9c1e.apk",
              "sha256": "4f2a9c1e" * 8, "serial": "emulator-5580"}
    base = {"base": "origin/develop", "head": "75e3e4a9a0c1d2e3", "apk": f"{apk}/base-75e3e4a9a0c1.apk",
            "sha256": "75e3e4a9" * 8, "serial": "emulator-5580"}
    external = {"external": True, "apk": f"{home}/Downloads/app-internal-debug.apk", "sha256": "9c0ffee1" * 8,
                "serial": "emulator-5580"}
    committed = {"name": "tabs", "path": str(SKILL / "proofs" / "tabs.py"), "repo_path": ".agents/skills/verify-android/proofs/tabs.py",
                 "committed": True, "doc": "The tab switcher lists one card per tab.\n\nRecipe: features/tabs.md.",
                 "source": "def run(android):\n    pass\n"}
    scratch = {**committed, "name": "fix", "path": f"{home}/scratch/fix.py", "repo_path": None, "committed": False,
               "doc": "Closing a tab keeps the other tabs.", "source": "def run(android):\n    android.check('kept', True)\n"}

    def run(verdict, build, proof, checks, **extra):
        return {"verdict": verdict, "error": None, "proof": proof, "device": "emulator-5580", "model": "sdk_gphone64_arm64",
                "api": "34", "build": build, "checks": [{"check": c, "ok": ok, "detail": d} for c, ok, d in checks],
                "live": [], "unverified": [], "videos": ["proof-tabs-1.mp4"], "notes": [f"saved in {home}"],
                "sections": [], "finished": "2026-11-17T10:00:00", "exit": {"PASS": 0, "FAIL": 1}.get(verdict, 2),
                "evidence": f"{REPO}/build/verify/evidence/20261117-100000/emulator", **extra}

    checks = [("at the switcher", True, "TabSwitcherActivity"), ("card titled with the page", True, ""),
              ("tab count 2", True, "2")]
    compare_dir = folder / "compare"
    compare_dir.mkdir()
    (compare_dir / "proof-tabs-base.json").write_text(json.dumps(run(
        "FAIL", base, committed, checks[:1] + [("card titled with the page", False, "")] + checks[2:])))
    (compare_dir / "proof-tabs.json").write_text(json.dumps(run("PASS", branch, committed, checks)))
    (folder / "error.json").write_text(json.dumps(run(
        "ERROR", branch, scratch, checks[:1], live=["duck.ai chat"],
        error=f"verify.sh tap id:close failed (1): not on screen in {REPO}\nscreen at the failure: layout-at-failure.json",
        unverified=[{"what": "the undo snackbar", "why": "it hides before the layout can be read"}])))
    (folder / "external.json").write_text(json.dumps(run("FAIL", external, committed, [("tab count 2", False, "1")])))
    (folder / "branch.json").write_text(json.dumps(run("PASS", branch, committed, [("tab count 2", True, "2")])))
    return compare_dir


def selftest(offline=False):
    """Runs the tests/ proofs and a stale build, and checks each verdict, after the pr-summary cases, which need no
    device. Exit 0 when all behave."""
    tests = SKILL / "tests"
    stale = REPO / "verify-selftest-stale.txt"

    def case(args, expected_exit, must_say=(), must_not_say=()):
        p = subprocess.run([sys.executable, __file__, *map(str, args)], capture_output=True, text=True,
                           env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"})
        out = p.stdout + p.stderr
        problems = [f"exit {p.returncode}, expected {expected_exit}"] if p.returncode != expected_exit else []
        problems += [f"output lacks {s!r}" for s in must_say if s not in out]
        problems += [f"output has {s!r}" for s in must_not_say if s and s in out]
        return problems, out

    results = []
    with tempfile.TemporaryDirectory() as tmp:
        folder = Path(tmp)
        compare_dir = summary_fixtures(folder)
        leaks = (str(REPO), str(Path.home()), tmp, os.environ.get("USER", ""), "Downloads", "emulator-5580", "evidence")
        results += [
            ("pr-summary pairs a compare's runs and shows what changed", *case(
                ["pr-summary", compare_dir], 0,
                ("**Fails on the base (1 of 3 checks); passes on this branch.**", "| card titled with the page | FAIL | pass |",
                 "`.agents/skills/verify-android/proofs/tabs.py`", "<details><summary>All checks", "$P compare", "API 34"),
                leaks)),
            ("pr-summary of an uncommitted proof that errored carries its source and no local paths", *case(
                ["pr-summary", folder / "error.json"], 0,
                ("Couldn't run on this branch (verify.sh tap id:close failed (1): not on screen in <repo>)", "```python", "save the proof source", "Not verified: the undo snackbar",
                 "Live services: duck.ai chat", "uncommitted changes"),
                (*leaks, "layout-at-failure"))),
            ("pr-summary marks an external APK", *case(
                ["pr-summary", folder / "external.json", folder / "branch.json"], 0,
                ("Fails on the external APK", "an external APK (sha256 `9c0ffee19c0f`)", "$V install --apk"), leaks)),
            ("pr-summary refuses a folder without a run", *case(["pr-summary", folder / "nothing"], 2, ("ERROR",))),
        ]
    if not offline:
        cases = [
            ("harness checks pass, and an unverified item doesn't fail them", "harness.py", 0, ("VERDICT PASS", "UNVERIFIED"), ()),
            ("a wrong expectation is FAIL", "must_fail.py", 1, ("FAIL pixel m_verify_never_fired fired", "FAIL a log line that was never written"), ()),
            ("a broken step is ERROR, stops the run and saves the screen", "must_error.py", 2,
             ("VERDICT ERROR", "layout-at-failure.json"), ("never reached",)),
            ("a proof with no checks isn't a pass", "no_checks.py", 1, ("VERDICT FAIL",), ()),
            ("an unverified item is never a pass", "only_unverified.py", 1, ("VERDICT FAIL",), ()),
        ]
        for name, proof, code, say, not_say in cases:
            results.append((name, *case([tests / proof], code, say, not_say)))
        # A source change after the build makes the installed app stale: the run must refuse to start.
        try:
            stale.write_text("verify selftest: makes the installed build stale; deleted when the selftest ends\n")
            results.append(("a stale build is ERROR before anything runs",
                            *case([tests / "harness.py"], 2, ("stale build",), ("PASS",))))
        finally:
            stale.unlink(missing_ok=True)

    for name, problems, out in results:
        print(f"{'ok  ' if not problems else 'BAD '} {name}" + (f": {'; '.join(problems)}" if problems else ""))
        if problems:
            print("     " + "\n     ".join(out.strip().splitlines()[-6:]))
    bad = sum(1 for _, problems, _ in results if problems)
    print(f"SELFTEST {'PASS' if not bad else 'FAIL'}  {len(results) - bad}/{len(results)} cases behaved"
          + ("  (offline: device cases skipped)" if offline else ""))
    return 1 if bad else 0


if __name__ == "__main__":
    # Proofs import Error from here; let them share this module's class instead of loading a second copy.
    sys.modules.setdefault("prove", sys.modules[__name__])
    args = sys.argv[1:]
    if args[:1] == ["pr-summary"] and len(args) in (2, 3):
        sys.exit(pr_summary(*args[1:]))
    if args[:1] == ["publish"] and len(args) in (2, 4) and (len(args) == 2 or args[2] == "--into"):
        sys.exit(publish(args[1], args[3] if len(args) == 4 else None))
    if args[:1] == ["compare"] and len(args) == 2:
        sys.exit(compare(args[1]))
    if args[:1] == ["selftest"] and args[1:] in ([], ["--offline"]):
        sys.exit(selftest(offline=args[1:] == ["--offline"]))
    if len(args) == 1 and not args[0].startswith("-") and args[0] not in ("pr-summary", "compare", "publish"):
        sys.exit(prove(args[0])["exit"])
    sys.exit(__doc__)
