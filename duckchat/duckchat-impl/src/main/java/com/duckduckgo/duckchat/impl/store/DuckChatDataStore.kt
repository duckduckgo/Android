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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.di.IsMainProcess
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_AI_INPUT_SCREEN_USER_SETTING
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_LAST_SESSION_TIMESTAMP
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_OPENED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SESSION_DELTA_TIMESTAMP
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_ADDRESS_BAR
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_MENU
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_USER_ENABLED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_USER_PREFERENCES
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
import kotlinx.coroutines.launch

interface DuckChatDataStore {
    suspend fun setDuckChatUserEnabled(enabled: Boolean)
    suspend fun setInputScreenUserSetting(enabled: Boolean)
    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)
    suspend fun setShowInAddressBar(showDuckChat: Boolean)

    fun observeDuckChatUserEnabled(): Flow<Boolean>
    fun observeInputScreenUserSettingEnabled(): Flow<Boolean>
    fun observeShowInBrowserMenu(): Flow<Boolean>
    fun observeShowInAddressBar(): Flow<Boolean>

    suspend fun isDuckChatUserEnabled(): Boolean
    suspend fun isInputScreenUserSettingEnabled(): Boolean
    suspend fun getShowInBrowserMenu(): Boolean
    suspend fun getShowInAddressBar(): Boolean

    suspend fun fetchAndClearUserPreferences(): String?
    suspend fun updateUserPreferences(userPreferences: String?)

    suspend fun registerOpened()
    suspend fun wasOpenedBefore(): Boolean
    suspend fun lastSessionTimestamp(): Long
    suspend fun sessionDeltaTimestamp(): Long
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDuckChatDataStore @Inject constructor(
    @DuckChat private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
    @IsMainProcess private val isMainProcess: Boolean,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckChatDataStore {

    private object Keys {
        val DUCK_CHAT_USER_ENABLED = booleanPreferencesKey(name = "DUCK_CHAT_USER_ENABLED")
        val DUCK_AI_INPUT_SCREEN_USER_SETTING = booleanPreferencesKey(name = "DUCK_AI_INPUT_SCREEN_USER_SETTING")
        val DUCK_CHAT_SHOW_IN_MENU = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_MENU")
        val DUCK_CHAT_SHOW_IN_ADDRESS_BAR = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_ADDRESS_BAR")
        val DUCK_CHAT_OPENED = booleanPreferencesKey(name = "DUCK_CHAT_OPENED")
        val DUCK_CHAT_USER_PREFERENCES = stringPreferencesKey("DUCK_CHAT_USER_PREFERENCES")
        val DUCK_CHAT_LAST_SESSION_TIMESTAMP = longPreferencesKey(name = "DUCK_CHAT_LAST_SESSION_TIMESTAMP")
        val DUCK_CHAT_SESSION_DELTA_TIMESTAMP = longPreferencesKey(name = "DUCK_CHAT_SESSION_DELTA_TIMESTAMP")
    }

    private fun Preferences.defaultShowInAddressBar(): Boolean {
        return this[DUCK_CHAT_SHOW_IN_ADDRESS_BAR]
            ?: this[DUCK_CHAT_SHOW_IN_MENU]
            ?: true
    }

    init {
        if (isMainProcess) {
            storeDerivedValues()
        }
    }

    private fun storeDerivedValues() = appCoroutineScope.launch(dispatchers.io()) {
        store.data.firstOrNull()?.let { prefs ->
            if (prefs[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] == null) {
                val default = prefs[DUCK_CHAT_SHOW_IN_MENU] ?: true
                store.edit { it[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] = default }
            }
        }
    }

    private val duckChatUserEnabled: StateFlow<Boolean> = store.data
        .map { prefs -> prefs[DUCK_CHAT_USER_ENABLED] ?: true }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val inputScreenUserSettingEnabled: StateFlow<Boolean> = store.data
        .map { prefs -> prefs[DUCK_AI_INPUT_SCREEN_USER_SETTING] ?: false }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    private val duckChatShowInBrowserMenu: StateFlow<Boolean> = store.data
        .map { prefs -> prefs[DUCK_CHAT_SHOW_IN_MENU] ?: true }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val duckChatShowInAddressBar: StateFlow<Boolean> = store.data
        .map { prefs -> prefs.defaultShowInAddressBar() }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    override suspend fun setDuckChatUserEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_USER_ENABLED] = enabled }
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_AI_INPUT_SCREEN_USER_SETTING] = enabled }
    }

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_MENU] = showDuckChat }
    }

    override suspend fun setShowInAddressBar(showDuckChat: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] = showDuckChat }
    }

    override fun observeDuckChatUserEnabled(): Flow<Boolean> = duckChatUserEnabled

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = inputScreenUserSettingEnabled

    override fun observeShowInBrowserMenu(): Flow<Boolean> = duckChatShowInBrowserMenu

    override fun observeShowInAddressBar(): Flow<Boolean> = duckChatShowInAddressBar

    override suspend fun isDuckChatUserEnabled(): Boolean {
        return store.data.firstOrNull()?.let { it[DUCK_CHAT_USER_ENABLED] } ?: true
    }

    override suspend fun isInputScreenUserSettingEnabled(): Boolean {
        return store.data.firstOrNull()?.let { it[DUCK_AI_INPUT_SCREEN_USER_SETTING] } ?: false
    }

    override suspend fun getShowInBrowserMenu(): Boolean {
        return store.data.firstOrNull()?.let { it[DUCK_CHAT_SHOW_IN_MENU] } ?: true
    }

    override suspend fun getShowInAddressBar(): Boolean {
        return store.data.firstOrNull()?.defaultShowInAddressBar() ?: true
    }

    override suspend fun fetchAndClearUserPreferences(): String? {
        val userPreferences = store.data.map { it[DUCK_CHAT_USER_PREFERENCES] }.firstOrNull()
        store.edit { prefs -> prefs.remove(DUCK_CHAT_USER_PREFERENCES) }
        return userPreferences
    }

    override suspend fun updateUserPreferences(userPreferences: String?) {
        store.edit { prefs ->
            if (userPreferences == null) {
                prefs.remove(DUCK_CHAT_USER_PREFERENCES)
            } else {
                prefs[DUCK_CHAT_USER_PREFERENCES] = userPreferences
            }
        }
    }

    override suspend fun registerOpened() {
        val now = System.currentTimeMillis()
        val lastOpened = lastSessionTimestamp()
        val delta = now - lastOpened

        store.edit { it[DUCK_CHAT_OPENED] = true }
        store.edit { it[DUCK_CHAT_LAST_SESSION_TIMESTAMP] = now }
        store.edit { it[DUCK_CHAT_SESSION_DELTA_TIMESTAMP] = delta }
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return store.data.map { it[DUCK_CHAT_OPENED] }.firstOrNull() ?: false
    }

    override suspend fun lastSessionTimestamp(): Long {
        return store.data.firstOrNull()?.let { it[DUCK_CHAT_LAST_SESSION_TIMESTAMP] } ?: 0L
    }

    override suspend fun sessionDeltaTimestamp(): Long {
        return store.data.firstOrNull()?.let { it[DUCK_CHAT_SESSION_DELTA_TIMESTAMP] } ?: 0L
    }
}
