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
import com.duckduckgo.common.ui.experiments.visual.ExperimentalThemingFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
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
import kotlinx.coroutines.withContext

@ContributesBinding(
    scope = AppScope::class,
    boundType = ExperimentalThemingDataStore::class,
)
@ContributesMultibinding(scope = AppScope::class, boundType = PrivacyConfigCallbackPlugin::class)
@SingleInstanceIn(scope = AppScope::class)
class ExperimentalThemingDataStoreImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val experimentalThemingFeature: ExperimentalThemingFeature,
) : ExperimentalThemingDataStore, PrivacyConfigCallbackPlugin {

    // TODO: Revisit this when the split omnibar feature is revived
    // private val _splitOmnibarFlagEnabled =
    //     MutableStateFlow(experimentalThemingFeature.self().isEnabled() && experimentalThemingFeature.splitOmnibarFeature().isEnabled())
    private val _splitOmnibarFlagEnabled = MutableStateFlow(false).asStateFlow()

  private val _newDesignFeatureFlagEnabled =
        MutableStateFlow(experimentalThemingFeature.singleOmnibarFeature().isEnabled())

    override val isSplitOmnibarEnabled: StateFlow<Boolean> = _splitOmnibarFlagEnabled.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = _splitOmnibarFlagEnabled.value,
    )

    override val isSingleOmnibarEnabled: StateFlow<Boolean> = combine(isSplitOmnibarEnabled, _newDesignFeatureFlagEnabled) {
            withBottomBar, withoutBottomBar ->
        !withBottomBar && withoutBottomBar
    }.stateIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = !isSplitOmnibarEnabled.value && _newDesignFeatureFlagEnabled.value,
    )

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch {
            updateFeatureState()
        }
    }

    @SuppressLint("DenyListedApi")
    override suspend fun changeExperimentFlagPreference(enabled: Boolean) = withContext(dispatcherProvider.io()) {
        experimentalThemingFeature.self().setRawStoredState(Toggle.State(remoteEnableState = enabled))

        // TODO: Revisit this when the split omnibar feature is revived
        // experimentalThemingFeature.splitOmnibarFeature().setRawStoredState(Toggle.State(remoteEnableState = enabled))

        updateFeatureState()
    }

    private suspend fun updateFeatureState() = withContext(dispatcherProvider.io()) {
        // TODO: Revisit this when the split omnibar feature is revived
        // _splitOmnibarFlagEnabled.value =
        //     experimentalThemingFeature.self().isEnabled() && experimentalThemingFeature.splitOmnibarFeature().isEnabled()
    }

    override suspend fun countSingleOmnibarUser() {
        withContext(dispatcherProvider.io()) {
            experimentalThemingFeature.singleOmnibarFeature().enroll()
        }
    }
}
