/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.*
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class OnboardingViewModel @Inject constructor(
    private val userStageStore: UserStageStore,
    private val pageLayoutManager: OnboardingPageManager,
    private val dispatchers: DispatcherProvider,
    private val onboardingSkipper: OnboardingSkipper,
    private val appBuildConfig: AppBuildConfig,
    private val dismissedCtaDao: DismissedCtaDao,
    private val onboardingStore: OnboardingStore,
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    suspend fun initializePages() {
        val isBrandDesignUpdateEnabled = withContext(dispatchers.io()) {
            onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()
        }
        if (isBrandDesignUpdateEnabled) {
            pageLayoutManager.buildBrandDesignUpdatePageBlueprints()
        } else {
            pageLayoutManager.buildPageBlueprints()
        }
    }

    fun pageCount(): Int {
        return pageLayoutManager.pageCount()
    }

    fun getItem(position: Int): OnboardingPageFragment? {
        return pageLayoutManager.buildPage(position)
    }

    suspend fun onOnboardingDone(extendedOnboardingFlow: ExtendedOnboardingFlow = DEFAULT) {
        withContext(dispatchers.io()) {
            // The orchestrator owns the terminal AppStage write when it drives the run; only the
            // legacy path writes it here. The extended-flow CTA seeding below always runs (it is
            // driven by the chosen demo query, not the orchestrator).
            if (!orchestratorDriven) {
                userStageStore.stageCompleted(AppStage.NEW)
            }

            when (extendedOnboardingFlow) {
                DEFAULT -> {
                    // no-op
                }

                DUCK_AI_FOCUSED -> {
                    // Mark this as a duck.ai onboarding path so CtaViewModel shows duck.ai-specific CTAs
                    onboardingStore.setDuckAiOnboardingFlow()

                    // Silence all standard DAX CTAs so they don't appear in the browser
                    listOf(
                        CtaId.DAX_INTRO,
                        CtaId.DAX_DIALOG_SERP,
                        CtaId.DAX_DIALOG_TRACKERS_FOUND,
                        CtaId.DAX_FIRE_BUTTON,
                        CtaId.DAX_END,
                    ).forEach { dismissedCtaDao.insert(DismissedCta(it)) }
                }

                DEFAULT_WITHOUT_INTRO_CTA -> {
                    dismissedCtaDao.insert(DismissedCta(CtaId.DAX_INTRO))
                }
            }
        }
    }

    fun onOnboardingSkipped() {
        viewModelScope.launch(dispatchers.io()) {
            // Orchestrator-driven runs already ran markOnboardingAsCompleted in onSkipped before
            // emitting Skipped; only the legacy path writes it here.
            if (!orchestratorDriven) {
                onboardingSkipper.markOnboardingAsCompleted()
            }
        }
    }

    /**
     * True once the orchestrator is driving this run (it never returns to NotStarted once started).
     * When true the orchestrator owns the terminal "onboarding is over" writes — its onCompleted /
     * onSkipped run before it emits the terminal state — and the active page navigates off that state,
     * so the legacy code here skips both the writes and the dev-skip navigation. NotStarted means the
     * orchestrator never engaged: the legacy path owns them.
     */
    val orchestratorDriven: Boolean
        get() = linearOnboardingOrchestrator.state.value !is LinearOnboardingState.NotStarted

    fun initializeOnboardingSkipper() {
        if (!appBuildConfig.canSkipOnboarding) return

        // delay showing skip button until privacy config downloaded
        viewModelScope.launch {
            onboardingSkipper.privacyConfigDownloaded.collect {
                _viewState.value = _viewState.value.copy(canShowSkipOnboardingButton = it.skipOnboardingPossible)
            }
        }
    }

    /**
     * Dev-only "skip all onboarding" shortcut. When [orchestratorDriven], the orchestrator owns the skip
     * (AbortPlan -> Skipped runs onSkipped, and the active page navigates off Skipped), so the caller
     * checks [orchestratorDriven] to know it must not also navigate. In the legacy path this writes the
     * terminal state directly and the caller still owns navigation.
     */
    suspend fun devOnlyFullyCompleteAllOnboarding() {
        // Apply the dev-only extra first so it lands before an orchestrator-driven page navigates.
        if (orchestratorDriven) {
            linearOnboardingOrchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        } else {
            onboardingSkipper.markOnboardingAsCompleted()
        }
    }

    companion object {
        data class ViewState(val canShowSkipOnboardingButton: Boolean = false)
    }

    enum class ExtendedOnboardingFlow {
        DEFAULT,
        DUCK_AI_FOCUSED,
        DEFAULT_WITHOUT_INTRO_CTA,
    }
}
