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

package com.duckduckgo.duckchat.impl

import android.net.Uri
import android.webkit.WebView
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.api.JsInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.localserver.api.DuckAiLocalServer
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class DuckAiMigrationJsPlugin @Inject constructor(
    private val localServer: DuckAiLocalServer,
    private val feature: DuckChatFeature,
) : JsInjectorPlugin {

    override fun onPageStarted(
        webView: WebView,
        url: String?,
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ) {
        if (url == null || !isDuckAiUrl(url)) return
        val script = when {
            feature.bridgeMigration().isEnabled() -> bridgeMigrationScript()
            feature.httpServerMigration().isEnabled() -> httpMigrationScript(localServer.port)
            else -> return
        }
        webView.evaluateJavascript(script, null)
    }

    override fun onPageFinished(webView: WebView, url: String?, site: Site?) {
        // Migration script runs at page start; nothing needed here.
    }

    private fun isDuckAiUrl(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        return host == "duck.ai" || (host == "duckduckgo.com" && uri.path?.startsWith("/duckchat/") == true)
    }

    private fun httpMigrationScript(port: Int): String = """
        (function __duckAiMigrate() {
            var base = 'http://127.0.0.1:$port';
            fetch(base + '/migration')
                .then(function(r) { return r.json(); })
                .then(function(status) {
                    if (status && status.done) return;
                    var settings = {};
                    for (var i = 0; i < localStorage.length; i++) {
                        var k = localStorage.key(i);
                        settings[k] = localStorage.getItem(k);
                    }
                    return fetch(base + '/settings', {
                        method: 'PUT',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify(settings)
                    }).then(function() {
                        return new Promise(function(resolve) {
                            var req = indexedDB.open('savedAIChatData');
                            req.onerror = function() { resolve(); };
                            req.onsuccess = function(e) {
                                var db = e.target.result;
                                if (!db.objectStoreNames.contains('saved-chats')) { resolve(); return; }
                                var tx = db.transaction('saved-chats', 'readonly');
                                tx.objectStore('saved-chats').getAll().onsuccess = function(e) {
                                    var chats = e.target.result || [];
                                    if (chats.length === 0) { resolve(); return; }
                                    Promise.all(chats.map(function(chat) {
                                        return fetch(base + '/chats/' + encodeURIComponent(chat.chatId), {
                                            method: 'PUT',
                                            headers: { 'Content-Type': 'application/json' },
                                            body: JSON.stringify(chat)
                                        }).catch(function() {});
                                    })).then(resolve).catch(resolve);
                                };
                                tx.onerror = function() { resolve(); };
                            };
                        });
                    }).then(function() {
                        return new Promise(function(resolve) {
                            var req = indexedDB.open('savedAIChatData');
                            req.onerror = function() { resolve(); };
                            req.onsuccess = function(e) {
                                var db = e.target.result;
                                if (!db.objectStoreNames.contains('chat-images')) { resolve(); return; }
                                var tx = db.transaction('chat-images', 'readonly');
                                tx.objectStore('chat-images').getAll().onsuccess = function(e) {
                                    var images = e.target.result || [];
                                    if (images.length === 0) { resolve(); return; }
                                    Promise.all(images.map(function(img) {
                                        return new Promise(function(imgResolve) {
                                            if (!img.file) { imgResolve(); return; }
                                            var reader = new FileReader();
                                            reader.onload = function() {
                                                var payload = { uuid: img.uuid, chatId: img.chatId, data: reader.result };
                                                fetch(base + '/images/' + encodeURIComponent(img.uuid), {
                                                    method: 'PUT',
                                                    headers: { 'Content-Type': 'application/json' },
                                                    body: JSON.stringify(payload)
                                                }).then(imgResolve).catch(imgResolve);
                                            };
                                            reader.onerror = function() { imgResolve(); };
                                            reader.readAsDataURL(img.file);
                                        });
                                    })).then(resolve).catch(resolve);
                                };
                                tx.onerror = function() { resolve(); };
                            };
                        });
                    }).then(function() {
                        return fetch(base + '/migration', { method: 'POST' });
                    });
                })
                .catch(function(e) { console.warn('[DuckAI] migration error:', e); });
        })();
    """.trimIndent()

    private fun bridgeMigrationScript(): String = """
        (function __duckAiMigrate() {
            if (!window.MigrationBridge) return;

            window.MigrationBridge.onmessage = function(event) {
                var msg = JSON.parse(event.data);
                if (msg.action !== 'isDone') return;
                if (msg.value) return;
                runMigration();
            };
            window.MigrationBridge.postMessage(JSON.stringify({action: 'isDone'}));

            function runMigration() {
                var settings = {};
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    settings[k] = localStorage.getItem(k);
                }
                if (window.SettingsBridge) {
                    window.SettingsBridge.postMessage(JSON.stringify({action: 'replaceAllSettings', data: settings}));
                }
                new Promise(function(resolve) {
                    var req = indexedDB.open('savedAIChatData');
                    req.onerror = function() { resolve(); };
                    req.onsuccess = function(e) {
                        var db = e.target.result;
                        if (!db.objectStoreNames.contains('saved-chats')) { resolve(); return; }
                        var tx = db.transaction('saved-chats', 'readonly');
                        tx.objectStore('saved-chats').getAll().onsuccess = function(e) {
                            var chats = e.target.result || [];
                            chats.forEach(function(chat) {
                                if (window.ChatsBridge) {
                                    window.ChatsBridge.postMessage(JSON.stringify({action: 'putChat', chatId: chat.chatId, data: chat}));
                                }
                            });
                            resolve();
                        };
                        tx.onerror = function() { resolve(); };
                    };
                }).then(function() {
                    return new Promise(function(resolve) {
                        var req = indexedDB.open('savedAIChatData');
                        req.onerror = function() { resolve(); };
                        req.onsuccess = function(e) {
                            var db = e.target.result;
                            if (!db.objectStoreNames.contains('chat-images')) { resolve(); return; }
                            var tx = db.transaction('chat-images', 'readonly');
                            tx.objectStore('chat-images').getAll().onsuccess = function(e) {
                                var images = e.target.result || [];
                                if (images.length === 0) { resolve(); return; }
                                Promise.all(images.map(function(img) {
                                    return new Promise(function(imgResolve) {
                                        if (!img.file) { imgResolve(); return; }
                                        var reader = new FileReader();
                                        reader.onload = function() {
                                            var payload = {uuid: img.uuid, chatId: img.chatId, data: reader.result};
                                            if (window.ImagesBridge) {
                                                window.ImagesBridge.postMessage(JSON.stringify({action: 'putImage', uuid: img.uuid, data: payload}));
                                            }
                                            imgResolve();
                                        };
                                        reader.onerror = function() { imgResolve(); };
                                        reader.readAsDataURL(img.file);
                                    });
                                })).then(resolve).catch(resolve);
                            };
                            tx.onerror = function() { resolve(); };
                        };
                    });
                }).then(function() {
                    if (window.MigrationBridge) {
                        window.MigrationBridge.postMessage(JSON.stringify({action: 'markDone'}));
                    }
                }).catch(function(e) { console.warn('[DuckAI] bridge migration error:', e); });
            }
        })();
    """.trimIndent()
}
