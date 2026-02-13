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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.downloads.impl.di.Downloads
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface FailedDownloadRetryUrlStore {
    suspend fun saveRetryUrl(downloadId: Long, url: String)
    suspend fun getRetryUrl(downloadId: Long): String?
    suspend fun removeRetryUrl(downloadId: Long)
    suspend fun clear()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesFailedDownloadRetryUrlStore @Inject constructor(
    @Downloads private val store: DataStore<Preferences>,
) : FailedDownloadRetryUrlStore {

    override suspend fun saveRetryUrl(downloadId: Long, url: String) {
        store.edit { it[keyForDownloadId(downloadId)] = url }
    }

    override suspend fun getRetryUrl(downloadId: Long): String? {
        return store.data.firstOrNull()?.get(keyForDownloadId(downloadId))
    }

    override suspend fun removeRetryUrl(downloadId: Long) {
        store.edit { it.remove(keyForDownloadId(downloadId)) }
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    private fun keyForDownloadId(downloadId: Long): Preferences.Key<String> =
        stringPreferencesKey("$KEY_PREFIX$downloadId")

    companion object {
        private const val KEY_PREFIX = "retry_url_"
    }
}
