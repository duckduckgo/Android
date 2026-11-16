#!/usr/bin/env python3
"""Local SponsorBlock mock. State is reset when the server restarts."""
import hashlib
import html
import json
import os
import re
import sqlite3
import subprocess
import tempfile
from threading import Lock
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

PORT = 8765
BASE = '/sponsorblock/v1'
versions = {1: set()}
draft = set()
state_lock = Lock()
PACKAGE = 'com.duckduckgo.mobile.android.debug'
DATABASE = 'databases/sponsor_block_dataset.db'
RECEIVER = PACKAGE + '/com.duckduckgo.adblocking.impl.sponsorblock.SponsorBlockMockSyncReceiver'

STYLE = '''
  :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #222; background: #f7f7f7; }
  * { box-sizing: border-box; }
  body { margin: 0; }
  main { max-width: 800px; margin: 0 auto; padding: 48px 24px; }
  .eyebrow { color: #de5833; font-size: 13px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
  h1 { margin: 8px 0 12px; font-size: 32px; letter-spacing: -.03em; }
  h2 { margin: 0; font-size: 20px; }
  p { line-height: 1.5; }
  .subtitle, .hint, .empty { color: #666; }
  .card { margin-top: 24px; padding: 24px; background: white; border: 1px solid #e5e5e5; border-radius: 12px; }
  .card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
  .badge { background: #edf2ff; color: #3154a5; padding: 6px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; white-space: nowrap; }
  label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 600; }
  .add-form { display: flex; gap: 8px; }
  input { min-width: 0; flex: 1; padding: 12px; border: 1px solid #b6b6b6; border-radius: 8px; font: inherit; }
  button { padding: 12px 18px; border: 0; border-radius: 8px; background: #3969ef; color: white; font: inherit; font-weight: 600; cursor: pointer; }
  button:hover { background: #2755d5; }
  button:disabled { background: #ddd; color: #666; cursor: default; }
  input:disabled { background: #f1f1f1; color: #888; }
  :focus-visible { outline: 3px solid #85a7ff; outline-offset: 2px; }
  .remove { background: transparent; color: #bd3434; padding: 8px; }
  .remove:hover { background: #fff1f1; }
  table { width: 100%; border-collapse: collapse; margin-top: 16px; text-align: left; }
  th { color: #666; font-size: 12px; font-weight: 600; }
  td, th { padding: 12px 8px; border-bottom: 1px solid #eee; }
  td:last-child { text-align: right; }
  code { font-size: 13px; }
  .hint { margin: 16px 0 0; font-size: 14px; }
  @media (max-width: 480px) { main { padding: 32px 16px; } .card { padding: 16px; } .add-form { flex-direction: column; } }
'''


def prefix(video_id):
    return hashlib.sha256(video_id.encode()).hexdigest()[:8]


def video_id(value):
    value = value.strip()
    url = urlsplit(value)
    if url.scheme in ('http', 'https'):
        host = (url.hostname or '').lower()
        parts = url.path.strip('/').split('/')
        if host in ('youtu.be', 'www.youtu.be') and len(parts) == 1:
            value = parts[0]
        elif host in ('youtube.com', 'www.youtube.com', 'm.youtube.com', 'music.youtube.com'):
            if url.path == '/watch':
                value = parse_qs(url.query).get('v', [''])[0]
            elif len(parts) == 2 and parts[0] in ('shorts', 'embed', 'live'):
                value = parts[1]
            else:
                value = ''
        else:
            value = ''
    if not re.fullmatch(r'[A-Za-z0-9_-]{1,64}', value):
        raise ValueError('Enter a video ID or a YouTube video URL')
    return value


def delta(revision):
    with state_lock:
        latest = max(versions)
        targets = [v for v in versions if revision < v <= min(latest, revision + 500)]
        if revision not in versions or revision <= 0 or latest - revision > 1000 or (revision < latest and not targets):
            return dict(insert=sorted(versions[latest]), delete=[], replace=True, revision=latest)
        target = max(targets) if targets else revision
        old = versions[revision]
        new = versions[target]
        return dict(insert=sorted(new - old), delete=sorted(old - new), replace=False, revision=target)


