/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.duckplayer.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_DISABLED_HELP_PAGE
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_OPEN_IN_NEW_TAB
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_RC
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_USER_ONBOARDED
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_WAS_USED
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_YOUTUBE_PATH
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.OVERLAY_INTERACTED
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.PRIVATE_PLAYER_MODE
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
import javax.inject.Inject

interface DuckPlayerDataStore {
    fun getDuckPlayerRemoteConfigJson(): String

    suspend fun setDuckPlayerRemoteConfigJson(value: String)

    fun getOverlayInteracted(): Boolean

    fun observeOverlayInteracted(): Flow<Boolean>

    suspend fun setOverlayInteracted(value: Boolean)

    fun getPrivatePlayerMode(): String

    fun observePrivatePlayerMode(): Flow<String>

    suspend fun setPrivatePlayerMode(value: String)

    fun getDuckPlayerDisabledHelpPageLink(): String?

    suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String?)

    fun getYouTubeWatchPath(): String

    suspend fun storeYouTubeWatchPath(youtubePath: String)

    fun getYoutubeEmbedUrl(): String

    suspend fun storeYoutubeEmbedUrl(embedUrl: String)

    fun getYouTubeUrl(): String

    suspend fun storeYouTubeUrl(youtubeUrl: String)

    fun getYouTubeVideoIDQueryParam(): String

    suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParams: String)

    fun getYouTubeReferrerQueryParams(): List<String>

    suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>)

    fun getYouTubeReferrerHeaders(): List<String>

    suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>)

    suspend fun setUserOnboarded()

    fun getUserOnboarded(): Boolean

    suspend fun setOpenInNewTab(enabled: Boolean)

    fun observeOpenInNewTab(): Flow<Boolean>

    fun getOpenInNewTab(): Boolean

    suspend fun wasUsedBefore(): Boolean

    suspend fun setUsed()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class SharedPreferencesDuckPlayerDataStore @Inject constructor(
    @DuckPlayer private val store: DataStore<Preferences>,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : DuckPlayerDataStore {

    private object Keys {
        val OVERLAY_INTERACTED = booleanPreferencesKey(name = "OVERLAY_INTERACTED")
        val DUCK_PLAYER_RC = stringPreferencesKey(name = "DUCK_PLAYER_RC")
        val PRIVATE_PLAYER_MODE = stringPreferencesKey(name = "PRIVATE_PLAYER_MODE")
        val DUCK_PLAYER_DISABLED_HELP_PAGE = stringPreferencesKey(name = "DUCK_PLAYER_DISABLED_HELP_PAGE")
        val DUCK_PLAYER_YOUTUBE_PATH = stringPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_PATH")
        val DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS = stringSetPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS")
        val DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS = stringSetPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS")
        val DUCK_PLAYER_YOUTUBE_URL = stringPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_URL")
        val DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS = stringPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS")
        val DUCK_PLAYER_YOUTUBE_EMBED_URL = stringPreferencesKey(name = "DUCK_PLAYER_YOUTUBE_EMBED_URL")
        val DUCK_PLAYER_USER_ONBOARDED = booleanPreferencesKey(name = "DUCK_PLAYER_USER_ONBOARDED")
        val DUCK_PLAYER_OPEN_IN_NEW_TAB = booleanPreferencesKey(name = "DUCK_PLAYER_OPEN_IN_NEW_TAB")
        val DUCK_PLAYER_WAS_USED = booleanPreferencesKey(name = "DUCK_PLAYER_WAS_USED")
    }

    private val overlayInteracted: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[OVERLAY_INTERACTED] ?: false
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    private val duckPlayerRC: StateFlow<String> = store.data
        .map { prefs ->
            prefs[DUCK_PLAYER_RC] ?: "{}"
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "{}")

    private val privatePlayerMode: StateFlow<String> = store.data
        .map { prefs ->
            prefs[PRIVATE_PLAYER_MODE] ?: "ALWAYS_ASK"
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "ALWAYS_ASK")

    private val duckPlayerDisabledHelpPageLink: StateFlow<String> = store.data
        .map { prefs ->
            prefs[DUCK_PLAYER_DISABLED_HELP_PAGE] ?: ""
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "")

    private val youtubePath: StateFlow<String> = store.data
        .map { prefs ->
            prefs[DUCK_PLAYER_YOUTUBE_PATH] ?: ""
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "")

    private val youtubeReferrerHeaders: StateFlow<List<String>> = store.data
        .map { prefs ->
            prefs[DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS]?.toList() ?: listOf()
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, listOf())

    private val youtubeReferrerQueryParams: StateFlow<List<String>> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS]?.toList() ?: listOf()
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, listOf())

    private val youtubeUrl: StateFlow<String> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_YOUTUBE_URL] ?: ""
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "")

    private val youtubeVideoIDQueryParams: StateFlow<String> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS] ?: ""
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "")

    private val youtubeEmbedUrl: StateFlow<String> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_YOUTUBE_EMBED_URL] ?: ""
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, "")

    private val duckPlayerUserOnboarded: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_USER_ONBOARDED] ?: false
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, false)

    private val duckPlayerOpenInNewTab: StateFlow<Boolean> = store.data
        .map { prefs ->
            prefs[Keys.DUCK_PLAYER_OPEN_IN_NEW_TAB] ?: true
        }
        .distinctUntilChanged()
        .stateIn(appCoroutineScope, SharingStarted.Eagerly, true)

    override fun getDuckPlayerRemoteConfigJson(): String {
        return duckPlayerRC.value
    }

    override suspend fun setDuckPlayerRemoteConfigJson(value: String) {
        store.edit { prefs -> prefs[DUCK_PLAYER_RC] = value }
    }

    override fun getOverlayInteracted(): Boolean {
        return overlayInteracted.value
    }

    override fun observeOverlayInteracted(): Flow<Boolean> {
        return overlayInteracted
    }

    override suspend fun setOverlayInteracted(value: Boolean) {
        store.edit { prefs -> prefs[OVERLAY_INTERACTED] = value }
    }

    override fun getPrivatePlayerMode(): String {
        return privatePlayerMode.value
    }

    override fun observePrivatePlayerMode(): Flow<String> {
        return privatePlayerMode
    }

    override suspend fun setPrivatePlayerMode(value: String) {
        store.edit { prefs -> prefs[PRIVATE_PLAYER_MODE] = value }
    }

    override suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String?) {
        store.edit { prefs -> prefs[DUCK_PLAYER_DISABLED_HELP_PAGE] = duckPlayerDisabledHelpPageLink ?: "" }
    }

    override fun getDuckPlayerDisabledHelpPageLink(): String? {
        return duckPlayerDisabledHelpPageLink.value.let { it.ifBlank { null } }
    }

    override suspend fun storeYouTubeWatchPath(youtubePath: String) {
        store.edit { prefs -> prefs[DUCK_PLAYER_YOUTUBE_PATH] = youtubePath }
    }

    override suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>) {
        store.edit { prefs -> prefs[DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS] = youtubeReferrerHeaders.toSet() }
    }

    override fun getYouTubeReferrerHeaders(): List<String> {
        return youtubeReferrerHeaders.value
    }

    override suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS] = youtubeReferrerQueryParams.toSet() }
    }

    override fun getYouTubeReferrerQueryParams(): List<String> {
        return youtubeReferrerQueryParams.value
    }

    override suspend fun storeYouTubeUrl(youtubeUrl: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_URL] = youtubeUrl }
    }

    override fun getYouTubeUrl(): String {
        return youtubeUrl.value
    }

    override suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParams: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS] = youtubeVideoIDQueryParams }
    }

    override fun getYouTubeVideoIDQueryParam(): String {
        return youtubeVideoIDQueryParams.value
    }

    override suspend fun storeYoutubeEmbedUrl(embedUrl: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_EMBED_URL] = embedUrl }
    }

    override fun getYoutubeEmbedUrl(): String {
        return youtubeEmbedUrl.value
    }

    override fun getYouTubeWatchPath(): String {
        return youtubePath.value
    }

    override suspend fun setUserOnboarded() {
        store.edit { prefs -> prefs[DUCK_PLAYER_USER_ONBOARDED] = true }
    }

    override fun getUserOnboarded(): Boolean {
        return duckPlayerUserOnboarded.value
    }

    override suspend fun setOpenInNewTab(enabled: Boolean) {
        store.edit { prefs -> prefs[DUCK_PLAYER_OPEN_IN_NEW_TAB] = enabled }
    }

    override fun observeOpenInNewTab(): Flow<Boolean> {
        return duckPlayerOpenInNewTab
    }

    override fun getOpenInNewTab(): Boolean {
        return duckPlayerOpenInNewTab.value
    }

    override suspend fun wasUsedBefore(): Boolean {
        return store.data.map { it[DUCK_PLAYER_WAS_USED] }.firstOrNull() ?: false
    }

    override suspend fun setUsed() {
        store.edit { it[DUCK_PLAYER_WAS_USED] = true }
    }
}
