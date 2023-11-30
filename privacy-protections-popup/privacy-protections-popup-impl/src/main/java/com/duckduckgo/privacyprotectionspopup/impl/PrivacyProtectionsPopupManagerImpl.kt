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
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupManagerImpl.PopupDismissed.DismissedAt
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupManagerImpl.PopupDismissed.NotDismissed
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.threeten.bp.Duration
import org.threeten.bp.Instant

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupManagerImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val featureAvailability: PrivacyProtectionsPopupFeatureAvailability,
    private val protectionsStateProvider: ProtectionsStateProvider,
    private val timeProvider: TimeProvider,
    private val repository: PrivacyProtectionsPopupRepository,
    private val userAllowListRepository: UserAllowListRepository,
) : PrivacyProtectionsPopupManager {

    private val state = MutableStateFlow(
        State(
            featureAvailable = false,
            protectionsEnabled = null,
            refreshTriggeredAt = null,
            domain = null,
            hasHttpErrorCodes = false,
            popupDismissed = null,
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
                dismissPopup()
            }

            DISMISS_CLICKED -> {
                // TODO pixel
                dismissPopup()
            }

            DISABLE_PROTECTIONS_CLICKED -> {
                // TODO pixel
                state.value.domain?.let { domain ->
                    appCoroutineScope.launch {
                        userAllowListRepository.addDomainToUserAllowList(domain)
                    }
                }
                dismissPopup()
            }
        }
    }

    override fun onPageRefreshTriggeredByUser() {
        state.update { it.copy(refreshTriggeredAt = timeProvider.getCurrentTime()) }
    }

    override fun onPageLoaded(
        url: String,
        httpErrorCodes: List<Int>,
    ) {
        state.update { oldState ->
            val newDomain = url.extractDomain().takeUnless { it.isNullOrBlank() }

            if (newDomain != oldState.domain) {
                oldState.copy(
                    refreshTriggeredAt = null,
                    protectionsEnabled = null,
                    domain = newDomain,
                    hasHttpErrorCodes = httpErrorCodes.isNotEmpty(),
                    popupDismissed = null,
                )
            } else {
                oldState.copy(hasHttpErrorCodes = httpErrorCodes.isNotEmpty())
            }
        }
    }

    private fun dismissPopup() {
        val popupDismissedAt = timeProvider.getCurrentTime()

        state.value.domain?.let { domain ->
            appCoroutineScope.launch {
                repository.setPopupDismissTime(domain, popupDismissedAt)
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
                .launchIn(this)

            state.map { it.domain }
                .distinctUntilChanged()
                .flatMapLatest { domain ->
                    if (domain != null) {
                        repository.getPopupDismissTime(domain)
                    } else {
                        flowOf(null)
                    }
                }
                .map { dismissedAt -> if (dismissedAt != null) DismissedAt(dismissedAt) else NotDismissed }
                .onEach { popupDismissed ->
                    state.update { it.copy(popupDismissed = popupDismissed) }
                }
                .launchIn(this)
        }
    }

    private suspend fun stopDataLoading() {
        dataLoadingJob?.cancelAndJoin()
        dataLoadingJob = null
    }

    private data class State(
        val featureAvailable: Boolean,
        val protectionsEnabled: Boolean?,
        val refreshTriggeredAt: Instant?,
        val domain: String?,
        val hasHttpErrorCodes: Boolean,
        val popupDismissed: PopupDismissed?,
    )

    private sealed class PopupDismissed {
        data object NotDismissed : PopupDismissed()
        data class DismissedAt(val timestamp: Instant) : PopupDismissed()
    }

    private fun createViewState(state: State): PrivacyProtectionsPopupViewState = with(state) {
        val refreshTriggered = refreshTriggeredAt != null &&
            refreshTriggeredAt + REFRESH_TRIGGER_VALID_DURATION > timeProvider.getCurrentTime()

        val popupDismissed = when (popupDismissed) {
            is DismissedAt -> {
                popupDismissed.timestamp + DISMISS_REMEMBER_DURATION > timeProvider.getCurrentTime()
            }

            NotDismissed -> false
            null -> null
        }

        val shouldShowPopup = featureAvailable &&
            refreshTriggered &&
            protectionsEnabled == true &&
            domain != null &&
            !hasHttpErrorCodes &&
            popupDismissed == false

        return PrivacyProtectionsPopupViewState(visible = shouldShowPopup)
    }

    private companion object {
        val REFRESH_TRIGGER_VALID_DURATION: Duration = Duration.ofMillis(100)
        val DISMISS_REMEMBER_DURATION: Duration = Duration.ofDays(1)
    }
}
