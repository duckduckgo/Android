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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@ContributesBinding(
    scope = AppScope::class,
    boundType = VisualDesignExperimentDataStore::class,
)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
@SingleInstanceIn(scope = AppScope::class)
class VisualDesignExperimentDataStoreImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val experimentalUIThemingFeature: ExperimentalUIThemingFeature,
) : VisualDesignExperimentDataStore, PrivacyConfigCallbackPlugin {

    private val _experimentFeatureFlagEnabled =
        MutableStateFlow(experimentalUIThemingFeature.self().isEnabled() && experimentalUIThemingFeature.visualUpdatesFeature().isEnabled())
    private val _duckAIFeatureFlagEnabled = MutableStateFlow(experimentalUIThemingFeature.duckAIPoCFeature().isEnabled())

    override val isExperimentEnabled: StateFlow<Boolean> = _experimentFeatureFlagEnabled.asStateFlow()
    override val isDuckAIPoCEnabled: StateFlow<Boolean> = _duckAIFeatureFlagEnabled.asStateFlow()

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
            _duckAIFeatureFlagEnabled.value = experimentalUIThemingFeature.duckAIPoCFeature().isEnabled()
        }
    }
}