def update_draft(fields):
    with state_lock:
        if 'video' in fields:
            video = video_id(fields['video'][0])
            draft.add(prefix(video))
        elif 'remove' in fields:
            draft.discard(fields['remove'][0])
        elif 'save' in fields:
            if draft == versions[max(versions)]:
                raise ValueError('There are no draft changes to save')
            try:
                revision = int(fields.get('revision', [''])[0])
            except ValueError:
                raise ValueError('Enter a valid revision number')
            if not max(versions) < revision <= 2147483647:
                raise ValueError('New revision must be greater than the current revision and at most 2147483647')
            versions[revision] = draft.copy()
        else:
            raise ValueError('Missing action')


def adb_command():
    adb = ['adb']
    if os.environ.get('SERIAL'):
        adb += ['-s', os.environ['SERIAL']]
    return adb


def trigger_sync():
    result = subprocess.run(adb_command() + ['shell', 'am', 'broadcast', '-n', RECEIVER], capture_output=True, timeout=15)
    output = (result.stdout + result.stderr).decode(errors='replace').strip()
    if result.returncode or 'Error:' in output:
        raise RuntimeError(output or 'Could not send the sync broadcast')


def device_dataset():

    def run(*args):
        return subprocess.run(adb_command() + list(args), capture_output=True, timeout=15)

    with tempfile.TemporaryDirectory() as directory:
        path = Path(directory) / 'dataset.db'
        database = run('exec-out', 'run-as', PACKAGE, 'cat', DATABASE)
        if database.returncode:
            raise RuntimeError(database.stderr.decode(errors='replace').strip() or 'Could not read the app database')
        path.write_bytes(database.stdout)
        wal_exists = run('shell', 'run-as', PACKAGE, 'test', '-f', DATABASE + '-wal')
        if wal_exists.returncode == 0:
            wal = run('exec-out', 'run-as', PACKAGE, 'cat', DATABASE + '-wal')
            if wal.returncode:
                raise RuntimeError(wal.stderr.decode(errors='replace').strip() or 'Could not read the database WAL')
            Path(str(path) + '-wal').write_bytes(wal.stdout)
        elif wal_exists.returncode != 1:
            raise RuntimeError(wal_exists.stderr.decode(errors='replace').strip() or 'Could not check the database WAL')
        with sqlite3.connect(f'{path.as_uri()}?mode=ro', uri=True) as connection:
            revision = connection.execute('SELECT revision FROM sponsor_revision WHERE id = 0').fetchone()
            prefixes = [row[0] for row in connection.execute('SELECT prefix FROM sponsor_prefixes ORDER BY prefix')]
        return {'revision': revision[0] if revision else None, 'prefixes': prefixes}


def initialize_from_device(dataset):
    revision = dataset['revision']
    if revision is None:
        return
    prefixes = set(dataset['prefixes'])
    if not 0 < revision <= 2147483647 or any(not re.fullmatch(r'[0-9a-f]{8}', value) for value in prefixes):
        raise ValueError('Invalid device dataset')
    with state_lock:
        versions.clear()
        versions[revision] = prefixes
        draft.clear()
        draft.update(prefixes)


