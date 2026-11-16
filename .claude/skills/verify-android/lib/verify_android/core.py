"""Environment, state, build identity and the device: everything below a proof."""
import datetime
import hashlib
import json
import os
import platform
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

SKILL = Path(__file__).resolve().parents[2]
REPO = Path(subprocess.check_output(["git", "-C", str(SKILL), "rev-parse", "--show-toplevel"], text=True).strip())

def _find_sdk():
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        if os.environ.get(var):
            return Path(os.environ[var])
    props = REPO / "local.properties"
    if props.exists():
        match = re.search(r"^sdk\.dir=(.+)$", props.read_text(), re.M)
        if match:
            return Path(match.group(1).strip().replace("\\:", ":"))
    for default in (Path.home() / "Library/Android/sdk", Path.home() / "Android/Sdk"):   # macOS, Linux
        if default.exists():
            return default
    return None


SDK = _find_sdk()
if SDK:
    os.environ["ANDROID_HOME"] = str(SDK)   # scripts/probe.sh and the SDK tools read it
    if not shutil.which("adb") and (SDK / "platform-tools/adb").exists():
        os.environ["PATH"] = f"{SDK / 'platform-tools'}{os.pathsep}{os.environ['PATH']}"
APP_ID = "com.duckduckgo.mobile.android.debug"
REPO_KEY = f"{REPO.name}-{hashlib.sha1(str(REPO).encode()).hexdigest()[:8]}"
STATE = Path(os.environ.get("VERIFY_ANDROID_HOME", Path.home() / ".ddg-verify-android")) / REPO_KEY
AVD = f"verify-android-{REPO_KEY}"
EMULATOR_PORT = 5600 + int(hashlib.sha1(REPO_KEY.encode()).hexdigest(), 16) % 40 * 2
RMEM_MAX = "/proc/sys/net/core/rmem_max"


class Error(Exception):
    """Setup or environment problem: exit 2, never a pass."""


def sdk():
    if not SDK:
        raise Error("Android SDK not found: set ANDROID_HOME, or sdk.dir in local.properties")
    return SDK


def require(tool, how):
    if not shutil.which(tool):
        raise Error(f"{tool} isn't on PATH: {how}")


def run(cmd, check=True, **kw):
    return subprocess.run(cmd, check=check, text=True, capture_output=True, **kw)


def adb(*args, check=True):
    return run(["adb", *args], check=check).stdout


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load(name):
    path = STATE / name
    return json.loads(path.read_text()) if path.exists() else None


def save(name, data):
    STATE.mkdir(parents=True, exist_ok=True)
    (STATE / name).write_text(json.dumps(data, indent=2) + "\n")


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat(timespec="seconds")


def java_version_required():
    """The version CI builds with (actions/setup-java reads the same file)."""
    pinned = REPO / ".github/.java-version"
    return int(pinned.read_text().strip()) if pinned.exists() else 21


def _java_major(java):
    version = re.search(r'version "(\d+)', run([java, "-version"], check=False).stderr) if java else None
    return int(version.group(1)) if version else None


def java_env():
    """The app build needs at least CI's JDK (21, for the Metro compiler plugin): fail here, not deep in Gradle.
    CI's exact version is preferred when macOS can find it."""
    required = java_version_required()
    env = dict(os.environ)
    if Path("/usr/libexec/java_home").exists():
        # java_home falls back to another JDK when the version isn't installed: only take it if it matches.
        home = run(["/usr/libexec/java_home", "-v", str(required)], check=False).stdout.strip()
        if home and _java_major(str(Path(home) / "bin/java")) == required:
            env["JAVA_HOME"] = home
    java = str(Path(env["JAVA_HOME"]) / "bin/java") if env.get("JAVA_HOME") else shutil.which("java")
    major = _java_major(java)
    if not major or major < required:
        raise Error(f"building needs JDK {required}, as in .github/.java-version "
                    f"(found {major or 'no java'}): set JAVA_HOME")
    if env.get("JAVA_HOME"):
        env["PATH"] = f"{env['JAVA_HOME']}/bin{os.pathsep}{env['PATH']}"
    return env


# --- build identity -------------------------------------------------------------------------------------------------

