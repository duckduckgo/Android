"""The `android` handle a proof acts and checks through, and `prove`, which runs a proof and writes its evidence."""
import datetime
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import time
import traceback
from pathlib import Path

from . import core
from .core import APP_ID, SKILL, Error, adb

PIXEL_LINE = re.compile(r"Pixel (?:sent|enqueued): (?:\S+ )?(\S+) with params")
SELECTOR_PAIR = re.compile(r"\s*(\w+)\s*[=:]\s*(/(?:\\.|[^/])*/i?|[^,]*)\s*(?:,|$)")
SELECTOR_FIELDS = {"text": "text", "txt": "text", "id": "resource-id", "res": "resource-id", "desc": "content-desc",
                   "content": "content-desc", "class": "class", "clazz": "class", "cls": "class", "clz": "class"}


def matches(target, node):
    """The CLI's selector matching for its string fields, for reading the layout: `android input` matches the same way."""
    pairs = SELECTOR_PAIR.findall(target)
    if not pairs:
        raise Error(f"not a selector: {target}")
    for key, value in pairs:
        if key not in SELECTOR_FIELDS:
            raise Error(f"visible()/wait_for() match text, id, desc and class, not {key}")
        actual = node.get(SELECTOR_FIELDS[key], "")
        if value.startswith("/"):
            pattern, flags = (value[1:-2], re.I) if value.endswith("/i") else (value[1:-1], 0)
            if not re.search(pattern.replace("\\/", "/"), actual, flags):
                return False
        elif not (actual if value == "*" else value.lower() in actual.lower()):
            return False
    return True


