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

package com.duckduckgo.downloads.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.impl.di.DownloadMenu
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DownloadMenuStateProvider {
    val hasNewDownloadFlow: Flow<Boolean>
    fun hasNewDownload(): Boolean
    fun onDownloadComplete()
    fun onDownloadsScreenViewed()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadMenuStateProvider @Inject constructor(
    @DownloadMenu private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : DownloadMenuStateProvider {

    private object Keys {
        val HAS_NEW_DOWNLOAD = booleanPreferencesKey("has_new_download")
    }

    private val _hasNewDownloadFlow = MutableStateFlow(false)
    override val hasNewDownloadFlow: Flow<Boolean> = _hasNewDownloadFlow.asStateFlow()

    init {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.data.collect { prefs ->
                _hasNewDownloadFlow.value = prefs[Keys.HAS_NEW_DOWNLOAD] ?: false
            }
        }
    }

    override fun hasNewDownload(): Boolean = _hasNewDownloadFlow.value

    override fun onDownloadComplete() {
        _hasNewDownloadFlow.value = true
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.edit { prefs -> prefs[Keys.HAS_NEW_DOWNLOAD] = true }
        }
    }

    override fun onDownloadsScreenViewed() {
        _hasNewDownloadFlow.value = false
        appCoroutineScope.launch(dispatcherProvider.io()) {
            store.edit { prefs -> prefs[Keys.HAS_NEW_DOWNLOAD] = false }
        }
    }
}
