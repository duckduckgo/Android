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
from collections import deque
from datetime import datetime
from threading import Lock
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

PORT = 8765
BASE = '/sponsorblock/v1'
versions = {1: set()}
replacement_revisions = set()
draft = set()
state_lock = Lock()
requests = deque(maxlen=100)
PACKAGE = 'com.duckduckgo.mobile.android.debug'
DATABASE = 'databases/sponsor_block_dataset.db'
RECEIVER = PACKAGE + '/com.duckduckgo.adblocking.impl.sponsorblock.SponsorBlockMockSyncReceiver'

STYLE = '''
  :root { color-scheme: light; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #222; background: #f7f7f7; }
  * { box-sizing: border-box; }
  body { margin: 0; }
  main { max-width: 1400px; margin: 0 auto; padding: 48px 24px; }
  .layout { display: grid; grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr); gap: 24px; align-items: start; }
  .request-column { position: sticky; top: 24px; max-height: calc(100vh - 48px); overflow-y: auto; }
  .request-column .card:first-child { margin-top: 0; }
  .eyebrow { color: #de5833; font-size: 13px; font-weight: 700; letter-spacing: .06em; text-transform: uppercase; }
  h1 { margin: 8px 0 12px; font-size: 32px; letter-spacing: -.03em; }
  h2 { margin: 0; font-size: 20px; }
  p { line-height: 1.5; }
  .subtitle, .hint, .empty { color: #666; }
  .card { margin-top: 24px; padding: 24px; background: white; border: 1px solid #e5e5e5; border-radius: 12px; }
  .card-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
  .badge { background: #edf2ff; color: #3154a5; padding: 6px 12px; border-radius: 999px; font-size: 13px; font-weight: 600; white-space: nowrap; }
  label { display: block; margin-bottom: 8px; font-size: 14px; font-weight: 600; }
  .checkbox { display: flex; align-items: center; gap: 8px; margin: 16px 0; }
  .checkbox input { flex: none; }
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
  pre { white-space: pre-wrap; overflow-wrap: anywhere; }
  @media (max-width: 800px) { .layout { grid-template-columns: 1fr; } .request-column { position: static; max-height: none; } }
  @media (max-width: 480px) { main { padding: 32px 16px; } .card { padding: 16px; } .add-form { flex-direction: column; } }
'''


def prefix(video_id):
    return hashlib.sha256(video_id.encode()).hexdigest()[:8]


def checksum(prefixes):
    return hashlib.sha256('\n'.join(sorted(prefixes)).encode()).hexdigest()[:8]


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
            return dict(insert=sorted(versions[latest]), delete=[], replace=True, revision=latest, checksum=checksum(versions[latest]))
        target = max(targets) if targets else revision
        if any(revision < replaced <= target for replaced in replacement_revisions):
            return dict(insert=sorted(versions[target]), delete=[], replace=True, revision=target, checksum=checksum(versions[target]))
        old = versions[revision]
        new = versions[target]
        return dict(insert=sorted(new - old), delete=sorted(old - new), replace=False, revision=target, checksum=checksum(new))


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
            if 'replace' in fields:
                replacement_revisions.add(revision)
        else:
            raise ValueError('Missing action')


def record_request(path, status, response):
    with state_lock:
        requests.append({'time': datetime.now().astimezone().isoformat(timespec='seconds'),
                         'path': path, 'status': status, 'response': response})


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
        replacement_revisions.clear()
        versions[revision] = prefixes
        draft.clear()
        draft.update(prefixes)


