/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.newtabpage.impl.shortcuts

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.newtabpage.impl.shortcuts.RealNewTabShortcutData.Keys.SECTION_IS_ENABLED
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface NewTabShortcutDataStore {
    val isEnabled: Flow<Boolean>

    suspend fun setIsEnabled(enabled: Boolean)
    suspend fun isEnabled(): Boolean
}

private val Context.newTabShortcutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "new_tab_shortcut_store",
)

@ContributesBinding(AppScope::class)
class RealNewTabShortcutData @Inject constructor(
    private val context: Context,
) : NewTabShortcutDataStore {

    override val isEnabled: Flow<Boolean>
        get() = context.newTabShortcutDataStore.data
            .map { prefs ->
                prefs[SECTION_IS_ENABLED] ?: true
            }
            .distinctUntilChanged()

    override suspend fun setIsEnabled(enabled: Boolean) {
        context.newTabShortcutDataStore.edit { prefs -> prefs[SECTION_IS_ENABLED] = enabled }
    }

    override suspend fun isEnabled(): Boolean =
        isEnabled.first()

    private object Keys {
        val SECTION_IS_ENABLED = booleanPreferencesKey(name = "shortcut_section_enabled")
    }
}
