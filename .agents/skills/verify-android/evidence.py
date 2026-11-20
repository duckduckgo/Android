"""What an attempt folder holds besides the raw captures: PR-ready images, report.md and index.html.

Every path in a run's proof.json, report.md and index.html is relative to its attempt folder, so the folder can be
copied or published as a whole and its pages keep working.
"""
import html
import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from urllib.parse import quote

SKILL = Path(__file__).resolve().parent
COMPOSE = SKILL / "Compose.java"
KEY_FRAMES, POSTER, BEFORE_AFTER = "key-frames.png", "poster.png", "before-after.png"
GENERATED = {KEY_FRAMES, POSTER}
HEIGHT = 720  # legible at full size, small enough to upload


def java():
    """The JDK verify.sh builds with, so drawing needs nothing an Android build doesn't."""
    for home in (os.environ.get("JAVA_HOME_21"), "/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home",
                 os.environ.get("JAVA_HOME")):
        if home and (Path(home) / "bin" / "java").is_file():
            return str(Path(home) / "bin" / "java")
    return shutil.which("java")


def compose(jobs):
    """Runs Compose.java over the jobs (lists of tab-separated fields); returns {out path: why} for images not drawn."""
    jobs = [[str(x) for x in j] for j in jobs]
    wanted = [j[1] for j in jobs if j[0] in ("grid", "poster")]
    if not wanted:
        return {}
    exe = java()
    if not exe:
        return {out: "no java found (JDK 21 at JAVA_HOME_21 or on PATH)" for out in wanted}
    with tempfile.NamedTemporaryFile("w", suffix=".tsv", delete=False) as f:
        f.write("".join("\t".join(map(str, j)) + "\n" for j in jobs))
    try:
        p = subprocess.run([exe, "-Djava.awt.headless=true", str(COMPOSE), f.name],
                           capture_output=True, text=True, timeout=180)
    except subprocess.TimeoutExpired:
        return {out: "Compose.java took over 180 s" for out in wanted}
    finally:
        os.unlink(f.name)
    done, failed = set(), {}
    for line in p.stdout.splitlines():
        parts = line.split("\t")
        if parts[0] == "ok":
            done.add(parts[1])
        elif parts[0] == "fail":
            failed[parts[1]] = parts[2] if len(parts) > 2 else "failed"
    detail = ((p.stderr or "").strip().splitlines() or [f"exit {p.returncode}"])[-1]
    return {out: failed.get(out, detail) for out in wanted if out not in done}


def last_frame(video, out):
    """The video's last frame as a PNG with ffmpeg; None when ffmpeg is missing or can't read it."""
    ffmpeg = shutil.which("ffmpeg")
    if not ffmpeg or not Path(video).is_file():
        return None
    # -update 1 keeps overwriting one file, so what's left is the last frame decoded after the seek.
    for seek in (["-sseof", "-1"], []):
        p = subprocess.run([ffmpeg, "-v", "error", "-y", *seek, "-i", str(video), "-update", "1", str(out)],
                           capture_output=True, timeout=60)
        if p.returncode == 0 and Path(out).is_file() and Path(out).stat().st_size:
            return out
    return None


def draw_run(attempt, m):
    """key-frames.png and poster.png in the run's device folder. Records them in m["images"]; an image that couldn't be
    drawn goes to m["problems"], never to the verdict."""
    attempt = Path(attempt)
    shots = [s for s in m.get("shots") or [] if (attempt / s).is_file()]
    device = m.get("device_dir")
    m.setdefault("images", {})
    if not device:
        return
    jobs, wanted, tmp = [], {}, None
    if shots:
        out = attempt / device / KEY_FRAMES
        jobs += [("grid", out, HEIGHT), ("row", "")] + [("cell", Path(s).stem, attempt / s) for s in shots]
        wanted[out] = "key_frames"
    source, m["poster_source"] = None, None
    if m.get("videos"):
        tmp = Path(tempfile.mkdtemp())
        source = last_frame(attempt / m["videos"][-1], tmp / "last.png")
        m["poster_source"] = "the video's last frame" if source else None
    if not source and shots:
        source = attempt / shots[-1]
        m["poster_source"] = "the last screenshot" + (" (ffmpeg not found)" if m.get("videos") and not shutil.which("ffmpeg") else "")
    if source:
        out = attempt / device / POSTER
        jobs.append(("poster", out, source, HEIGHT))
        wanted[out] = "poster"
    failed = compose(jobs)
    if tmp:
        shutil.rmtree(tmp, ignore_errors=True)
    for out, key in wanted.items():
        if str(out) in failed:
            m.setdefault("problems", []).append(f"{out.name} not drawn: {failed[str(out)]}")
        else:
            m["images"][key] = str(out.relative_to(attempt))
    if not source and m.get("videos"):
        m.setdefault("problems", []).append(f"{POSTER} not drawn: no frame from the video and no screenshot")