def source_fingerprint():
    """The committed tree and every uncommitted change, all outside .claude/, so editing or committing the skill
    doesn't make a build stale."""
    git = ["git", "-C", str(REPO)]
    head = run(git + ["rev-parse", "HEAD"]).stdout.strip()
    # Top-level tree entries hash everything below them, so this covers the whole tree outside .claude.
    tree = [line for line in run(git + ["ls-tree", "HEAD"]).stdout.splitlines() if line.split("\t", 1)[1] != ".claude"]
    h = hashlib.sha256("\n".join(tree).encode())
    h.update(subprocess.run(git + ["diff", "HEAD", "--binary", "--", ".", ":(exclude).claude"],
                            check=True, capture_output=True).stdout)
    untracked = [n for n in run(git + ["ls-files", "-z", "--others", "--exclude-standard", "--", ".", ":(exclude).claude"])
                 .stdout.split("\0") if n]
    for name in sorted(untracked):
        h.update(name.encode())
        h.update(sha256(REPO / name).encode())
    dirty = bool(run(git + ["status", "--porcelain", "--", ".", ":(exclude).claude"]).stdout.strip())
    return {"head": head, "dirty": dirty, "fingerprint": h.hexdigest()}


def assemble(kept_as, root=REPO):
    """Builds internalDebug and keeps a copy, so a later Gradle build can't change the APK a record points at."""
    subprocess.run(["./gradlew", "-q", ":app:assembleInternalDebug"], cwd=root, env=java_env(), check=True)
    # The file name carries the version, so APKs of earlier versions stay in the folder: the newest is this build.
    apks = sorted((root / "app/build/outputs/apk/internal/debug").glob("*.apk"), key=lambda p: p.stat().st_mtime)
    if not apks:
        raise Error("the build produced no internalDebug APK")
    kept_as.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(apks[-1], kept_as)
    return kept_as


def build():
    source = source_fingerprint()
    print("building :app:assembleInternalDebug ...", file=sys.stderr)
    staging = assemble(STATE / "builds" / "staging.apk")
    digest = sha256(staging)
    kept = staging.rename(staging.with_name(f"{digest[:16]}.apk"))
    record = {**source, "apk": str(kept), "sha256": digest, "built_at": now()}
    save("build.json", record)
    return record


def build_base(ref):
    """Builds `ref` (e.g. origin/develop) in a worktree the tool owns, for the "before" side of a comparison.

    One worktree is moved between base commits, so later base builds are incremental. APKs are kept per commit.
    """
    git = ["git", "-C", str(REPO)]
    sha = run(git + ["rev-parse", "--verify", f"{ref}^{{commit}}"]).stdout.strip()
    kept = STATE / "builds" / f"base-{sha[:12]}.apk"
    if not kept.exists():
        tree = STATE / "base-worktree"
        if not (tree / ".git").exists():
            run(git + ["worktree", "add", "--detach", str(tree), sha])
        else:
            # Nobody edits this worktree, so forcing the checkout can't lose work.
            run(["git", "-C", str(tree), "checkout", "--detach", "--force", sha])
        run(["git", "-C", str(tree), "submodule", "update", "--init", "--recursive"])
        if (REPO / "local.properties").exists():
            shutil.copyfile(REPO / "local.properties", tree / "local.properties")
        print(f"building {ref} ({sha[:10]}) in {tree} ...", file=sys.stderr)
        assemble(kept, root=tree)
    record = {"base": ref, "head": sha, "apk": str(kept), "sha256": sha256(kept), "built_at": now()}
    save("base.json", record)
    return record


def installed_sha256():
    path = adb("shell", "pm", "path", APP_ID, check=False).strip()
    if not path.startswith("package:"):
        return None
    out = adb("shell", "sha256sum", path.split(":", 1)[1].splitlines()[0], check=False).split()
    return out[0] if out else None


