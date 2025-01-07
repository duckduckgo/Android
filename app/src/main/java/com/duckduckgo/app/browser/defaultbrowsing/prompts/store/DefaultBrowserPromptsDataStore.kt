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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.browser.defaultbrowsing.prompts.di.DefaultBrowserPrompts
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DefaultBrowserPromptsDataStore {
    val overflowMenuState: Flow<DefaultBrowserPromptsOverflowMenuState?>
    val messageDialogOpenedCount: Flow<Int>
    val alreadyConverted: Flow<Boolean>

    suspend fun storeOverflowMenuState(state: DefaultBrowserPromptsOverflowMenuState?)
    suspend fun storeMessageDialogOpenedCount(count: Int)
    suspend fun convert()
}

data class DefaultBrowserPromptsOverflowMenuState(
    val activatedDate: String,
    val highlightOverflowMenuIcon: Boolean,
)

@ContributesBinding(AppScope::class)
class DefaultBrowserPromptsPrefsDataStoreImpl @Inject constructor(
    @DefaultBrowserPrompts private val store: DataStore<Preferences>,
) : DefaultBrowserPromptsDataStore {
    companion object {
        private const val PREF_KEY_MENU_STATE_ACTIVATED_DATE = "additional_default_browser_prompts_overflow_menu_activated_date"
        private const val PREF_KEY_MENU_STATE_HIGHLIGHT_OVERFLOW_ICON = "additional_default_browser_prompts_overflow_menu_highlight_icon"
        private const val PREF_KEY_DIALOG_OPENED_COUNT = "additional_default_browser_prompts_dialog_opened_count"
        private const val PREF_KEY_ALREADY_CONVERTED = "additional_default_browser_prompts_already_converted"
    }

    override val overflowMenuState: Flow<DefaultBrowserPromptsOverflowMenuState?> = store.data.map { preferences ->
        val activatedDate = preferences[stringPreferencesKey(PREF_KEY_MENU_STATE_ACTIVATED_DATE)] ?: return@map null
        val highlightOverflowMenuIcon = preferences[booleanPreferencesKey(PREF_KEY_MENU_STATE_HIGHLIGHT_OVERFLOW_ICON)] ?: return@map null

        DefaultBrowserPromptsOverflowMenuState(
            activatedDate = activatedDate,
            highlightOverflowMenuIcon = highlightOverflowMenuIcon,
        )
    }

    override val messageDialogOpenedCount: Flow<Int> = store.data.map { preferences ->
        preferences[intPreferencesKey(PREF_KEY_DIALOG_OPENED_COUNT)] ?: 0
    }

    override val alreadyConverted: Flow<Boolean> = store.data.map { preferences ->
        preferences[booleanPreferencesKey(PREF_KEY_ALREADY_CONVERTED)] ?: false
    }

    override suspend fun storeOverflowMenuState(state: DefaultBrowserPromptsOverflowMenuState?) {
        store.edit { preferences ->
            if (state != null) {
                preferences[stringPreferencesKey(PREF_KEY_MENU_STATE_ACTIVATED_DATE)] = state.activatedDate
                preferences[booleanPreferencesKey(PREF_KEY_MENU_STATE_HIGHLIGHT_OVERFLOW_ICON)] = state.highlightOverflowMenuIcon
            } else {
                preferences.remove(stringPreferencesKey(PREF_KEY_MENU_STATE_ACTIVATED_DATE))
                preferences.remove(booleanPreferencesKey(PREF_KEY_MENU_STATE_HIGHLIGHT_OVERFLOW_ICON))
            }
        }
    }

    override suspend fun storeMessageDialogOpenedCount(count: Int) {
        store.edit { preferences ->
            preferences[intPreferencesKey(PREF_KEY_DIALOG_OPENED_COUNT)] = count
        }
    }

    override suspend fun convert() {
        store.edit { preferences ->
            preferences[booleanPreferencesKey(PREF_KEY_ALREADY_CONVERTED)] = true
        }
    }
}
