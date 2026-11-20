#!/usr/bin/env python3
"""Runs a proof against the app on the selected device and gives a verdict.

  prove.py [--evidence-dir <dir> | --decision-log <decisions.tsv>] proofs/<name>.py
  prove.py compare [--evidence-dir <dir> | --decision-log <decisions.tsv>] proofs/<name>.py
                               runs it on the base build (verify.sh build --base <ref>), then on this checkout's
                               build, and lines up the checks and key frames: before and after
  prove.py pr-summary <attempt folder | compare folder | proof.json> [<after>]
                               the PR's Verification scenario, in auto's format; safe for a public PR: no local paths
                               or logs, and evidence links are served URLs once the run is published
  prove.py publish <attempt folder | compare folder> [--into <auto evidence folder>]
                               uploads the folder to auto's evidence store (copying it into an auto evidence folder
                               first when it isn't in one), records the served URLs and prints the index.html URL:
                               the one for the PR template's Evidence URL field
  prove.py selftest [--offline] [--evidence-dir <dir>]
                               checks the harness itself (run it after changing prove.py); see tests/.
                               --offline runs only the cases that need no device

A proof is a module with a docstring (the claim) and run(android). It acts through verify.sh and records
checks with android.check(). Exit 0 PASS (at least one check, all passed), 1 FAIL (a check failed),
2 ERROR (setup, a driving step or the installed build went wrong). An error is never a pass, and the run
stops at the first one instead of failing every check after it.

Each run writes a fresh attempt folder, <evidence dir>/<stamp>-<name>-<id>/, and never reuses one: proof.py,
proof.json, report.md, index.html, and per device the video, screenshots, ui.log, key-frames.png and poster.png.
Without --evidence-dir or --decision-log the attempt folders go in build/verify/evidence/.
"""
import datetime
import importlib.util
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import threading
import time
import traceback
import uuid
import zlib
from pathlib import Path
from urllib.parse import quote

import evidence as ev

SKILL = Path(__file__).resolve().parent
REPO = Path(subprocess.run(["git", "-C", str(SKILL), "rev-parse", "--show-toplevel"],
                           capture_output=True, text=True, check=True).stdout.strip())
V = str(SKILL / "verify.sh")
STATE = REPO / "build" / "verify" / "state"
DEFAULT_EVIDENCE = REPO / "build" / "verify" / "evidence"
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
    def __init__(self, serial, evidence):
        self.serial, self.evidence = serial, evidence
        self.checks, self.notes, self.sections, self._mark = [], [], [], None
        self.unverified_items, self.failure_files, self.shots = [], [], []

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
        verify("shot", f"{name}.png")
        self.shots.append(f"{name}.png")

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
            (self.evidence / "layout-at-failure.json").write_text(layout.stdout)
            self.failure_files.append("layout-at-failure.json")
        if verify("shot", "at-failure.png", check=False).returncode == 0:
            self.failure_files.append("at-failure.png")
            self.shots.append("at-failure.png")

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
        video = f"recording-{len(self.videos) + 1}.mp4"
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
    if not build:
        return "build not read: the run stopped before doctor passed"
    if build.get("external"):
        return f"external APK {build.get('sha256', '?')[:16]}, not built from this source"
    label = f"build {build.get('sha256', '?')[:16]} of {build.get('head', '?')[:10]}"
    if build.get("base"):
        return f"base {label} ({build['base']})"
    return label + (" + uncommitted changes" if build.get("dirty") else "")


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


def inside(path, root):
    path, root = Path(path).resolve(), Path(root).resolve()
    return path == root or root in path.parents


def evidence_root(evidence_dir=None, decision_log=None):
    """Where attempt folders go: --evidence-dir, proofs/ beside auto's decision log, or build/verify/evidence."""
    if evidence_dir and decision_log:
        raise Error("pass --evidence-dir or --decision-log, not both")
    if evidence_dir:
        root, what = Path(evidence_dir).expanduser().resolve(), "--evidence-dir"
    elif decision_log:
        log = Path(decision_log).expanduser().resolve()
        if not log.is_file() or log.suffix != ".tsv":
            raise Error(f"--decision-log must be an existing decisions .tsv file: {decision_log}")
        root, what = log.parent / "proofs", "the decision log"
    else:
        return DEFAULT_EVIDENCE
    if inside(root, REPO):
        # Untracked evidence there could be committed, and would make every build look stale.
        raise Error(f"keep {what} outside this checkout: evidence written there would land in the repo")
    return root


def new_attempt(root, stem):
    root.mkdir(parents=True, exist_ok=True)
    folder = root / f"{datetime.datetime.now():%Y%m%d-%H%M%S}-{stem}-{uuid.uuid4().hex[:4]}"
    folder.mkdir()  # never exist_ok: two attempts never share a folder
    return folder


