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

package com.duckduckgo.app.fire.clearing

import android.content.Context
import com.duckduckgo.app.global.file.FileDeleter
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.webViewDataDir
import com.duckduckgo.dataclearing.api.plugin.ClearableData
import com.duckduckgo.dataclearing.api.plugin.DataClearingPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import java.io.File
import javax.inject.Inject

/**
 * Wipes the on-disk remainder of the Fire WebView profile (IndexedDB, cache, service workers, …).
 * Excludes `Cookies` and `Local Storage`, which the cookies/web-storage plugins own.
 */
@ContributesMultibinding(AppScope::class)
class WebViewDirectoriesDataClearingPlugin @Inject constructor(
    private val context: Context,
    private val fileDeleter: FileDeleter,
) : DataClearingPlugin {

    override suspend fun onClearData(types: Set<ClearableData>) {
        types.forEach { type ->
            val browserMode = when (type) {
                is ClearableData.BrowserData.All -> BrowserMode.FIRE
                is ClearableData.BrowserData.AllForMode -> type.mode
                else -> null
            }
            if (browserMode == BrowserMode.FIRE) performDelete(browserMode)
        }
    }

    private suspend fun performDelete(browserMode: BrowserMode) {
        val dir = File(context.applicationInfo.dataDir, browserMode.webViewDataDir)
        logcat { "Wiping $browserMode WebView profile dir: $dir" }
        fileDeleter.deleteContents(dir, listOf("Cookies", "Local Storage"))
    }
}
