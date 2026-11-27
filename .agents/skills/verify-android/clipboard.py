#!/usr/bin/env python3
"""Transfer text between explicit Android emulators without printing clipboard contents.

get(serial) returns str, put(serial, value) accepts str, and transfer(source, target)
returns None after exact destination readback. Empty text clears the clipboard.
Operations briefly foreground the helper and then return to the previous activity.
"""

import argparse
import contextlib
import fcntl
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import zipfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
PACKAGE = "dev.verifyandroid.clipboardbridge"
CACHE = REPO / "build/verify/clipboard"


def _sdk():
    for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(name):
            return Path(os.environ[name])
    props = REPO / "local.properties"
    if props.exists():
        match = re.search(r"^sdk\.dir=(.+)$", props.read_text(), re.MULTILINE)
        if match:
            return Path(match[1].replace("\\ ", " ").replace("\\:", ":"))
    return Path.home() / "Library/Android/sdk"


def _run(args, *, data=None, timeout=60):
    try:
        result = subprocess.run(list(map(str, args)), input=data, capture_output=True, timeout=timeout)
    except (OSError, subprocess.TimeoutExpired):
        raise RuntimeError("Clipboard bridge command could not complete") from None
    if result.returncode:
        raise RuntimeError("Clipboard bridge command failed")
    return result.stdout


def _adb(serial, *args, data=None):
    return _run([_sdk() / "platform-tools/adb", "-s", serial, *args], data=data)


def _validate(serial):
    if not re.fullmatch(r"emulator-\d+", serial):
        raise RuntimeError("Clipboard bridge requires an explicit emulator-<port> serial")
    if _adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("Clipboard bridge is restricted to Android emulators")


@contextlib.contextmanager
def _lock(name):
    CACHE.mkdir(parents=True, exist_ok=True)
    with (CACHE / name).open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        yield


def _latest(paths):
    stable = [p for p in paths if re.fullmatch(r"(?:android-)?\d+(?:\.\d+)*", p.name)]
    if not stable:
        raise RuntimeError("Clipboard bridge needs installed Android SDK build-tools and a platform")
    return max(stable, key=lambda p: tuple(map(int, re.findall(r"\d+", p.name))))


def _apk():
    sources = [HERE / "clipboard.py", HERE / "clipboard/BridgeActivity.java", HERE / "clipboard/AndroidManifest.xml"]
    digest = hashlib.sha256(b"".join(p.read_bytes() for p in sources)).hexdigest()[:20]
    apk = CACHE / ("bridge-" + digest + ".apk")
    with _lock("build.lock"):
        if apk.is_file():
            return apk
        sdk = _sdk()
        bt = _latest((sdk / "build-tools").iterdir())
        platform = _latest((sdk / "platforms").iterdir()) / "android.jar"
        java = Path(os.environ.get("JAVA_HOME_21", "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")) / "bin"
        key = Path.home() / ".android/debug.keystore"
        if not key.is_file():
            raise RuntimeError("Build the app once to create the Android debug keystore")
        with tempfile.TemporaryDirectory(prefix="build-", dir=CACHE) as folder:
            out = Path(folder)
            (out / "classes").mkdir()
            (out / "dex").mkdir()
            _run([java / "javac", "-source", "17", "-target", "17", "-cp", platform,
                  "-d", out / "classes", sources[1]])
            _run([java / "jar", "cf", out / "classes.jar", "-C", out / "classes", "."])
            _run([bt / "d8", "--lib", platform, "--min-api", "26", "--output", out / "dex", out / "classes.jar"])
            _run([bt / "aapt2", "link", "-I", platform, "--manifest", sources[2], "-o", out / "unaligned.apk"])
            with zipfile.ZipFile(out / "unaligned.apk", "a") as archive:
                archive.write(out / "dex/classes.dex", "classes.dex")
            _run([bt / "zipalign", "-f", "4", out / "unaligned.apk", out / "bridge.apk"])
            _run([bt / "apksigner", "sign", "--ks", key, "--ks-pass", "pass:android",
                  "--ks-key-alias", "androiddebugkey", out / "bridge.apk"])
            _run([bt / "apksigner", "verify", out / "bridge.apk"])
            (out / "bridge.apk").replace(apk)
    return apk


def _install(serial):
    apk = _apk()
    path = _adb(serial, "shell", "pm", "path", PACKAGE).decode().strip()
    if path.startswith("package:"):
        actual = _adb(serial, "shell", "sha256sum", path.removeprefix("package:")).split()[0]
        if actual.decode() == hashlib.sha256(apk.read_bytes()).hexdigest():
            return
    _adb(serial, "install", "-r", apk)