def revision():
    git = ["git", "-C", str(REPO)]
    head = subprocess.run([*git, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    dirty = subprocess.run([*git, "status", "--porcelain"], capture_output=True, text=True).stdout.splitlines()
    return {"head": head, "dirty_files": len(dirty)}


def run_details(m):
    rev = m.get("revision") or {}
    head = f"`{rev['head'][:10]}`" if rev.get("head") else "unknown"
    proof = m["proof"]
    return [f"Revision: {head}" + (f" + {rev['dirty_files']} uncommitted files" if rev.get("dirty_files") else ", clean"),
            f"Build: {build_label(m.get('build'))}", f"Device: {ev.device_name(m)}",
            f"Live services: {', '.join(m.get('live') or []) or 'none'}",
            f"Proof: {proof.get('repo_path') or proof.get('name', 'proof') + '.py'}"
            + (" (committed)" if proof.get("committed") else " (not committed; proof.py here is its source)"),
            f"Poster: {m['poster_source']}" if m.get("poster_source") else "Poster: none",
            f"Finished: {m.get('finished')}",
            "Rerun: " + " ; ".join(rerun_lines(None, m, proof.get("repo_path") or f"proofs/{proof.get('name')}.py"))]


def write_run(attempt, m):
    attempt = Path(attempt)
    (attempt / "proof.json").write_text(json.dumps({**m, "sections": [list(s) for s in m.get("sections") or []]}, indent=2) + "\n")
    (attempt / "report.md").write_text(ev.report_md(m, run_details(m)))
    (attempt / "index.html").write_text(ev.index_html(m, run_details(m)))


def prove(path, attempt):
    """Runs one proof into its attempt folder; returns its manifest, whose "exit" is 0 PASS, 1 FAIL or 2 ERROR."""
    name = Path(path).stem
    attempt = Path(attempt)
    devices = STATE / "devices"
    selected = devices.read_text().split() if devices.exists() else []
    serial = os.environ.get("VERIFY_SERIAL") or (selected[0] if selected else None)
    error, build, proof, android, videos, props, device_dir, folder = None, {}, None, None, [], {}, None, None
    if Path(path).is_file():
        shutil.copy2(path, attempt / "proof.py")
    # verify.sh shot and record write here instead of the shared run folder.
    os.environ["VERIFY_EVIDENCE_DIR"] = str(attempt)
    try:
        if not serial:
            raise Error("no device selected: run verify.sh use <serial>")
        os.environ["VERIFY_SERIAL"] = serial
        # The gate: the app on the device must be verify.sh's build of the current source.
        doctor = verify("doctor", check=False)
        if doctor.returncode != 0:
            raise Error("verify.sh doctor failed: " + "; ".join(l[5:] for l in doctor.stdout.splitlines() if l.startswith("FAIL")))
        build = json.loads((STATE / "dev" / serial / "installed.json").read_text())
        proof = load(path)
        android = Android(serial, None)
        for prop, key in (("ro.product.model", "model"), ("ro.build.version.sdk", "api")):
            props[key] = android._adb("shell", "getprop", prop).strip()
        # Named after the model, not the serial: the folder name reaches published URLs.
        device_dir = re.sub(r"[^A-Za-z0-9._-]+", "_", props.get("model") or "").strip("_") or "device"
        folder = android.evidence = attempt / device_dir
        folder.mkdir()
        os.environ["VERIFY_EVIDENCE_DIR"] = str(folder)
        os.environ["VERIFY_UI_LOG"] = str(folder / "ui.log")
        recording = Recording(name)
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
        os.environ.pop("VERIFY_EVIDENCE_DIR", None)
    if error and android and android.failure_files:
        error += f"\nscreen at the failure: {', '.join(f'{device_dir}/{f}' for f in android.failure_files)}; commands run: {device_dir}/ui.log"

    checks = android.checks if android else []
    verdict = "ERROR" if error else "PASS" if checks and all(c["ok"] for c in checks) else "FAIL"
    repo_path, committed = proof_origin(path)
    kept = lambda names: [f"{device_dir}/{n}" for n in names if folder and (folder / n).is_file()]
    m = {"verdict": verdict, "error": error,
         "proof": {"name": name, "path": str(Path(path).resolve()), "doc": (proof.__doc__ or "").strip() if proof else "",
                   "repo_path": repo_path, "committed": committed,
                   "source": Path(path).read_text() if Path(path).is_file() else ""},
         "device": serial, **props, "build": build, "revision": revision(), "checks": checks,
         "live": list(getattr(proof, "LIVE", [])) if proof else [],
         "unverified": android.unverified_items if android else [],
         "notes": android.notes if android else [], "sections": android.sections if android else [],
         "attempt": str(attempt), "device_dir": device_dir if folder else None,
         "videos": kept(videos), "shots": kept(dict.fromkeys(android.shots if android else [])),
         "logs": kept(["ui.log", "layout-at-failure.json"]),
         "problems": [f"{v} missing: screenrecord saved nothing" for v in videos if not (folder / v).is_file()],
         "finished": datetime.datetime.now().isoformat(timespec="seconds"),
         "exit": {"PASS": 0, "FAIL": 1}.get(verdict, 2)}
    ev.draw_run(attempt, m)
    write_run(attempt, m)
    passed = sum(c["ok"] for c in checks)
    drawn = ", ".join(Path(p).name for p in m["images"].values())
    print(f"VERDICT {verdict}  checks {passed}/{len(checks)}" + (f"\nERROR {error}" if error else "")
          + (f"\ndrew {drawn}" if drawn else "") + "".join(f"\nPROBLEM {p}" for p in m["problems"])
          + f"\nevidence {attempt}  (report.md, index.html)")
    return m


def paired(checks):
    """Checks keyed by (name, occurrence), so a name checked twice pairs up in order."""
    seen, out = {}, {}
    for c in checks:
        n = seen[c["check"]] = seen.get(c["check"], 0) + 1
        out[(c["check"], n)] = c
    return out


def check_rows(before, after):
    """(check, before, after) per check, each "pass", "FAIL" or "-", in the after run's order."""
    b, a = paired(before["checks"]), paired(after["checks"])
    keys = list(a) + [k for k in b if k not in a]
    mark = lambda c: "-" if c is None else "pass" if c["ok"] else "FAIL"
    return [(k[0] if k[1] == 1 else f"{k[0]} ({k[1]})", mark(b.get(k)), mark(a.get(k))) for k in keys]


def compare(path, root):
    """Before and after: the proof on the base build, then on this checkout's build, each in its own attempt folder
    under one compare folder. Exit is the after run's."""
    name = Path(path).stem
    folder = new_attempt(root, f"{name}-vs-base")
    runs = {}
    for side, args in (("before", ["--base"]), ("after", [])):
        installed = verify("install", *args, check=False)
        if installed.returncode != 0:
            print(f"ERROR installing the {side} build: {(installed.stderr or installed.stdout).strip()}")
            return 2
        print(f"== {side}: {installed.stdout.strip()}", flush=True)
        (folder / side).mkdir()
        runs[side] = prove(path, folder / side)
    before, after = runs["before"], runs["after"]

    rows = check_rows(before, after)
    if after["verdict"] == "PASS" and before["verdict"] != "PASS":
        meaning = "fails before and passes after: the change makes the proof's claim true"
    elif after["verdict"] == "PASS":
        meaning = ("passes on both: expected for a refactor or a removal; for a fix, the proof doesn't catch the bug "
                   "the change fixes")
    else:
        meaning = f"the after run is {after['verdict']}: the change doesn't hold up"
    labels = (f"Before: {build_label(before['build'])}", f"After: {build_label(after['build'])}")
    image, problem = ev.draw_before_after(folder, before, after, labels)
    c = {"proof": {"name": name, "doc": after["proof"]["doc"]}, "before": "before/proof.json", "after": "after/proof.json",
         "verdicts": [before["verdict"], after["verdict"]], "meaning": meaning, "rows": rows, "labels": labels,
         "before_after": image, "problems": [problem] if problem else [], "exit": after["exit"]}
    details = [*labels, f"Device: {ev.device_name(after)}", run_details(after)[0], run_details(after)[3],
               "Rerun: " + " ; ".join(rerun_lines(before, after, after["proof"].get("repo_path") or f"proofs/{name}.py"))]
    (folder / "compare.json").write_text(json.dumps(c, indent=2) + "\n")
    (folder / "report.md").write_text(ev.compare_report_md(c, before, after, details))
    (folder / "index.html").write_text(ev.compare_index_html(c, before, after, details))

    print(f"\nCOMPARE {before['verdict']} before, {after['verdict']} after: {meaning}")
    for n, x, y in rows:
        if x != y:
            print(f"  {x:>4} -> {y:<4}  {n}")
    print(f"evidence {folder}  (report.md, index.html" + (f", {image})" if image else ")")
          + (f"\nPROBLEM {problem}" if problem else ""))
    return after["exit"]


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


def outcome(run):
    where = {"Base": "the base", "External APK": "the external APK"}.get(column(run["build"]), "this branch")
    bad = [c for c in run["checks"] if not c["ok"]]
    if run["verdict"] == "PASS":
        return f"passes on {where}"
    if run["verdict"] == "ERROR":
        return f"couldn't run on {where} ({ev.headline(run['error'])})"
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


def auto_root():
    home = Path(os.environ.get("DDG_AUTO_HOME") or Path.home() / ".auto")
    return Path(os.environ.get("DDG_AUTO_AUDIT_ROOT") or home / "evidence").resolve()


def publish(arg, into=None):
    """Uploads an attempt or compare folder to auto's evidence store, copying it into an auto evidence folder first
    when it isn't in one, records each index.html's served URL as evidence_url in its JSON (here and in the copy),
    and prints the URLs."""
    try:
        src = Path(arg).resolve()
        src = src.parent if src.is_file() else src
        # A side of a comparison is published with the comparison, so its links keep working.
        if src.name in ("before", "after") and (src.parent / "compare.json").is_file():
            src = src.parent
        compared = (src / "compare.json").is_file()
        if not compared and not (src / "proof.json").is_file():
            raise Error(f"no run at {arg}: pass an attempt folder (proof.json) or a compare folder (compare.json)")
        scripts = auto_evidence_scripts()
        os.umask(0o022)  # the store serves what it uploads; keep files readable
        root = auto_root()
        target = Path(into).resolve() if into else None if inside(src, root) else Path(node(scripts / "new-evidence.ts"))
        dest = src
        if target and not inside(src, target):
            dest = target / src.name
            if dest.exists():
                raise Error(f"{dest} exists: the store never replaces a file, so publish a run once")
            shutil.copytree(src, dest)
        if not inside(dest, root) or dest == root:
            raise Error(f"{dest} isn't in auto's evidence store ({root}): pass --into an evidence folder from new-evidence.ts")
        urls = {}
        for rel in (["compare.json", "before/proof.json", "after/proof.json"] if compared else ["proof.json"]):
            url = node(scripts / "evidence-url.ts", (dest / rel).parent / "index.html")
            urls[str(Path(rel).parent / "index.html").removeprefix("./")] = url
            for copy in {dest / rel, src / rel}:
                data = json.loads(copy.read_text())
                data["evidence_url"] = url
                copy.write_text(json.dumps(data, indent=2) + "\n")
        uploaded = json.loads(node(scripts / "upload-evidence.ts", "--directory", root / dest.relative_to(root).parts[0]))
        if uploaded.get("failed"):
            raise Error(f"{uploaded['failed']} files failed to upload; rerun publish on {dest}")
    except Error as e:
        print(f"ERROR {e}", file=sys.stderr)
        return 2
    for name, url in urls.items():
        print(f"{name}  {url}")
    print(f"Evidence URL: {urls['index.html']}")
    return 0


# --- pr-summary: the repo is public, so the Markdown carries only what the runs' JSON records about the proof,
# builds, device and checks, and links to evidence: served URLs once published. Never a path, log or user name.

def read_run(arg):
    path = Path(arg)
    path = path / "proof.json" if path.is_dir() else path
    if not path.is_file() or path.name == "compare.json":
        raise Error(f"no run at {arg}: pass an attempt folder or its proof.json, or a compare folder")
    return json.loads(path.read_text()), path.parent


def load_runs(args):
    """(before, after, comparison): each run is (manifest, the prefix its unpublished links take); comparison is the
    compare folder's compare.json, or None."""
    first = Path(args[0])
    folder = first if first.is_dir() else first.parent if first.name == "compare.json" else None
    if len(args) == 1 and folder and (folder / "compare.json").is_file():
        c = json.loads((folder / "compare.json").read_text())
        return (read_run(folder / "before")[0], "before/"), (read_run(folder / "after")[0], "after/"), c
    runs = [read_run(a) for a in args]
    if len(runs) == 1:
        return None, (runs[0][0], ""), None
    (b, b_dir), (a, a_dir) = runs
    return (b, f"{b_dir.name}/"), (a, f"{a_dir.name}/"), None


def href(run, rel):
    """A file of a run: its served URL when published (siblings of the served index.html), else a relative path."""
    m, prefix = run
    if m.get("evidence_url"):
        return m["evidence_url"].rsplit("/", 1)[0] + "/" + quote(rel)
    return quote(prefix + rel)


def thumb(src, alt, target=None):
    # auto's publish-pr finds exactly this form and swaps the src for a GitHub attachment.
    alt = re.sub(r"[`\"<>]", "", alt)
    return f"[<img src=\"{src}\" width=\"200\" alt=\"{alt}\">]({target or src})"


def heading(run, compared):
    build = run[0]["build"]
    if build.get("base"):
        return f"Before (base `{build['base']}`)"
    return {"External APK": "External APK"}.get(column(build), "After" if compared else "This branch")


def evidence_table(runs, compared, comparison):
    lines, ms = [], [m for m, _ in runs]
    names = ev.shot_names(*ms)
    labels = [heading(r, compared) for r in runs]
    if names or any(m.get("videos") for m in ms):
        lines += ["| | " + " | ".join(labels) + " |", "|---" * (len(runs) + 1) + "|"]
        for n in names[:6]:
            cells = [thumb(href(r, ev.shot_path(r[0], n)), f"{label}: {n}") if ev.shot_path(r[0], n) else "not taken"
                     for r, label in zip(runs, labels)]
            lines.append(f"| {n} | " + " | ".join(cells) + " |")
        if any(m.get("videos") for m in ms):
            cells = []
            for r, label in zip(runs, labels):
                video, poster = (r[0].get("videos") or [None])[0], (r[0].get("images") or {}).get("poster")
                cells.append("no video" if not video else thumb(href(r, poster), f"{label}: video", href(r, video))
                             if poster else f"[▶ Watch video]({href(r, video)})")
            lines.append("| video | " + " | ".join(cells) + " |")
        lines.append("")
    watch = [f"{label}: [▶ Watch video]({href(r, r[0]['videos'][0])})"
             + "".join(f" ([part {i}]({href(r, v)}))" for i, v in enumerate(r[0]["videos"][1:], 2))
             for r, label in zip(runs, labels) if r[0].get("videos")]
    lines += [" · ".join(watch), ""] if watch else []
    more = [f"{len(names) - 6} more screenshots in the full report"] if len(names) > 6 else []
    if comparison and comparison.get("before_after"):
        url = (comparison["evidence_url"].rsplit("/", 1)[0] + "/" if comparison.get("evidence_url") else "") + comparison["before_after"]
        lines += [f"Key frames paired by name: [before-after.png]({url})" + "".join(f"; {x}" for x in more) + ".", ""]
    else:
        frames = [f"[{label}]({href(r, r[0]['images']['key_frames'])})" for r, label in zip(runs, labels)
                  if (r[0].get("images") or {}).get("key_frames")]
        if frames or more:
            lines += ["; ".join(([f"Key frames: {', '.join(frames)}"] if frames else []) + more) + ".", ""]
    return lines


def summarize(before, after, comparison=None):
    runs = [r for r in (before, after) if r]
    b, a = (before[0] if before else None), after[0]
    proof = a["proof"]
    committed = proof.get("repo_path") if proof.get("committed") else None
    claim = (proof.get("doc") or "").split("\n\n")[0].replace("\n", " ").strip() or proof.get("name") or "proof"
    sentence = "; ".join(outcome(r) for r in (b, a) if r)
    device = ev.device_name(a) if a.get("model") else None
    on = f"{a['model']} (API {a.get('api') or '?'})" if a.get("model") else None
    compared = bool(b and b["build"].get("base"))
    how = f" Driven through the app's UI on a {on}" + (", on the base build and on this branch" if compared else "") + "." if device else ""
    lines = [f"### {ev.claim(a)}", "", f"**Verified:** {claim}{how}", "", *evidence_table(runs, compared, comparison)]

    outcome_line = f"**Outcome:** {sentence[0].upper()}{sentence[1:]}."
    if b:
        changed = [(n, x, y) for n, x, y in check_rows(b, a) if x != y]
        outcome_line += (" Changed: " + "; ".join(f"{n}: {x} → {y}" for n, x, y in changed[:5]) + "."
                         if changed else f" All {len(check_rows(b, a))} checks give the same result on both.")
    else:
        bad = [c for c in a["checks"] if not c["ok"]]
        outcome_line += (" Failed: " + "; ".join(c["check"] + (f" ({c['detail']})" if c["detail"] else "") for c in bad[:5]) + "."
                         if bad else " The one check passed." if len(a["checks"]) == 1
                         else f" All {len(a['checks'])} checks passed." if a["checks"] else " No checks ran.")
    problems = [p for m in (b, a) if m for p in m.get("problems") or []] + ((comparison or {}).get("problems") or [])
    outcome_line += (" Problems: " + "; ".join(problems) + ".") if problems else ""
    lines += [outcome_line, ""]

    details = [f"proof `{committed}`" if committed else "proof written for this change (source below)",
               "builds " + (f"{build_public(b['build'])} → " if b else "") + build_public(a["build"])]
    details += [device] if device else []
    details += [f"live services: {', '.join(a.get('live') or []) or 'none'}"]
    if b:
        differs = [k for k in ("model", "api") if b.get(k) != a.get(k)]
        differs += ["proof source"] if b["proof"].get("source") != proof.get("source") else []
        details += [f"not like for like: the runs differ in {', '.join(differs)}"] if differs else []
    if comparison:
        report = (comparison["evidence_url"] if comparison.get("evidence_url") else "index.html")
    else:
        report = href(after, "index.html")
    lines += [f"**Details:** `prove.py{' compare' if comparison else ''}`, " + ", ".join(details) + f". [Full report]({report})", ""]
    if a.get("unverified"):
        lines += ["**Unverified:** " + "; ".join(f"{u['what']} ({u['why']})" for u in a["unverified"]) + ".", ""]

    if b:
        header = [f"| Check | {column(b['build'])} | {column(a['build'])} |", "|---|---|---|"]
        bp, ap = paired(b["checks"]), paired(a["checks"])
        keys = list(ap) + [k for k in bp if k not in ap]
        label = lambda k: k[0] if k[1] == 1 else f"{k[0]} ({k[1]})"
        lines += ["<details><summary>All checks</summary>", "", *header,
                  *[f"| {label(k)} | {cell(bp.get(k))} | {cell(ap.get(k))} |" for k in keys], "", "</details>"]
    else:
        lines += ["<details><summary>All checks</summary>", "", "| Check | Result |", "|---|---|",
                  *[f"| {c['check']} | {cell(c)} |" for c in a["checks"]], "", "</details>"]
    for run in (r for r in (b, a) if r):
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
              *rerun_lines(b, a, committed or saved), "```", "", "</details>"]
    return public("\n".join(lines) + "\n")


def pr_summary(*args):
    """Prints the Verification scenario for a compare folder, one run, or a before and an after run."""
    try:
        before, after, comparison = load_runs(args)
    except Error as e:
        print(f"ERROR {e}", file=sys.stderr)
        return 2
    print(summarize(before, after, comparison), end="")
    published = [r[0].get("evidence_url") for r in (before, after) if r] + ([comparison.get("evidence_url")] if comparison else [])
    if not all(published):
        print("NOTE not published: evidence links are local relative paths, which a PR can't open; "
              "run prove.py publish on the folder, then pr-summary again", file=sys.stderr)
    return 0


# --- selftest

def tiny_png(path, rgb, width=54, height=120):
    """A solid PNG with the standard library only, so the offline cases need no image tool."""
    raw = b"".join(b"\x00" + bytes(rgb) * width for _ in range(height))
    chunk = lambda kind, data: (struct.pack(">I", len(data)) + kind + data
                                + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff))
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def png_size(path):
    data = Path(path).read_bytes()[:24]
    return struct.unpack(">II", data[16:24]) if data[:8] == b"\x89PNG\r\n\x1a\n" else (0, 0)