def shot_names(*runs):
    """Screenshot names (no folder, no .png) in the after run's order, then names only the earlier runs took."""
    names = []
    for run in reversed(runs):
        for s in run.get("shots") or []:
            if Path(s).stem not in names:
                names.append(Path(s).stem)
    return names


def shot_path(run, name):
    return next((s for s in run.get("shots") or [] if Path(s).stem == name), None)


def draw_before_after(folder, before, after, labels):
    """before-after.png: the base run's key frames above this checkout's, paired by screenshot name."""
    folder = Path(folder)
    names = shot_names(before, after)
    if not names:
        return None, None
    out = folder / BEFORE_AFTER
    jobs = [("grid", out, HEIGHT)]
    for side, run, label in (("before", before, labels[0]), ("after", after, labels[1])):
        jobs.append(("row", label))
        for n in names:
            s = shot_path(run, n)
            jobs.append(("cell", n, folder / side / s if s else ""))
    failed = compose(jobs)
    return (None, f"{BEFORE_AFTER} not drawn: {failed[str(out)]}") if failed else (BEFORE_AFTER, None)


def headline(error):
    lines = [l.strip() for l in (error or "error").splitlines() if l.strip()] or ["error"]
    # A proof that raised carries a traceback, whose last line is the exception itself.
    return lines[-1] if lines[0].startswith("the proof raised") else lines[0]


def claim(m):
    return ((m["proof"].get("doc") or "").strip().splitlines() or [m["proof"].get("name") or "proof"])[0]


def device_name(m):
    return ", ".join(x for x in (m.get("model"), f"API {m['api']}" if m.get("api") else "") if x) or "device not read"


def verdict_line(m):
    checks = m.get("checks") or []
    passed = sum(c["ok"] for c in checks)
    tail = f": {headline(m['error'])}" if m.get("error") else ""
    return f"**{m['verdict']}**, {passed} of {len(checks)} checks passed{tail}"


def link(rel, text=None):
    return f"[{text or Path(rel).name}]({quote(rel)})"


def report_md(m, details):
    """report.md in the order a PR scenario presents it: Verified, Evidence, Outcome, Details, Unverified."""
    out = [f"# {claim(m)}", "", verdict_line(m), "", "## Verified", "", (m["proof"].get("doc") or "").strip() or "No claim.", ""]
    out += ["## Evidence", ""]
    if m.get("device_dir"):
        imgs = m.get("images") or {}
        out += [f"{device_name(m)}:", ""]
        out += [f"- Video: " + ", ".join(link(v) for v in m["videos"])] if m.get("videos") else ["- Video: none"]
        out += [f"- Key frames: {link(imgs['key_frames'])}"] if imgs.get("key_frames") else []
        out += [f"- Poster: {link(imgs['poster'])} ({m.get('poster_source')})"] if imgs.get("poster") else []
        out += ["- Screenshots: " + ", ".join(link(s, Path(s).stem) for s in m["shots"])] if m.get("shots") else []
        out += ["- Logs: " + ", ".join(link(l) for l in m.get("logs") or [])] if m.get("logs") else []
        out += [f"- {link('index.html')} plays the video inline", ""]
        out += [f"![Key frames]({quote(imgs['key_frames'])})", ""] if imgs.get("key_frames") else []
    else:
        out += ["None: the run stopped before it reached the device.", ""]
    out += ["## Outcome", "", "| Check | Result | Detail |", "|---|---|---|"]
    out += [f"| {c['check']} | {'pass' if c['ok'] else 'FAIL'} | {str(c['detail']).replace('|', chr(92) + '|')} |"
            for c in m.get("checks") or []] or ["| no checks ran | | |"]
    if m.get("error"):
        out += ["", "Error:", "", "```", m["error"].rstrip(), "```"]
    if m.get("problems"):
        out += ["", "Problems:", "", *[f"- {p}" for p in m["problems"]]]
    if m.get("notes"):
        out += ["", "Notes:", "", *[f"- {n}" for n in m["notes"]]]
    out += ["", "## Details", "", *[f"- {d}" for d in details], "", "## Unverified", ""]
    out += [f"- {u['what']}: {u['why']}" for u in m.get("unverified") or []] or ["Nothing the proof reported."]
    for title, body in m.get("sections") or []:
        out += ["", f"## {title}", "", body]
    return "\n".join(out) + "\n"


