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

import android.content.Intent
import android.os.Bundle
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.launch.LaunchViewModel.Command.Home
import com.duckduckgo.app.launch.LaunchViewModel.Command.Onboarding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanBootstrapper
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.referral.StubAppReferrerFoundStateListener
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingHost
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.testseeder.api.TestScenarioSeeder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
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
    private val pixel: Pixel = mock()
    private val testScenarioSeeder: TestScenarioSeeder = mock()
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper = mock {
        onBlocking { startNewUserOnboardingPlan() }.thenReturn(
            LinearOnboardingState.InProgress(
                rootPlanId = "test_plan",
                currentPlan = LinearOnboardingPlan(id = "test_plan", steps = listOf(stepHostedIn(LinearOnboardingHost.OnboardingActivity))),
                currentStepIndex = 0,
            ),
        )
    }
    private val enabledToggle: Toggle = mock { on { it.isEnabled() } doReturn true }
    private val disabledToggle: Toggle = mock { on { it.isEnabled() } doReturn false }

    // Brand design update on by default -> orchestrator drives onboarding.
    private val brandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = mock {
        on { brandDesignUpdate() } doReturn enabledToggle
    }

    private lateinit var testee: LaunchViewModel

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsQuicklyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataReturnsButNotInstantlyThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOnboardingShouldShowAndReferrerDataTimesOutThenCommandIsOnboarding() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenOrchestratorEngagedWithOnboardingActivityHostThenCommandIsOnboarding() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        val inProgress = LinearOnboardingState.InProgress(
            rootPlanId = "test_plan",
            currentPlan = LinearOnboardingPlan(id = "test_plan", steps = listOf(stepHostedIn(LinearOnboardingHost.OnboardingActivity))),
            currentStepIndex = 0,
        )
        whenever(newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()).thenReturn(inProgress)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Onboarding>())
    }

    @Test
    fun whenShowOnboardingOrHomeAndOrchestratorStartsWithBrowserActivityHostThenCommandIsHome() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        val inProgress = LinearOnboardingState.InProgress(
            rootPlanId = "test_plan",
            currentPlan = LinearOnboardingPlan(id = "test_plan", steps = listOf(stepHostedIn(LinearOnboardingHost.BrowserActivity))),
            currentStepIndex = 0,
        )
        whenever(newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()).thenReturn(inProgress)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        testee.command.observeForever(mockCommandObserver)

        testee.showOnboardingOrHome()

        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenShowOnboardingOrHomeAndOrchestratorStartsWithUnsupportedHostThenThrows() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        val inProgress = LinearOnboardingState.InProgress(
            rootPlanId = "test_plan",
            currentPlan = LinearOnboardingPlan(
                id = "test_plan",
                steps = listOf(stepHostedIn(LinearOnboardingHost.SubscriptionOnboardingActivity)),
            ),
            currentStepIndex = 0,
        )
        whenever(newUserOnboardingPlanBootstrapper.startNewUserOnboardingPlan()).thenReturn(inProgress)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )

        val exception = runCatching { testee.showOnboardingOrHome() }.exceptionOrNull()

        assertTrue(exception is IllegalArgumentException)
    }

    @Test
    fun whenShowOnboardingOrHomeAndNotNewUserThenCommandIsHome() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        testee.command.observeForever(mockCommandObserver)

        testee.showOnboardingOrHome()

        verify(mockCommandObserver).onChanged(any<Home>())
        verify(newUserOnboardingPlanBootstrapper, never()).startNewUserOnboardingPlan()
    }

    @Test
    fun whenNewUserAndBrandDesignUpdateDisabledThenOnboardingCommandAndPlanNotStarted() = runTest {
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        whenever(brandDesignUpdateToggles.brandDesignUpdate()).thenReturn(disabledToggle)
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        // Legacy WelcomePage path: the orchestrator is not started.
        verify(mockCommandObserver).onChanged(any<Onboarding>())
        verify(newUserOnboardingPlanBootstrapper, never()).startNewUserOnboardingPlan()
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsQuicklyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataReturnsButNotInstantlyThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = 1_000),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenOnboardingShouldNotShowAndReferrerDataTimesOutThenCommandIsHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)
        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenReferrerDataTimesOutThenPixelIsSent() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx", mockDelayMs = Long.MAX_VALUE),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(pixel).fire(AppPixelName.TIMEOUT_WAITING_FOR_APP_REFERRER)
    }

    @Test
    fun whenStartThenSeederIsInvokedWithIntentExtras() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        val intent = intentWithExtras(
            "isMaestro" to "true",
            "omnibarPosition" to "bottom",
            "nativeInputToggle" to "true",
            "inputWithAiToggle" to "true",
            "addFavorites" to "3",
        )

        testee.start(intent)
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(testScenarioSeeder).seedIfNeeded(
            eq(
                mapOf(
                    "isMaestro" to "true",
                    "omnibarPosition" to "bottom",
                    "nativeInputToggle" to "true",
                    "inputWithAiToggle" to "true",
                    "addFavorites" to "3",
                ),
            ),
        )
    }

    @Test
    fun whenSeederThrowsThenStartStillRoutesToHome() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        whenever(testScenarioSeeder.seedIfNeeded(anyOrNull())).thenThrow(RuntimeException("seed failed"))
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        verify(mockCommandObserver).onChanged(any<Home>())
    }

    @Test
    fun whenStartThenSeedingCompletesBeforeNavigationCommandIsEmitted() = runTest {
        testee = LaunchViewModel(
            userStageStore,
            StubAppReferrerFoundStateListener("xx"),
            pixel = pixel,
            testScenarioSeeder = testScenarioSeeder,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            brandDesignUpdateToggles = brandDesignUpdateToggles,
        )
        whenever(userStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.command.observeForever(mockCommandObserver)

        testee.start(mock<Intent>())
        coroutineRule.testDispatcher.scheduler.advanceUntilIdle()

        inOrder(testScenarioSeeder, mockCommandObserver).apply {
            verify(testScenarioSeeder).seedIfNeeded(anyOrNull())
            verify(mockCommandObserver).onChanged(any<Home>())
        }
    }

    private fun stepHostedIn(stepHost: LinearOnboardingHost): LinearOnboardingStep = object : LinearOnboardingStep {
        override val id: String = "step"
        override val host: LinearOnboardingHost = stepHost
        override val precondition: suspend () -> Boolean = { true }
        override val transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { LinearOnboardingTransition.Stay }
    }

    private fun intentWithExtras(vararg pairs: Pair<String, String>): Intent {
        val bundle = mock<Bundle>().apply {
            whenever(keySet()).thenReturn(pairs.map { it.first }.toSet())
            pairs.forEach { (key, value) -> whenever(getString(key)).thenReturn(value) }
        }
        return mock<Intent>().apply { whenever(extras).thenReturn(bundle) }
    }
}
