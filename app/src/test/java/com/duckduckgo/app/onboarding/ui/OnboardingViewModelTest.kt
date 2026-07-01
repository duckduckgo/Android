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
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.FullOnboardingSkipper.ViewState
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT_WITHOUT_INTRO_CTA
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DUCK_AI_FOCUSED
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
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

    private var userStageStore: UserStageStore = mock()

    private val pageLayout: OnboardingPageManager = mock()

    private val onboardingSkipper: OnboardingSkipper = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val dismissedCtaDao: DismissedCtaDao = mock()

    private val onboardingStore: OnboardingStore = mock()

    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock()
    private val enabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    private val orchestratorState = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator = mock {
        on { state } doReturn orchestratorState
    }

    private val duckAiOnboardingDemo: DuckAiOnboardingDemo = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(
            userStageStore = userStageStore,
            pageLayoutManager = pageLayout,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingSkipper = onboardingSkipper,
            appBuildConfig = appBuildConfig,
            dismissedCtaDao = dismissedCtaDao,
            onboardingStore = onboardingStore,
            onboardingBrandDesignUpdateToggles = onboardingBrandDesignUpdateToggles,
            linearOnboardingOrchestrator = linearOnboardingOrchestrator,
            duckAiOnboardingDemo = duckAiOnboardingDemo,
        )
    }

    @Test
    fun whenOnboardingDoneThenCompleteStage() = runTest {
        testee.onOnboardingDone()
        verify(userStageStore).stageCompleted(AppStage.NEW)
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
    fun whenOnOnboardingSkippedCalledThenMarkOnboardingAsCompleted() = runTest {
        testee.onOnboardingSkipped()
        verify(onboardingSkipper).markOnboardingAsCompleted()
    }

    @Test
    fun whenOnboardingDoneAndOrchestratorDroveRunThenAppStageNotWrittenButExtendedFlowStillApplied() = runTest {
        // At terminal time an orchestrator-driven run is Completed/Skipped, never NotStarted.
        orchestratorState.value = LinearOnboardingState.Completed(rootPlanId = "test_plan")

        testee.onOnboardingDone(extendedOnboardingFlow = DEFAULT_WITHOUT_INTRO_CTA)

        verify(userStageStore, never()).stageCompleted(any())
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
    }

    @Test
    fun whenOnOnboardingSkippedAndOrchestratorDroveRunThenSkipperNotInvoked() = runTest {
        orchestratorState.value = LinearOnboardingState.Skipped(rootPlanId = "test_plan")

        testee.onOnboardingSkipped()

        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }

    @Test
    fun whenDevSkipAndOrchestratorNotEngagedThenMarkOnboardingAsCompletedAndNotDriven() = runTest {
        orchestratorState.value = LinearOnboardingState.NotStarted

        testee.devOnlyFullyCompleteAllOnboarding()

        assertFalse(testee.orchestratorDriven)
        verify(onboardingSkipper).markOnboardingAsCompleted()
        verify(linearOnboardingOrchestrator, never()).onEvent(any())
    }

    @Test
    fun whenDevSkipAndOrchestratorEngagedThenAbortsOrchestratorAndDriven() = runTest {
        orchestratorState.value = LinearOnboardingState.InProgress(
            rootPlanId = "test_plan",
            currentPlan = LinearOnboardingPlan(id = "test_plan", steps = emptyList()),
            currentStepIndex = 0,
        )

        testee.devOnlyFullyCompleteAllOnboarding()

        assertTrue(testee.orchestratorDriven)
        verify(linearOnboardingOrchestrator).onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }

    @Test
    fun whenInitializePagesCalledAndBrandDesignUpdateDisabledThenBuildPageBlueprints() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)

        testee.initializePages()

        verify(pageLayout).buildPageBlueprints()
    }

    @Test
    fun whenInitializePagesCalledAndBrandDesignUpdateEnabledThenBuildBrandDesignUpdatePageBlueprints() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)

        testee.initializePages()

        verify(pageLayout).buildBrandDesignUpdatePageBlueprints()
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