class Android:
    def __init__(self, serial, evidence, params):
        self.serial = serial
        self.evidence = evidence
        self.params = params
        self.model = adb("shell", "getprop", "ro.product.model").strip()
        self.api = adb("shell", "getprop", "ro.build.version.sdk").strip()
        self.checks, self.notes, self.unverified_items, self.sections = [], [], [], []
        self._mark = None
        self._probe_installed = False
        self._ui_ready = False

    # --- checks and evidence

    def check(self, what, ok, detail=""):
        if isinstance(detail, dict):
            detail = ", ".join(f"{k}={v}" for k, v in detail.items())
        self.checks.append({"check": what, "ok": bool(ok), "detail": str(detail)})
        return bool(ok)

    def note(self, text):
        self.notes.append(text)

    def unverified(self, what, why):
        self.unverified_items.append({"what": what, "why": why})

    def section(self, title, markdown):
        """Extra report content, e.g. a table of what the proof measured."""
        self.sections.append((title, markdown))

    def save(self, name, text):
        (self.evidence / name).write_text(text)

    def shot(self, name):
        png = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True).stdout
        (self.evidence / f"{name}.png").write_bytes(png)

    def param(self, name, default=None):
        return self.params.get(name, default)

    # --- device

    def shell(self, *cmd, check=True):
        return adb("shell", *cmd, check=check)

    def broadcast(self, action, **extras):
        args = [arg for key, value in extras.items() for arg in ("--es", key, str(value))]
        self.shell("am", "broadcast", "-a", action, *args)

    def tunnel_up(self):
        return bool(re.search(r"\btun\d+:", self.shell("ip", "-o", "link")))

    # --- the UI, through the Android CLI (https://developer.android.com/tools/agents/android-cli)
    # A target is the CLI's selector: comma-separated key=value pairs; strings match as case-insensitive substrings,
    # /regex/ matches exactly what it says. `id=skipOnboardingButton` works on the .debug package as on release.

    def _android(self, *args):
        if not self._ui_ready:
            core.require("android", "install the Android CLI (https://developer.android.com/tools/agents/android-cli)")
            locale = self.shell("getprop", "persist.sys.locale").strip() or self.shell("getprop", "ro.product.locale").strip()
            if locale and not locale.startswith("en"):
                raise Error(f"UI targets match English text; set the device language to English (it's {locale})")
            self._ui_ready = True
        result = core.run(["android", "--no-metrics", *args, f"--device={self.serial}"], check=False)
        with open(self.evidence / "ui.log", "a") as log:
            log.write(f"$ android {' '.join(args)}\n" + (result.stdout + result.stderr if result.returncode else ""))
        return result

    def _input(self, *args):
        result = self._android("input", *args)
        if result.returncode != 0:
            self.save("layout-at-failure.json", json.dumps(self.layout(), indent=1))
            errors = re.findall(r'"error": "([^"]*)"', result.stdout) or [result.stderr.strip()]
            raise Error(f"android input {' '.join(args)} failed: {'; '.join(errors)}; see ui.log and layout-at-failure.json")

    def launch(self, allow_notifications=True):
        """Starts the app. Its notification permission is granted first unless a proof is about that prompt."""
        if allow_notifications and int(self.api) >= 33:
            self.shell("pm", "grant", APP_ID, "android.permission.POST_NOTIFICATIONS")
        # Named, because debug builds have a second launcher activity (LeakCanary's).
        self.shell("am", "start", "-W", "-n", f"{APP_ID}/com.duckduckgo.app.launch.Launcher")

    def tap(self, target, wait=10, first=False):
        """Taps the element, waiting up to `wait` seconds for it; `first` picks the first of several matches."""
        self._input("tap", target, f"--wait={wait * 1000}", *(["--match=first"] if first else []))

    def type(self, text, target, submit=False, wait=10):
        self._input("text", text, target, f"--wait={wait * 1000}", *(["--submit"] if submit else []))

    def scroll_to(self, target, container="scrollable=true", direction="down", wait=10):
        self._input("scroll", container, f"--until={target}", f"--dir={direction}", f"--wait={wait * 1000}",
                    "--match=first")

    def key(self, key):
        """back, home, recents, enter, tab, esc, or an Android keycode."""
        self._input("key", key)

    def hide_keyboard(self):
        if any(window["type"] == "INPUT_METHOD" for window in self._windows()):
            self.key("back")

    def _windows(self):
        # Animated screens (AppTP's enabled screen) never go idle; wait_for() polls instead.
        result = self._android("layout", "--flat", "--no-idle")
        if result.returncode != 0:
            raise Error(f"android layout failed: {result.stderr.strip()}")
        return json.loads(result.stdout)

    def layout(self):
        """Every element on screen, in every window (app, system dialogs, keyboard), as the CLI reports it."""
        return [node for window in self._windows() for node in window["content"]]

    def visible(self, target):
        return any(matches(target, node) for node in self.layout())

    def wait_for(self, *targets, timeout=30):
        """The first of `targets` to appear within `timeout` seconds, or None."""
        deadline = time.time() + timeout
        while True:
            nodes = self.layout()
            for target in targets:
                if any(matches(target, node) for node in nodes):
                    return target
            if time.time() > deadline:
                return None
            time.sleep(0.5)

    def expect_visible(self, what, target, timeout=30):
        shown = self.wait_for(target, timeout=timeout) is not None
        if not shown:
            self.save("layout-at-failure.json", json.dumps(self.layout(), indent=1))
        return self.check(what, shown, "" if shown else f"{target} not on screen after {timeout}s; see layout-at-failure.json")

    # --- logs and telemetry

    def mark(self):
        """Logs and telemetry count from here: call it right before the action under test."""
        self._mark = self.shell("date", "+%s.%N").strip()[:14]
        return self._mark

    def logs(self, since=None):
        since = since or self._mark
        if not since:
            raise Error("logs() needs a mark: call android.mark() before the action")
        return adb("logcat", "-d", "-T", since)

    def pixels(self, since=None):
        return PIXEL_LINE.findall(self.logs(since))

    def expect_log(self, what, pattern, since=None):
        return self.check(what, re.search(pattern, self.logs(since)))

    def expect_pixel(self, name, since=None):
        """Debug builds log every pixel they fire (and send it as a dev pixel); this reads those lines."""
        fired = self.pixels(since)
        return self.check(f"pixel {name} fired", name in fired, "fired" if name in fired else f"not among {sorted(set(fired))}")

    def expect_no_pixel(self, name, since=None):
        fired = self.pixels(since)
        return self.check(f"pixel {name} not fired", name not in fired, "fired" if name in fired else "")

    # --- the probe app

    def probe(self, hosts, mode="single", timeout_ms=3000):
        """Connects to each host as a third-party app and returns {host: outcome}; see probe/ProbeActivity.java."""
        if not self._probe_installed:
            subprocess.run([str(SKILL / "scripts/probe.sh"), "install"], check=True, capture_output=True,
                           env=core.java_env())
            self._probe_installed = True
        out = core.run([str(SKILL / "scripts/probe.sh"), "run", ",".join(hosts), mode, str(timeout_ms)], check=False)
        if out.returncode != 0:
            raise Error(f"probe run incomplete ({mode}): {out.stderr.strip()}")
        results = {}
        for line in out.stdout.splitlines():
            if line.startswith("RESULT"):
                fields = dict(f.split("=", 1) for f in line.split()[1:] if "=" in f)
                results[fields["host"]] = fields["outcome"]
        missing = [h for h in hosts if h not in results]
        if missing:
            raise Error(f"probe returned no result for {missing} in {mode}; rerun")
        if {"CONNECT_TIMEOUT", "CONNECT_FAILED"} & set(results.values()):
            raise Error(f"connection never opened in {mode} ({results}): the network, not the app; rerun")
        return results


