/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.adblocking.impl.sponsor

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.duckduckgo.adblocking.impl.di.AdBlockingPreferences
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SponsorMode {
    ALWAYS_ASK,
    ALWAYS_SKIP,
    OFF,
}

interface SponsorUserPreferences {
    fun modeFlow(): Flow<SponsorMode>

    /**
     * The last mode read from storage, for callers that can't suspend. It holds the default until the first read completes, so
     * callers that can suspend should read [modeFlow] instead.
     */
    fun cachedMode(): SponsorMode

    suspend fun setMode(mode: SponsorMode)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSponsorUserPreferences @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
    @AppCoroutineScope appScope: CoroutineScope,
) : SponsorUserPreferences {

    private val mode: StateFlow<SponsorMode> = modeFlow().stateIn(appScope, SharingStarted.Eagerly, DEFAULT_MODE)

    override fun modeFlow(): Flow<SponsorMode> = dataStore.data
        .map { prefs -> prefs[KEY_MODE]?.let { stored -> SponsorMode.entries.firstOrNull { it.name == stored } } ?: DEFAULT_MODE }
        .distinctUntilChanged()

    override fun cachedMode(): SponsorMode = mode.value

    override suspend fun setMode(mode: SponsorMode) {
        dataStore.edit { prefs -> prefs[KEY_MODE] = mode.name }
    }

    private companion object {
        val DEFAULT_MODE = SponsorMode.ALWAYS_ASK
        val KEY_MODE = stringPreferencesKey("sponsor_block_mode")
    }
}
