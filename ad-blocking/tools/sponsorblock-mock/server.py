#!/usr/bin/env python3
"""Local SponsorBlock mock. State is reset when the server restarts."""
import argparse
import hashlib
import gzip
import html
import json
import os
import re
import struct
import subprocess
from collections import deque
from datetime import datetime
from threading import Lock
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlsplit

PORT = 8765
BASE = '/sponsorblock/v1'
versions = {1: set()}
replacement_revisions = set()
draft = set()
draft_labels = {}
dataset_cache = {"source": None, "size": None, "sorted": []}
state_lock = Lock()
requests = deque(maxlen=100)
PACKAGE = 'com.duckduckgo.mobile.android.debug'
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
  .prefix-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(110px, 1fr)); gap: 8px 16px; margin: 16px 0; }
  .pagination { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; margin-top: 16px; }
  .pagination input { flex: none; width: 95px; padding: 8px; }
  .pending { list-style: none; padding: 0; margin: 12px 0; }
  .pending li { display: flex; align-items: center; gap: 8px; overflow-wrap: anywhere; }
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


def binary_full(prefixes, revision):
    values = sorted(int(value, 16) for value in prefixes)
    offsets = [0] * 65537
    suffixes = bytearray()
    for value in values:
        offsets[(value >> 16) + 1] += 1
        suffixes.extend(struct.pack('<H', value & 0xffff))
    for index in range(1, len(offsets)):
        offsets[index] += offsets[index - 1]
    body = struct.pack('<65537I', *offsets) + suffixes
    digest = hashlib.sha256(body).digest()[:4]
    header = struct.pack('<4sBBHI4sIII', b'SBPL', 1, 0, 0, revision, digest, len(values), 0, 0)
    return header + body


def binary_delta(response, prefixes):
    full = binary_full(prefixes, response['revision'])
    if response['replace']:
        return full
    inserts = sorted(int(value, 16) for value in response['insert'])
    deletes = sorted(int(value, 16) for value in response['delete'])
    header = struct.pack('<4sBBHI4sIII', b'SBPL', 1, 1, 0, response['revision'], full[12:16],
                         len(prefixes), len(inserts), len(deletes))
    return header + b''.join(struct.pack('<I', value) for value in inserts + deletes)


def binary_preview(content):
    magic, version, kind, reserved, revision, digest, count, inserts, deletes = struct.unpack_from('<4sBBHI4sIII', content)
    shown = content[:256]
    lines = [f'{offset:08x}  {shown[offset:offset + 16].hex(" ")}' for offset in range(0, len(shown), 16)]
    omitted = f'\n… {len(content) - len(shown):,} bytes omitted' if len(content) > len(shown) else ''
    return (f'Binary response (decompressed): {len(content):,} bytes\n'
            f'Type: {"full list" if kind == 0 else "diff"} · Format version: {version} · Revision: {revision}\n'
            f'Entries: {count:,} · Inserts: {inserts:,} · Deletes: {deletes:,} · Checksum: {digest.hex()}\n'
            f'\nHex bytes (offset on the left):\n' + '\n'.join(lines) + omitted)


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
        if any(revision < replaced <= target for replaced in replacement_revisions):
            return dict(insert=sorted(versions[target]), delete=[], replace=True, revision=target)
        old = versions[revision]
        new = versions[target]
        return dict(insert=sorted(new - old), delete=sorted(old - new), replace=False, revision=target)