class Handler(BaseHTTPRequestHandler):
    def send(self, code, content, content_type):
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def json(self, value):
        self.send(200, json.dumps(value, separators=(',', ':')).encode(), 'application/json')

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path == '/':
            with state_lock:
                draft_videos = draft.copy()
                revision = max(versions)
                saved_videos = versions[revision].copy()
            rows = ''.join(
                f'<tr><td><code>{html.escape(video)}</code></td>'
                f'<td><form method="post"><button class="remove" name="remove" value="{html.escape(video, quote=True)}" '
                f'aria-label="Remove {html.escape(video, quote=True)}">Remove</button></form></td></tr>'
                for video in sorted(draft_videos)
            )
            contents = (f'<table><thead><tr><th>Hash prefix</th><th></th></tr></thead>'
                        f'<tbody>{rows}</tbody></table>' if rows else '<p class="empty">No prefixes added yet.</p>')
            saved = ''.join(f'<tr><td><code>{html.escape(video)}</code></td></tr>'
                            for video in sorted(saved_videos))
            saved_contents = (f'<table><thead><tr><th>Hash prefix</th></tr></thead>'
                              f'<tbody>{saved}</tbody></table>' if saved else '<p class="empty">No prefixes in this revision.</p>')
            changed = draft_videos != saved_videos
            disabled = '' if changed else ' disabled'
            body = f'''<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>SponsorBlock mock</title>
<style>{STYLE}</style></head><body><main>
<div class="eyebrow">DuckDuckGo · Local testing</div><h1>SponsorBlock mock</h1>
<section class="card"><label for="video">YouTube video URL or ID</label>
<form class="add-form" method="post"><input id="video" name="video" required placeholder="Paste a video URL or ID">
<button>Add video</button></form></section>
<section class="card"><div class="card-heading"><h2>Draft prefixes ({len(draft_videos)})</h2>
<span class="badge">{'Unsaved changes' if changed else 'No changes'}</span></div>{contents}
<form method="post"><label for="revision">New revision number</label><div class="add-form">
<input id="revision" name="revision" type="number" min="{revision + 1}" max="2147483647" value="{revision + 1}" required{disabled}>
<button name="save" value="1"{disabled}>Save revision</button></div></form>
<p id="revision-hint" class="hint"></p></section>
<section class="card"><div class="card-heading"><h2>Current dataset ({len(saved_videos)})</h2>
<span class="badge">Revision {revision}</span></div>{saved_contents}</section>
<section class="card"><h2>Preview response</h2><p class="hint">See the response for a client revision.</p>
<form id="preview-form"><label for="preview-revision">Client revision</label><div class="add-form">
<input id="preview-revision" type="number" min="0" max="2147483647" value="0" required>
<button>Show response</button></div></form><pre id="preview-result" aria-live="polite"></pre></section>
<section class="card"><div class="card-heading"><h2>Device database</h2>
<button id="inspect" type="button">Refresh</button></div>
<p class="hint">Read the revision and prefixes stored by the debug app on the connected device.</p>
<p id="device-status" aria-live="polite">Select Refresh to inspect the device.</p>
<pre id="device-prefixes"></pre><button id="sync" type="button">Force device to request revision</button>
<p id="sync-status" class="hint" aria-live="polite"></p></section>
</main><script>
const serverRevision = {revision};
const revisionInput = document.getElementById('revision');
const previewInput = document.getElementById('preview-revision');
let revisionEdited = false;
let previewEdited = false;
revisionInput.addEventListener('input', () => {{ revisionEdited = true; }});
previewInput.addEventListener('input', () => {{ previewEdited = true; }});

async function refreshDevice() {{
  const status = document.getElementById('device-status');
  const prefixes = document.getElementById('device-prefixes');
  const response = await fetch('/device-dataset');
  const result = await response.json();
  if (!response.ok) throw new Error(result.error);
  status.textContent = result.revision === null ? 'No revision saved yet.' : `Revision: ${{result.revision}}`;
  prefixes.textContent = result.prefixes.length ? 'Prefixes\\n---\\n' + result.prefixes.join('\\n') : 'No prefixes saved.';
  if (!previewEdited) previewInput.value = result.revision ?? 0;
  if (result.revision !== null && result.revision > serverRevision) {{
    const suggested = result.revision + 1;
    document.getElementById('revision-hint').textContent = suggested <= 2147483647
      ? `Device revision is newer. Suggested new revision: ${{suggested}}.`
      : 'Device revision is too high to suggest a new revision. Clear app data to reset it.';
    if (!revisionEdited && suggested <= 2147483647) revisionInput.value = suggested;
  }} else {{
    document.getElementById('revision-hint').textContent = '';
  }}
  return result;
}}

document.getElementById('preview-form').addEventListener('submit', async (event) => {{
  event.preventDefault();
  const result = document.getElementById('preview-result');
  try {{
    const revision = document.getElementById('preview-revision').value;
    const response = await fetch('/sponsorblock/v1/hashPrefix?revision=' + encodeURIComponent(revision));
    if (!response.ok) throw new Error(await response.text());
    result.textContent = JSON.stringify(await response.json(), null, 2);
  }} catch (error) {{ result.textContent = 'Unable to load response: ' + error.message; }}
}});
document.getElementById('inspect').addEventListener('click', async () => {{
  const status = document.getElementById('device-status');
  status.textContent = 'Reading device database…';
  try {{ await refreshDevice(); }}
  catch (error) {{ status.textContent = 'Unable to read device database: ' + error.message; }}
}});
document.getElementById('sync').addEventListener('click', async () => {{
  const status = document.getElementById('sync-status');
  const button = document.getElementById('sync');
  button.disabled = true;
  status.textContent = 'Sending sync request…';
  try {{
    const response = await fetch('/device-sync', {{ method: 'POST' }});
    const result = await response.json();
    if (!response.ok) throw new Error(result.error);
    status.textContent = 'Sync requested. Waiting for the device database…';
    let device;
    for (let attempt = 0; attempt < 10; attempt++) {{
      await new Promise(resolve => setTimeout(resolve, 2000));
      device = await refreshDevice();
      if (device.revision !== null && device.revision >= serverRevision) break;
    }}
    status.textContent = device.revision !== null && device.revision >= serverRevision
      ? 'Device database refreshed.' : 'Sync requested; the device has not reached the current revision yet.';
  }} catch (error) {{ status.textContent = 'Unable to request sync: ' + error.message; }}
  finally {{ button.disabled = false; }}
}});
refreshDevice().catch(error => {{ document.getElementById('device-status').textContent = 'Unable to read device database: ' + error.message; }});
</script></body></html>'''
            self.send(200, body.encode(), 'text/html; charset=utf-8')
        elif url.path == '/device-dataset':
            try:
                self.json(device_dataset())
            except (OSError, sqlite3.Error, subprocess.TimeoutExpired, RuntimeError) as error:
                self.send(500, json.dumps({'error': str(error)}).encode(), 'application/json')
        elif url.path == BASE + '/revision':
            with state_lock:
                latest = max(versions)
            self.json({'revision': latest})
        elif url.path == BASE + '/hashPrefix':
            try:
                revision = int(parse_qs(url.query)['revision'][0])
                if revision < 0:
                    raise ValueError()
            except (ValueError, KeyError):
                self.send(400, b'Invalid revision', 'text/plain')
                return
            self.json(delta(revision))
        else:
            self.send(404, b'Not found', 'text/plain')

    def do_POST(self):
        if urlsplit(self.path).path == '/device-sync':
            try:
                trigger_sync()
                self.json({'requested': True})
            except (OSError, subprocess.TimeoutExpired, RuntimeError) as error:
                self.send(500, json.dumps({'error': str(error)}).encode(), 'application/json')
            return
        if urlsplit(self.path).path != '/':
            self.send(404, b'Not found', 'text/plain')
            return
        size = int(self.headers.get('Content-Length', '0'))
        if size > 100000:
            self.send(413, b'Too large', 'text/plain')
            return
        fields = parse_qs(self.rfile.read(size).decode(), keep_blank_values=True)
        try:
            update_draft(fields)
        except ValueError as error:
            self.send(400, str(error).encode(), 'text/plain; charset=utf-8')
            return
        self.send_response(303)
        self.send_header('Location', '/')
        self.send_header('Content-Length', '0')
        self.end_headers()


if __name__ == '__main__':
    if os.environ.get('NO_DEVICE') != '1':
        try:
            initialize_from_device(device_dataset())
        except (OSError, sqlite3.Error, subprocess.TimeoutExpired, RuntimeError, ValueError) as error:
            print(f'Could not initialize from device: {error}', flush=True)
    print(f'SponsorBlock mock: http://127.0.0.1:{PORT}/', flush=True)
    ThreadingHTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
