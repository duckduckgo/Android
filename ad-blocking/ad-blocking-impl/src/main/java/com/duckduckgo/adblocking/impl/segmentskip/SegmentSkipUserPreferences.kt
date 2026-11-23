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
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.adblocking.impl.di.AdBlockingPreferences
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
@ContributesBinding(AppScope::class, binding = binding<SegmentSkipUserPreferences>())
@ContributesIntoSet(scope = AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class RealSegmentSkipUserPreferences @Inject constructor(
    @AdBlockingPreferences private val dataStore: DataStore<Preferences>,
    @AppCoroutineScope private val appScope: CoroutineScope,
    private val feature: SegmentSkipFeature,
) : SegmentSkipUserPreferences, MainProcessLifecycleObserver {

    private val mode: StateFlow<SegmentSkipMode> =
        modeFlow().stateIn(appScope, SharingStarted.Eagerly, defaultMode(feature.mvpEnabled().isEnabled()))

    private val setModeMutex = Mutex()

    private val consent = MutableStateFlow<Boolean?>(null)

    override fun onCreate(owner: LifecycleOwner) {
        appScope.launch {
            while (consent.value == null) {
                try {
                    setModeMutex.withLock {
                        if (consent.value == null) {
                            consent.value = dataStore.data.first().hasStoredConsent()
                        }
                    }
                } catch (_: IOException) {
                    delay(CONSENT_READ_RETRY_MILLIS)
                }
            }
        }
    }

    override fun consentFlow(): Flow<Boolean> = consent.map { it == true }.distinctUntilChanged()

    override fun hasConsent(): Boolean = consent.value == true

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
        val previousConsent = consent.value
        if (mode == SegmentSkipMode.OFF) consent.value = false

        // Keep the consent flow in sync with the write if the settings screen closes during the save.
        withContext(NonCancellable) {
            var changed = false
            val saved = try {
                dataStore.edit { prefs ->
                    val previous = prefs[KEY_MODE]?.let { stored ->
                        SegmentSkipMode.entries.firstOrNull { it.name == stored }
                    } ?: defaultMode(feature.mvpEnabled().isEnabled())
                    changed = previous != mode
                    prefs[KEY_MODE] = mode.name
                    if (mode != SegmentSkipMode.OFF && previous == SegmentSkipMode.OFF) {
                        prefs[KEY_CONSENT] = userConsent
                    }
                }
            } catch (e: Exception) {
                consent.value = previousConsent
                throw e
            }
            consent.value = saved.hasStoredConsent()
            changed
        }
    }

    private fun Preferences.hasStoredConsent(): Boolean = this[KEY_CONSENT] == true && this[KEY_MODE] != SegmentSkipMode.OFF.name

    private fun defaultMode(mvpEnabled: Boolean): SegmentSkipMode =
        if (mvpEnabled) SegmentSkipMode.ALWAYS_ASK else SegmentSkipMode.OFF

    private companion object {
        const val CONSENT_READ_RETRY_MILLIS = 1_000L
        val KEY_CONSENT = booleanPreferencesKey("segment_skip_telemetry_consent")
        val KEY_MODE = stringPreferencesKey("segment_skip_mode")
    }
}
