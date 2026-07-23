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
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.ui.FullOnboardingSkipper.ViewState
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DEFAULT_WITHOUT_INTRO_CTA
import com.duckduckgo.app.onboarding.ui.OnboardingViewModel.ExtendedOnboardingFlow.DUCK_AI_FOCUSED
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import kotlinx.coroutines.flow.MutableSharedFlow
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

    private val userStageStore: UserStageStore = mock()

    private val pageLayout: OnboardingPageManager = mock()

    private val onboardingSkipper: OnboardingSkipper = mock()

    private val appBuildConfig: AppBuildConfig = mock()

    private val dismissedCtaDao: DismissedCtaDao = mock()

    private val enabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    // Brand design update and config-driven dialogs off by default -> legacy WelcomePage path
    // (orchestrator does not drive the run).
    private val onboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock {
        on { brandDesignUpdate() } doReturn disabledToggle
        on { configDrivenDialogs() } doReturn disabledToggle
    }

    private val linearOnboardingOrchestrator: LinearOnboardingOrchestrator = mock()

    private val duckAiOnboardingDemo: DuckAiOnboardingDemo = mock()

    private val testee: OnboardingViewModel by lazy {
        OnboardingViewModel(
            userStageStore = userStageStore,
            pageLayoutManager = pageLayout,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingSkipper = onboardingSkipper,
            appBuildConfig = appBuildConfig,
            dismissedCtaDao = dismissedCtaDao,
            onboardingBrandDesignUpdateToggles = onboardingBrandDesignUpdateToggles,
            linearOnboardingOrchestrator = linearOnboardingOrchestrator,
            duckAiOnboardingDemo = duckAiOnboardingDemo,
        )
    }

    @Test
    fun whenOnboardingDoneAndBrandDesignUpdateDisabledThenCompleteStage() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)

        testee.onOnboardingDone()

        verify(userStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenOnboardingDoneAndBrandDesignUpdateEnabledThenAppStageNotWrittenButExtendedFlowStillApplied() = runTest {
        // The orchestrator owns the terminal AppStage write on the BrandDesignUpdate page.
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)

        testee.onOnboardingDone(extendedOnboardingFlow = DEFAULT_WITHOUT_INTRO_CTA)

        verify(userStageStore, never()).stageCompleted(any())
        verify(dismissedCtaDao).insert(DismissedCta(CtaId.DAX_INTRO))
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
    fun whenOnOnboardingSkippedAndBrandDesignUpdateDisabledThenMarkOnboardingAsCompleted() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)

        testee.onOnboardingSkipped()

        verify(onboardingSkipper).markOnboardingAsCompleted()
    }

    @Test
    fun whenOnOnboardingSkippedAndBrandDesignUpdateEnabledThenSkipperNotInvoked() = runTest {
        // The orchestrator owns the skip terminal write on the BrandDesignUpdate page.
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)

        testee.onOnboardingSkipped()

        verify(onboardingSkipper, never()).markOnboardingAsCompleted()
    }

    @Test
    fun whenDevSkipAndBrandDesignUpdateDisabledThenMarkOnboardingAsCompletedAndCallerNavigates() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)

        val callerNavigates = testee.devOnlyFullyCompleteAllOnboarding()

        assertTrue(callerNavigates)
        verify(onboardingSkipper).markOnboardingAsCompleted()
        verify(linearOnboardingOrchestrator, never()).onEvent(any())
    }

    @Test
    fun whenDevSkipAndBrandDesignUpdateEnabledThenAbortsOrchestratorAndCallerDoesNotNavigate() = runTest {
        whenever(onboardingBrandDesignUpdateToggles.brandDesignUpdate()).thenReturn(enabledToggle)

        val callerNavigates = testee.devOnlyFullyCompleteAllOnboarding()

        assertFalse(callerNavigates)
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
