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

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.normalizeScheme
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupManager
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISABLE_PROTECTIONS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISSED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DISMISS_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.DONT_SHOW_AGAIN_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupUiEvent.PRIVACY_DASHBOARD_CLICKED
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.privacyprotectionspopup.impl.PrivacyProtectionsPopupExperimentVariant.TEST
import com.duckduckgo.privacyprotectionspopup.impl.db.PopupDismissDomainRepository
import com.duckduckgo.privacyprotectionspopup.impl.store.PrivacyProtectionsPopupDataStore
import com.squareup.anvil.annotations.ContributesBinding
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
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.Duration
import javax.inject.Inject

@ContributesBinding(FragmentScope::class)
class PrivacyProtectionsPopupManagerImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val featureFlag: PrivacyProtectionsPopupFeature,
    private val dataProvider: PrivacyProtectionsPopupManagerDataProvider,
    private val timeProvider: TimeProvider,
    private val popupDismissDomainRepository: PopupDismissDomainRepository,
    private val dataStore: PrivacyProtectionsPopupDataStore,
    private val userAllowListRepository: UserAllowListRepository,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val variantRandomizer: PrivacyProtectionsPopupExperimentVariantRandomizer,
    private val pixels: PrivacyProtectionsPopupPixels,
) : PrivacyProtectionsPopupManager {

    private val state = MutableStateFlow(
        State(
            featureAvailable = false,
            popupData = null,
            domain = null,
            hasHttpErrorCodes = false,
            hasBrowserError = false,
            viewState = PrivacyProtectionsPopupViewState.Gone,
        ),
    )

    private var dataLoadingJob: Job? = null

    override val viewState = state
        .map { state -> state.viewState }
        .onStart { startDataLoading() }
        .onCompletion { stopDataLoading() }
        .stateIn(
            scope = appCoroutineScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = PrivacyProtectionsPopupViewState.Gone,
        )

    override fun onUiEvent(event: PrivacyProtectionsPopupUiEvent) {
        when (event) {
            DISMISSED -> {
                dismissPopup()
                pixels.reportPopupDismissedViaClickOutside()
            }

            DISMISS_CLICKED -> {
                dismissPopup()
                pixels.reportPopupDismissedViaButton()
            }

            DISABLE_PROTECTIONS_CLICKED -> {
                state.value.domain?.let { domain ->
                    appCoroutineScope.launch {
                        userAllowListRepository.addDomainToUserAllowList(domain)
                    }
                }
                dismissPopup()
                pixels.reportProtectionsDisabled()
            }

            PRIVACY_DASHBOARD_CLICKED -> {
                dismissPopup()
                pixels.reportPrivacyDashboardOpened()
            }

            DONT_SHOW_AGAIN_CLICKED -> {
                appCoroutineScope.launch {
                    dataStore.setDoNotShowAgainClicked(clicked = true)
                }
                dismissPopup()
                pixels.reportDoNotShowAgainClicked()
            }
        }
    }

    override fun onPageRefreshTriggeredByUser(isOmnibarAtTheTop: Boolean) {
        var popupTriggered = false
        var experimentVariantToStore: PrivacyProtectionsPopupExperimentVariant? = null

        val updatedState = state.updateAndGet { oldState ->
            if (oldState.popupData == null) return@updateAndGet oldState

            val popupConditionsMet = arePopupConditionsMet(state = oldState)

            var experimentVariant = oldState.popupData.experimentVariant

            if (experimentVariant == null && popupConditionsMet) {
                experimentVariant = variantRandomizer.getRandomVariant()
                experimentVariantToStore = experimentVariant
            } else {
                experimentVariantToStore = null
            }

            val shouldShowPopup = popupConditionsMet && experimentVariant == TEST

            popupTriggered = shouldShowPopup && oldState.viewState is PrivacyProtectionsPopupViewState.Gone

            oldState.copy(
                viewState = if (shouldShowPopup) {
                    PrivacyProtectionsPopupViewState.Visible(
                        doNotShowAgainOptionAvailable = oldState.popupData.popupTriggerCount > 0,
                        isOmnibarAtTheTop = isOmnibarAtTheTop,
                    )
                } else {
                    PrivacyProtectionsPopupViewState.Gone
                },
            )
        }

        appCoroutineScope.launch {
            experimentVariantToStore?.let { variant ->
                dataStore.setExperimentVariant(variant)
                pixels.reportExperimentVariantAssigned()
                logcat(tag = PrivacyProtectionsPopupManagerImpl::class.simpleName) {
                    "Experiment variant assigned: $variant"
                }
            }

            if (popupTriggered) {
                val count = dataStore.getPopupTriggerCount()
                dataStore.setPopupTriggerCount(count + 1)
                pixels.reportPopupTriggered()
            }

            tryReportPageRefreshOnPossibleBreakage(updatedState)
        }
    }

    override fun onPageLoaded(
        url: String,
        httpErrorCodes: List<Int>,
        hasBrowserError: Boolean,
    ) {
        state.update { oldState ->
            val newDomain = url.extractDomain().takeUnless { it.isNullOrBlank() }

            if (newDomain != oldState.domain) {
                oldState.copy(
                    popupData = null,
                    domain = newDomain,
                    hasHttpErrorCodes = httpErrorCodes.isNotEmpty(),
                    hasBrowserError = hasBrowserError,
                )
            } else {
                oldState.copy(
                    hasHttpErrorCodes = httpErrorCodes.isNotEmpty(),
                    hasBrowserError = hasBrowserError,
                )
            }
        }
    }

    private fun dismissPopup() {
        state.update { it.copy(viewState = PrivacyProtectionsPopupViewState.Gone) }

        val popupDismissedAt = timeProvider.getCurrentTime()

        state.value.domain?.let { domain ->
            appCoroutineScope.launch {
                popupDismissDomainRepository.setPopupDismissTime(domain, popupDismissedAt)
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startDataLoading() {
        dataLoadingJob = appCoroutineScope.launch {
            state.update { it.copy(featureAvailable = featureFlag.isEnabled()) }

            state.map { it.domain }
                .distinctUntilChanged()
                .flatMapLatest { domain ->
                    if (domain != null) {
                        dataProvider.getData(domain)
                    } else {
                        flowOf(null)
                    }
                }
                .onEach { popupData ->
                    state.update { it.copy(popupData = popupData) }
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
        val popupData: PrivacyProtectionsPopupManagerData?,
        val domain: String?,
        val hasHttpErrorCodes: Boolean,
        val hasBrowserError: Boolean,
        val viewState: PrivacyProtectionsPopupViewState,
    )

    private fun arePopupConditionsMet(state: State): Boolean = with(state) {
        if (popupData == null) return@with false

        val popupDismissed = popupData.popupDismissedAt != null &&
            popupData.popupDismissedAt + DISMISS_REMEMBER_DURATION > timeProvider.getCurrentTime()

        val toggleUsed = popupData.toggleUsedAt != null &&
            popupData.toggleUsedAt + TOGGLE_USAGE_REMEMBER_DURATION > timeProvider.getCurrentTime()

        val isDuckDuckGoDomain = domain?.let { duckDuckGoUrlDetector.isDuckDuckGoUrl(it.normalizeScheme()) }

        featureAvailable &&
            popupData.protectionsEnabled &&
            domain != null &&
            isDuckDuckGoDomain == false &&
            !hasHttpErrorCodes &&
            !hasBrowserError &&
            !popupDismissed &&
            !toggleUsed &&
            !popupData.doNotShowAgainClicked
    }

    private fun tryReportPageRefreshOnPossibleBreakage(state: State) = with(state) {
        if (popupData == null) return

        val isDuckDuckGoDomain = domain?.let { duckDuckGoUrlDetector.isDuckDuckGoUrl(it.normalizeScheme()) }

        if (
            popupData.protectionsEnabled &&
            domain != null &&
            isDuckDuckGoDomain == false &&
            !hasHttpErrorCodes &&
            !hasBrowserError
        ) {
            pixels.reportPageRefreshOnPossibleBreakage()
        }
    }

    companion object {
        private val DISMISS_REMEMBER_DURATION: Duration = Duration.ofDays(2)
        val TOGGLE_USAGE_REMEMBER_DURATION: Duration = Duration.ofDays(30)
    }
}
