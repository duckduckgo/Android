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
import com.duckduckgo.app.browser.defaultbrowsing.prompts.di.DefaultBrowserPrompts
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface DefaultBrowserPromptsDataStore {
    // val data: Flow<TabSwitcherData>

    // suspend fun setUserState(userState: UserState)
}

@ContributesBinding(AppScope::class)
class DefaultBrowserPromptsPrefsDataStore @Inject constructor(
    @DefaultBrowserPrompts private val store: DataStore<Preferences>,
) : DefaultBrowserPromptsDataStore {
    companion object {
        const val KEY_USER_STATE = "KEY_USER_STATE"
        const val KEY_LAYOUT_TYPE = "KEY_LAYOUT_TYPE"
    }

    // override val data: Flow<TabSwitcherData> = store.data.map { preferences ->
    //     TabSwitcherData(
    //         userState = UserState.valueOf(preferences[stringPreferencesKey(KEY_USER_STATE)] ?: UserState.UNKNOWN.name),
    //         layoutType = LayoutType.valueOf(preferences[stringPreferencesKey(KEY_LAYOUT_TYPE)] ?: LayoutType.GRID.name),
    //     )
    // }

    // override suspend fun setUserState(userState: UserState) {
    //     store.edit { preferences ->
    //         preferences[stringPreferencesKey(KEY_USER_STATE)] = userState.name
    //     }
    // }

    // override suspend fun setTabLayoutType(layoutType: LayoutType) {
    //     store.edit { preferences ->
    //         preferences[stringPreferencesKey(KEY_LAYOUT_TYPE)] = layoutType.name
    //     }
    // }
}
