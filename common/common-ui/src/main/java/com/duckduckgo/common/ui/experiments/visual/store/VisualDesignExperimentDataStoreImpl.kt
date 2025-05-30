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

package com.duckduckgo.common.ui.experiments.visual.store

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualDesignExperimentDataStore::class,
)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
@SingleInstanceIn(scope = AppScope::class)
class VisualDesignExperimentDataStoreImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val experimentalUIThemingFeature: ExperimentalUIThemingFeature,
    private val featureTogglesInventory: FeatureTogglesInventory,
) : VisualDesignExperimentDataStore, PrivacyConfigCallbackPlugin {

    private companion object {

        private val conflictingExperimentsNames = listOf(
            "senseOfProtectionNewUserExperimentApr25",
            "senseOfProtectionExistingUserExperimentApr25",
            "senseOfProtectionNewUserExperimentMay25",
            "senseOfProtectionExistingUserExperimentMay25",
            "senseOfProtectionNewUserExperiment27May25",
            "senseOfProtectionExistingUserExperiment27May25",
            "defaultBrowserAdditionalPrompts202501",
        )
    }

    private val _anyConflictingExperimentEnabled = MutableStateFlow(isAnyConflictingExperimentEnabled())
    override val anyConflictingExperimentEnabled = _anyConflictingExperimentEnabled.asStateFlow()

    private val _experimentFeatureFlagEnabled = MutableStateFlow(experimentalUIThemingFeature.visualUpdatesFeature().isEnabled())
    private val _duckAIFeatureFlagEnabled = MutableStateFlow(experimentalUIThemingFeature.duckAIPoCFeature().isEnabled())

    override val isExperimentEnabled: StateFlow<Boolean> =
        combine(
            _experimentFeatureFlagEnabled,
            _anyConflictingExperimentEnabled,
        ) { experimentEnabled, conflicts ->
            experimentEnabled && !conflicts
        }.stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = _experimentFeatureFlagEnabled.value && !_anyConflictingExperimentEnabled.value,
        )

    override val isDuckAIPoCEnabled: StateFlow<Boolean> =
        combine(_duckAIFeatureFlagEnabled, isExperimentEnabled) { duckAIFeatureFlagEnabled, experimentEnabled ->
            duckAIFeatureFlagEnabled && experimentEnabled
        }.stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.Eagerly,
            initialValue = _duckAIFeatureFlagEnabled.value && isExperimentEnabled.value,
        )

    /**
     * This is a blocking call but it only blocks the main thread when the class initializes, so when the splash screen is visible.
     * All subsequent calls are moved off of the main thread.
     */
    private fun isAnyConflictingExperimentEnabled(): Boolean = runBlocking {
        val activeExperimentsNames = featureTogglesInventory.getAllActiveExperimentToggles().map { it.featureName().name }
        conflictingExperimentsNames.any { activeExperimentsNames.contains(it) }
    }

    override fun onPrivacyConfigDownloaded() {
        updateFeatureState()
    }

    @SuppressLint("DenyListedApi")
    override fun changeExperimentFlagPreference(enabled: Boolean) {
        experimentalUIThemingFeature.self().setRawStoredState(Toggle.State(remoteEnableState = enabled))
        experimentalUIThemingFeature.visualUpdatesFeature().setRawStoredState(Toggle.State(remoteEnableState = enabled))
        updateFeatureState()
    }

    @SuppressLint("DenyListedApi")
    override fun changeDuckAIPoCFlagPreference(enabled: Boolean) {
        experimentalUIThemingFeature.duckAIPoCFeature().setRawStoredState(Toggle.State(remoteEnableState = enabled))
        updateFeatureState()
    }

    private fun updateFeatureState() {
        appCoroutineScope.launch {
            _experimentFeatureFlagEnabled.value = experimentalUIThemingFeature.visualUpdatesFeature().isEnabled()
            _duckAIFeatureFlagEnabled.value = experimentalUIThemingFeature.duckAIPoCFeature().isEnabled()
            _anyConflictingExperimentEnabled.value = isAnyConflictingExperimentEnabled()
        }
    }
}