STYLE = """
:root { color-scheme: light dark; --bg: #fff; --fg: #1f2328; --muted: #59636e; --line: #d1d9e0; --card: #f6f8fa;
  --link: #0969da; --pass: #1a7f37; --fail: #cf222e; }
@media (prefers-color-scheme: dark) { :root { --bg: #0d1117; --fg: #e6edf3; --muted: #9198a1; --line: #3d444d;
  --card: #151b23; --link: #4493f8; --pass: #3fb950; --fail: #f85149; } }
body { margin: 0; background: var(--bg); color: var(--fg); font: 15px/1.5 -apple-system, system-ui, sans-serif; }
main { max-width: 1200px; margin: 0 auto; padding: 24px 16px 64px; }
a { color: var(--link); }
h1 { font-size: 24px; line-height: 1.3; margin: 0 0 8px; }
h2 { font-size: 18px; margin: 32px 0 12px; padding-bottom: 6px; border-bottom: 1px solid var(--line); }
.badge { display: inline-block; border-radius: 999px; padding: 2px 12px; font-weight: 600; font-size: 13px;
  border: 1px solid currentColor; }
.pass { color: var(--pass); } .fail { color: var(--fail); } .muted { color: var(--muted); }
.cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 16px; }
.card { background: var(--card); border: 1px solid var(--line); border-radius: 8px; padding: 12px 16px; }
video { display: block; max-width: 100%; max-height: 560px; border-radius: 6px; background: #000; }
.strip { overflow-x: auto; } .strip img { max-height: 400px; }
.shots { display: flex; flex-wrap: wrap; gap: 12px; } .shots figure { margin: 0; } .shots img { height: 240px; }
figcaption { font-size: 13px; color: var(--muted); }
table { border-collapse: collapse; width: 100%; font-size: 14px; }
th, td { text-align: left; padding: 4px 8px; border-bottom: 1px solid var(--line); vertical-align: top; }
code, pre { font: 13px ui-monospace, SFMono-Regular, Menlo, monospace; } pre { white-space: pre-wrap; }
"""


def e(text):
    return html.escape(str(text))


def page(title, body):
    return (f"<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
            f"<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>{e(title)}</title>"
            f"<style>{STYLE}</style></head><body><main>\n{body}\n</main></body></html>\n")


def badge(verdict):
    return f"<span class=\"badge {'pass' if verdict == 'PASS' else 'fail'}\">{e(verdict)}</span>"


def media_html(m, prefix=""):
    """The video, key frames and screenshots of one run, with links relative to the page at `prefix`."""
    if not m.get("device_dir"):
        return "<p class=\"muted\">No captures: the run stopped before it reached the device.</p>"
    imgs, out = m.get("images") or {}, []
    poster = f" poster=\"{quote(prefix + imgs['poster'])}\"" if imgs.get("poster") else ""
    for v in m.get("videos") or []:
        out.append(f"<video controls preload=\"metadata\"{poster} src=\"{quote(prefix + v)}\"></video>"
                   f"<p class=\"muted\"><a href=\"{quote(prefix + v)}\">{e(Path(v).name)}</a></p>")
    if imgs.get("key_frames"):
        out.append(f"<div class=\"strip\"><a href=\"{quote(prefix + imgs['key_frames'])}\">"
                   f"<img alt=\"Key frames\" src=\"{quote(prefix + imgs['key_frames'])}\"></a></div>")
    if m.get("shots"):
        out.append("<div class=\"shots\">" + "".join(
            f"<figure><a href=\"{quote(prefix + s)}\"><img alt=\"{e(Path(s).stem)}\" src=\"{quote(prefix + s)}\"></a>"
            f"<figcaption>{e(Path(s).stem)}</figcaption></figure>" for s in m["shots"]) + "</div>")
    return "\n".join(out) or "<p class=\"muted\">No video or screenshots.</p>"


def checks_html(rows, headers):
    """rows: (failed first?, cells); failures go first."""
    rows = sorted(rows, key=lambda r: not r[0])
    head = "".join(f"<th>{e(h)}</th>" for h in headers)
    body = "".join("<tr>" + "".join(f"<td>{c}</td>" for c in cells) + "</tr>" for _, cells in rows)
    return f"<table><tr>{head}</tr>{body}</table>" if rows else "<p>No checks ran.</p>"


def result_cell(c):
    if c is None:
        return "<span class=\"muted\">not run</span>"
    return f"<span class=\"{'pass' if c['ok'] else 'fail'}\">{'pass' if c['ok'] else 'FAIL'}</span>"


def list_html(items):
    return "<ul>" + "".join(f"<li>{i}</li>" for i in items) + "</ul>"


