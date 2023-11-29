/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.privacyprotectionspopup.impl

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupManagerImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val featureAvailability: PrivacyProtectionsPopupFeatureAvailability,
    private val protectionsStateProvider: ProtectionsStateProvider,
) : PrivacyProtectionsPopupManager {

    private val state = MutableStateFlow(
        State(
            featureAvailable = false,
            protectionsEnabled = null,
            refreshTriggered = false,
            domain = null,
            hasHttpErrorCodes = false,
        ),
    )

    private var dataLoadingJob: Job? = null

    override val viewState = state
        .map { createViewState(it) }
        .onStart { startDataLoading() }
        .onCompletion { stopDataLoading() }
        .stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = PrivacyProtectionsPopupViewState(visible = false),
        )

    override fun onUiEvent(event: PrivacyProtectionsPopupUiEvent) {
        when (event) {
            DISMISSED -> {
                state.update { oldState -> oldState.copy(refreshTriggered = false) }
            }

            DISMISS_CLICKED -> {
                // TODO pixel
                state.update { oldState -> oldState.copy(refreshTriggered = false) }
            }

            DISABLE_PROTECTIONS_CLICKED -> {
                // TODO pixel
                // TODO add to allowlist
                state.update { oldState -> oldState.copy(refreshTriggered = false) }
            }
        }
    }

    override fun onPageRefreshTriggeredByUser() {
        state.update { it.copy(refreshTriggered = true) }
    }

    override fun onPageLoaded(
        url: String,
        httpErrorCodes: List<Int>,
    ) {
        state.update { oldState ->
            val newDomain = url.extractDomain().takeUnless { it.isNullOrBlank() }

            if (newDomain != oldState.domain) {
                oldState.copy(
                    refreshTriggered = false,
                    protectionsEnabled = null,
                    domain = newDomain,
                    hasHttpErrorCodes = httpErrorCodes.isNotEmpty(),
                )
            } else {
                oldState.copy(hasHttpErrorCodes = httpErrorCodes.isNotEmpty())
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDataLoading() {
        dataLoadingJob = appCoroutineScope.launch {
            val featureAvailable = featureAvailability.isAvailable()
            state.update { it.copy(featureAvailable = featureAvailable) }

            if (!featureAvailable) return@launch

            state.map { it.domain }
                .distinctUntilChanged()
                .flatMapLatest { domain ->
                    if (domain != null) {
                        protectionsStateProvider.areProtectionsEnabled(domain)
                    } else {
                        flowOf(null)
                    }
                }
                .onEach { protectionsEnabled ->
                    state.update { it.copy(protectionsEnabled = protectionsEnabled) }
                }
                .collect()
        }
    }

    private suspend fun stopDataLoading() {
        dataLoadingJob?.cancelAndJoin()
        dataLoadingJob = null
    }

    private data class State(
        val featureAvailable: Boolean,
        val protectionsEnabled: Boolean?,
        val refreshTriggered: Boolean,
        val domain: String?,
        val hasHttpErrorCodes: Boolean,
    )

    private fun createViewState(state: State): PrivacyProtectionsPopupViewState = with(state) {
        val shouldShowPopup = featureAvailable &&
            refreshTriggered &&
            protectionsEnabled == true &&
            domain != null &&
            !hasHttpErrorCodes

        return PrivacyProtectionsPopupViewState(visible = shouldShowPopup)
    }
}
