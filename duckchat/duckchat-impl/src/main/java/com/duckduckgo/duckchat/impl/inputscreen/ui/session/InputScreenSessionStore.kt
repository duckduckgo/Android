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

package com.duckduckgo.duckchat.impl.inputscreen.ui.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.inputscreen.ui.session.RealInputScreenSessionStore.Keys.HAS_USED_CHAT_MODE_KEY
import com.duckduckgo.duckchat.impl.inputscreen.ui.session.RealInputScreenSessionStore.Keys.HAS_USED_SEARCH_MODE_KEY
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

interface InputScreenSessionStore {
    suspend fun hasUsedSearchMode(): Boolean
    suspend fun hasUsedChatMode(): Boolean

    suspend fun setHasUsedSearchMode(used: Boolean)
    suspend fun setHasUsedChatMode(used: Boolean)

    suspend fun resetSession()
}

@ContributesBinding(AppScope::class)
class RealInputScreenSessionStore @Inject constructor(
    @InputScreenSession private val dataStore: DataStore<Preferences>,
) : InputScreenSessionStore {

    private object Keys {
        val HAS_USED_SEARCH_MODE_KEY = booleanPreferencesKey("HAS_USED_SEARCH_MODE")
        val HAS_USED_CHAT_MODE_KEY = booleanPreferencesKey("HAS_USED_CHAT_MODE")
    }

    override suspend fun hasUsedSearchMode(): Boolean {
        return dataStore.data.firstOrNull()?.let { it[HAS_USED_SEARCH_MODE_KEY] } ?: false
    }

    override suspend fun hasUsedChatMode(): Boolean {
        return dataStore.data.firstOrNull()?.let { it[HAS_USED_CHAT_MODE_KEY] } ?: false
    }

    override suspend fun setHasUsedSearchMode(used: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_USED_SEARCH_MODE_KEY] = used
        }
    }

    override suspend fun setHasUsedChatMode(used: Boolean) {
        dataStore.edit { preferences ->
            preferences[HAS_USED_CHAT_MODE_KEY] = used
        }
    }

    override suspend fun resetSession() {
        dataStore.edit { preferences ->
            preferences[HAS_USED_SEARCH_MODE_KEY] = false
            preferences[HAS_USED_CHAT_MODE_KEY] = false
        }
    }
}
