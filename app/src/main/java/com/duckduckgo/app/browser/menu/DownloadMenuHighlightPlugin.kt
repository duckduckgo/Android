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

package com.duckduckgo.app.browser.menu

import androidx.core.content.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.FileDownloadCallbackPlugin
import com.duckduckgo.downloads.api.NewDownloadState
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesMultibinding(AppScope::class, boundType = BrowserMenuHighlightPlugin::class)
@ContributesMultibinding(AppScope::class, boundType = FileDownloadCallbackPlugin::class)
class DownloadMenuHighlightPlugin @Inject constructor(
    private val newDownloadState: NewDownloadState,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : BrowserMenuHighlightPlugin, FileDownloadCallbackPlugin {

    private val preferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME)
    }

    private val seenState = MutableStateFlow<Boolean?>(null)

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            val persisted = preferences.getBoolean(KEY_SEEN, false)
            seenState.compareAndSet(null, persisted)
        }
    }

    private val seen: Flow<Boolean> = seenState.filterNotNull()

    override fun isHighlighted(mode: BrowserViewMode): Flow<Boolean> {
        if (mode == BrowserViewMode.CustomTab) return flowOf(false)
        return combine(newDownloadState.hasNewDownloadFlow, seen) { has, seen -> has && !seen }
    }

    override fun onFileDownloaded() {
        setSeen(false)
    }

    fun onDownloadMenuHighlightAcknowledged() {
        setSeen(true)
    }

    private fun setSeen(seen: Boolean) {
        preferences.edit {
            putBoolean(KEY_SEEN, seen)
        }
        seenState.value = seen
    }

    companion object {
        const val FILENAME = "com.duckduckgo.app.browser.menu.download.highlight"
        const val KEY_SEEN = "SEEN"
    }
}
