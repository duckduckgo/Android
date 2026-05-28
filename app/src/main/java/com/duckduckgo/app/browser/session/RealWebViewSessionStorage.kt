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

package com.duckduckgo.app.browser.session

import android.os.Bundle
import android.os.Parcel
import android.webkit.WebView
import com.duckduckgo.app.browser.navigation.safeCopyBackForwardList
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.INFO
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class RealWebViewSessionStorage @Inject constructor(
    private val dao: WebViewSessionDao,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
    private val pixel: Pixel,
) : WebViewSessionStorage {

    override fun saveSession(webView: WebView?, tabId: String) {
        webView ?: return logcat(WARN) { "WebView is null; cannot save session" }

        val bundle = Bundle().also { webView.saveState(it) }
        val bytes = bundle.toMarshalledBytes() ?: return

        if (bytes.size > LARGE_SESSION_THRESHOLD_BYTES) {
            pixel.fire(AppPixelName.WEBVIEW_SESSION_LARGE_BYTES)
        }

        appScope.launch(dispatchers.io()) {
            runCatching {
                dao.upsert(WebViewSessionEntity(tabId, bytes, System.currentTimeMillis()))
                logcat(INFO) { "Saved WebView session for $tabId (${bytes.size} bytes)" }
            }.onFailure { t -> logcat(WARN) { "Failed to save session for $tabId: ${t.asLog()}" } }
        }
    }

    override suspend fun restoreSession(webView: WebView?, tabId: String): Boolean {
        webView ?: return false

        val bytes = withContext(dispatchers.io()) {
            runCatching { dao.get(tabId)?.sessionBundle }.getOrNull()
        } ?: return false

        val bundle = bytes.toBundle() ?: return false

        return runCatching {
            webView.restoreState(bundle)
            (webView.safeCopyBackForwardList()?.size ?: 0) > 0
        }.getOrDefault(false)
    }

    override fun deleteSession(tabId: String) {
        appScope.launch(dispatchers.io()) {
            runCatching { dao.delete(tabId) }
                .onFailure { t -> logcat(WARN) { "Failed to delete session for $tabId: ${t.asLog()}" } }
        }
    }

    override suspend fun deleteAllSessions() {
        withContext(dispatchers.io()) {
            runCatching { dao.deleteAll() }
                .onFailure { t -> logcat(WARN) { "Failed to delete all sessions: ${t.asLog()}" } }
        }
    }

    private fun Bundle.toMarshalledBytes(): ByteArray? {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(this)
            parcel.marshall()
        } catch (t: Throwable) {
            logcat(WARN) { "Failed to marshal session bundle: ${t.asLog()}" }
            null
        } finally {
            parcel.recycle()
        }
    }

    private fun ByteArray.toBundle(): Bundle? {
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(this, 0, size)
            parcel.setDataPosition(0)
            parcel.readBundle(WebView::class.java.classLoader)
        } catch (t: Throwable) {
            logcat(WARN) { "Failed to unmarshal session bundle: ${t.asLog()}" }
            null
        } finally {
            parcel.recycle()
        }
    }

    companion object {
        private const val LARGE_SESSION_THRESHOLD_BYTES = 256 * 1024 // 256 KiB
    }
}
