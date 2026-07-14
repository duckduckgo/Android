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
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.*
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
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
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles,
    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
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
            // The orchestrator owns the terminal AppStage write when it drives the run (BrandDesignUpdate
            // page). The legacy WelcomePage path (brand design update off) does not touch the orchestrator,
            // so it writes the terminal state here. The extended-flow CTA seeding below always runs (it is
            // driven by the chosen demo query, not the orchestrator).
            if (!onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()) {
                userStageStore.stageCompleted(AppStage.NEW)
            }

            when (extendedOnboardingFlow) {
                DEFAULT -> {
                    // no-op
                }

                DUCK_AI_FOCUSED -> {
                    // Arm the in-browser Duck.ai demo (sets the flow + silences the standard DAX CTAs).
                    // Shared with the linear-onboarding duck_ai_demo step so both paths arm identically.
                    duckAiOnboardingDemo.arm()
                }

                DEFAULT_WITHOUT_INTRO_CTA -> {
                    dismissedCtaDao.insert(DismissedCta(CtaId.DAX_INTRO))
                }
            }
        }
    }

    fun onOnboardingSkipped() {
        viewModelScope.launch(dispatchers.io()) {
            // The orchestrator owns the skip terminal write when it drives the run (BrandDesignUpdate
            // page): its onSkipped runs markOnboardingAsCompleted before it emits Skipped. The legacy
            // WelcomePage path (brand design update off) does not touch the orchestrator, so it writes here.
            if (!onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()) {
                onboardingSkipper.markOnboardingAsCompleted()
            }
        }
    }

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
     * Dev-only "skip all onboarding" shortcut. Returns true when the caller must navigate away itself.
     *
     * In the BrandDesignUpdate (orchestrator) path the orchestrator owns the skip: AbortPlan -> Skipped
     * runs onSkipped and the active page navigates off Skipped, so the caller must not also navigate
     * (returns false). In the legacy WelcomePage path this writes the terminal state directly and nothing
     * navigates automatically, so the caller still owns navigation (returns true).
     */
    suspend fun devOnlyFullyCompleteAllOnboarding(): Boolean {
        val brandDesignUpdateEnabled = withContext(dispatchers.io()) {
            onboardingBrandDesignUpdateToggles.brandDesignUpdate().isEnabled()
        }
        if (brandDesignUpdateEnabled) {
            linearOnboardingOrchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        } else {
            onboardingSkipper.markOnboardingAsCompleted()
        }
        return !brandDesignUpdateEnabled
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
