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

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.NewDownloadState
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealNewDownloadState @Inject constructor(
    sharedPreferencesProvider: SharedPreferencesProvider,
) : NewDownloadState {

    // No migration from the previous SettingsDataStore-backed flag: it is a single self-correcting
    // boolean, so on upgrade it simply defaults to false and is set again on the next completed download.
    private val preferences: SharedPreferences =
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = false)

    private val _hasNewDownloadFlow = MutableStateFlow(preferences.getBoolean(KEY_HAS_NEW_DOWNLOAD, false))
    override val hasNewDownloadFlow: Flow<Boolean> = _hasNewDownloadFlow.asStateFlow()

    override fun hasNewDownload(): Boolean = _hasNewDownloadFlow.value

    override fun onDownloadComplete() {
        setHasNewDownload(true)
    }

    override fun onDownloadsScreenViewed() {
        setHasNewDownload(false)
    }

    private fun setHasNewDownload(value: Boolean) {
        preferences.edit { putBoolean(KEY_HAS_NEW_DOWNLOAD, value) }
        _hasNewDownloadFlow.value = value
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.downloads.newdownloadstate.prefs"
        private const val KEY_HAS_NEW_DOWNLOAD = "KEY_HAS_NEW_DOWNLOAD"
    }
}