def install(apk=None, base=False):
    use_device()
    if base:
        record = load("base.json")
        if not record:
            raise Error("no base build: run `verify-android build --base <ref>` first")
        apk = Path(record["apk"])
    elif apk:
        apk = Path(apk)
        record = {"apk": str(apk.resolve()), "sha256": sha256(apk), "external": True}
    else:
        record = load("build.json")
        if not record:
            raise Error("no recorded build: run `verify-android build` first")
        if record["fingerprint"] != source_fingerprint()["fingerprint"]:
            raise Error("stale build: the source changed since `verify-android build`; build again")
        apk = Path(record["apk"])
    adb("install", "-r", "-d", str(apk))   # -d: a before/after pair can have either versionCode
    if installed_sha256() != record["sha256"]:
        raise Error("the installed APK doesn't match the one just installed")
    save(installed_record(), {**record, "serial": os.environ["ANDROID_SERIAL"], "installed_at": now()})
    return apk, record


def installed_record():
    """One record per device, so moving between the emulator and a phone doesn't force a reinstall."""
    return f"installed-{os.environ['ANDROID_SERIAL']}.json"


def check_installed():
    """The app on the device must be the recorded build of the current source, or a base or external APK installed
    explicitly (the evidence says which)."""
    record = load(installed_record())
    if not record:
        raise Error("nothing installed by verify-android on this device: run `verify-android install`")
    if installed_sha256() != record["sha256"]:
        raise Error("the app on the device isn't the APK verify-android installed; install again")
    if not (record.get("external") or record.get("base")) and record["fingerprint"] != source_fingerprint()["fingerprint"]:
        raise Error("stale build: the source changed since it was built; build and install again")
    return record


# --- device ---------------------------------------------------------------------------------------------------------

def emulator_serial():
    return f"emulator-{EMULATOR_PORT}"


def device_online(serial):
    require("adb", "install the SDK's platform-tools")
    return run(["adb", "-s", serial, "get-state"], check=False).stdout.strip() == "device"


def use_device():
    """An explicit ANDROID_SERIAL wins (e.g. a real device); otherwise the skill's own emulator, never whatever is attached."""
    serial = os.environ.get("ANDROID_SERIAL") or emulator_serial()
    if not device_online(serial):
        raise Error(f"device {serial} isn't running: `verify-android device start`, or set ANDROID_SERIAL")
    os.environ["ANDROID_SERIAL"] = serial
    return serial


def host_abi():
    """An emulator only runs at usable speed on the host's own architecture."""
    return "arm64-v8a" if platform.machine().lower() in ("arm64", "aarch64") else "x86_64"


def rootable_image():
    abi = host_abi()
    images = sorted(sdk().glob(f"system-images/android-*/google_apis/{abi}/system.img"),
                    key=lambda p: int(re.sub(r"\D", "", p.parts[-4]) or 0))
    if not images:
        raise Error("no rootable system image for this machine; install one, e.g. "
                    f"`sdkmanager \"system-images;android-34;google_apis;{abi}\"`")
    api, tag, abi = images[-1].parts[-4:-1]
    return f"system-images;{api};{tag};{abi}"


def start_emulator():
    serial = emulator_serial()
    if device_online(serial):
        return serial
    emulator = sdk() / "emulator/emulator"
    if not emulator.exists():
        raise Error("the SDK has no emulator: `sdkmanager emulator`")
    avds = run([str(emulator), "-list-avds"]).stdout.split()
    if AVD not in avds:
        subprocess.run([str(avdmanager()), "create", "avd", "-n", AVD, "-k", rootable_image(), "-d", "pixel_6"],
                       input="no\n", text=True, check=True, capture_output=True, env=java_env())
    STATE.mkdir(parents=True, exist_ok=True)
    with open(STATE / "emulator.log", "w") as log:
        subprocess.Popen([str(emulator), "-avd", AVD, "-port", str(EMULATOR_PORT),
                          "-no-window", "-no-audio", "-no-snapshot-save"],
                         stdout=log, stderr=log, start_new_session=True)
    deadline = time.time() + 300
    while time.time() < deadline:
        if device_online(serial) and run(["adb", "-s", serial, "shell", "getprop", "sys.boot_completed"],
                                         check=False).stdout.strip() == "1":
            break
        time.sleep(3)
    else:
        raise Error(f"{AVD} didn't boot in 5 minutes; see {STATE / 'emulator.log'}")
    run(["adb", "-s", serial, "root"])
    run(["adb", "-s", serial, "wait-for-device"])
    return serial


