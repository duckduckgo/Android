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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface FailedDownloadRetryUrlStore {
    fun saveRetryUrl(downloadId: Long, url: String)
    fun getRetryUrl(downloadId: Long): String?
    fun removeRetryUrl(downloadId: Long)
    fun clear()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesFailedDownloadRetryUrlStore @Inject constructor(
    private val context: Context,
) : FailedDownloadRetryUrlStore {

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override fun saveRetryUrl(downloadId: Long, url: String) {
        preferences.edit {
            putString(keyForDownloadId(downloadId), url)
        }
    }

    override fun getRetryUrl(downloadId: Long): String? {
        return preferences.getString(keyForDownloadId(downloadId), null)
    }

    override fun removeRetryUrl(downloadId: Long) {
        preferences.edit {
            remove(keyForDownloadId(downloadId))
        }
    }

    override fun clear() {
        preferences.edit {
            clear()
        }
    }

    private fun keyForDownloadId(downloadId: Long): String = "$KEY_PREFIX$downloadId"

    companion object {
        const val FILENAME = "com.duckduckgo.downloads.retry_urls"
        private const val KEY_PREFIX = "retry_url_"
    }
}