def update_draft(fields):
    with state_lock:
        if 'video' in fields:
            video = video_id(fields['video'][0])
            value = prefix(video)
            draft.add(value)
            draft_labels[value] = video
        elif 'remove_video' in fields:
            value = prefix(video_id(fields['remove_video'][0]))
            draft.discard(value)
            draft_labels.pop(value, None)
        elif 'remove' in fields:
            draft.discard(fields['remove'][0])
            draft_labels.pop(fields['remove'][0], None)
        elif 'undo_remove' in fields:
            value = fields['undo_remove'][0]
            if value in versions[max(versions)]:
                draft.add(value)
        elif 'save' in fields:
            if draft == versions[max(versions)]:
                raise ValueError('There are no draft changes to save')
            try:
                revision = int(fields.get('revision', [''])[0])
            except ValueError:
                raise ValueError('Enter a valid revision number')
            if not max(versions) < revision <= 4294967295:
                raise ValueError('New revision must be greater than the current revision and at most 4294967295')
            versions[revision] = draft.copy()
            draft_labels.clear()
            if 'replace' in fields:
                replacement_revisions.add(revision)
        else:
            raise ValueError('Missing action')


def dataset_page(page):
    with state_lock:
        revision = max(versions)
        values = versions[revision]
        if dataset_cache['source'] is not values or dataset_cache['size'] != len(values):
            dataset_cache.update(source=values, size=len(values), sorted=sorted(values))
        ordered = dataset_cache['sorted']
        pages = max(1, (len(ordered) + 99) // 100)
        page = max(1, min(page, pages))
        return {'revision': revision, 'page': page, 'pages': pages, 'total': len(ordered),
                'prefixes': ordered[(page - 1) * 100:page * 100]}


def record_request(path, status, response):
    with state_lock:
        if isinstance(response, dict):
            response = response.copy()
            for field in ('insert', 'delete'):
                values = response.get(field, [])
                if len(values) > 10000:
                    response[field] = f'{len(values)} prefixes (omitted)'
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


def device_dataset(limit=None):

    def run(*args):
        return subprocess.run(adb_command() + list(args), capture_output=True, timeout=15)

    result = run('exec-out', 'run-as', PACKAGE, 'cat', 'files/sponsorblock-prefixes.bin')
    if result.returncode:
        error = result.stderr.decode(errors='replace').strip()
        if 'No such file or directory' in error:
            return {'revision': None, 'prefixes': [], 'count': 0}
        raise RuntimeError(error or 'Could not read the binary dataset')
    raw = result.stdout
    if len(raw) < 28 + 65537 * 4:
        raise ValueError('Incomplete binary dataset')
    revision = struct.unpack_from('<I', raw, 8)[0]
    offsets = struct.unpack_from('<65537I', raw, 28)
    count = offsets[-1]
    if raw[:4] != b'SBPL' or len(raw) != 28 + 65537 * 4 + count * 2:
        raise ValueError('Invalid binary dataset size')
    prefixes = []
    for bucket in range(65536):
        if offsets[bucket] > offsets[bucket + 1]:
            raise ValueError('Invalid binary dataset offsets')
        for index in range(offsets[bucket], offsets[bucket + 1]):
            if limit is not None and len(prefixes) >= limit:
                return {'revision': revision, 'prefixes': prefixes, 'count': count}
            suffix = struct.unpack_from('<H', raw, 28 + 65537 * 4 + index * 2)[0]
            prefixes.append(f'{bucket:04x}{suffix:04x}')
    return {'revision': revision, 'prefixes': prefixes, 'count': count}


def initialize_from_device(dataset):
    revision = dataset['revision']
    if revision is None:
        return
    prefixes = set(dataset['prefixes'])
    if not 0 < revision <= 4294967295 or any(not re.fullmatch(r'[0-9a-f]{8}', value) for value in prefixes):
        raise ValueError('Invalid device dataset')
    with state_lock:
        versions.clear()
        draft_labels.clear()
        replacement_revisions.clear()
        versions[revision] = prefixes
        draft.clear()
        draft.update(prefixes)


def setup_two_step_scenario(dataset):
    revision = dataset['revision']
    prefixes = set(dataset['prefixes'])
    if revision is None or not 0 < revision <= 4294967295 - 501:
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
        draft_labels.clear()
        replacement_revisions.clear()
        versions[revision] = prefixes
        versions[revision + 500] = prefixes | {additions[0]}
        versions[revision + 501] = prefixes | set(additions)
        draft.clear()
        draft.update(versions[revision + 501])


class Handler(BaseHTTPRequestHandler):
    def send(self, code, content, content_type):
        compressed = (len(content) > 1024 or content_type == 'application/octet-stream') and 'gzip' in self.headers.get('Accept-Encoding', '').lower()
        if compressed:
            content = gzip.compress(content)
        self.send_response(code)
        self.send_header('Content-Type', content_type)
        if compressed:
            self.send_header('Content-Encoding', 'gzip')
        self.send_header('Vary', 'Accept-Encoding')
        self.send_header('Content-Length', str(len(content)))
        self.end_headers()
        self.wfile.write(content)

    def json(self, value):
        self.send(200, json.dumps(value, separators=(',', ':')).encode(), 'application/json')

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path in ('/', BASE, BASE + '/'):
            with state_lock:
                revision = max(versions)
                saved_videos = versions[revision]
                added = sorted(draft - saved_videos)
                removed = sorted(saved_videos - draft)
                labels = draft_labels.copy()
            added_items = ''.join(
                f'<li><span>{html.escape(labels.get(value, value))} · <code>{value}</code></span>'
                f'<form method="post"><button class="remove" name="remove" value="{value}">Remove</button></form></li>'
                for value in added
            )
            removed_items = ''.join(
                f'<li><code>{value}</code><form method="post"><button class="remove" name="undo_remove" '
                f'value="{value}">Undo</button></form></li>' for value in removed
            )
            added_contents = f'<ul class="pending">{added_items}</ul>' if added else '<p class="hint">No videos added.</p>'
            removed_contents = f'<ul class="pending">{removed_items}</ul>' if removed else '<p class="hint">No prefixes removed.</p>'
            changed = bool(added or removed)
            disabled = '' if changed else ' disabled'
            body = f'''<!doctype html><html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>SponsorBlock mock</title>
<style>{STYLE}</style></head><body><main>
<div class="eyebrow">DuckDuckGo · Local testing</div><h1>SponsorBlock mock</h1>
<div class="layout"><div class="controls">
<section class="card"><div class="card-heading"><h2>Update current revision</h2>
<span class="badge">{'Unsaved changes' if changed else 'No changes'}</span></div>
<label for="video">YouTube video URL or ID</label>
<form class="add-form" method="post"><input id="video" name="video" required placeholder="Paste a video URL or ID">
<button>Add video</button></form>{added_contents}
<label for="remove-video">Remove video URL or ID</label><form class="add-form" method="post">
<input id="remove-video" name="remove_video" required placeholder="Paste a video URL or ID"><button>Remove video</button></form>
<label for="remove-prefix">Remove hash prefix</label><form class="add-form" method="post">
<input id="remove-prefix" name="remove" pattern="[0-9a-f]{{8}}" required placeholder="8 lowercase hex characters"><button>Remove prefix</button></form>
{removed_contents}
<form method="post"><label for="revision">New revision number</label><div class="add-form">
<input id="revision" name="revision" type="number" min="{revision + 1}" max="4294967295" value="{revision + 1}" required{disabled}>
<button name="save" value="1"{disabled}>Save revision</button></div>
<label class="checkbox"><input type="checkbox" name="replace" value="1"{disabled}> Force clients to replace their dataset</label></form>
<p id="revision-hint" class="hint"></p></section>
<section class="card"><div class="card-heading"><h2>Current dataset ({len(saved_videos):,})</h2>
<span class="badge">Revision {revision}</span></div>
<div id="dataset-grid" class="prefix-grid" aria-live="polite">Loading prefixes…</div>
<div class="pagination"><button id="dataset-prev" type="button">Previous</button>
<label for="dataset-page">Page</label><input id="dataset-page" type="number" min="1" value="1">
<span id="dataset-pages"></span><button id="dataset-go" type="button">Go</button>
<button id="dataset-next" type="button">Next</button></div></section>
<section class="card"><h2>Test scenarios</h2>
<p class="hint">Prepare a dataset that requires the device to make two diff requests in one sync. This replaces the mock revision history with the device's current dataset and two new revisions.</p>
<p class="hint">Save or remove draft changes first.</p>
<button id="two-step" type="button"{' disabled' if changed else ''}>Set up two-request sync</button>
<p id="scenario-status" class="hint" aria-live="polite"></p></section>
<section class="card"><h2>Preview response</h2><p class="hint">See the response for a client revision.</p>
<form id="preview-form"><label for="preview-revision">Client revision</label><div class="add-form">
<input id="preview-revision" type="number" min="0" max="4294967295" value="0" required>
<button>Show response</button></div></form><p id="preview-download" class="hint" hidden><a id="preview-link" download="sponsorblock.bin">Download binary response</a></p><pre id="preview-result" aria-live="polite"></pre></section>
</div><aside class="request-column">
<section class="card"><div class="card-heading"><h2>Device storage</h2>
<button id="inspect" type="button">Refresh</button></div>
<p class="hint">Updates every second while this page is open.</p>
<p id="device-status" aria-live="polite">Reading device storage…</p>
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
let datasetPage = 1;
let datasetPages = 1;
async function showDataset(page) {{
  const grid = document.getElementById('dataset-grid');
  grid.textContent = 'Loading prefixes…';
  try {{
    const response = await fetch('/dataset?page=' + encodeURIComponent(page));
    if (!response.ok) throw new Error(await response.text());
    const result = await response.json();
    datasetPage = result.page;
    datasetPages = result.pages;
    document.getElementById('dataset-page').value = datasetPage;
    document.getElementById('dataset-page').max = datasetPages;
    document.getElementById('dataset-pages').textContent = 'of ' + datasetPages.toLocaleString();
    document.getElementById('dataset-prev').disabled = datasetPage <= 1;
    document.getElementById('dataset-next').disabled = datasetPage >= datasetPages;
    grid.replaceChildren();
    if (!result.prefixes.length) grid.textContent = 'No prefixes in this revision.';
    for (const prefix of result.prefixes) {{
      const code = document.createElement('code');
      code.textContent = prefix;
      grid.append(code);
    }}
  }} catch (error) {{ grid.textContent = 'Unable to load dataset: ' + error.message; }}
}}
document.getElementById('dataset-prev').addEventListener('click', () => showDataset(datasetPage - 1));
document.getElementById('dataset-next').addEventListener('click', () => showDataset(datasetPage + 1));
document.getElementById('dataset-go').addEventListener('click', () => showDataset(document.getElementById('dataset-page').value));
showDataset(1);
revisionInput.addEventListener('input', () => {{ revisionEdited = true; }});
previewInput.addEventListener('input', () => {{ previewEdited = true; }});
document.getElementById('two-step').addEventListener('click', async () => {{
  const button = document.getElementById('two-step');
  const status = document.getElementById('scenario-status');
  button.disabled = true;
  status.textContent = 'Preparing scenario from the device storage…';
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
  status.textContent = result.revision === null ? 'No revision saved yet.' : `Revision: ${{result.revision}} · ${{result.count}} prefixes`;
  if (result.count > result.prefixes.length) status.textContent += ' (showing first 100)';
  prefixes.textContent = result.prefixes.length ? 'Prefixes\\n---\\n' + result.prefixes.join('\\n') : 'No prefixes saved.';
  if (!previewEdited) previewInput.value = result.revision ?? 0;
  if (result.revision !== null && result.revision > serverRevision) {{
    const suggested = result.revision + 1;
    document.getElementById('revision-hint').textContent = suggested <= 4294967295
      ? `Device revision is newer. Suggested new revision: ${{suggested}}.`
      : 'Device revision is too high to suggest a new revision. Clear app data to reset it.';
    if (!revisionEdited && suggested <= 4294967295) revisionInput.value = suggested;
  }} else {{
    document.getElementById('revision-hint').textContent = '';
  }}
  return result;
}}

async function pollDevice() {{
  try {{ await refreshDevice(); }}
  catch (error) {{
    document.getElementById('device-status').textContent = 'Unable to read device storage: ' + error.message;
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
    result.textContent = await response.text();
    document.getElementById('preview-link').href = '/sponsorblock/v1/hashPrefixBinary?format=binary&version=1&revision=' + encodeURIComponent(revision);
    document.getElementById('preview-download').hidden = false;
  }} catch (error) {{ result.textContent = 'Unable to load response: ' + error.message; }}
}});
document.getElementById('inspect').addEventListener('click', async () => {{
  const status = document.getElementById('device-status');
  status.textContent = 'Reading device storage…';
  try {{ await refreshDevice(); }}
  catch (error) {{
    status.textContent = 'Unable to read device storage: ' + error.message;
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
    status.textContent = 'Sync requested. The device storage updates automatically.';
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
        elif url.path == '/dataset':
            try:
                page = int(parse_qs(url.query).get('page', ['1'])[0])
                if page < 1:
                    raise ValueError()
                self.json(dataset_page(page))
            except ValueError:
                self.send(400, b'Invalid page', 'text/plain')
        elif url.path == '/device-dataset':
            try:
                self.json(device_dataset(limit=100))
            except (OSError, subprocess.TimeoutExpired, RuntimeError, ValueError) as error:
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
        elif url.path in (BASE + '/hashPrefixBinary', '/preview'):
            try:
                revision = int(parse_qs(url.query)['revision'][0])
                if revision < 0:
                    raise ValueError()
            except (ValueError, KeyError):
                if url.path != '/preview':
                    record_request(self.path, 400, 'Invalid revision')
                self.send(400, b'Invalid revision', 'text/plain')
                return
            if url.path.endswith('/hashPrefixBinary'):
                params = parse_qs(url.query)
                try:
                    supported = int(params.get('version', ['1'])[0])
                    if params.get('format', ['binary'])[0] != 'binary' or supported < 1:
                        raise ValueError()
                except ValueError:
                    self.send(400, b'Unsupported format', 'text/plain')
                    return
            raw = delta(revision)
            with state_lock:
                target = versions[raw['revision']].copy()
            content = binary_delta(raw, target)
            if url.path == '/preview':
                self.send(200, binary_preview(content).encode(), 'text/plain; charset=utf-8')
            else:
                response = dict(raw, checksum=content[12:16].hex(), entries=len(target), format='binary')
                record_request(self.path, 200, response)
                self.send(200, content, 'application/octet-stream')
        else:
            self.send(404, b'Not found', 'text/plain')

    def do_POST(self):
        if urlsplit(self.path).path == '/scenario/two-step':
            try:
                setup_two_step_scenario(device_dataset())
                self.json({'prepared': True})
            except (OSError, subprocess.TimeoutExpired, RuntimeError, ValueError) as error:
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


def load_dataset(path):
    prefixes = set()
    previous = None
    with open(path, encoding='ascii') as source:
        for line in source:
            value = line.strip()
            if not re.fullmatch(r'[0-9a-f]{8}', value) or (previous is not None and value <= previous):
                raise ValueError('Dataset must contain unique, sorted, lowercase 8-character hex prefixes')
            prefixes.add(value)
            previous = value
    return prefixes


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dataset', help='Sorted file of unique eight-character hex prefixes')
    parser.add_argument('--port', type=int, default=PORT)
    args = parser.parse_args()
    if args.dataset:
        prefixes = load_dataset(args.dataset)
        versions[1] = prefixes
        draft.update(prefixes)
    elif os.environ.get('NO_DEVICE') != '1':
        try:
            initialize_from_device(device_dataset())
        except (OSError, subprocess.TimeoutExpired, RuntimeError, ValueError) as error:
            print(f'Could not initialize from device: {error}', flush=True)
    print(f'SponsorBlock mock: http://127.0.0.1:{args.port}/', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