def avdmanager():
    tools = list(sdk().glob("cmdline-tools/*/bin/avdmanager"))
    if not tools:
        raise Error("the SDK has no cmdline-tools: `sdkmanager \"cmdline-tools;latest\"`")

    def version(path):   # "latest" wins, then the highest numeric version (so 11.0 beats 9.0)
        name = path.parts[-3]
        return name == "latest", [int(n) for n in re.findall(r"\d+", name)]

    return max(tools, key=version)


def stop_emulator():
    serial = emulator_serial()
    if device_online(serial):
        run(["adb", "-s", serial, "emu", "kill"])
    return serial


# --- device settings a run changes ----------------------------------------------------------------------------------

def _rmem_record():
    return STATE / f"rmem-max-{os.environ['ANDROID_SERIAL']}.json"


def set_rmem_max(value):
    """Remembers the device's own value before the first change, so a run killed mid-way is undone by the next one."""
    original = adb("shell", "cat", RMEM_MAX).strip()
    adb("shell", f"echo {value} > {RMEM_MAX}", check=False)
    if adb("shell", "cat", RMEM_MAX).strip() != str(value):
        raise Error(f"couldn't set rmem_max to {value}: the device needs root (`adb root`)")
    if not _rmem_record().exists():
        save(_rmem_record().name, {"original": original})


def restore_device_settings():
    """Returns a problem to report, or None. The record goes either way, so one failed restore can't block every
    later run on the device."""
    if not _rmem_record().exists():
        return None
    original = json.loads(_rmem_record().read_text())["original"]
    _rmem_record().unlink()
    adb("shell", f"echo {original} > {RMEM_MAX}", check=False)
    if adb("shell", "cat", RMEM_MAX, check=False).strip() != original:
        return f"couldn't restore rmem_max to {original}; reboot the device to reset it"
    return None


# --- cleanup --------------------------------------------------------------------------------------------------------

def _size(path):
    if path.is_file():
        return path.stat().st_size
    return sum(f.stat().st_size for f in path.rglob("*") if f.is_file() and not f.is_symlink())


def _remove(path):
    freed = _size(path)
    shutil.rmtree(path) if path.is_dir() else path.unlink()
    return freed


def cleanup(everything=False, evidence_days=30):
    """Removes what the tool created. By default: APKs no record points at, and evidence older than evidence_days.
    With everything: the base worktree, this checkout's emulator and all state too."""
    removed, freed = [], 0
    if everything:
        tree = STATE / "base-worktree"
        if tree.exists():
            freed += _size(tree)
            run(["git", "-C", str(REPO), "worktree", "remove", "--force", str(tree)], check=False)
            run(["git", "-C", str(REPO), "worktree", "prune"])
            removed.append("base worktree")
        if device_online(emulator_serial()):
            stop_emulator()
            time.sleep(3)
        if AVD in run([str(sdk() / "emulator/emulator"), "-list-avds"], check=False).stdout.split():
            run([str(avdmanager()), "delete", "avd", "-n", AVD], env=java_env())
            removed.append(f"emulator {AVD}")
        if STATE.exists():
            freed += _remove(STATE)
            removed.append(str(STATE))
        return {"removed": removed, "freed_mb": freed // (1 << 20)}

    records = [load("build.json"), load("base.json"), *(load(f.name) for f in STATE.glob("installed-*.json"))]
    keep = {Path(r["apk"]).name for r in records if r}
    for apk in sorted((STATE / "builds").glob("*.apk")):
        # The netguard 1.10.2 build takes minutes to make and only selftest --with-broken-build uses it.
        if apk.name not in keep and not apk.name.startswith("broken-"):
            freed += _remove(apk)
            removed.append(f"builds/{apk.name}")
    cutoff = time.time() - evidence_days * 86400
    for run_dir in sorted((STATE / "evidence").glob("*")):
        if run_dir.stat().st_mtime < cutoff:
            freed += _remove(run_dir)
            removed.append(f"evidence/{run_dir.name}")
    return {"removed": removed, "freed_mb": freed // (1 << 20)}
