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
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import javax.inject.Inject

enum class SegmentSkipMode(val pixelValue: String) {
    ALWAYS_ASK("ask"),
    ALWAYS_SKIP("skip"),
    OFF("off"),
}

interface SegmentSkipUserPreferences {
    fun modeFlow(): Flow<SegmentSkipMode>

    /**
     * The last mode read from storage, for callers that can't suspend. It holds the default until the first read completes, so
     * callers that can suspend should read [modeFlow] instead.
     */
    fun cachedMode(): SegmentSkipMode

    fun consentFlow(): Flow<Boolean>

    fun hasConsent(): Boolean

    suspend fun setMode(mode: SegmentSkipMode, userConsent: Boolean = false): Boolean
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealSegmentSkipUserPreferences @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
    @AppCoroutineScope appScope: CoroutineScope,
    private val feature: SegmentSkipFeature,
) : SegmentSkipUserPreferences {

    private val mode: StateFlow<SegmentSkipMode> =
        modeFlow().stateIn(appScope, SharingStarted.Eagerly, defaultMode(feature.mvpEnabled().isEnabled()))

    private val setModeMutex = Mutex()

    private val consent = consentFlow().stateIn(appScope, SharingStarted.Eagerly, false)

    override fun consentFlow(): Flow<Boolean> = dataStore.data.map { it[KEY_CONSENT] == true && it[KEY_MODE] != SegmentSkipMode.OFF.name }
        .catch { cause ->
            if (cause !is IOException) throw cause
            emit(false)
        }.distinctUntilChanged()

    override fun hasConsent(): Boolean = consent.value

    override fun modeFlow(): Flow<SegmentSkipMode> = combine(
        dataStore.data,
        feature.mvpEnabled().enabled(),
    ) { prefs, mvpEnabled ->
        prefs[KEY_MODE]?.let { stored -> SegmentSkipMode.entries.firstOrNull { it.name == stored } }
            ?: defaultMode(mvpEnabled)
    }
        .catch { cause ->
            if (cause !is IOException) throw cause
            emit(SegmentSkipMode.OFF)
        }
        .distinctUntilChanged()

    override fun cachedMode(): SegmentSkipMode = mode.value

    override suspend fun setMode(mode: SegmentSkipMode, userConsent: Boolean): Boolean = setModeMutex.withLock {
        var changed = false
        dataStore.edit { prefs ->
            val previous = prefs[KEY_MODE]?.let { stored ->
                SegmentSkipMode.entries.firstOrNull {
                    it.name == stored
                }
            } ?: defaultMode(feature.mvpEnabled().isEnabled())
            changed = previous != mode
            prefs[KEY_MODE] = mode.name
            if (mode != SegmentSkipMode.OFF && previous == SegmentSkipMode.OFF) {
                prefs[KEY_CONSENT] = userConsent
            }
        }
        changed
    }

    private fun defaultMode(mvpEnabled: Boolean): SegmentSkipMode =
        if (mvpEnabled) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF

    private companion object {
        val KEY_CONSENT = booleanPreferencesKey("segment_skip_telemetry_consent")
        val KEY_MODE = stringPreferencesKey("segment_skip_mode")
    }
}
