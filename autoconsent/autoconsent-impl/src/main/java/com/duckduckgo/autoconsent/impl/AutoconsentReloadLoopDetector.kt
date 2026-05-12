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

package com.duckduckgo.autoconsent.impl

import android.net.Uri
import android.webkit.WebView
import androidx.core.net.toUri
import com.duckduckgo.autoconsent.impl.pixels.AutoConsentPixel
import com.duckduckgo.autoconsent.impl.pixels.AutoconsentPixelManager
import com.duckduckgo.di.scopes.AppScope
import dagger.SingleInstanceIn
import logcat.logcat
import java.util.WeakHashMap
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
class AutoconsentReloadLoopDetector @Inject constructor(
    private val autoconsentPixelManager: AutoconsentPixelManager,
) {

    private data class TabState(
        var lastUrl: Uri? = null,
        var lastHandledCMP: String? = null,
        var reloadLoopDetected: Boolean = false,
    )

    private val tabStates: MutableMap<WebView, TabState> = WeakHashMap()

    fun updateUrl(webView: WebView, url: String) {
        val newUri = url.toUri()
        val state = tabStates.getOrPut(webView) { TabState() }
        val oldUri = state.lastUrl

        if (oldUri != null && !isSamePageUrl(oldUri, newUri)) {
            logcat { "URL changed from $oldUri to $newUri, clearing reload loop state" }
            state.lastHandledCMP = null
            state.reloadLoopDetected = false
        }
        state.lastUrl = newUri
    }

    fun detectReloadLoop(webView: WebView, cmp: String) {
        val state = tabStates[webView] ?: return
        if (!state.reloadLoopDetected && state.lastHandledCMP == cmp) {
            logcat { "Reload loop detected: $cmp on ${state.lastUrl}" }
            state.reloadLoopDetected = true
            autoconsentPixelManager.fireDailyPixel(AutoConsentPixel.AUTOCONSENT_ERROR_RELOAD_LOOP_DAILY)
        }
    }

    fun rememberLastHandledCMP(webView: WebView, cmp: String, isCosmetic: Boolean) {
        val state = tabStates.getOrPut(webView) { TabState() }
        if (isCosmetic) {
            state.lastHandledCMP = null
            state.reloadLoopDetected = false
            return
        }
        if (state.lastHandledCMP != cmp) {
            state.lastHandledCMP = null
            state.reloadLoopDetected = false
        }
        state.lastHandledCMP = cmp
    }

    fun isReloadLoopDetected(webView: WebView): Boolean {
        return tabStates[webView]?.reloadLoopDetected == true
    }

    fun getLastHandledCMP(webView: WebView): String? {
        return tabStates[webView]?.lastHandledCMP
    }

    private fun isSamePageUrl(a: Uri, b: Uri): Boolean {
        return a.scheme == b.scheme && a.host == b.host && a.path == b.path
    }
}
