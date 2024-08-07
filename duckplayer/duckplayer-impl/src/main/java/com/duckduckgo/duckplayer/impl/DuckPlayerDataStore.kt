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
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_DISABLED_HELP_PAGE
import com.duckduckgo.duckplayer.impl.SharedPreferencesDuckPlayerDataStore.Keys.DUCK_PLAYER_RC
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
    suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String)
    suspend fun getDuckPlayerDisabledHelpPageLink(): String
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

    override suspend fun storeDuckPlayerDisabledHelpPageLink(duckPlayerDisabledHelpPageLink: String) {
        store.edit { prefs -> prefs[Keys.DUCK_PLAYER_DISABLED_HELP_PAGE] = duckPlayerDisabledHelpPageLink }
    }

    override suspend fun getDuckPlayerDisabledHelpPageLink(): String {
        return duckPlayerDisabledHelpPageLink.first()
    }
}