def setup_two_step_scenario(dataset):
    revision = dataset['revision']
    prefixes = set(dataset['prefixes'])
    if revision is None or not 0 < revision <= 2147483647 - 501:
        raise ValueError('The device needs a saved revision with room for 501 more revisions')
    if any(not re.fullmatch(r'[0-9a-f]{8}', value) for value in prefixes):
        raise ValueError('Invalid device dataset')
    with state_lock:
        if draft != versions[max(versions)]:
            raise ValueError('Save or discard draft changes before setting up the scenario')
        additions = []
        index = 0
        while len(additions) < 2:
            value = prefix(f'two-step-scenario-{index}')
            if value not in prefixes and value not in additions:
                additions.append(value)
            index += 1
        versions.clear()
        replacement_revisions.clear()
        versions[revision] = prefixes
        versions[revision + 500] = prefixes | {additions[0]}
        versions[revision + 501] = prefixes | set(additions)
        draft.clear()
        draft.update(versions[revision + 501])


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
<div class="layout"><div class="controls">
<section class="card"><label for="video">YouTube video URL or ID</label>
<form class="add-form" method="post"><input id="video" name="video" required placeholder="Paste a video URL or ID">
<button>Add video</button></form></section>
<section class="card"><div class="card-heading"><h2>Draft prefixes ({len(draft_videos)})</h2>
<span class="badge">{'Unsaved changes' if changed else 'No changes'}</span></div>{contents}
<form method="post"><label for="revision">New revision number</label><div class="add-form">
<input id="revision" name="revision" type="number" min="{revision + 1}" max="2147483647" value="{revision + 1}" required{disabled}>
<button name="save" value="1"{disabled}>Save revision</button></div>
<label class="checkbox"><input type="checkbox" name="replace" value="1"{disabled}> Force clients to replace their dataset</label></form>
<p id="revision-hint" class="hint"></p></section>
<section class="card"><div class="card-heading"><h2>Current dataset ({len(saved_videos)})</h2>
<span class="badge">Revision {revision}</span></div>{saved_contents}</section>
<section class="card"><h2>Test scenarios</h2>
<p class="hint">Prepare a dataset that requires the device to make two diff requests in one sync. This replaces the mock revision history with the device's current dataset and two new revisions.</p>
<p class="hint">Save or remove draft changes first.</p>
<button id="two-step" type="button"{' disabled' if changed else ''}>Set up two-request sync</button>
<p id="scenario-status" class="hint" aria-live="polite"></p></section>
<section class="card"><h2>Preview response</h2><p class="hint">See the response for a client revision.</p>
<form id="preview-form"><label for="preview-revision">Client revision</label><div class="add-form">
<input id="preview-revision" type="number" min="0" max="2147483647" value="0" required>
<button>Show response</button></div></form><pre id="preview-result" aria-live="polite"></pre></section>
</div><aside class="request-column">
<section class="card"><div class="card-heading"><h2>Device database</h2>
<button id="inspect" type="button">Refresh</button></div>
<p class="hint">Updates every second while this page is open.</p>
<p id="device-status" aria-live="polite">Reading device database…</p>
<pre id="device-prefixes"></pre><button id="sync" type="button">Force device to request revision</button>
<p id="sync-status" class="hint" aria-live="polite"></p></section>
<section class="card"><h2>Phone requests</h2><p class="hint">Latest 100 dataset requests, newest first. Updates automatically.</p>
<div id="requests" aria-live="polite">No requests yet.</div></section>
</aside></div></main><script>
const serverRevision = {revision};
const revisionInput = document.getElementById('revision');
const previewInput = document.getElementById('preview-revision');
let revisionEdited = false;
let previewEdited = false;
revisionInput.addEventListener('input', () => {{ revisionEdited = true; }});
previewInput.addEventListener('input', () => {{ previewEdited = true; }});
document.getElementById('two-step').addEventListener('click', async () => {{
  const button = document.getElementById('two-step');
  const status = document.getElementById('scenario-status');
  button.disabled = true;
  status.textContent = 'Preparing scenario from the device database…';
  try {{
    const response = await fetch('/scenario/two-step', {{ method: 'POST' }});
    const result = await response.json();
    if (!response.ok) throw new Error(result.error);
    window.location.reload();
  }} catch (error) {{
    status.textContent = 'Unable to prepare scenario: ' + error.message;
    button.disabled = false;
  }}
}});

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

