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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.duckchat.impl.di.DuckChat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

class HighUsageModelNoticeDismissalStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
) {
    val dismissedModelIds: Flow<Set<String>> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { preferences -> preferences[DUCK_AI_HIGH_USAGE_NOTICE_DISMISSED_MODELS].orEmpty() }
        .distinctUntilChanged()

    suspend fun dismiss(modelId: String) {
        store.edit { preferences ->
            preferences[DUCK_AI_HIGH_USAGE_NOTICE_DISMISSED_MODELS] =
                preferences[DUCK_AI_HIGH_USAGE_NOTICE_DISMISSED_MODELS].orEmpty() + modelId
        }
    }

    private companion object {
        val DUCK_AI_HIGH_USAGE_NOTICE_DISMISSED_MODELS =
            stringSetPreferencesKey("DUCK_AI_HIGH_USAGE_NOTICE_DISMISSED_MODELS")
    }
}
