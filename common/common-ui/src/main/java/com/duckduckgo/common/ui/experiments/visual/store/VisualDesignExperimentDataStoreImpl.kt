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

class VisualDesignExperimentDataStoreImpl(
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

    private val _experimentFeatureFlagEnabled =
        MutableStateFlow(experimentalUIThemingFeature.self().isEnabled() && experimentalUIThemingFeature.visualUpdatesFeature().isEnabled())
    private val _duckAIFeatureFlagEnabled =
        MutableStateFlow(_experimentFeatureFlagEnabled.value && experimentalUIThemingFeature.duckAIPoCFeature().isEnabled())

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
            _experimentFeatureFlagEnabled.value =
                experimentalUIThemingFeature.self().isEnabled() && experimentalUIThemingFeature.visualUpdatesFeature().isEnabled()
            _duckAIFeatureFlagEnabled.value =
                _experimentFeatureFlagEnabled.value && experimentalUIThemingFeature.duckAIPoCFeature().isEnabled()
            _anyConflictingExperimentEnabled.value = isAnyConflictingExperimentEnabled()
        }
    }
}

/**
 * Factory for integration with [VisualDesignExperimentDataStoreLazyProvider] and testing purposes,
 * to get an actual instance for your use case just inject [VisualDesignExperimentDataStore].
 */
interface VisualDesignExperimentDataStoreImplFactory {
    fun create(
        appCoroutineScope: CoroutineScope,
        experimentalUIThemingFeature: ExperimentalUIThemingFeature,
        featureTogglesInventory: FeatureTogglesInventory,
    ): VisualDesignExperimentDataStoreImpl
}

@ContributesBinding(scope = AppScope::class)
class VisualDesignExperimentDataStoreImplFactoryImpl @Inject constructor() : VisualDesignExperimentDataStoreImplFactory {
    override fun create(
        appCoroutineScope: CoroutineScope,
        experimentalUIThemingFeature: ExperimentalUIThemingFeature,
        featureTogglesInventory: FeatureTogglesInventory,
    ): VisualDesignExperimentDataStoreImpl {
        return VisualDesignExperimentDataStoreImpl(
            appCoroutineScope = appCoroutineScope,
            experimentalUIThemingFeature = experimentalUIThemingFeature,
            featureTogglesInventory = featureTogglesInventory,
        )
    }
}
