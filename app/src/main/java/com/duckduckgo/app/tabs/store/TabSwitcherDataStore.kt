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

package com.duckduckgo.app.tabs.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.tabs.model.TabSwitcherData
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType
import com.duckduckgo.app.tabs.model.TabSwitcherData.UserState
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TabSwitcherDataStore {
    val data: Flow<TabSwitcherData>

    suspend fun setUserState(userState: UserState)
    suspend fun setWasAnnouncementDismissed(wasDismissed: Boolean)
    suspend fun setAnnouncementDisplayCount(displayCount: Int)
    suspend fun setTabLayoutType(layoutType: LayoutType)
}

@ContributesBinding(AppScope::class)
class TabSwitcherPrefsDataStore @Inject constructor(
    @TabSwitcher private val store: DataStore<Preferences>,
) : TabSwitcherDataStore {
    companion object {
        const val KEY_USER_STATE = "KEY_USER_STATE"
        const val KEY_WAS_ANNOUNCEMENT_DISMISSED = "KEY_WAS_ANNOUNCEMENT_DISMISSED"
        const val KEY_ANNOUNCEMENT_DISPLAY_COUNT = "KEY_ANNOUNCEMENT_DISPLAY_COUNT"
        const val KEY_LAYOUT_TYPE = "KEY_LAYOUT_TYPE"
    }

    override val data: Flow<TabSwitcherData> = store.data.map { preferences ->
        TabSwitcherData(
            userState = UserState.valueOf(preferences[stringPreferencesKey(KEY_USER_STATE)] ?: UserState.UNKNOWN.name),
            wasAnnouncementDismissed = preferences[booleanPreferencesKey(KEY_WAS_ANNOUNCEMENT_DISMISSED)] ?: false,
            announcementDisplayCount = preferences[intPreferencesKey(KEY_ANNOUNCEMENT_DISPLAY_COUNT)] ?: 0,
            layoutType = LayoutType.valueOf(preferences[stringPreferencesKey(KEY_LAYOUT_TYPE)] ?: LayoutType.GRID.name),
        )
    }

    override suspend fun setUserState(userState: UserState) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(KEY_USER_STATE)] = userState.name
        }
    }

    override suspend fun setWasAnnouncementDismissed(wasDismissed: Boolean) {
        store.edit { preferences ->
            preferences[booleanPreferencesKey(KEY_WAS_ANNOUNCEMENT_DISMISSED)] = wasDismissed
        }
    }

    override suspend fun setAnnouncementDisplayCount(displayCount: Int) {
        store.edit { preferences ->
            preferences[intPreferencesKey(KEY_ANNOUNCEMENT_DISPLAY_COUNT)] = displayCount
        }
    }

    override suspend fun setTabLayoutType(layoutType: LayoutType) {
        store.edit { preferences ->
            preferences[stringPreferencesKey(KEY_LAYOUT_TYPE)] = layoutType.name
        }
    }
}