class Recording:
    """Records the screen for the whole run, as 3-minute clips (screenrecord's limit): video-1.mp4, video-2.mp4, ..."""
    DIR = "/sdcard/verify-android-video"

    def __init__(self, evidence):
        self.evidence = evidence
        adb("shell", "rm", "-rf", self.DIR, check=False)
        adb("shell", "mkdir", "-p", self.DIR)
        # Each clip ends at the time limit or on SIGINT; the stop file ends the loop.
        loop = (f"i=1; while [ ! -f {self.DIR}/stop ]; do "
                f"screenrecord --bit-rate 2000000 --time-limit 180 {self.DIR}/video-$i.mp4; i=$((i+1)); done")
        self.process = subprocess.Popen(["adb", "shell", loop], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def stop(self):
        """Saves the clips to the evidence and returns their names; a recording that failed never fails the run."""
        adb("shell", "touch", f"{self.DIR}/stop", check=False)
        adb("shell", "pkill", "-INT", "screenrecord", check=False)
        try:
            self.process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            self.process.kill()
        clips = sorted(adb("shell", "ls", self.DIR, check=False).split(), key=lambda f: (len(f), f))
        saved = []
        for clip in [c for c in clips if c.endswith(".mp4")]:
            if core.run(["adb", "pull", f"{self.DIR}/{clip}", str(self.evidence / clip)], check=False).returncode == 0:
                saved.append(clip)
        adb("shell", "rm", "-rf", self.DIR, check=False)
        return saved


def load_proof(path):
    if not Path(path).is_file():
        raise Error(f"no proof at {path}")
    spec = importlib.util.spec_from_file_location(Path(path).stem.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception:
        raise Error(f"{path} doesn't load:\n{traceback.format_exc(limit=1)}")
    if not (module.__doc__ or "").strip():
        raise Error(f"{path}: a proof starts with a docstring saying what it proves")
    if not callable(getattr(module, "run", None)):
        raise Error(f"{path}: a proof defines run(android)")
    return module


def prove(path, params, rmem_max=None, rerun=""):
    serial = core.use_device()
    installed = core.check_installed()
    proof = load_proof(path)
    stamp = datetime.datetime.now().strftime("%Y%m%d-%H%M%S")
    evidence = core.STATE / "evidence" / f"{stamp}-{Path(path).stem}"
    evidence.mkdir(parents=True)
    shutil.copyfile(path, evidence / "proof.py")
    android = Android(serial, evidence, params)

    error = None
    problems = [p for p in [core.restore_device_settings()] if p]
    recording = Recording(evidence)
    try:
        if rmem_max:
            core.set_rmem_max(rmem_max)
        android.rmem_max = adb("shell", "cat", core.RMEM_MAX).strip()
        proof.run(android)
    except Error as e:
        error = str(e) or "Error"
    except Exception:
        error = "the proof raised:\n" + traceback.format_exc()
    finally:
        problems += [p for p in [core.restore_device_settings()] if p]
    android.shot("final")
    videos = recording.stop()

    if error is not None or problems:
        verdict = "ERROR"
    elif android.checks and all(c["ok"] for c in android.checks):
        verdict = "PASS"
    else:
        verdict = "FAIL"
    manifest = {
        "verdict": verdict,
        "error": error or ("; ".join(problems) if problems else None),
        "problems": problems,
        "proof": {"path": str(Path(path).resolve()), "doc": proof.__doc__.strip(), "params": params,
                  "rmem_max": rmem_max},
        "live": getattr(proof, "LIVE", []),
        "device": {"serial": serial, "model": android.model, "api": android.api,
                   "rmem_max": getattr(android, "rmem_max", None)},
        "build": installed,
        "source_now": core.source_fingerprint(),
        "checks": android.checks,
        "notes": android.notes,
        "unverified": android.unverified_items,
        "sections": android.sections,
        "videos": videos,
        "rerun": rerun,
    }
    (evidence / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    (evidence / "report.md").write_text(report(manifest, android.sections))
    print(json.dumps({"verdict": verdict, "evidence": str(evidence), **({"error": error} if error else {})}, indent=2))
    return {"PASS": 0, "FAIL": 1}.get(verdict, 2)


def report(m, sections):
    b, d = m["build"], m["device"]
    if b.get("base"):
        origin = f"base {b['base']} ({b['head'][:10]})"
    elif b.get("external"):
        origin = "external APK"
    else:
        origin = f"this branch, {b['head'][:10]}{' (dirty)' if b['dirty'] else ''}"
    lines = [
        f"# {m['proof']['doc'].splitlines()[0]}",
        "",
        f"**{m['verdict']}**" + (f": {m['error'].splitlines()[0]}" if m["error"] else ""),
        "",
        f"- Device: {d['model']} (API {d['api']}, {d['serial']}), rmem_max {d['rmem_max']}",
        f"- Build: {origin}, APK sha256 {b['sha256'][:16]}",
        f"- Live services: {', '.join(m['live']) or 'none'}",
        f"- Video: {', '.join(m['videos']) or 'none recorded'}",
        f"- Rerun: `{m['rerun']}`",
        "",
        "## Checks",
        "",
        "| | Check | Detail |",
        "|---|---|---|",
        *[f"| {'✅' if c['ok'] else '❌'} | {c['check']} | {c['detail']} |" for c in m["checks"]],
    ]
    if m["unverified"]:
        lines += ["", "## Unverified", "", *[f"- {u['what']}: {u['why']}" for u in m["unverified"]]]
    if m["notes"]:
        lines += ["", "## Notes", "", *[f"- {n}" for n in m["notes"]]]
    for title, markdown in sections:
        lines += ["", f"## {title}", "", markdown]
    return "\n".join(lines) + "\n"
