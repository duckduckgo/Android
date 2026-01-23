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
import com.duckduckgo.duckchat.impl.contextual.PageContextData
import com.duckduckgo.duckchat.impl.di.DuckChat
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_AI_AUTOMATIC_CONTEXT_ATTACHMENT
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_AI_CONTEXTUAL_ONBOARDING_DISMISSED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_AI_INPUT_SCREEN_USER_SETTING
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_BACKGROUND_TIMESTAMP
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_FULLSCREEN_MODE_SETTING
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_HISTORY_ENABLED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_LAST_SESSION_TIMESTAMP
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_OPENED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_PAGE_CONTEXT_CLEARED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_PAGE_CONTEXT_JSON
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_PAGE_CONTEXT_TAB_ID
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SESSION_DELTA_TIMESTAMP
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_ADDRESS_BAR
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_MENU
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_SHOW_IN_VOICE_SEARCH
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_USER_ENABLED
import com.duckduckgo.duckchat.impl.store.SharedPreferencesDuckChatDataStore.Keys.DUCK_CHAT_USER_PREFERENCES
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

interface DuckChatDataStore {
    suspend fun setDuckChatUserEnabled(enabled: Boolean)

    suspend fun setInputScreenUserSetting(enabled: Boolean)

    suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean)

    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)

    suspend fun setShowInAddressBar(showDuckChat: Boolean)

    suspend fun setFullScreenModeUserSetting(enabled: Boolean)

    suspend fun setShowInVoiceSearch(showToggle: Boolean)

    suspend fun setAutomaticPageContextAttachment(enabled: Boolean)

    fun observeDuckChatUserEnabled(): Flow<Boolean>

    fun observeInputScreenUserSettingEnabled(): Flow<Boolean>

    fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?>

    fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean>

    suspend fun isCosmeticInputScreenUserSettingEnabled(): Boolean

    fun observeShowInBrowserMenu(): Flow<Boolean>

    fun observeShowInAddressBar(): Flow<Boolean>

    fun observeShowInVoiceSearch(): Flow<Boolean>

    suspend fun isDuckChatUserEnabled(): Boolean

    suspend fun isInputScreenUserSettingEnabled(): Boolean

    suspend fun isFullScreenUserSettingEnabled(): Boolean

    suspend fun getShowInBrowserMenu(): Boolean

    suspend fun getShowInAddressBar(): Boolean

    suspend fun getShowInVoiceSearch(): Boolean

    suspend fun fetchAndClearUserPreferences(): String?

    suspend fun updateUserPreferences(userPreferences: String?)

    suspend fun registerOpened()

    suspend fun wasOpenedBefore(): Boolean

    suspend fun lastSessionTimestamp(): Long

    suspend fun sessionDeltaTimestamp(): Long

    suspend fun setAppBackgroundTimestamp(timestamp: Long?)

    suspend fun getAppBackgroundTimestamp(): Long?

    suspend fun setAIChatHistoryEnabled(enabled: Boolean)

    suspend fun isAIChatHistoryEnabled(): Boolean

    suspend fun setDuckChatPageContext(tabId: String, serializedPageData: String)

    suspend fun clearDuckChatPageContext(tabId: String)

    fun observeDuckChatPageContext(): Flow<PageContextData?>

    suspend fun setContextualOnboardingCompleted(completed: Boolean)

    suspend fun isContextualOnboardingCompleted(): Boolean
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
        val DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING = booleanPreferencesKey(name = "DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING")
        val DUCK_CHAT_SHOW_IN_MENU = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_MENU")
        val DUCK_CHAT_SHOW_IN_ADDRESS_BAR = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_ADDRESS_BAR")
        val DUCK_CHAT_SHOW_IN_VOICE_SEARCH = booleanPreferencesKey(name = "DUCK_CHAT_SHOW_IN_VOICE_SEARCH")
        val DUCK_CHAT_OPENED = booleanPreferencesKey(name = "DUCK_CHAT_OPENED")
        val DUCK_CHAT_USER_PREFERENCES = stringPreferencesKey("DUCK_CHAT_USER_PREFERENCES")
        val DUCK_CHAT_LAST_SESSION_TIMESTAMP = longPreferencesKey(name = "DUCK_CHAT_LAST_SESSION_TIMESTAMP")
        val DUCK_CHAT_SESSION_DELTA_TIMESTAMP = longPreferencesKey(name = "DUCK_CHAT_SESSION_DELTA_TIMESTAMP")
        val DUCK_CHAT_FULLSCREEN_MODE_SETTING = booleanPreferencesKey(name = "DUCK_CHAT_FULLSCREEN_MODE_SETTING")
        val DUCK_CHAT_BACKGROUND_TIMESTAMP = longPreferencesKey(name = "DUCK_CHAT_BACKGROUND_TIMESTAMP")
        val DUCK_CHAT_HISTORY_ENABLED = booleanPreferencesKey(name = "DUCK_CHAT_HISTORY_ENABLED")
        val DUCK_AI_AUTOMATIC_CONTEXT_ATTACHMENT = booleanPreferencesKey(name = "DUCK_AI_AUTOMATIC_CONTEXT_ATTACHMENT")
        val DUCK_CHAT_PAGE_CONTEXT_TAB_ID = stringPreferencesKey(name = "DUCK_CHAT_PAGE_CONTEXT_TAB_ID")
        val DUCK_CHAT_PAGE_CONTEXT_JSON = stringPreferencesKey(name = "DUCK_CHAT_PAGE_CONTEXT_JSON")
        val DUCK_CHAT_PAGE_CONTEXT_CLEARED = booleanPreferencesKey(name = "DUCK_CHAT_PAGE_CONTEXT_CLEARED")
        val DUCK_AI_CONTEXTUAL_ONBOARDING_DISMISSED = booleanPreferencesKey(name = "DUCK_AI_CONTEXTUAL_ONBOARDING_DISMISSED")
    }

    private fun Preferences.defaultShowInAddressBar(): Boolean =
        this[DUCK_CHAT_SHOW_IN_ADDRESS_BAR]
            ?: this[DUCK_CHAT_SHOW_IN_MENU]
            ?: true

    init {
        if (isMainProcess) {
            storeDerivedValues()
        }
    }

    private fun storeDerivedValues() =
        appCoroutineScope.launch(dispatchers.io()) {
            store.data.firstOrNull()?.let { prefs ->
                if (prefs[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] == null) {
                    val default = prefs[DUCK_CHAT_SHOW_IN_MENU] ?: true
                    store.edit { it[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] = default }
                }
            }
        }

    private val duckChatUserEnabled: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[DUCK_CHAT_USER_ENABLED] ?: true }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val inputScreenUserSettingEnabled: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[DUCK_AI_INPUT_SCREEN_USER_SETTING] ?: false }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    private val cosmeticInputScreenUserSettingEnabled: StateFlow<Boolean?> =
        store.data
            .map { prefs -> prefs[DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING] }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, null)

    private val automaticContextAttachment: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[DUCK_AI_AUTOMATIC_CONTEXT_ATTACHMENT] ?: false }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    private val duckChatShowInBrowserMenu: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[DUCK_CHAT_SHOW_IN_MENU] ?: true }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val duckChatShowInAddressBar: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs.defaultShowInAddressBar() }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    private val duckChatShowInVoiceSearch: StateFlow<Boolean> =
        store.data
            .map { prefs -> prefs[DUCK_CHAT_SHOW_IN_VOICE_SEARCH] ?: true }
            .distinctUntilChanged()
            .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    override suspend fun setDuckChatUserEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_USER_ENABLED] = enabled }
    }

    override suspend fun setInputScreenUserSetting(enabled: Boolean) {
        store.edit { prefs ->
            prefs[DUCK_AI_INPUT_SCREEN_USER_SETTING] = enabled
            prefs[DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING] = enabled
        }
    }

    override suspend fun setCosmeticInputScreenUserSetting(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING] = enabled }
    }

    override suspend fun setFullScreenModeUserSetting(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_FULLSCREEN_MODE_SETTING] = enabled }
    }

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_MENU] = showDuckChat }
    }

    override suspend fun setShowInAddressBar(showDuckChat: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_ADDRESS_BAR] = showDuckChat }
    }

    override suspend fun setShowInVoiceSearch(showToggle: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_SHOW_IN_VOICE_SEARCH] = showToggle }
    }

    override suspend fun setAutomaticPageContextAttachment(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_AI_AUTOMATIC_CONTEXT_ATTACHMENT] = enabled }
    }

    override fun observeDuckChatUserEnabled(): Flow<Boolean> = duckChatUserEnabled

    override fun observeInputScreenUserSettingEnabled(): Flow<Boolean> = inputScreenUserSettingEnabled

    override fun observeCosmeticInputScreenUserSettingEnabled(): Flow<Boolean?> = cosmeticInputScreenUserSettingEnabled

    override fun observeAutomaticContextAttachmentUserSettingEnabled(): Flow<Boolean> = automaticContextAttachment

    override fun observeShowInBrowserMenu(): Flow<Boolean> = duckChatShowInBrowserMenu

    override fun observeShowInAddressBar(): Flow<Boolean> = duckChatShowInAddressBar

    override fun observeShowInVoiceSearch(): Flow<Boolean> = duckChatShowInVoiceSearch

    override suspend fun isDuckChatUserEnabled(): Boolean = store.data.firstOrNull()?.let { it[DUCK_CHAT_USER_ENABLED] } ?: true

    override suspend fun isInputScreenUserSettingEnabled(): Boolean = store.data.firstOrNull()?.let {
        it[DUCK_AI_INPUT_SCREEN_USER_SETTING]
    } ?: false

    override suspend fun isFullScreenUserSettingEnabled(): Boolean = store.data.firstOrNull()?.let {
        it[DUCK_CHAT_FULLSCREEN_MODE_SETTING]
    } ?: false

    override suspend fun isCosmeticInputScreenUserSettingEnabled(): Boolean = store.data.firstOrNull()?.let {
        it[DUCK_AI_INPUT_SCREEN_COSMETIC_SETTING]
    } ?: false

    override suspend fun getShowInBrowserMenu(): Boolean = store.data.firstOrNull()?.let { it[DUCK_CHAT_SHOW_IN_MENU] } ?: true

    override suspend fun getShowInAddressBar(): Boolean = store.data.firstOrNull()?.defaultShowInAddressBar() ?: true

    override suspend fun getShowInVoiceSearch(): Boolean = store.data.firstOrNull()?.let { it[DUCK_CHAT_SHOW_IN_VOICE_SEARCH] } ?: true

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

    override suspend fun wasOpenedBefore(): Boolean = store.data.map { it[DUCK_CHAT_OPENED] }.firstOrNull() ?: false

    override suspend fun lastSessionTimestamp(): Long = store.data.firstOrNull()?.let { it[DUCK_CHAT_LAST_SESSION_TIMESTAMP] } ?: 0L

    override suspend fun sessionDeltaTimestamp(): Long = store.data.firstOrNull()?.let { it[DUCK_CHAT_SESSION_DELTA_TIMESTAMP] } ?: 0L

    override suspend fun setAppBackgroundTimestamp(timestamp: Long?) {
        store.edit { prefs ->
            if (timestamp == null) {
                prefs.remove(DUCK_CHAT_BACKGROUND_TIMESTAMP)
            } else {
                prefs[DUCK_CHAT_BACKGROUND_TIMESTAMP] = timestamp
            }
        }
    }

    override suspend fun getAppBackgroundTimestamp(): Long? = store.data.firstOrNull()?.let { it[DUCK_CHAT_BACKGROUND_TIMESTAMP] }

    override suspend fun setAIChatHistoryEnabled(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_CHAT_HISTORY_ENABLED] = enabled }
    }

    override suspend fun isAIChatHistoryEnabled(): Boolean = store.data.firstOrNull()?.let { it[DUCK_CHAT_HISTORY_ENABLED] } ?: false

    override suspend fun setContextualOnboardingCompleted(completed: Boolean) {
        store.edit { it[DUCK_AI_CONTEXTUAL_ONBOARDING_DISMISSED] = completed }
    }

    override suspend fun isContextualOnboardingCompleted(): Boolean = store.data.firstOrNull()?.let {
        it[DUCK_AI_CONTEXTUAL_ONBOARDING_DISMISSED]
    } ?: false

    override suspend fun setDuckChatPageContext(tabId: String, serializedPageData: String) {
        store.edit { prefs ->
            prefs[DUCK_CHAT_PAGE_CONTEXT_TAB_ID] = tabId
            prefs[DUCK_CHAT_PAGE_CONTEXT_JSON] = serializedPageData
            prefs[DUCK_CHAT_PAGE_CONTEXT_CLEARED] = false
        }
    }

    override suspend fun clearDuckChatPageContext(tabId: String) {
        store.edit { prefs ->
            prefs[DUCK_CHAT_PAGE_CONTEXT_TAB_ID] = tabId
            prefs[DUCK_CHAT_PAGE_CONTEXT_JSON] = ""
            prefs[DUCK_CHAT_PAGE_CONTEXT_CLEARED] = true
        }
    }

    override fun observeDuckChatPageContext(): Flow<PageContextData?> {
        return store.data
            .map { prefs ->
                val tabId = prefs[DUCK_CHAT_PAGE_CONTEXT_TAB_ID] ?: return@map null
                val serialized = prefs[DUCK_CHAT_PAGE_CONTEXT_JSON] ?: ""
                val cleared = prefs[DUCK_CHAT_PAGE_CONTEXT_CLEARED] ?: false
                PageContextData(tabId = tabId, serializedPageData = serialized, collectedAtMs = System.currentTimeMillis(), isCleared = cleared)
            }
            .distinctUntilChanged()
    }
}
