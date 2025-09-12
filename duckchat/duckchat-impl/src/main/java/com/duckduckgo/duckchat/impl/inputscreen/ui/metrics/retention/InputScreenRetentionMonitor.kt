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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.retention

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStore
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(scope = AppScope::class)
class InputScreenRetentionMonitor @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @InputScreenRetentionMonitorStore private val retentionMonitorDataStore: DataStore<Preferences>,
    private val duckAiFeatureState: DuckAiFeatureState,
    private val pixel: Pixel,
    private val timeProvider: TimeProvider,
) : MainProcessLifecycleObserver {

    private object Key {
        val LAST_CHECK_DATE_TIME_ET = stringPreferencesKey("LAST_CHECK_DATE_TIME_ET")
        val LAST_ENABLED_STATE = booleanPreferencesKey("LAST_ENABLED_STATE")
    }

    private val processingMutex = Mutex()

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)

        coroutineScope.launch {
            processingMutex.withLock {
                val persistedData = retentionMonitorDataStore.data.firstOrNull()
                val lastCheckETString = persistedData?.get(Key.LAST_CHECK_DATE_TIME_ET)
                if (lastCheckETString != null) {
                    try {
                        val lastCheckET = ZonedDateTime.parse(lastCheckETString)
                        val nowMinus24hET = timeProvider.nowInEasternTime().minusHours(24)
                        if (nowMinus24hET > lastCheckET) {
                            val wasFeatureEnabled = persistedData[Key.LAST_ENABLED_STATE]
                            if (wasFeatureEnabled == true) {
                                val isStillEnabled = duckAiFeatureState.showInputScreen.value
                                pixel.fire(
                                    pixel = DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_DAILY_RETENTION,
                                    parameters = mapOf("still_enabled" to isStillEnabled.toString())
                                )
                            }
                        } else {
                            return@launch
                        }
                    } catch (ex: Exception) {
                        logcat(priority = ERROR) {
                            "couldn't parse last check date time ('$lastCheckETString') due to exception: ${ex.asLog()}"
                        }
                        // try to recover next time
                        val currentTimeETString = timeProvider.nowInEasternTime().toString()
                        retentionMonitorDataStore.edit { preferences ->
                            preferences[Key.LAST_CHECK_DATE_TIME_ET] = currentTimeETString
                        }
                        return@launch
                    }
                }

                val currentTimeETString = timeProvider.nowInEasternTime().toString()
                val isFeatureEnabled = duckAiFeatureState.showInputScreen.value
                retentionMonitorDataStore.edit { preferences ->
                    preferences[Key.LAST_CHECK_DATE_TIME_ET] = currentTimeETString
                    preferences[Key.LAST_ENABLED_STATE] = isFeatureEnabled
                }
            }
        }
    }
}