def summary_fixtures(folder):
    """Attempt folders as prove() and compare() write them, with this machine's paths in every field that has one."""
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
    dev = "sdk_gphone64_arm64"

    def run(attempt, verdict, build, proof, checks, shots=("before-switcher", "switcher"), **extra):
        m = {"verdict": verdict, "error": None, "proof": proof, "device": "emulator-5580", "model": dev,
             "api": "34", "build": build, "revision": {"head": build.get("head", "0" * 10), "dirty_files": 2},
             "checks": [{"check": c, "ok": ok, "detail": d} for c, ok, d in checks],
             "live": [], "unverified": [], "notes": [f"saved in {home}"], "sections": [], "attempt": str(attempt),
             "device_dir": dev, "videos": [f"{dev}/recording-1.mp4"], "shots": [f"{dev}/{s}.png" for s in shots],
             "logs": [f"{dev}/ui.log"], "problems": [], "images": {"key_frames": f"{dev}/key-frames.png", "poster": f"{dev}/poster.png"},
             "poster_source": "the video's last frame", "finished": "2026-11-17T10:00:00",
             "exit": {"PASS": 0, "FAIL": 1}.get(verdict, 2), **extra}
        attempt.mkdir(parents=True)
        (attempt / "proof.json").write_text(json.dumps(m))
        return m

    checks = [("at the switcher", True, "TabSwitcherActivity"), ("card titled with the page", True, ""),
              ("tab count 2", True, "2")]
    compare_dir = folder / "20261117-100000-tabs-vs-base-ab12"
    before = run(compare_dir / "before", "FAIL", base, committed, checks[:1] + [("card titled with the page", False, "")] + checks[2:])
    after = run(compare_dir / "after", "PASS", branch, committed, checks)
    (compare_dir / "compare.json").write_text(json.dumps({
        "proof": {"name": "tabs", "doc": committed["doc"]}, "before": "before/proof.json", "after": "after/proof.json",
        "rows": check_rows(before, after), "before_after": "before-after.png", "problems": []}))
    run(folder / "error", "ERROR", branch, scratch, checks[:1], live=["duck.ai chat"],
        error=f"verify.sh tap id:close failed (1): not on screen in {REPO}\nscreen at the failure: {dev}/layout-at-failure.json",
        unverified=[{"what": "the undo snackbar", "why": "it hides before the layout can be read"}])
    run(folder / "external", "FAIL", external, committed, [("tab count 2", False, "1")])
    run(folder / "branch", "PASS", branch, committed, [("tab count 2", True, "2")])
    served = "https://artifacts.go.duckduckgo.com/view/auto/076c07cc-9e60-42df-8edb-53a6896bf5a4/20261117-100000-tabs-ab12"
    run(folder / "published", "PASS", branch, committed, checks, evidence_url=f"{served}/index.html")
    return compare_dir, served