async function pollDevice() {{
  try {{ await refreshDevice(); }}
  catch (error) {{
    document.getElementById('device-status').textContent = error.message === 'file is not a database'
      ? "Database doesn't exist yet" : 'Unable to read device database: ' + error.message;
    document.getElementById('device-prefixes').textContent = '';
  }}
  finally {{ setTimeout(pollDevice, 1000); }}
}}

document.getElementById('preview-form').addEventListener('submit', async (event) => {{
  event.preventDefault();
  const result = document.getElementById('preview-result');
  try {{
    const revision = document.getElementById('preview-revision').value;
    const response = await fetch('/preview?revision=' + encodeURIComponent(revision));
    if (!response.ok) throw new Error(await response.text());
    result.textContent = JSON.stringify(await response.json(), null, 2);
  }} catch (error) {{ result.textContent = 'Unable to load response: ' + error.message; }}
}});
document.getElementById('inspect').addEventListener('click', async () => {{
  const status = document.getElementById('device-status');
  status.textContent = 'Reading device database…';
  try {{ await refreshDevice(); }}
  catch (error) {{
    status.textContent = error.message === 'file is not a database'
      ? "Database doesn't exist yet" : 'Unable to read device database: ' + error.message;
  }}
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
    status.textContent = 'Sync requested. The device database updates automatically.';
  }} catch (error) {{ status.textContent = 'Unable to request sync: ' + error.message; }}
  finally {{ button.disabled = false; }}
}});
pollDevice();
let lastRequests = null;
async function refreshRequests() {{
  try {{
    const response = await fetch('/requests', {{ cache: 'no-store' }});
    if (!response.ok) throw new Error('Request log unavailable');
    const entries = await response.json();
    const snapshot = JSON.stringify(entries);
    if (snapshot === lastRequests) return;
    lastRequests = snapshot;
    const container = document.getElementById('requests');
    container.replaceChildren();
    if (!entries.length) {{ container.textContent = 'No requests yet.'; return; }}
    for (const entry of entries) {{
      const details = document.createElement('details');
      const summary = document.createElement('summary');
      summary.textContent = `${{new Date(entry.time).toLocaleString()}} · GET ${{entry.path}} — HTTP ${{entry.status}}`;
      const body = document.createElement('pre');
      body.textContent = JSON.stringify(entry.response, null, 2);
      details.append(summary, body);
      container.append(details);
    }}
  }} catch (error) {{
    lastRequests = null;
    document.getElementById('requests').textContent = 'Unable to load requests: ' + error.message;
  }}
}}
refreshRequests();
setInterval(refreshRequests, 2000);
</script></body></html>'''
            self.send(200, body.encode(), 'text/html; charset=utf-8')
        elif url.path == '/device-dataset':
            try:
                self.json(device_dataset())
            except (OSError, sqlite3.Error, subprocess.TimeoutExpired, RuntimeError) as error:
                self.send(500, json.dumps({'error': str(error)}).encode(), 'application/json')
        elif url.path == '/requests':
            with state_lock:
                entries = list(reversed(requests))
            self.json(entries)
        elif url.path == BASE + '/revision':
            with state_lock:
                latest = max(versions)
            response = {'revision': latest}
            record_request(self.path, 200, response)
            self.json(response)
        elif url.path in (BASE + '/hashPrefix', '/preview'):
            try:
                revision = int(parse_qs(url.query)['revision'][0])
                if revision < 0:
                    raise ValueError()
            except (ValueError, KeyError):
                if url.path != '/preview':
                    record_request(self.path, 400, 'Invalid revision')
                self.send(400, b'Invalid revision', 'text/plain')
                return
            response = delta(revision)
            if url.path != '/preview':
                record_request(self.path, 200, response)
            self.json(response)
        else:
            self.send(404, b'Not found', 'text/plain')

    def do_POST(self):
        if urlsplit(self.path).path == '/scenario/two-step':
            try:
                setup_two_step_scenario(device_dataset())
                self.json({'prepared': True})
            except (OSError, sqlite3.Error, subprocess.TimeoutExpired, RuntimeError, ValueError) as error:
                self.send(500, json.dumps({'error': str(error)}).encode(), 'application/json')
            return
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