def index_html(m, details):
    files = ["report.md", "proof.json", "proof.py", *(m.get("logs") or [])]
    body = [f"<h1>{e(claim(m))}</h1>", f"<p>{badge(m['verdict'])} <span class=\"muted\">{e(device_name(m))}</span></p>",
            f"<pre>{e((m['proof'].get('doc') or '').strip())}</pre>", "<h2>Evidence</h2>", media_html(m)]
    body += ["<h2>Checks</h2>", checks_html([(not c["ok"], [e(c["check"]), result_cell(c), e(c["detail"])])
                                             for c in m.get("checks") or []], ["Check", "Result", "Detail"])]
    if m.get("error"):
        body += [f"<p class=\"fail\">Error:</p><pre>{e(m['error'])}</pre>"]
    if m.get("problems"):
        body += ["<h2>Problems</h2>", list_html(map(e, m["problems"]))]
    if m.get("unverified"):
        body += ["<h2>Unverified</h2>", list_html(f"{e(u['what'])}: {e(u['why'])}" for u in m["unverified"])]
    if m.get("notes"):
        body += ["<h2>Notes</h2>", list_html(map(e, m["notes"]))]
    for title, md in m.get("sections") or []:
        body += [f"<h2>{e(title)}</h2>", f"<pre>{e(md)}</pre>"]
    body += ["<h2>Details</h2>", list_html(map(e, details)),
             "<p>" + " · ".join(f"<a href=\"{quote(f)}\">{e(f)}</a>" for f in files) + "</p>"]
    return page(claim(m), "\n".join(body))


def compare_report_md(c, before, after, details):
    rows = c["rows"]
    out = [f"# {claim(after)}: before and after", "", f"**{before['verdict']} before, {after['verdict']} after**: {c['meaning']}.",
           "", "## Verified", "", (after["proof"].get("doc") or "").strip(), "", "## Evidence", ""]
    if c.get("before_after"):
        out += [f"![Before and after]({c['before_after']})", ""]
    for side, m in (("before", before), ("after", after)):
        out += [f"- {side.capitalize()} ({device_name(m)}): " + ", ".join(
            [link(f"{side}/{v}") for v in m.get("videos") or []] + [link(f"{side}/index.html", f"{side}/index.html"),
                                                                   link(f"{side}/report.md", f"{side}/report.md")])]
    out += ["", "## Outcome", "", "| Check | Before | After |", "|---|---|---|", *[f"| {n} | {x} | {y} |" for n, x, y in rows]]
    for side, m in (("Before", before), ("After", after)):
        if m.get("error"):
            out += ["", f"{side} stopped with an error: {headline(m['error'])}"]
    problems = [f"{s}: {p}" for s, m in (("before", before), ("after", after)) for p in m.get("problems") or []]
    problems += c.get("problems") or []
    if problems:
        out += ["", "Problems:", "", *[f"- {p}" for p in problems]]
    out += ["", "## Details", "", *[f"- {d}" for d in details], "", "## Unverified", ""]
    out += [f"- {u['what']}: {u['why']}" for u in after.get("unverified") or []] or ["Nothing the proof reported."]
    return "\n".join(out) + "\n"


def compare_index_html(c, before, after, details):
    mark = lambda x: f"<span class=\"{'pass' if x == 'pass' else 'fail' if x == 'FAIL' else 'muted'}\">{e(x)}</span>"
    body = [f"<h1>{e(claim(after))}: before and after</h1>",
            f"<p>{badge(before['verdict'])} before · {badge(after['verdict'])} after: {e(c['meaning'])}</p>",
            f"<pre>{e((after['proof'].get('doc') or '').strip())}</pre>", "<h2>Evidence</h2>", "<div class=\"cols\">"]
    for side, m in (("before", before), ("after", after)):
        body += [f"<div class=\"card\"><h3>{side.capitalize()}: {e(c['labels'][0 if side == 'before' else 1])}</h3>",
                 media_html(m, f"{side}/"), f"<p><a href=\"{side}/index.html\">Full {side} run</a></p></div>"]
    body.append("</div>")
    if c.get("before_after"):
        body += ["<h2>Key frames, paired by name</h2>",
                 f"<div class=\"strip\"><a href=\"{c['before_after']}\"><img alt=\"Before and after\" src=\"{c['before_after']}\"></a></div>"]
    body += ["<h2>Checks</h2>", checks_html([(x != y, [e(n), mark(x), mark(y)]) for n, x, y in c["rows"]],
                                            ["Check", "Before", "After"]), "<p class=\"muted\">Changed checks first.</p>"]
    problems = [f"{s}: {p}" for s, m in (("before", before), ("after", after)) for p in m.get("problems") or []]
    problems += c.get("problems") or []
    if problems:
        body += ["<h2>Problems</h2>", list_html(map(e, problems))]
    if after.get("unverified"):
        body += ["<h2>Unverified</h2>", list_html(f"{e(u['what'])}: {e(u['why'])}" for u in after["unverified"])]
    body += ["<h2>Details</h2>", list_html(map(e, details)), "<p><a href=\"report.md\">report.md</a> · "
             "<a href=\"compare.json\">compare.json</a></p>"]
    return page(f"{claim(after)}: before and after", "\n".join(body))
