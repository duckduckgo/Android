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
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.*
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT
import com.duckduckgo.app.onboarding.ui.page.OnboardingPageFragment
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
    private val pageLayoutManager: OnboardingPageManager,
    private val dispatchers: DispatcherProvider,
    private val onboardingSkipper: OnboardingSkipper,
    private val appBuildConfig: AppBuildConfig,
    private val dismissedCtaDao: DismissedCtaDao,
    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator,
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo,
) : ViewModel() {

    private val _viewState = MutableStateFlow(ViewState())
    val viewState = _viewState.asStateFlow()

    suspend fun initializePages() {
        pageLayoutManager.buildPageBlueprints()
    }

    fun pageCount(): Int {
        return pageLayoutManager.pageCount()
    }

    fun getItem(position: Int): OnboardingPageFragment? {
        return pageLayoutManager.buildPage(position)
    }

    suspend fun onOnboardingDone(extendedOnboardingFlow: ExtendedOnboardingFlow = DEFAULT) {
        withContext(dispatchers.io()) {
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

    fun initializeOnboardingSkipper() {
        if (!appBuildConfig.canSkipOnboarding) return

        // delay showing skip button until privacy config downloaded
        viewModelScope.launch {
            onboardingSkipper.privacyConfigDownloaded.collect {
                _viewState.value = _viewState.value.copy(canShowSkipOnboardingButton = it.skipOnboardingPossible)
            }
        }
    }

    /** Dev-only "skip all onboarding" shortcut handled by the linear onboarding orchestrator. */
    suspend fun devOnlyFullyCompleteAllOnboarding() {
        linearOnboardingOrchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
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