def selftest_evidence(folder):
    """Draws a run's images and writes its pages from fixture captures: the offline half of what prove() leaves."""
    problems = []
    attempt = folder / "drawn"
    for i, name in enumerate(("before-tap", "after-tap")):
        tiny_png(attempt / "dev" / f"{name}.png", (40 * i, 120, 200))
    (attempt / "dev" / "recording-1.mp4").write_bytes(b"not a video")
    m = {"verdict": "FAIL", "error": None, "proof": {"name": "drawn", "doc": "A tap shows the next screen."},
         "model": "Pixel 7", "api": "36", "build": {}, "checks": [{"check": "ok", "ok": True, "detail": ""},
                                                                  {"check": "bad", "ok": False, "detail": "x"}],
         "unverified": [], "notes": [], "sections": [], "device_dir": "dev", "videos": ["dev/recording-1.mp4"],
         "shots": ["dev/before-tap.png", "dev/after-tap.png"], "logs": [], "problems": []}
    ev.draw_run(attempt, m)
    width, height = png_size(attempt / "dev" / ev.KEY_FRAMES)
    if not (width > 2 * 54 and height > ev.HEIGHT):
        problems.append(f"key-frames.png is {width}x{height}, not two labelled frames ({m['problems']})")
    if png_size(attempt / "dev" / ev.POSTER)[1] != ev.HEIGHT:
        problems.append(f"poster.png wasn't drawn from the last screenshot ({m['problems']})")
    write_run(attempt, m)
    report = (attempt / "report.md").read_text()
    order = [report.find(h) for h in ("## Verified", "## Evidence", "## Outcome", "## Details", "## Unverified")]
    if -1 in order or order != sorted(order):
        problems.append(f"report.md sections out of order: {order}")
    page = (attempt / "index.html").read_text()
    if "<video controls" not in page or page.find(">bad<") > page.find(">ok<"):
        problems.append("index.html lacks the video, or doesn't list failed checks first")
    if re.search(r"(src|href)=\"(https?:)?//", page):
        problems.append("index.html loads something from elsewhere")
    other = folder / "other"
    tiny_png(other / "after" / "dev" / "after-tap.png", (0, 0, 0))
    (other / "before").mkdir()
    shutil.copytree(attempt / "dev", other / "before" / "dev")
    image, problem = ev.draw_before_after(other, m, {**m, "shots": ["dev/after-tap.png"]}, ("Before", "After"))
    if not image or png_size(other / ev.BEFORE_AFTER)[1] < 2 * ev.HEIGHT:
        problems.append(f"before-after.png wasn't drawn as two rows: {problem}")
    return problems


