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

package com.duckduckgo.duckchat.impl.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_OPENED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_MENU
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface DuckChatDataStore {
    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)
    fun observeShowInBrowserMenu(): Flow<Boolean>
    fun getShowInBrowserMenu(): Boolean
    suspend fun fetchAndClearUserPreferences(): String?
    suspend fun updateUserPreferences(userPreferences: String?)
    suspend fun registerOpened()
    suspend fun wasOpenedBefore(): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDuckChatDataStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckChatDataStore {

    private object Keys {
        val DUCK_CHAT_SHOW_IN_MENU = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_MENU")
        val DUCK_CHAT_OPENED = booleanPreferencesKey(name = "DUCK_CHAT_OPENED")
        val DUCK_CHAT_USER_PREFERENCES = stringPreferencesKey("DUCK_CHAT_USER_PREFERENCES")
    }

    private val duckChatShowInBrowserMenu: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[DUCK_CHAT_SHOW_IN_MENU] ?: true
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_MENU] = showDuckChat }
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatShowInBrowserMenu
    }

    override fun getShowInBrowserMenu(): Boolean {
        return duckChatShowInBrowserMenu.value
    }

    override suspend fun fetchAndClearUserPreferences(): String? {
        val userPreferences = store.data.map { it[Keys.DUCK_CHAT_USER_PREFERENCES] }.firstOrNull()
        store.edit { prefs -> prefs.remove(Keys.DUCK_CHAT_USER_PREFERENCES) }
        return userPreferences
    }

    override suspend fun updateUserPreferences(userPreferences: String?) {
        store.edit { prefs ->
            if (userPreferences == null) {
                prefs.remove(Keys.DUCK_CHAT_USER_PREFERENCES)
            } else {
                prefs[Keys.DUCK_CHAT_USER_PREFERENCES] = userPreferences
            }
        }
    }

    override suspend fun registerOpened() {
        store.edit { it[DUCK_CHAT_OPENED] = true }
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return store.data.map { it[DUCK_CHAT_OPENED] }.firstOrNull() ?: false
    }
}