def _operation(serial, action, value=None):
    _validate(serial)
    with _lock(serial + ".lock"):
        _install(serial)
        _adb(serial, "shell", "run-as", PACKAGE, "mkdir", "-p", "files")
        _adb(serial, "shell", "run-as", PACKAGE, "rm", "-f", "files/status", "files/value")
        try:
            if value is not None:
                _adb(serial, "shell", "run-as " + PACKAGE + " sh -c 'umask 077; cat > files/value'", data=value.encode("utf-8"))
            _adb(serial, "shell", "am", "start", "-W", "-n", PACKAGE + "/.BridgeActivity", "--es", "command", action)
            deadline = time.monotonic() + 10
            while time.monotonic() < deadline:
                status = _adb(serial, "shell", "run-as " + PACKAGE + " sh -c 'cat files/status 2>/dev/null || true'").strip()
                if status == b"ok":
                    if action == "read":
                        return _adb(serial, "exec-out", "run-as", PACKAGE, "cat", "files/value").decode("utf-8")
                    return None
                if status:
                    raise RuntimeError("Foreground clipboard operation failed; only single-item text clips are supported")
                time.sleep(0.1)
            raise RuntimeError("Foreground clipboard operation timed out")
        finally:
            _adb(serial, "shell", "run-as", PACKAGE, "rm", "-f", "files/value", "files/status")


def get(serial):
    """Read a single text clip; return empty text when the clipboard is empty."""
    return _operation(serial, "read")


def put(serial, value):
    """Set text through stdin and private files, or clear when value is empty."""
    if not isinstance(value, str):
        raise TypeError("Clipboard value must be str")
    _operation(serial, "write" if value else "clear", value if value else None)


def transfer(source, target):
    """Copy a nonempty text clip and verify the destination without displaying it."""
    if source == target:
        raise RuntimeError("Clipboard transfer requires two different emulator serials")
    _validate(source)
    _validate(target)
    value = get(source)
    if not value:
        raise RuntimeError("Source Android clipboard is empty")
    put(target, value)
    if get(target) != value:
        raise RuntimeError("Android clipboard transfer was not preserved")


def _test(source, target):
    if source == target:
        raise RuntimeError("Clipboard test requires two different emulator serials")
    originals = {serial: get(serial) for serial in (source, target)}
    try:
        for start, end in ((source, target), (target, source)):
            put(end, "Destination awaiting transfer")
            value = "Verification clipboard: 'quotes' & symbols\nUnicode: Żółw 🦆\n" + "0123456789" * 512
            put(start, value)
            if get(start) != value:
                raise RuntimeError("Source guest clipboard readback failed")
            transfer(start, end)
            if get(end) != value:
                raise RuntimeError("Destination guest clipboard readback failed")
            print("PASS: exact guest clipboard transfer " + start + " to " + end)
        for serial in originals:
            put(serial, "")
            if get(serial) != "":
                raise RuntimeError("Guest clipboard clear failed")
        print("PASS: guest clipboards cleared")
    finally:
        failures = []
        for serial, value in originals.items():
            try:
                put(serial, value)
                if get(serial) != value:
                    raise RuntimeError("Clipboard restoration readback failed")
            except Exception:
                failures.append(serial)
        if failures:
            raise RuntimeError("Could not restore original clipboard on " + ", ".join(failures))
        print("PASS: original guest clipboard text restored")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    for name in ("test", "copy"):
        command = commands.add_parser(name)
        command.add_argument("source")
        command.add_argument("target")
    command = commands.add_parser("clear")
    command.add_argument("serials", nargs="+")
    args = parser.parse_args()
    try:
        if args.command == "test":
            _test(args.source, args.target)
        elif args.command == "copy":
            transfer(args.source, args.target)
            print("PASS: destination guest clipboard matches source")
        else:
            for serial in args.serials:
                _validate(serial)
            for serial in args.serials:
                put(serial, "")
                if get(serial) != "":
                    raise RuntimeError("Guest clipboard clear failed")
            print("PASS: guest clipboards cleared")
    except (RuntimeError, OSError, UnicodeError) as error:
        # Never include subprocess output or clipboard values in diagnostics.
        print("clipboard: " + (str(error) if isinstance(error, RuntimeError) else "operation failed"), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