def selftest(offline=False, evidence_dir=None):
    """Runs the offline cases (pr-summary, evidence folders, images and pages), then the tests/ proofs and a stale build,
    and checks each verdict. Exit 0 when all behave."""
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
        compare_dir, served = summary_fixtures(folder)
        leaks = (str(REPO), str(Path.home()), tmp, os.environ.get("USER", ""), "Downloads", "emulator-5580")
        results += [
            ("pr-summary of a compare folder is one auto scenario with Before | After", *case(
                ["pr-summary", compare_dir], 0,
                ("### The tab switcher lists one card per tab.", "**Verified:** The tab switcher lists one card per tab.",
                 "| | Before (base `origin/develop`) | After |", "| switcher | [<img src=\"before/sdk_gphone64_arm64/switcher.png\"",
                 "[▶ Watch video](after/sdk_gphone64_arm64/recording-1.mp4)", "before-after.png",
                 "**Outcome:** Fails on the base (1 of 3 checks); passes on this branch. Changed: card titled with the page: FAIL → pass.",
                 "**Details:** `prove.py compare`", "[Full report](index.html)", "`.agents/skills/verify-android/proofs/tabs.py`",
                 "<details><summary>All checks", "$P compare", "API 34", "NOTE not published"),
                leaks)),
            ("pr-summary of a published run links served thumbnails publish-pr can attach", *case(
                ["pr-summary", folder / "published"], 0,
                (f"[<img src=\"{served}/sdk_gphone64_arm64/switcher.png\" width=\"200\" alt=\"This branch: switcher\">]"
                 f"({served}/sdk_gphone64_arm64/switcher.png)", f"[▶ Watch video]({served}/sdk_gphone64_arm64/recording-1.mp4)",
                 f"[Full report]({served}/index.html)", f"Key frames: [This branch]({served}/sdk_gphone64_arm64/key-frames.png)"),
                (*leaks, "NOTE not published"))),
            ("pr-summary of an uncommitted proof that errored carries its source and no local paths", *case(
                ["pr-summary", folder / "error" / "proof.json"], 0,
                ("Couldn't run on this branch (verify.sh tap id:close failed (1): not on screen in <repo>)", "```python",
                 "save the proof source", "**Unverified:** the undo snackbar", "live services: duck.ai chat", "uncommitted changes"),
                (*leaks, "layout-at-failure"))),
            ("pr-summary marks an external APK", *case(
                ["pr-summary", folder / "external", folder / "branch"], 0,
                ("Fails on the external APK", "an external APK (sha256 `9c0ffee19c0f`)", "$V install --apk", "| | External APK | This branch |"),
                leaks)),
            ("pr-summary refuses a folder without a run", *case(["pr-summary", folder / "nothing"], 2, ("ERROR",))),
            ("an evidence dir inside the checkout is refused before anything runs", *case(
                ["--evidence-dir", REPO / "build" / "verify-selftest", tests / "harness.py"], 2, ("outside this checkout",), ("VERDICT",))),
            ("a decision log must be an existing .tsv", *case(
                ["compare", "--decision-log", folder / "decisions.md", tests / "harness.py"], 2, ("--decision-log",), ("VERDICT",))),
        ]
        drawn = selftest_evidence(folder)
        results.append(("an attempt gets key frames, a poster, report.md in scenario order and a self-contained index.html",
                        drawn, "; ".join(drawn)))
        if not offline:
            root = Path(evidence_dir).resolve() if evidence_dir else folder / "attempts"
            cases = [
                ("harness checks pass, an unverified item doesn't fail them, and the run is filmed", "harness.py", 0,
                 ("VERDICT PASS", "UNVERIFIED", "drew poster.png", "report.md, index.html"), ("PROBLEM",)),
                ("a wrong expectation is FAIL", "must_fail.py", 1, ("FAIL pixel m_verify_never_fired fired", "FAIL a log line that was never written"), ()),
                ("a broken step is ERROR, stops the run and saves the screen as a key frame", "must_error.py", 2,
                 ("VERDICT ERROR", "layout-at-failure.json", "drew key-frames.png"), ("never reached",)),
                ("a proof with no checks isn't a pass", "no_checks.py", 1, ("VERDICT FAIL",), ()),
                ("an unverified item is never a pass", "only_unverified.py", 1, ("VERDICT FAIL",), ()),
            ]
            for name, proof, code, say, not_say in cases:
                results.append((name, *case(["--evidence-dir", root, tests / proof], code, say, not_say)))
            attempts = [d for d in root.iterdir() if d.is_dir()] if root.is_dir() else []
            results.append(("every run wrote its own attempt folder",
                            [] if len(attempts) >= len(cases) else [f"{len(attempts)} attempt folders for {len(cases)} runs"], ""))
            # A source change after the build makes the installed app stale: the run must refuse to start.
            try:
                stale.write_text("verify selftest: makes the installed build stale; deleted when the selftest ends\n")
                results.append(("a stale build is ERROR before anything runs",
                                *case(["--evidence-dir", root, tests / "harness.py"], 2, ("stale build",), ("PASS",))))
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


