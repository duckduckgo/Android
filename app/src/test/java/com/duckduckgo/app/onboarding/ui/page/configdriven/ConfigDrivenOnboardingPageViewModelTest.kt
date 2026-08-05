/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityAction
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanBootstrapper
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep
import com.duckduckgo.app.onboarding.ui.page.configdriven.ConfigDrivenOnboardingPageViewModel.Command
import com.duckduckgo.app.onboarding.ui.page.configdriven.ConfigDrivenOnboardingPageViewModel.Screen
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingResult
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class ConfigDrivenOnboardingPageViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val mockContext: Context = mock()
    private val pixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val mockWidgetCapabilities: WidgetCapabilities = mock()
    private val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper = mock()
    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockShownPixels: OnboardingDialogShownPixels = mock()

    // Default harness: mock orchestrator left NotStarted, so the view model renders no dialog and emits no
    // commands on its own — the interaction tests drive a single method and assert exactly what it emits.
    private val orchestratorState = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
    private val mockOrchestrator: LinearOnboardingOrchestrator = mock {
        on { state } doReturn orchestratorState
    }

    // Real orchestrator, used by the flow tests that need an actual plan and step rendered.
    private val realOrchestrator = LinearOnboardingOrchestratorImpl()

    private val recordedEvents = mutableListOf<LinearOnboardingEvent>()

    // Records the event the view model emitted and stays on the same dialog. Presented is the view model's
    // "step rendered" signal, so it is filtered out as noise.
    private val recordAndStay: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
        if (event !is NewUserOnboardingEvent.Presented) {
            recordedEvents.add(event)
        }
        LinearOnboardingTransition.Stay
    }

    @Before
    fun setUp() {
        runBlocking { whenever(customAiOnboardingStore.isEnabled()).thenReturn(false) }
    }

    private fun createViewModel(
        orchestrator: LinearOnboardingOrchestrator = mockOrchestrator,
    ): ConfigDrivenOnboardingPageViewModel = ConfigDrivenOnboardingPageViewModel(
        orchestrator = orchestrator,
        newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
        dialogConfigResolver = DialogConfigResolver(mockOnboardingStore),
        shownPixels = mockShownPixels,
        dispatchers = coroutineRule.testDispatcherProvider,
        widgetCapabilities = mockWidgetCapabilities,
        defaultRoleBrowserDialog = mockDefaultRoleBrowserDialog,
        context = mockContext,
        pixel = pixel,
        appInstallStore = mockAppInstallStore,
        customAiOnboardingStore = customAiOnboardingStore,
    )

    // A one-step plan that renders [dialog]. By default the step records every event it is handed and stays put,
    // so the view model keeps showing [dialog] and the test can assert what it emitted.
    private fun planAt(
        dialog: NewUserOnboardingActivityDialog,
        id: String = "step",
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = recordAndStay,
        result: suspend () -> LinearOnboardingResult? = { null },
    ): LinearOnboardingPlan = LinearOnboardingPlan(
        id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
        steps = listOf(NewUserOnboardingActivityStep(id = id, pixelName = null, transition = transition, resolveDialog = { dialog })),
        result = result,
    )

    private suspend fun startAt(
        dialog: NewUserOnboardingActivityDialog,
        id: String = "step",
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = recordAndStay,
        result: suspend () -> LinearOnboardingResult? = { null },
    ): ConfigDrivenOnboardingPageViewModel {
        realOrchestrator.startPlan(planAt(dialog, id, transition, result))
        return createViewModel(realOrchestrator)
    }

    private suspend fun startAtBrowserStep(): ConfigDrivenOnboardingPageViewModel {
        val browserStep = NewUserBrowserActivityStep(
            id = "duck_ai_demo",
            pixelName = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo("x") },
        )
        realOrchestrator.startPlan(LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(browserStep)))
        return createViewModel(realOrchestrator)
    }

    @Test
    fun `publishes the resolved config and reports the step as presented`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        val screen = testee.viewState.value.screen as Screen.Dialog
        assertEquals("step", screen.stepId)
        assertEquals(OnboardingBackgroundStep.ComparisonChart, screen.config.background)
        assertTrue(screen.animateEntry)
    }

    @Test
    fun `stops animating a step's entry once it has been rendered`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onDialogRendered("step")

        assertFalse((testee.viewState.value.screen as Screen.Dialog).animateEntry)
    }

    @Test
    fun `keeps animating when a later step is reported as rendered`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onDialogRendered("a_different_step")

        assertTrue((testee.viewState.value.screen as Screen.Dialog).animateEntry)
    }

    @Test
    fun `forwards a cta event to the orchestrator untouched`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onEvent(NewUserOnboardingEvent.ContinueClicked)
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.ContinueClicked), recordedEvents)
    }

    @Test
    fun `requests the notification permission for the notification dialog`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.NotificationPermission)

        testee.commands.test {
            advanceUntilIdle()
            assertEquals(Command.RequestNotificationPermissions, awaitItem())
        }
    }

    @Test
    fun `shows the default browser dialog when the system offers one`() = runTest {
        whenever(mockDefaultRoleBrowserDialog.createIntent(any())).thenReturn(Intent())
        val testee = startAt(NewUserOnboardingActivityDialog.DefaultBrowserPrompt)

        testee.commands.test {
            advanceUntilIdle()
            assertTrue(awaitItem() is Command.ShowDefaultBrowserDialog)
        }
    }

    @Test
    fun `finishes onboarding when the plan completes with no result`() = runTest {
        val testee = startAt(
            dialog = NewUserOnboardingActivityDialog.ComparisonChart,
            transition = { LinearOnboardingTransition.Advance },
        )

        testee.commands.test {
            testee.onEvent(NewUserOnboardingEvent.ContinueClicked)
            advanceUntilIdle()
            assertEquals(Command.Finish, awaitItem())
        }
    }

    @Test
    fun `hands off to the browser when the current step is browser hosted`() = runTest {
        val testee = startAtBrowserStep()

        testee.commands.test {
            advanceUntilIdle()
            assertEquals(Command.HandOffToBrowserActivity, awaitItem())
        }
    }

    @Test
    fun `asks for the intro to play on the intro step, passing through withDuckAi`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = true))
        advanceUntilIdle()

        assertEquals(Screen.Intro.Play(withDuckAi = true), testee.viewState.value.screen)
    }

    @Test
    fun `asks for the intro to be restored once it has started, without waiting for it to finish`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = true))
        advanceUntilIdle()

        testee.onIntroAnimationStarted()

        assertEquals(Screen.Intro.Restore(withDuckAi = true), testee.viewState.value.screen)
    }

    @Test
    fun `keeps the intro on screen while the flow crosses a step with no dialog to render`() = runTest {
        val introStep = NewUserOnboardingActivityStep(
            id = "intro",
            pixelName = null,
            transition = { event ->
                if (event is NewUserOnboardingEvent.IntroAnimationFinished) {
                    LinearOnboardingTransition.Advance
                } else {
                    LinearOnboardingTransition.Stay
                }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = true) },
        )
        val promptStep = NewUserOnboardingActivityStep(
            id = "prompt",
            pixelName = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveDialog = { NewUserOnboardingActivityDialog.DefaultBrowserPrompt },
        )
        realOrchestrator.startPlan(
            LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(introStep, promptStep)),
        )
        val testee = createViewModel(realOrchestrator)
        advanceUntilIdle()
        testee.onIntroAnimationStarted()

        testee.onIntroAnimationFinished()
        advanceUntilIdle()

        assertEquals(Screen.Intro.Restore(withDuckAi = true), testee.viewState.value.screen)
    }

    @Test
    fun `keeps the last dialog on screen while the flow crosses a step with no dialog to render`() = runTest {
        val comparisonStep = NewUserOnboardingActivityStep(
            id = "comparison",
            pixelName = null,
            transition = { event ->
                if (event is NewUserOnboardingEvent.IntroAnimationFinished) {
                    LinearOnboardingTransition.Advance
                } else {
                    LinearOnboardingTransition.Stay
                }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.ComparisonChart },
        )
        val promptStep = NewUserOnboardingActivityStep(
            id = "prompt",
            pixelName = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveDialog = { NewUserOnboardingActivityDialog.DefaultBrowserPrompt },
        )
        realOrchestrator.startPlan(
            LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(comparisonStep, promptStep)),
        )
        val testee = createViewModel(realOrchestrator)
        advanceUntilIdle()

        testee.onIntroAnimationFinished()
        advanceUntilIdle()

        assertEquals("comparison", (testee.viewState.value.screen as Screen.Dialog).stepId)
    }

    @Test
    fun `asks for nothing to be drawn on a step with no dialog to render`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.DefaultBrowserPrompt)
        advanceUntilIdle()

        assertEquals(Screen.None, testee.viewState.value.screen)
    }

    @Test
    fun `asks for nothing before the flow has reached a step`() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()

        assertNull(testee.viewState.value.screen)
    }

    @Test
    fun `stops asking for the intro once the flow leaves the intro step`() = runTest {
        val introStep = NewUserOnboardingActivityStep(
            id = "intro",
            pixelName = null,
            transition = { event ->
                if (event is NewUserOnboardingEvent.IntroAnimationFinished) {
                    LinearOnboardingTransition.Advance
                } else {
                    LinearOnboardingTransition.Stay
                }
            },
            resolveDialog = { NewUserOnboardingActivityDialog.IntroAnimation() },
        )
        val comparisonStep = NewUserOnboardingActivityStep(
            id = "comparison",
            pixelName = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveDialog = { NewUserOnboardingActivityDialog.ComparisonChart },
        )
        realOrchestrator.startPlan(
            LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(introStep, comparisonStep)),
        )
        val testee = createViewModel(realOrchestrator)
        advanceUntilIdle()
        testee.onIntroAnimationStarted()

        testee.onIntroAnimationFinished()
        advanceUntilIdle()

        assertEquals("comparison", (testee.viewState.value.screen as Screen.Dialog).stepId)
    }

    @Test
    fun `emits IntroAnimationFinished to the orchestrator when the intro finishes`() = runTest {
        val testee = startAt(dialog = NewUserOnboardingActivityDialog.IntroAnimation())
        advanceUntilIdle()

        testee.onIntroAnimationFinished()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.IntroAnimationFinished), recordedEvents)
    }

    @Test
    fun `forwards a submitted input demo query to the orchestrator`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onContentInteraction(ContentInteraction.SubmitInput(query = "cats", isChat = true, fromSuggestion = true))
        advanceUntilIdle()

        assertEquals(
            listOf(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = true)),
            recordedEvents,
        )
    }

    @Test
    fun `fires the shown pixel for a dialog it renders`() = runTest {
        startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        verify(mockShownPixels).fireFor(NewUserOnboardingActivityDialog.ComparisonChart)
    }

    @Test
    fun `fires no shown pixel for a command only dialog`() = runTest {
        startAt(NewUserOnboardingActivityDialog.NotificationPermission)
        advanceUntilIdle()

        verifyNoInteractions(mockShownPixels)
    }

    @Test
    fun `renders the sync restore dialog instead of skipping onboarding`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.SyncRestore)
        advanceUntilIdle()

        val content = (testee.viewState.value.screen as Screen.Dialog).config.content as ContentConfig.Welcome
        assertEquals(TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle), content.title)
        assertTrue(recordedEvents.isEmpty())
    }
}
