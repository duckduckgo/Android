/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.connectionclass

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ConnectionQualityStore @Inject constructor(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(FILENAME, multiprocess = true, migrate = false)
    }
    private var connectionLatencyMs: Int
        get() = preferences.getInt(CONNECTION_QUALITY_TAG, -1)
        set(value) = preferences.edit { putInt(CONNECTION_QUALITY_TAG, value) }

    internal suspend fun saveConnectionLatency(value: Int) = withContext(dispatcherProvider.io()) {
        connectionLatencyMs = value
    }

    suspend fun getConnectionLatency(): Int = withContext(dispatcherProvider.io()) {
        return@withContext connectionLatencyMs
    }

    internal suspend fun reset() = withContext(dispatcherProvider.io()) {
        preferences.edit { clear() }
    }

    companion object {
        private const val FILENAME = "com.duckduckgo.networkprotection.internal.connectionclass.v1"
        private const val CONNECTION_QUALITY_TAG = "CONNECTION_QUALITY_TAG"
    }
}
