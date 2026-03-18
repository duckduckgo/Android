/*
 * Copyright (c) 2025 DuckDuckGo
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

import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface DownloadMenuStateProvider {
    /**
     * Reactive flow indicating whether there is a new download that has not been viewed by the user yet.
     */
    val hasNewDownloadFlow: Flow<Boolean>

    /**
     * Indicates whether there is a new download that has not been viewed by the user yet.
     */
    fun hasNewDownload(): Boolean

    /**
     * Indicates that a new download has completed, and the user should be notified about it until they view the downloads screen.
     */
    fun onDownloadComplete()

    /**
     * Indicates that the user has viewed the downloads screen, and any new download notifications can be cleared.
     */
    fun onDownloadsScreenViewed()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadMenuStateProvider @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : DownloadMenuStateProvider {

    private val _hasNewDownloadFlow = MutableStateFlow(settingsDataStore.hasNewDownload)
    override val hasNewDownloadFlow: Flow<Boolean> = _hasNewDownloadFlow.asStateFlow()

    override fun hasNewDownload(): Boolean = settingsDataStore.hasNewDownload

    override fun onDownloadComplete() {
        settingsDataStore.hasNewDownload = true
        _hasNewDownloadFlow.value = true
    }

    override fun onDownloadsScreenViewed() {
        settingsDataStore.hasNewDownload = false
        _hasNewDownloadFlow.value = false
    }
}
