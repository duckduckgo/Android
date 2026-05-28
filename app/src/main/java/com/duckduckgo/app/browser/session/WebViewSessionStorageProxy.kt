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

import android.webkit.WebView
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WebViewSessionStorageProxy @Inject constructor(
    private val roomBacked: RoomWebViewSessionStorage,
    private val inMemory: InMemoryWebViewSessionStorage,
    private val browserConfig: AndroidBrowserConfigFeature,
) : WebViewSessionStorage {

    private val useRoomBacked by lazy { browserConfig.webViewSessionPersistence().isEnabled() }

    private val active: WebViewSessionStorage
        get() = if (useRoomBacked) roomBacked else inMemory

    override fun saveSession(webView: WebView?, tabId: String) =
        active.saveSession(webView, tabId)

    override suspend fun restoreSession(webView: WebView?, tabId: String): Boolean =
        active.restoreSession(webView, tabId)

    override fun deleteSession(tabId: String) =
        active.deleteSession(tabId)

    override suspend fun deleteAllSessions() =
        active.deleteAllSessions()
}
