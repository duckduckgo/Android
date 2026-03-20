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

package com.duckduckgo.downloads.impl.ui

import android.content.Context
import android.content.SharedPreferences
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.api.DownloadMenuStateProvider
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealDownloadMenuStateProvider @Inject constructor(
    private val context: Context,
) : DownloadMenuStateProvider {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun hasNewDownload(): Boolean = preferences.getBoolean(KEY_HAS_NEW_DOWNLOAD, false)

    override fun onDownloadComplete() {
        preferences.edit().putBoolean(KEY_HAS_NEW_DOWNLOAD, true).apply()
    }

    override fun onDownloadsScreenViewed() {
        preferences.edit().putBoolean(KEY_HAS_NEW_DOWNLOAD, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "com.duckduckgo.downloads.menu_state"
        private const val KEY_HAS_NEW_DOWNLOAD = "has_new_download"
    }
}
