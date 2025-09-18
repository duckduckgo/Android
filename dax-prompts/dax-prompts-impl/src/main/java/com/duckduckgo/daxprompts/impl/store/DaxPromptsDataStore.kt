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

package com.duckduckgo.daxprompts.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.duckduckgo.daxprompts.impl.di.DaxPrompts
import com.duckduckgo.daxprompts.impl.store.SharedPreferencesDaxPromptsDataStore.Keys.DAX_PROMPTS_SHOW_BROWSER_COMPARISON
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.firstOrNull

interface DaxPromptsDataStore {
    suspend fun setDaxPromptsShowBrowserComparison(show: Boolean)
    suspend fun getDaxPromptsShowBrowserComparison(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDaxPromptsDataStore @Inject constructor(
    @DaxPrompts private val store: DataStore<Preferences>,
) : DaxPromptsDataStore {

    private object Keys {
        val DAX_PROMPTS_SHOW_BROWSER_COMPARISON = booleanPreferencesKey(name = "DAX_PROMPTS_SHOW_BROWSER_COMPARISON")
    }

    override suspend fun setDaxPromptsShowBrowserComparison(show: Boolean) {
        store.edit { it[DAX_PROMPTS_SHOW_BROWSER_COMPARISON] = show }
    }

    override suspend fun getDaxPromptsShowBrowserComparison(): Boolean {
        return store.data.firstOrNull()?.get(DAX_PROMPTS_SHOW_BROWSER_COMPARISON) ?: true
    }
}
