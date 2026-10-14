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

import android.annotation.SuppressLint
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.FullOnboardingSkipper.ViewState
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT_WITHOUT_INTRO_CTA
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DUCK_AI_FOCUSED
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@Suppress("EXPERIMENTAL_API_USAGE")
class OnboardingViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val pageLayout: OnboardingPageManager = mock()

    private val onboardingSkipper: OnboardingSkipper = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val dismissedCtaDao: DismissedCtaDao = mock()

    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator = mock()

    private val duckAiOnboardingDemo: DuckAiOnboardingDemo = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(
            pageLayoutManager = pageLayout,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingSkipper = onboardingSkipper,
            appBuildConfig = appBuildConfig,
            dismissedCtaDao = dismissedCtaDao,
            linearOnboardingOrchestrator = linearOnboardingOrchestrator,
            duckAiOnboardingDemo = duckAiOnboardingDemo,
        )
    }

    @Test
    fun whenOnboardingDoneWithDefaultFlowThenNoCtasDismissedAndDemoNotArmed() = runTest {
        testee.onOnboardingDone()

        verifyNoInteractions(dismissedCtaDao)
        verify(duckAiOnboardingDemo, never()).arm()
    }

    @Test
    fun whenOnboardingDoneWithDuckAiFocusedFlowThenDemoIsArmed() = runTest {
        testee.onOnboardingDone(extendedOnboardingFlow = DUCK_AI_FOCUSED)

        verify(duckAiOnboardingDemo).arm()
    }

    @Test
    fun whenOnboardingDoneWithDefaultWithoutIntroCtaFlowThenOnlyIntroCtaIsDismissed() = runTest {
        testee.onOnboardingDone(extendedOnboardingFlow = DEFAULT_WITHOUT_INTRO_CTA)

        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
        verifyNoMoreInteractions(dismissedCtaDao)
        verify(duckAiOnboardingDemo, never()).arm()
    }

    @Test
    fun whenAppBuildConfigPreventsSkippingOnboardingThenOnboardingSkipperNotInteractedWith() = runTest {
        configureAppBuildConfigPreventsSkipping()
        testee.initializeOnboardingSkipper()
        verifyNoInteractions(onboardingSkipper)
    }

    @Test
    fun whenAppBuildConfigAllowsSkippingOnboardingAndPrivacyConfigDownloadedSkippingIsPossible() = runTest {
        configureAppBuildConfigAllowsSkipping()
        configureSkipperFlow()
        testee.initializeOnboardingSkipper()
        verify(onboardingSkipper).privacyConfigDownloaded
    }

    @Test
    fun whenDevSkipThenAbortsOrchestrator() = runTest {
        testee.devOnlyFullyCompleteAllOnboarding()

        verify(linearOnboardingOrchestrator).onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
    }

    @Test
    fun whenInitializePagesCalledThenBuildPageBlueprints() = runTest {
        testee.initializePages()

        verify(pageLayout).buildPageBlueprints()
    }

    private fun configureSkipperFlow() = runTest {
        val flow = MutableSharedFlow<ViewState>()
        flow.emit(ViewState(skipOnboardingPossible = true))
        whenever(onboardingSkipper.privacyConfigDownloaded).thenReturn(flow)
    }

    private fun configureAppBuildConfigAllowsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(true)
    }

    private fun configureAppBuildConfigPreventsSkipping() {
        whenever(appBuildConfig.canSkipOnboarding).thenReturn(false)
    }
}
