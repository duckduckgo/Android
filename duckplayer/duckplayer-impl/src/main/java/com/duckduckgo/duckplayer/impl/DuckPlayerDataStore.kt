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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_DISABLED_HELP_PAGE
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_RC
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_YOUTUBE_PATH
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.OVERLAY_INTERACTED
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.PRIVATE_PLAYER_MODE
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface DuckPlayerDataStore {
    suspend fun getDuckPlayerRemoteConfigJson(): String

    suspend fun setDuckPlayerRemoteConfigJson(value: String)

    suspend fun getOverlayInteracted(): Boolean

    fun observeOverlayInteracted(): Flow<Boolean>

    suspend fun setOverlayInteracted(value: Boolean)

    suspend fun getPrivatePlayerMode(): String

    fun observePrivatePlayerMode(): Flow<String>

    suspend fun setPrivatePlayerMode(value: String)

    suspend fun getDuckPlayerDisabledHelpPageLink(): String?

    suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String?)

    suspend fun getYouTubeWatchPath(): String

    suspend fun storeYouTubeWatchPath(youtubePath: String)

    suspend fun getYoutubeEmbedUrl(): String

    suspend fun storeYoutubeEmbedUrl(embedUrl: String)

    suspend fun getYouTubeUrl(): String

    suspend fun storeYouTubeUrl(youtubeUrl: String)

    suspend fun getYouTubeVideoIDQueryParam(): String

    suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParams: String)

    suspend fun getYouTubeReferrerQueryParams(): List<String>

    suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>)

    suspend fun getYouTubeReferrerHeaders(): List<String>

    suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>)
}

@ContributesBinding(AppScope::class)
class SharedPreferencesDuckPlayerDataStore @Inject constructor(
    @DuckPlayer private val store: DataStore<Preferences>,
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
    }

    private val overlayInteracted: Flow<Boolean>
        get() = store.data
            .map { prefs ->
                prefs[OVERLAY_INTERACTED] ?: false
            }
            .distinctUntilChanged()

    private val duckPlayerRC: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[DUCK_PLAYER_RC] ?: ""
            }
            .distinctUntilChanged()

    private val privatePlayerMode: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[PRIVATE_PLAYER_MODE] ?: "ALWAYS_ASK"
            }
            .distinctUntilChanged()

    private val duckPlayerDisabledHelpPageLink: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[DUCK_PLAYER_DISABLED_HELP_PAGE] ?: ""
            }
            .distinctUntilChanged()

    private val youtubePath: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[DUCK_PLAYER_YOUTUBE_PATH] ?: ""
            }
            .distinctUntilChanged()

    private val youtubeReferrerHeaders: Flow<List<String>>
        get() = store.data
            .map { prefs ->
                prefs[DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS]?.toList() ?: listOf()
            }
            .distinctUntilChanged()

    private val youtubeReferrerQueryParams: Flow<List<String>>
        get() = store.data
            .map { prefs ->
                prefs[Keys.DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS]?.toList() ?: listOf()
            }
            .distinctUntilChanged()

    private val youtubeUrl: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[Keys.DUCK_PLAYER_YOUTUBE_URL] ?: ""
            }
            .distinctUntilChanged()

    private val youtubeVideoIDQueryParams: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[Keys.DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS] ?: ""
            }
            .distinctUntilChanged()

    private val youtubeEmbedUrl: Flow<String>
        get() = store.data
            .map { prefs ->
                prefs[Keys.DUCK_PLAYER_YOUTUBE_EMBED_URL] ?: ""
            }
            .distinctUntilChanged()

    override suspend fun getDuckPlayerRemoteConfigJson(): String {
        return duckPlayerRC.first()
    }

    override suspend fun setDuckPlayerRemoteConfigJson(value: String) {
        store.edit { prefs -> prefs[DUCK_PLAYER_RC] = value }
    }

    override suspend fun getOverlayInteracted(): Boolean {
        return overlayInteracted.first()
    }

    override fun observeOverlayInteracted(): Flow<Boolean> {
        return overlayInteracted
    }

    override suspend fun setOverlayInteracted(value: Boolean) {
        store.edit { prefs -> prefs[OVERLAY_INTERACTED] = value }
    }

    override suspend fun getPrivatePlayerMode(): String {
        return privatePlayerMode.first()
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

    override suspend fun getDuckPlayerDisabledHelpPageLink(): String? {
        return duckPlayerDisabledHelpPageLink.first().let { it.ifBlank { null } }
    }

    override suspend fun storeYouTubeWatchPath(youtubePath: String) {
        store.edit { prefs -> prefs[DUCK_PLAYER_YOUTUBE_PATH] = youtubePath }
    }

    override suspend fun storeYouTubeReferrerHeaders(youtubeReferrerHeaders: List<String>) {
        store.edit { prefs -> prefs[DUCK_PLAYER_YOUTUBE_REFERRER_HEADERS] = youtubeReferrerHeaders.toSet() }
    }

    override suspend fun getYouTubeReferrerHeaders(): List<String> {
        return youtubeReferrerHeaders.first()
    }

    override suspend fun storeYouTubeReferrerQueryParams(youtubeReferrerQueryParams: List<String>) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_REFERRER_QUERY_PARAMS] = youtubeReferrerQueryParams.toSet() }
    }

    override suspend fun getYouTubeReferrerQueryParams(): List<String> {
        return youtubeReferrerQueryParams.first()
    }

    override suspend fun storeYouTubeUrl(youtubeUrl: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_URL] = youtubeUrl }
    }

    override suspend fun getYouTubeUrl(): String {
        return youtubeUrl.first()
    }

    override suspend fun storeYouTubeVideoIDQueryParam(youtubeVideoIDQueryParams: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_VIDEO_ID_QUERY_PARAMS] = youtubeVideoIDQueryParams }
    }

    override suspend fun getYouTubeVideoIDQueryParam(): String {
        return youtubeVideoIDQueryParams.first()
    }

    override suspend fun storeYoutubeEmbedUrl(embedUrl: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_YOUTUBE_EMBED_URL] = embedUrl }
    }

    override suspend fun getYoutubeEmbedUrl(): String {
        return youtubeEmbedUrl.first()
    }

    override suspend fun getYouTubeWatchPath(): String {
        return youtubePath.first()
    }
}
