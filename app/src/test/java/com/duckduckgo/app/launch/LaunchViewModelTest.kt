/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.launch

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.launch.LaunchViewModel.Command.DaxPromptBrowserComparison
import com.duckduckgo.app.launch.LaunchViewModel.Command.Home
import com.duckduckgo.app.launch.LaunchViewModel.Command.Onboarding
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboardingdesignexperiment.OnboardingDesignExperimentManager
import com.duckduckgo.app.referral.StubAppReferrerFoundStateListener
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.daxprompts.api.DaxPrompts
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doSuspendableAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LaunchViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val userStageStore = mock<UserStageStore>()
    private val mockCommandObserver: Observer<LaunchViewModel.Command> = mock()
    private val mockDaxPrompts: DaxPrompts = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockOnboardingExperiment: OnboardingDesignExperimentManager = mock()

    private lateinit var testee: LaunchViewModel

    @Before
    fun before() = runTest {
        whenever(mockOnboardingExperiment.isWaitForLocalPrivacyConfigEnabled()).thenReturn(false)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsQuicklyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsButNotInstantlyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataTimesOutThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsQuicklyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsButNotInstantlyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataTimesOutThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenEvaluateReturnsBrowserComparisonVariantThenCommandIsDaxPromptBrowserComparison() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.SHOW_VARIANT_BROWSER_COMPARISON)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.determineViewToShow()
        verify(mockCommandObserver).onChanged(any<DaxPromptBrowserComparison>())
    }

    @Test
    fun whenOnboardingShouldShowAndPrivacyConfigIsEnabledThenCommandIsOnboarding() = runTest {
        whenever(mockOnboardingExperiment.isWaitForLocalPrivacyConfigEnabled()).thenReturn(true)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockOnboardingExperiment).waitForPrivacyConfig()
        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndPrivacyConfigIsEnabledThenCommandIsHome() = runTest {
        whenever(mockOnboardingExperiment.isWaitForLocalPrivacyConfigEnabled()).thenReturn(true)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockOnboardingExperiment).waitForPrivacyConfig()
        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingExperimentIsEnabledAndOnboardingShouldShowAndReferrerTimesOutThenCommandIsOnboarding() = runTest {
        whenever(mockOnboardingExperiment.isWaitForLocalPrivacyConfigEnabled()).thenReturn(true)

        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockOnboardingExperiment).waitForPrivacyConfig()
        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingExperimentIsEnabledAndOnboardingShouldShowAndWaitForPrivacyConfigTimesOutThenCommandIsOnboarding() = runTest {
        whenever(mockOnboardingExperiment.isWaitForLocalPrivacyConfigEnabled()).thenReturn(true)
        whenever(mockOnboardingExperiment.waitForPrivacyConfig()).doSuspendableAnswer {
            CompletableDeferred<Boolean>().await()
        }

        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            mockDaxPrompts,
            mockAppInstallStore,
            mockOnboardingExperiment,
        )
        whenever(mockDaxPrompts.evaluate()).thenReturn(ActionType.NONE)
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.determineViewToShow()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }
}
