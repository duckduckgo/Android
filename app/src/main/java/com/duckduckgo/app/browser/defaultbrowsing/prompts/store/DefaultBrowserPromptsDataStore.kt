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
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.browser.defaultbrowsing.prompts.di.DefaultBrowserPrompts
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.NOT_ENROLLED
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface DefaultBrowserPromptsDataStore {
    val stage: Flow<Stage>
    val showSetAsDefaultPopupMenuItem: Flow<Boolean>
    val highlightPopupMenu: Flow<Boolean>
    val showSetAsDefaultMessage: Flow<Boolean>

    suspend fun storeExperimentStage(stage: Stage)
    suspend fun storeShowSetAsDefaultPopupMenuItemState(show: Boolean)
    suspend fun storeHighlightPopupMenuState(highlight: Boolean)
    suspend fun storeShowSetAsDefaultMessageState(show: Boolean)

    enum class Stage {
        NOT_ENROLLED,
        ENROLLED,
        STAGE_1,
        STAGE_2,
        STAGE_3,
        STOPPED,
        CONVERTED,
    }
}

@ContributesBinding(AppScope::class)
class DefaultBrowserPromptsPrefsDataStoreImpl @Inject constructor(
    @DefaultBrowserPrompts private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : DefaultBrowserPromptsDataStore {
    companion object {
        private const val PREF_KEY_EXPERIMENT_STAGE_ID = "additional_default_browser_prompts_experiment_stage_id"
        private const val PREF_KEY_SHOW_OVERFLOW_MENU_ITEM = "additional_default_browser_prompts_show_overflow_menu_item"
        private const val PREF_KEY_HIGHLIGHT_OVERFLOW_MENU_ICON = "additional_default_browser_prompts_highlight_overflow_menu_icon"
        private const val PREF_KEY_SHOW_SET_AS_DEFAULT_MESSAGE = "additional_default_browser_prompts_show_set_as_default_message"
    }

    override val stage: Flow<Stage> = store.data.map { preferences ->
        preferences[stringPreferencesKey(PREF_KEY_EXPERIMENT_STAGE_ID)]?.let { Stage.valueOf(it) } ?: NOT_ENROLLED
    }

    override val showSetAsDefaultPopupMenuItem: Flow<Boolean> = store.data.map { preferences ->
        preferences[booleanPreferencesKey(PREF_KEY_SHOW_OVERFLOW_MENU_ITEM)] ?: false
    }

    override val highlightPopupMenu: Flow<Boolean> = store.data.map { preferences ->
        preferences[booleanPreferencesKey(PREF_KEY_HIGHLIGHT_OVERFLOW_MENU_ICON)] ?: false
    }

    override val showSetAsDefaultMessage: Flow<Boolean> = store.data.map { preferences ->
        preferences[booleanPreferencesKey(PREF_KEY_SHOW_SET_AS_DEFAULT_MESSAGE)] ?: false
    }

    override suspend fun storeExperimentStage(stage: Stage) {
        withContext(dispatchers.io()) {
            store.edit { preferences ->
                preferences[stringPreferencesKey(PREF_KEY_EXPERIMENT_STAGE_ID)] = stage.name
            }
        }
    }

    override suspend fun storeShowSetAsDefaultPopupMenuItemState(show: Boolean) {
        withContext(dispatchers.io()) {
            store.edit { preferences ->
                preferences[booleanPreferencesKey(PREF_KEY_SHOW_OVERFLOW_MENU_ITEM)] = show
            }
        }
    }

    override suspend fun storeHighlightPopupMenuState(highlight: Boolean) {
        withContext(dispatchers.io()) {
            store.edit { preferences ->
                preferences[booleanPreferencesKey(PREF_KEY_HIGHLIGHT_OVERFLOW_MENU_ICON)] = highlight
            }
        }
    }

    override suspend fun storeShowSetAsDefaultMessageState(show: Boolean) {
        withContext(dispatchers.io()) {
            store.edit { preferences ->
                preferences[booleanPreferencesKey(PREF_KEY_SHOW_SET_AS_DEFAULT_MESSAGE)] = show
            }
        }
    }
}
