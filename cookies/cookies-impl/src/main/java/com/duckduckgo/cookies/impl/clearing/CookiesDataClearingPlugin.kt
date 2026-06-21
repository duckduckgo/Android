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

package com.duckduckgo.cookies.impl.clearing

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject
import kotlin.coroutines.resume

/** Clears the Fire WebView profile's cookies (all of them — Fire keeps nothing). */
@ContributesMultibinding(AppScope::class)
class CookiesDataClearingPlugin @Inject constructor(
    private val cookieManagerModeProvider: BrowserModeDataProvider<CookieManager>,
    private val dispatchers: DispatcherProvider,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            val browserMode = when (type) {
                is ClearableData.BrowserData.All -> BrowserMode.FIRE // every mode → clear Fire
                is ClearableData.BrowserData.AllForMode -> type.mode
                else -> null
            }
            if (browserMode == BrowserMode.FIRE) performDelete(browserMode)
        }
    }

    private suspend fun performDelete(browserMode: BrowserMode) {
        withContext(dispatchers.main()) {
            val cookieManager = cookieManagerModeProvider.forMode(browserMode)
            logcat { "Clearing all $browserMode cookies" }
            suspendCancellableCoroutine { continuation ->
                cookieManager.removeAllCookies { continuation.resume(Unit) }
            }
            cookieManager.flush()
        }
    }
}
