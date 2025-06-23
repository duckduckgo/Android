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

package com.duckduckgo.duckchat.impl.repository

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.ui.experiments.visual.ExperimentalUIThemingFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiVisibilityRepository
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.duckduckgo.feature.toggles.api.Toggle
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
// todo observe config changes?
class DuckAiVisibilityRepositoryImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChatFeature: DuckChatFeature,
    private val experimentalUIThemingFeature: ExperimentalUIThemingFeature,
    private val duckChatDataStore: DuckChatDataStore,
) : DuckAiVisibilityRepository {

    private val globalUserPreferenceFeature = UserPreferenceFeature(
        coroutineScope = appCoroutineScope,
        dispatcherProvider = dispatcherProvider,
        userPreferenceFlow = duckChatDataStore.observeDuckChatUserEnabled(),
        featureToggle = duckChatFeature.self(),
    )
    override val showSettings: SharedFlow<Boolean> = globalUserPreferenceFeature.state

    private val inputSwitchUserPreferenceFeature = UserPreferenceFeature(
        coroutineScope = appCoroutineScope,
        dispatcherProvider = dispatcherProvider,
        userPreferenceFlow = duckChatDataStore.observeInputSwitchEnabled(),
        parentUserPreferenceFeatureStateFlow = globalUserPreferenceFeature.state,
        featureToggle = experimentalUIThemingFeature.duckAIPoCFeature(),
    )
    override val showInputSwitch: SharedFlow<Boolean> = inputSwitchUserPreferenceFeature.state

    private val menuShortcutUserPreferenceFeature = UserPreferenceFeature(
        coroutineScope = appCoroutineScope,
        dispatcherProvider = dispatcherProvider,
        userPreferenceFlow = duckChatDataStore.observeShowInBrowserMenu(),
        parentUserPreferenceFeatureStateFlow = globalUserPreferenceFeature.state,
    )
    override val showPopupMenuShortcuts: SharedFlow<Boolean> = menuShortcutUserPreferenceFeature.state

    private val omnibarShortcutUserPreferenceFeature = UserPreferenceFeature(
        coroutineScope = appCoroutineScope,
        dispatcherProvider = dispatcherProvider,
        userPreferenceFlow = duckChatDataStore.observeShowInAddressBar(),
        parentUserPreferenceFeatureStateFlow = globalUserPreferenceFeature.state,
    )
    override val showOmnibarShortcutOnNtpAndOnFocus: SharedFlow<Boolean> = omnibarShortcutUserPreferenceFeature.state

    private val _showOmnibarShortcutInAllStates = MutableSharedFlow<Boolean>(replay = 1)
    override val showOmnibarShortcutInAllStates: SharedFlow<Boolean> = combine(
        omnibarShortcutUserPreferenceFeature.state,
        _showOmnibarShortcutInAllStates,
    ) { omnibarShortcutUserPreferenceFeature, showOmnibarShortcutInAllStates ->
        omnibarShortcutUserPreferenceFeature && showOmnibarShortcutInAllStates
    }.shareIn(
        scope = appCoroutineScope,
        started = SharingStarted.Eagerly,
        replay = 1
    )

    init {
        updateToggleState()
    }

    private fun updateToggleState() {
        globalUserPreferenceFeature.updateToggleState()
        inputSwitchUserPreferenceFeature.updateToggleState()
        menuShortcutUserPreferenceFeature.updateToggleState()
        omnibarShortcutUserPreferenceFeature.updateToggleState()
        appCoroutineScope.launch(context = dispatcherProvider.io()) {
            _showOmnibarShortcutInAllStates.emit(duckChatFeature.duckAiButtonInBrowser().isEnabled())
        }
    }
}

class UserPreferenceFeature(
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    userPreferenceFlow: Flow<Boolean>,
    parentUserPreferenceFeatureStateFlow: Flow<Boolean> = flowOf(true),
    private val featureToggle: Toggle? = null,
) {
    private val _available = MutableSharedFlow<Boolean>(replay = 1)

    /**
     * Returns [DuckAiFeatureStateRepository.UserPreferenceFeatureState] of this feature.
     *
     * If the designated parent feature is disabled, both [DuckAiFeatureStateRepository.UserPreferenceFeatureState.isAvailable] and [DuckAiFeatureStateRepository.UserPreferenceFeatureState.isEnabled] will report `false`,
     * until the parent feature gets enabled.
     */
    val state: SharedFlow<Boolean> =
        combine(
            _available,
            userPreferenceFlow,
            parentUserPreferenceFeatureStateFlow,
        ) { available, userPrefEnabled, parentState ->
            parentState && available && userPrefEnabled
        }
        .distinctUntilChanged()
        .shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
            replay = 1
        )

    fun updateToggleState() {
        coroutineScope.launch(context = dispatcherProvider.io()) {
            _available.emit(featureToggle?.isEnabled() ?: true)
        }
    }
}
