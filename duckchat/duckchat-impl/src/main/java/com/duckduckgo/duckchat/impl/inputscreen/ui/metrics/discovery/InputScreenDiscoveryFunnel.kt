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

package com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.FeatureEnabled
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.FullyConverted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.OmnibarInteracted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.PromptSubmitted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.SearchSubmitted
import com.duckduckgo.duckchat.impl.inputscreen.ui.metrics.discovery.InputScreenDiscoveryFunnelStep.SettingsSeen
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface InputScreenDiscoveryFunnel {
    fun onDuckAiSettingsSeen()
    fun onInputScreenEnabled()
    fun onInputScreenDisabled()
    fun onInputScreenOpened()
    fun onSearchSubmitted()
    fun onPromptSubmitted()
}

@ContributesBinding(AppScope::class)
class InputScreenDiscoveryFunnelImpl @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    @InputScreenDiscoveryFunnelStore private val dataStore: DataStore<Preferences>,
    private val pixel: Pixel,
    private val duckAiFeatureState: DuckAiFeatureState,
) : InputScreenDiscoveryFunnel {

    private val stepProcessingMutex = Mutex()

    override fun onDuckAiSettingsSeen() {
        if (!duckAiFeatureState.showInputScreen.value) {
            onStep(SettingsSeen)
        }
    }

    override fun onInputScreenEnabled() {
        onStep(FeatureEnabled)
    }

    override fun onInputScreenDisabled() {
        resetFunnel()
    }

    override fun onInputScreenOpened() {
        onStep(OmnibarInteracted)
    }

    override fun onSearchSubmitted() {
        onStep(SearchSubmitted)
    }

    override fun onPromptSubmitted() {
        onStep(PromptSubmitted)
    }

    private fun resetFunnel() {
        coroutineScope.launch {
            stepProcessingMutex.withLock {
                dataStore.edit { preferences ->
                    preferences[SettingsSeen.prefKey] = false
                    preferences[FeatureEnabled.prefKey] = false
                    preferences[OmnibarInteracted.prefKey] = false
                    preferences[SearchSubmitted.prefKey] = false
                    preferences[PromptSubmitted.prefKey] = false
                    preferences[FullyConverted.prefKey] = false
                }
            }
        }
    }

    private fun onStep(step: InputScreenDiscoveryFunnelStep) = coroutineScope.launch {
        stepProcessingMutex.withLock {
            val data = dataStore.data.firstOrNull()
            val stepReached = data?.get(step.prefKey) == true
            if (!stepReached) {
                val dependenciesSatisfied = step.dependsOn.all { data?.get(it.prefKey) == true }
                if (dependenciesSatisfied) {
                    pixel.fire(step.pixelName)
                    dataStore.edit { prefs ->
                        prefs[step.prefKey] = true
                    }
                    checkForFullConversion()
                }
            }
        }
    }

    private suspend fun checkForFullConversion() {
        val data = dataStore.data.firstOrNull()
        val isFullyConverted = data?.get(FullyConverted.prefKey) == true
        if (!isFullyConverted) {
            val fullConversionDependenciesSatisfied = FullyConverted.dependsOn.all { data?.get(it.prefKey) == true }
            if (fullConversionDependenciesSatisfied) {
                pixel.fire(FullyConverted.pixelName)
                dataStore.edit { prefs ->
                    prefs[FullyConverted.prefKey] = true
                }
            }
        }
    }
}
