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

package com.duckduckgo.duckchat.impl.inputscreen.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface InputScreenDataStore {
    suspend fun getLastUsedMode(): InputScreenMode?
    suspend fun setLastUsedMode(mode: InputScreenMode)
}

enum class InputScreenMode {
    CHAT,
    SEARCH,
    ;
}

@ContributesBinding(FragmentScope::class)
class InputScreenDataStoreImpl @Inject constructor(
    @DuckChat private val dataStore: DataStore<Preferences>,
) : InputScreenDataStore {

    private val lastUsedModeKey = stringPreferencesKey("duck.ai_input-screen_last-used-mode")

    override suspend fun getLastUsedMode(): InputScreenMode? {
        return dataStore.data.map { preferences ->
            preferences[lastUsedModeKey]?.let { modeString ->
                try {
                    InputScreenMode.valueOf(modeString)
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }.firstOrNull()
    }

    override suspend fun setLastUsedMode(mode: InputScreenMode) {
        dataStore.edit { preferences ->
            preferences[lastUsedModeKey] = mode.name
        }
    }
}
