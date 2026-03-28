/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.duckchat.store.internal

import android.content.Context
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeChatsDao
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingEntity
import com.duckduckgo.duckchat.store.impl.store.DuckAiBridgeSettingsDao
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import fi.iki.elonen.NanoHTTPD
import logcat.logcat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

interface DuckAiDebugServer {
    val port: Int
    val isRunning: Boolean
    fun start()
    fun stop()
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckAiDebugServer @Inject constructor(
    private val settingsDao: DuckAiBridgeSettingsDao,
    private val chatsDao: DuckAiBridgeChatsDao,
    private val context: Context,
) : DuckAiDebugServer {

    override val port = 8765
    private var server: InternalNanoServer? = null
    override val isRunning get() = server?.isAlive == true

    override fun start() {
        if (isRunning) return
        server = InternalNanoServer().also { it.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }
        logcat { "DuckAiDebugServer started on port $port" }
    }

    override fun stop() {
        server?.stop()
        server = null
        logcat { "DuckAiDebugServer stopped" }
    }

    private val filesDir: File get() = File(context.filesDir, "duck_ai_bridge_files")

    private inner class InternalNanoServer : NanoHTTPD(port) {
        override fun serve(session: IHTTPSession): Response {
            val uri = session.uri.trimEnd('/')
            val method = session.method

            return try {
                when {
                    uri == "" || uri == "/debug" && method == Method.GET -> serveHtml()

                    uri == "/debug/settings" && method == Method.GET -> {
                        val obj = JSONObject()
                        settingsDao.getAll().forEach { obj.put(it.key, it.value) }
                        jsonResponse(obj.toString())
                    }

                    uri.startsWith("/debug/settings/") && method == Method.PUT -> {
                        val key = uri.removePrefix("/debug/settings/")
                        val body = readBody(session)
                        val value = JSONObject(body).optString("value", "")
                        settingsDao.upsert(DuckAiBridgeSettingEntity(key = key, value = value))
                        jsonResponse("""{"ok":true}""")
                    }

                    uri.startsWith("/debug/settings/") && method == Method.DELETE -> {
                        val key = uri.removePrefix("/debug/settings/")
                        settingsDao.delete(key)
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/settings" && method == Method.DELETE -> {
                        settingsDao.deleteAll()
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/chats" && method == Method.GET -> {
                        val arr = JSONArray()
                        chatsDao.getAll().forEach { arr.put(JSONObject(it.data)) }
                        jsonResponse(JSONObject().put("chats", arr).toString())
                    }

                    uri == "/debug/chats" && method == Method.POST -> {
                        val body = readBody(session)
                        val json = JSONObject(body)
                        val chatId = json.optString("chatId")
                        if (chatId.isBlank()) {
                            newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Missing chatId")
                        } else {
                            chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = body))
                            jsonResponse("""{"ok":true}""")
                        }
                    }

                    uri.startsWith("/debug/chats/") && method == Method.PUT -> {
                        val chatId = uri.removePrefix("/debug/chats/")
                        val body = readBody(session)
                        runCatching { JSONObject(body) }.getOrNull()
                            ?: return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Invalid JSON")
                        chatsDao.upsert(DuckAiBridgeChatEntity(chatId = chatId, data = body))
                        jsonResponse("""{"ok":true}""")
                    }

                    uri.startsWith("/debug/chats/") && method == Method.DELETE -> {
                        val chatId = uri.removePrefix("/debug/chats/")
                        chatsDao.delete(chatId)
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/chats" && method == Method.DELETE -> {
                        chatsDao.deleteAll()
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/files" && method == Method.GET -> {
                        val arr = JSONArray()
                        filesDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                            runCatching { JSONObject(file.readText()) }.getOrNull()?.let { json ->
                                // Infer MIME type from the first data URL found among string values
                                val inferredMime = json.keys().asSequence()
                                    .mapNotNull { k -> json.optString(k).takeIf { it.startsWith("data:") } }
                                    .firstOrNull()
                                    ?.let { dataUrl ->
                                        val end = dataUrl.indexOf(';').takeIf { it > 5 } ?: return@let null
                                        dataUrl.substring(5, end)
                                    } ?: ""
                                arr.put(
                                    JSONObject()
                                        .put("uuid", json.optString("uuid", file.name))
                                        .put("chatId", json.optString("chatId", ""))
                                        .put("fileName", json.optString("fileName", ""))
                                        .put("mimeType", inferredMime)
                                        .put("dataSize", file.length()),
                                )
                            }
                        }
                        jsonResponse(JSONObject().put("files", arr).toString())
                    }

                    uri.startsWith("/debug/files/") && method == Method.GET -> {
                        val uuid = uri.removePrefix("/debug/files/")
                        val candidate = File(filesDir, uuid).canonicalFile
                        if (candidate.parentFile?.canonicalFile != filesDir.canonicalFile) {
                            newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
                        } else if (!candidate.exists()) {
                            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                        } else {
                            jsonResponse(candidate.readText())
                        }
                    }

                    uri.startsWith("/debug/files/") && method == Method.DELETE -> {
                        val uuid = uri.removePrefix("/debug/files/")
                        val candidate = File(filesDir, uuid).canonicalFile
                        if (candidate.parentFile?.canonicalFile == filesDir.canonicalFile) {
                            candidate.delete()
                        }
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/files" && method == Method.DELETE -> {
                        filesDir.listFiles()?.forEach { it.delete() }
                        jsonResponse("""{"ok":true}""")
                    }

                    uri == "/debug/migration" && method == Method.DELETE -> {
                        settingsDao.delete("__duckAiMigrationDone")
                        jsonResponse("""{"ok":true}""")
                    }

                    else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
                }
            } catch (e: Exception) {
                logcat { "DuckAiDebugServer error: $e" }
                newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "Error")
            }
        }

        private fun readBody(session: IHTTPSession): String {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength <= 0) return "{}"
            val buf = ByteArray(contentLength)
            java.io.DataInputStream(session.inputStream).readFully(buf)
            return String(buf)
        }

        private fun jsonResponse(json: String) =
            newFixedLengthResponse(Response.Status.OK, "application/json", json)
                .also { it.addHeader("Access-Control-Allow-Origin", "*") }

        private fun serveHtml() =
            newFixedLengthResponse(Response.Status.OK, "text/html", DEBUG_HTML)
    }

    companion object {
        @Suppress("LongMethod")
        private val DEBUG_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>Duck.ai Native Storage Debug</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 24px; background: #f5f5f5; }
                h1 { color: #333; }
                h2 { color: #555; margin-top: 32px; border-bottom: 1px solid #ddd; padding-bottom: 8px; }
                table { width: 100%; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 1px 3px rgba(0,0,0,.1); }
                th { background: #eee; text-align: left; padding: 10px 12px; font-size: 13px; }
                td { padding: 10px 12px; border-top: 1px solid #f0f0f0; font-size: 13px; word-break: break-all; }
                td.nowrap { word-break: normal; white-space: nowrap; }
                button { padding: 5px 12px; border: none; border-radius: 4px; cursor: pointer; font-size: 12px; white-space: nowrap; }
                .del { background: #e53e3e; color: white; }
                .del:hover { background: #c53030; }
                .del-all { background: #e53e3e; color: white; padding: 8px 16px; margin-top: 8px; }
                .reset { background: #dd6b20; color: white; padding: 8px 16px; margin-top: 8px; }
                .add { background: #38a169; color: white; padding: 6px 14px; }
                .add:hover { background: #276749; }
                .edit-btn { background: #3182ce; color: white; }
                .edit-btn:hover { background: #2c5282; }
                .preview-btn { background: #805ad5; color: white; }
                .preview-btn:hover { background: #6b46c1; }
                .btns { display: flex; flex-direction: column; gap: 4px; align-items: flex-start; width: 1%; white-space: nowrap; }
                .add-form { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; align-items: flex-start; }
                .add-form input { padding: 6px 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; flex: 1; min-width: 120px; }
                .add-form textarea { padding: 6px 8px; border: 1px solid #ddd; border-radius: 4px; font-size: 13px; flex: 1; min-height: 60px; min-width: 220px; font-family: monospace; resize: vertical; }
                .status { display: inline-block; padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: bold; }
                .done { background: #c6f6d5; color: #276749; }
                .not-done { background: #fed7d7; color: #9b2c2c; }
                .actions { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
                .trunc { max-width: 260px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: block; }
                .model-badge { display: inline-block; padding: 2px 7px; border-radius: 10px; background: #e2e8f0; color: #4a5568; font-size: 11px; }
                /* Modals */
                .modal-overlay { display:none; position:fixed; top:0;left:0;right:0;bottom:0; background:rgba(0,0,0,.4); align-items:center; justify-content:center; z-index:100; }
                .modal-overlay.open { display:flex; }
                .modal-box { background:white; padding:24px; border-radius:8px; width:560px; max-width:95vw; max-height:90vh; display:flex; flex-direction:column; }
                .modal-title { font-weight:600; font-size:15px; margin-bottom:12px; }
                .modal-box input { width:100%; box-sizing:border-box; padding:8px; margin:8px 0 16px; border:1px solid #ddd; border-radius:4px; }
                .modal-box textarea { width:100%; box-sizing:border-box; padding:8px; border:1px solid #ddd; border-radius:4px; font-family:monospace; font-size:12px; flex:1; min-height:300px; resize:vertical; }
                .modal-actions { display:flex; gap:8px; justify-content:flex-end; margin-top:12px; }
                .btn-primary { background:#3182ce; color:white; padding:8px 16px; border:none; border-radius:4px; cursor:pointer; text-decoration:none; }
                .btn-cancel { background:#eee; color:#333; padding:8px 16px; border:none; border-radius:4px; cursor:pointer; }
                .modal-img { max-width:100%; max-height:60vh; border-radius:4px; display:block; margin:0 auto; }
                .img-meta { font-size:12px; color:#666; margin-top:8px; text-align:center; }
                .json-error { color: #e53e3e; font-size: 12px; margin-top: 4px; display:none; }
              </style>
            </head>
            <body>
              <h1>🦆 Duck.ai Native Storage Debug</h1>

              <h2>Migration</h2>
              <div id="migrationStatus"></div>
              <div class="actions">
                <button class="reset" onclick="resetMigration()">Reset Migration Flag</button>
              </div>

              <h2>Settings</h2>
              <table>
                <thead><tr><th>Key</th><th>Value</th><th></th></tr></thead>
                <tbody id="settingsBody"></tbody>
              </table>
              <div class="add-form">
                <input id="newSettingKey" type="text" placeholder="Key" />
                <input id="newSettingValue" type="text" placeholder="Value" />
                <button class="add" onclick="addSetting()">Add / Update</button>
              </div>
              <div class="actions">
                <button class="del-all" onclick="deleteAll('settings')">Delete All Settings</button>
              </div>

              <h2>Chats</h2>
              <table>
                <thead><tr><th>Title</th><th>Model</th><th style="text-align:center">Msgs</th><th>Last Edit</th><th></th></tr></thead>
                <tbody id="chatsBody"></tbody>
              </table>
              <div class="add-form">
                <textarea id="newChatJson" placeholder='{"chatId":"...","messages":[]}'></textarea>
                <button class="add" onclick="addChat()">Add Chat (JSON)</button>
              </div>
              <div class="actions">
                <button class="del-all" onclick="deleteAll('chats')">Delete All Chats</button>
              </div>

              <h2>Files</h2>
              <table>
                <thead><tr><th>UUID</th><th>Chat ID</th><th>File Name</th><th>MIME Type</th><th>Size</th><th></th></tr></thead>
                <tbody id="filesBody"></tbody>
              </table>
              <div class="actions">
                <button class="del-all" onclick="deleteAll('files')">Delete All Files</button>
              </div>

              <!-- Setting edit modal -->
              <div id="settingModal" class="modal-overlay">
                <div class="modal-box" style="width:400px">
                  <div class="modal-title" id="settingEditKey"></div>
                  <input id="settingEditValue" type="text" />
                  <div class="modal-actions">
                    <button class="btn-cancel" onclick="closeModal('settingModal')">Cancel</button>
                    <button class="btn-primary" onclick="saveSettingEdit()">Save</button>
                  </div>
                </div>
              </div>

              <!-- Chat JSON edit modal -->
              <div id="chatModal" class="modal-overlay">
                <div class="modal-box">
                  <div class="modal-title" id="chatModalTitle">Edit Chat</div>
                  <textarea id="chatEditJson" spellcheck="false"></textarea>
                  <div id="chatJsonError" class="json-error"></div>
                  <div class="modal-actions">
                    <button class="btn-cancel" onclick="closeModal('chatModal')">Cancel</button>
                    <button class="btn-primary" onclick="saveChatEdit()">Save</button>
                  </div>
                </div>
              </div>

              <!-- File inspect modal -->
              <div id="inspectModal" class="modal-overlay" onclick="closeModal('inspectModal')">
                <div class="modal-box" onclick="event.stopPropagation()">
                  <div class="modal-title" id="inspectTitle">Inspect File</div>
                  <div id="inspectContent" style="width:100%;overflow:auto;max-height:60vh"></div>
                  <div class="modal-actions">
                    <button class="btn-cancel" onclick="closeModal('inspectModal')">Close</button>
                  </div>
                </div>
              </div>

              <!-- File preview modal -->
              <div id="previewModal" class="modal-overlay" onclick="closeModal('previewModal')">
                <div class="modal-box" style="align-items:center" onclick="event.stopPropagation()">
                  <div class="modal-title" id="previewTitle">File Preview</div>
                  <div id="previewContent" style="width:100%;text-align:center"></div>
                  <div id="previewMeta" class="img-meta"></div>
                  <div class="modal-actions">
                    <a id="previewDownload" style="display:none" class="btn-primary" download>Download</a>
                    <button class="btn-cancel" onclick="closeModal('previewModal')">Close</button>
                  </div>
                </div>
              </div>

              <script>
                let editingSettingKey = null;
                let editingChatId = null;

                // ── Load ─────────────────────────────────────────────────────────────
                async function load() {
                  loadSettings(); loadChats(); loadFiles(); loadMigration();
                }

                async function loadMigration() {
                  const obj = await fetchJson('/debug/settings');
                  const done = '__duckAiMigrationDone' in obj;
                  document.getElementById('migrationStatus').innerHTML =
                    '<span class="status ' + (done ? 'done' : 'not-done') + '">' +
                    (done ? '✅ Migration done' : '❌ Not migrated') + '</span>';
                }

                async function loadSettings() {
                  const obj = await fetchJson('/debug/settings');
                  const tbody = document.getElementById('settingsBody');
                  const rows = Object.entries(obj).map(([k, v]) =>
                    '<tr><td class="nowrap">' + esc(k) + '</td><td>' + esc(v) + '</td><td class="btns">' +
                    '<button class="del" data-key="' + esc(k) + '" onclick="deleteSetting(this.dataset.key)">Delete</button>' +
                    '<button class="edit-btn" data-key="' + esc(k) + '" data-val="' + esc(v) + '" onclick="openSettingEdit(this.dataset.key, this.dataset.val)">Edit</button>' +
                    '</td></tr>'
                  );
                  tbody.innerHTML = rows.join('') || '<tr><td colspan="3"><em>No settings</em></td></tr>';
                }

                async function loadChats() {
                  const { chats } = await fetchJson('/debug/chats');
                  const tbody = document.getElementById('chatsBody');
                  const rows = chats.map(c => {
                    const title = c.title || c.chatId || '(no title)';
                    const model = c.model || '—';
                    const msgCount = Array.isArray(c.messages) ? c.messages.length : '—';
                    const lastEdit = c.lastEdit ? new Date(c.lastEdit).toLocaleString() : '—';
                    const jsonStr = esc(JSON.stringify(c, null, 2));
                    return '<tr>' +
                      '<td><span class="trunc" title="' + esc(title) + '">' + esc(title) + '</span></td>' +
                      '<td class="nowrap"><span class="model-badge">' + esc(model) + '</span></td>' +
                      '<td style="text-align:center">' + msgCount + '</td>' +
                      '<td class="nowrap">' + esc(lastEdit) + '</td>' +
                      '<td class="btns">' +
                        '<button class="edit-btn" data-id="' + esc(c.chatId) + '" data-json="' + jsonStr + '" onclick="openChatEdit(this.dataset.id, this.dataset.json)">Edit</button>' +
                        '<button class="del" data-id="' + esc(c.chatId) + '" onclick="deleteChat(this.dataset.id)">Delete</button>' +
                      '</td></tr>';
                  });
                  tbody.innerHTML = rows.join('') || '<tr><td colspan="5"><em>No chats</em></td></tr>';
                }

                async function loadFiles() {
                  const { files } = await fetchJson('/debug/files');
                  const tbody = document.getElementById('filesBody');
                  const rows = files.map(f => {
                    const sizeKb = f.dataSize > 1024 ? (f.dataSize / 1024).toFixed(1) + ' KB' : f.dataSize + ' B';
                    return '<tr>' +
                      '<td class="nowrap">' + esc(f.uuid) + '</td>' +
                      '<td class="nowrap">' + esc(f.chatId) + '</td>' +
                      '<td class="nowrap">' + esc(f.fileName || '—') + '</td>' +
                      '<td class="nowrap">' + esc(f.mimeType || '—') + '</td>' +
                      '<td class="nowrap">' + sizeKb + '</td>' +
                      '<td class="btns">' +
                        '<button class="preview-btn" data-uuid="' + esc(f.uuid) + '" onclick="inspectFile(this.dataset.uuid)">Inspect</button>' +
                        '<button class="preview-btn" data-uuid="' + esc(f.uuid) + '" onclick="previewFile(this.dataset.uuid)">Preview</button>' +
                        '<button class="preview-btn" data-uuid="' + esc(f.uuid) + '" onclick="downloadFile(this.dataset.uuid)">Download</button>' +
                        '<button class="del" data-uuid="' + esc(f.uuid) + '" onclick="deleteFile(this.dataset.uuid)">Delete</button>' +
                      '</td></tr>';
                  });
                  tbody.innerHTML = rows.join('') || '<tr><td colspan="6"><em>No files</em></td></tr>';
                }

                // ── CRUD ─────────────────────────────────────────────────────────────
                async function deleteSetting(key) {
                  await fetch('/debug/settings/' + encodeURIComponent(key), { method: 'DELETE' });
                  loadSettings(); loadMigration();
                }

                async function deleteChat(chatId) {
                  await fetch('/debug/chats/' + encodeURIComponent(chatId), { method: 'DELETE' });
                  loadChats();
                }

                async function deleteFile(uuid) {
                  await fetch('/debug/files/' + encodeURIComponent(uuid), { method: 'DELETE' });
                  loadFiles();
                }

                function findDataUrl(file) {
                  // Case 1: some key already contains a full data URL
                  for (const k of Object.keys(file)) {
                    const v = file[k];
                    if (typeof v === 'string' && v.startsWith('data:')) return v;
                  }
                  // Case 2: FE stores raw base64 in 'data' with 'mimeType' alongside
                  if (file.data && file.mimeType) {
                    return 'data:' + file.mimeType + ';base64,' + file.data;
                  }
                  return null;
                }

                async function downloadFile(uuid) {
                  const file = await fetchJson('/debug/files/' + encodeURIComponent(uuid));
                  const data = findDataUrl(file);
                  if (!data) { alert('No data URL found in file. Keys: ' + Object.keys(file).join(', ')); return; }
                  const mime = data.substring(5, data.indexOf(';'));
                  const ext = mime.split('/')[1] || 'bin';
                  const a = document.createElement('a');
                  a.href = data;
                  a.download = file.fileName || (uuid + '.' + ext);
                  a.click();
                }

                async function deleteAll(type) {
                  if (!confirm('Delete all ' + type + '?')) return;
                  await fetch('/debug/' + type, { method: 'DELETE' });
                  load();
                }

                async function resetMigration() {
                  await fetch('/debug/migration', { method: 'DELETE' });
                  loadMigration(); loadSettings();
                }

                async function addSetting() {
                  const key = document.getElementById('newSettingKey').value.trim();
                  const value = document.getElementById('newSettingValue').value;
                  if (!key) { alert('Key is required'); return; }
                  await fetch('/debug/settings/' + encodeURIComponent(key), {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ value })
                  });
                  document.getElementById('newSettingKey').value = '';
                  document.getElementById('newSettingValue').value = '';
                  loadSettings(); loadMigration();
                }

                async function addChat() {
                  const raw = document.getElementById('newChatJson').value.trim();
                  if (!raw) return;
                  let parsed;
                  try { parsed = JSON.parse(raw); } catch(e) { alert('Invalid JSON: ' + e.message); return; }
                  if (!parsed.chatId) { alert('JSON must have a "chatId" field'); return; }
                  await fetch('/debug/chats', {
                    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: raw
                  });
                  document.getElementById('newChatJson').value = '';
                  loadChats();
                }

                // ── Setting edit modal ───────────────────────────────────────────────
                function openSettingEdit(key, value) {
                  editingSettingKey = key;
                  document.getElementById('settingEditKey').textContent = key;
                  document.getElementById('settingEditValue').value = value;
                  openModal('settingModal');
                }

                async function saveSettingEdit() {
                  const value = document.getElementById('settingEditValue').value;
                  await fetch('/debug/settings/' + encodeURIComponent(editingSettingKey), {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ value })
                  });
                  closeModal('settingModal'); loadSettings();
                }

                // ── Chat edit modal ──────────────────────────────────────────────────
                function openChatEdit(chatId, jsonStr) {
                  editingChatId = chatId;
                  document.getElementById('chatModalTitle').textContent = 'Edit Chat — ' + chatId;
                  document.getElementById('chatEditJson').value = jsonStr;
                  document.getElementById('chatJsonError').style.display = 'none';
                  openModal('chatModal');
                }

                async function saveChatEdit() {
                  const raw = document.getElementById('chatEditJson').value;
                  const errEl = document.getElementById('chatJsonError');
                  let parsed;
                  try { parsed = JSON.parse(raw); } catch(e) {
                    errEl.textContent = 'Invalid JSON: ' + e.message;
                    errEl.style.display = 'block';
                    return;
                  }
                  errEl.style.display = 'none';
                  await fetch('/debug/chats/' + encodeURIComponent(editingChatId), {
                    method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: raw
                  });
                  closeModal('chatModal'); loadChats();
                }

                // ── File preview modal ───────────────────────────────────────────────
                async function previewFile(uuid) {
                  document.getElementById('previewTitle').textContent = 'Preview — ' + uuid;
                  document.getElementById('previewContent').innerHTML = '';
                  document.getElementById('previewMeta').textContent = 'Loading…';
                  document.getElementById('previewDownload').style.display = 'none';
                  openModal('previewModal');

                  const file = await fetchJson('/debug/files/' + encodeURIComponent(uuid));
                  const data = findDataUrl(file) || '';
                  const sizeKb = (data.length / 1024).toFixed(1);
                  const meta = 'Chat: ' + (file.chatId || '—') + ' · ' + sizeKb + ' KB';

                  if (!data) {
                    document.getElementById('previewContent').textContent = 'No data URL found. Keys: ' + Object.keys(file).join(', ');
                    document.getElementById('previewMeta').textContent = meta;
                    return;
                  }

                  // Parse MIME type from data URL: data:<mime>;base64,...
                  const mime = data.substring(5, data.indexOf(';'));
                  const dlLink = document.getElementById('previewDownload');
                  dlLink.href = data;
                  dlLink.download = uuid + '.' + (mime.split('/')[1] || 'bin');
                  dlLink.style.display = 'inline-block';

                  const content = document.getElementById('previewContent');
                  if (mime.startsWith('image/')) {
                    content.innerHTML = '<img src="' + data + '" class="modal-img" alt="Preview" />';
                  } else if (mime === 'application/pdf') {
                    content.innerHTML = '<embed src="' + data + '" type="application/pdf" style="width:100%;height:60vh;border:none;" />';
                  } else if (mime.startsWith('video/')) {
                    content.innerHTML = '<video src="' + data + '" controls style="max-width:100%;max-height:60vh"></video>';
                  } else if (mime.startsWith('audio/')) {
                    content.innerHTML = '<audio src="' + data + '" controls style="width:100%"></audio>';
                  } else if (mime.startsWith('text/')) {
                    const text = atob(data.split(',')[1]);
                    content.innerHTML = '<pre style="text-align:left;max-height:60vh;overflow:auto;font-size:12px;white-space:pre-wrap">' + esc(text) + '</pre>';
                  } else {
                    content.textContent = mime + ' — use Download to open.';
                  }
                  document.getElementById('previewMeta').textContent = meta + ' · ' + mime;
                }

                async function inspectFile(uuid) {
                  document.getElementById('inspectTitle').textContent = 'Inspect — ' + uuid;
                  document.getElementById('inspectContent').innerHTML = 'Loading…';
                  openModal('inspectModal');

                  const file = await fetchJson('/debug/files/' + encodeURIComponent(uuid));
                  const rows = Object.entries(file).map(([k, v]) => {
                    let display;
                    if (typeof v === 'string' && v.startsWith('data:')) {
                      const mime = v.substring(5, v.indexOf(';'));
                      const sizeKb = (v.length * 0.75 / 1024).toFixed(1);
                      display = '<em>[data URL: ' + esc(mime) + ', ~' + sizeKb + ' KB]</em>';
                    } else {
                      display = '<code>' + esc(typeof v === 'object' ? JSON.stringify(v) : String(v)) + '</code>';
                    }
                    return '<tr><td class="nowrap" style="vertical-align:top"><strong>' + esc(k) + '</strong></td><td>' + display + '</td></tr>';
                  });
                  document.getElementById('inspectContent').innerHTML =
                    '<table style="width:100%"><tbody>' + rows.join('') + '</tbody></table>';
                }


                // ── Modal helpers ────────────────────────────────────────────────────
                function openModal(id) { document.getElementById(id).classList.add('open'); }
                function closeModal(id) { document.getElementById(id).classList.remove('open'); }

                // ── Utilities ────────────────────────────────────────────────────────
                async function fetchJson(path) {
                  const r = await fetch(path);
                  return r.json();
                }

                function esc(s) {
                  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
                }

                // Close modals on Escape
                document.addEventListener('keydown', e => {
                  if (e.key === 'Escape') {
                    ['settingModal','chatModal','previewModal'].forEach(id => closeModal(id));
                  }
                });

                load();
              </script>
            </body>
            </html>
        """.trimIndent()
    }
}