def parse(args, flags):
    """Positional arguments and the given --flags (each taking a value, or None for a switch); None on a bad flag."""
    opts, pos, it = {}, [], iter(args)
    for a in it:
        if a in flags:
            opts[a] = True if flags[a] is None else next(it, None)
            if opts[a] is None:
                return None, None
        elif a.startswith("-"):
            return None, None
        else:
            pos.append(a)
    return opts, pos


def main(args):
    where = {"--evidence-dir": "", "--decision-log": ""}
    command = args[0] if args and args[0] in ("compare", "pr-summary", "publish", "selftest") else None
    opts, pos = parse(args[1:] if command else args, {
        "compare": where, None: where, "pr-summary": {}, "publish": {"--into": ""},
        "selftest": {"--offline": None, "--evidence-dir": ""}}[command])
    if opts is None:
        return __doc__
    if command == "pr-summary" and len(pos) in (1, 2):
        return pr_summary(*pos)
    if command == "publish" and len(pos) == 1:
        return publish(pos[0], opts.get("--into"))
    if command == "selftest" and not pos:
        return selftest(offline=bool(opts.get("--offline")), evidence_dir=opts.get("--evidence-dir"))
    if command in ("compare", None) and len(pos) == 1:
        try:
            root = evidence_root(opts.get("--evidence-dir"), opts.get("--decision-log"))
            return compare(pos[0], root) if command else prove(pos[0], new_attempt(root, Path(pos[0]).stem))["exit"]
        except Error as e:
            print(f"ERROR {e}")
            return 2
    return __doc__


if __name__ == "__main__":
    # Proofs import Error from here; let them share this module's class instead of loading a second copy.
    sys.modules.setdefault("prove", sys.modules[__name__])
    sys.exit(main(sys.argv[1:]))
