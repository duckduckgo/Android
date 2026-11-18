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

package com.duckduckgo.adblocking.impl.segmentskip

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class SegmentSkipMode {
    ALWAYS_ASK,
    ALWAYS_SKIP,
    OFF,
}

interface SegmentSkipUserPreferences {
    fun modeFlow(): Flow<SegmentSkipMode>

    /**
     * The last mode read from storage, for callers that can't suspend. It holds the default until the first read completes, so
     * callers that can suspend should read [modeFlow] instead.
     */
    fun cachedMode(): SegmentSkipMode

    suspend fun setMode(mode: SegmentSkipMode)
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSegmentSkipUserPreferences @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
    @AppCoroutineScope appScope: CoroutineScope,
    private val feature: SegmentSkipFeature,
) : SegmentSkipUserPreferences {

    private val mode: StateFlow<SegmentSkipMode> = modeFlow().stateIn(appScope, SharingStarted.Eagerly, defaultMode())

    override fun modeFlow(): Flow<SegmentSkipMode> = combine(
        dataStore.data,
        feature.mvpEnabled().enabled(),
        feature.softLaunchEnabled().enabled(),
    ) { prefs, mvpEnabled, softLaunchEnabled ->
        prefs[KEY_MODE]?.let { stored -> SegmentSkipMode.entries.firstOrNull { it.name == stored } }
            ?: if (mvpEnabled && softLaunchEnabled) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF
    }
        .distinctUntilChanged()

    override fun cachedMode(): SegmentSkipMode = mode.value

    override suspend fun setMode(mode: SegmentSkipMode) {
        dataStore.edit { prefs -> prefs[KEY_MODE] = mode.name }
    }

    private fun defaultMode(): SegmentSkipMode =
        if (feature.mvpEnabled().isEnabled() && feature.softLaunchEnabled().isEnabled()) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF

    private companion object {
        val KEY_MODE = stringPreferencesKey("sponsor_block_mode")
    }
}
