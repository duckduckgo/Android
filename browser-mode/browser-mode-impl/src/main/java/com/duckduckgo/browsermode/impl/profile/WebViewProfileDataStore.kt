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

package com.duckduckgo.browsermode.impl.profile

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

interface WebViewProfileDataStore {
    suspend fun getProfileIndex(mode: BrowserMode): Int
    suspend fun incrementProfileIndex(mode: BrowserMode): Int
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealWebViewProfileDataStore @Inject constructor(
    @param:WebViewProfileData private val store: DataStore<Preferences>,
) : WebViewProfileDataStore {

    override suspend fun getProfileIndex(mode: BrowserMode): Int =
        store.data.firstOrNull()?.get(mode.indexKey()) ?: 0

    override suspend fun incrementProfileIndex(mode: BrowserMode): Int {
        var next = 0
        store.edit { preferences ->
            val current = preferences[mode.indexKey()] ?: 0
            next = current + 1
            preferences[mode.indexKey()] = next
        }
        return next
    }

    private fun BrowserMode.indexKey() = when (this) {
        BrowserMode.REGULAR -> KEY_REGULAR_INDEX
        BrowserMode.FIRE -> KEY_FIRE_INDEX
    }

    private companion object {
        val KEY_REGULAR_INDEX = intPreferencesKey("REGULAR_INDEX")
        val KEY_FIRE_INDEX = intPreferencesKey("FIRE_INDEX")
    }
}
