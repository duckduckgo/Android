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

package com.duckduckgo.app.onboarding.ui.page

import android.annotation.SuppressLint
import app.cash.turbine.test
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityAction
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingResult
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingStepIds
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingResult
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class BrandDesignUpdatePageViewModelOrchestratorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    private val orchestrator = LinearOnboardingOrchestratorImpl()
    private val recordedEvents = mutableListOf<LinearOnboardingEvent>()

    // Default step transition: record the event the view model emitted and stay on the same dialog.
    private val recordAndStay: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
        recordedEvents.add(event)
        LinearOnboardingTransition.Stay
    }

    @Before
    fun setup() {
        whenever(onboardingStore.getSearchOptions()).thenReturn(emptyList())
        whenever(onboardingStore.getChatSuggestions()).thenReturn(emptyList())
        runBlocking { whenever(customAiOnboardingStore.isEnabled()).thenReturn(false) }
    }

    // A one-step plan that renders [dialog]. By default the step records every event it is handed and stays put,
    // so the view model keeps showing [dialog] across callbacks and the test can assert what it emitted. Override
    // [transition] (and [result]) to drive the run to a terminal state instead.
    private fun planAt(
        dialog: NewUserOnboardingActivityDialog,
        id: String = "step",
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = recordAndStay,
        result: suspend () -> LinearOnboardingResult? = { null },
    ): LinearOnboardingPlan =
        LinearOnboardingPlan(
            id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
            steps = listOf(NewUserOnboardingActivityStep(id = id, transition = transition, resolveDialog = { dialog })),
            result = result,
        )

    // Starts the real orchestrator on [planAt] and builds the view model, which enters orchestrator mode because
    // a run is already in progress and immediately renders the started step.
    private suspend fun startAt(
        dialog: NewUserOnboardingActivityDialog,
        id: String = "step",
        transition: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = recordAndStay,
        result: suspend () -> LinearOnboardingResult? = { null },
    ): BrandDesignUpdatePageViewModel {
        orchestrator.startPlan(planAt(dialog, id, transition, result))
        return createViewModel()
    }

    private fun createViewModel(): BrandDesignUpdatePageViewModel =
        BrandDesignUpdatePageViewModel(
            defaultRoleBrowserDialog = mock(),
            context = mock(),
            pixel = pixel,
            appInstallStore = mock(),
            settingsDataStore = mock(),
            dispatchers = coroutineRule.testDispatcherProvider,
            appBuildConfig = mock(),
            onboardingStore = onboardingStore,
            androidBrowserConfigFeature = mock(),
            duckChat = mock(),
            inputScreenOnboardingWideEvent = mock(),
            duckAiOnboardingAvailability = mock(),
            onboardingQuickSetupExperimentManager = mock(),
            defaultBrowserDetector = mock(),
            widgetCapabilities = mock(),
            syncAutoRestore = mock(),
            brandDesignOnboardingPixelSender = mock(),
            orchestrator = orchestrator,
            customAiOnboardingStore = customAiOnboardingStore,
        )

    @Test
    fun `when intro animation step then plays intro animation`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = false),
            id = NewUserOnboardingStepIds.INTRO_ANIMATION,
        )
        testee.commands.test {
            advanceUntilIdle()
            assertEquals(
                BrandDesignUpdatePageViewModel.Command.PlayIntroAnimation(withDuckAi = false),
                awaitItem(),
            )
        }
    }

    @Test
    fun `when state is initial then view state shows initial and fires shown pixel`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.INITIAL, testee.viewState.value.currentDialog)
        verify(pixel).fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `when state is comparison chart then view state shows comparison chart and fires shown pixel`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.COMPARISON_CHART, testee.viewState.value.currentDialog)
        verify(pixel).fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `when continue clicked on initial then emits continue clicked`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.ContinueClicked), recordedEvents)
    }

    @Test
    fun `when continue clicked on comparison chart then emits continue clicked`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.ContinueClicked), recordedEvents)
    }

    @Test
    fun `when address bar confirmed then emits with selected type`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = false))
        advanceUntilIdle()

        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_BOTTOM)), recordedEvents)
    }

    @Test
    fun `when input mode confirmed then emits with selection`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.InputScreen)
        advanceUntilIdle()

        testee.onInputScreenOptionSelected(withAi = false)
        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.InputModeConfirmed(withAi = false)), recordedEvents)
    }

    @Test
    fun `when restore then skip on sync restore then emits restore requested then skip requested`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.SyncRestore)
        advanceUntilIdle()

        testee.onPrimaryCtaClicked()
        testee.onSecondaryCtaClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(NewUserOnboardingEvent.RestoreRequested, NewUserOnboardingEvent.SkipRequested),
            recordedEvents,
        )
    }

    @Test
    fun `when skip on reinstall user then emits skip requested`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.InitialReinstallUser)
        advanceUntilIdle()

        testee.onSecondaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.SkipRequested), recordedEvents)
    }

    @Test
    fun `when skip confirmed then resume on skip option then emits skip confirmed then resume requested`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.SkipNewUserOnboardingOption)
        advanceUntilIdle()

        testee.onPrimaryCtaClicked()
        testee.onSecondaryCtaClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(NewUserOnboardingEvent.SkipConfirmed, NewUserOnboardingEvent.ResumeRequested),
            recordedEvents,
        )
    }

    @Test
    fun `when demo query submitted then forwards event`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false))
        advanceUntilIdle()

        testee.onInputModeDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = false)
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true)), recordedEvents)
    }

    @Test
    fun `when run completes with no result then sends plain finish`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.Initial, transition = { LinearOnboardingTransition.Advance })
        testee.commands.test {
            advanceUntilIdle()
            orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // -> Advance -> run completes
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.Finish, awaitItem())
        }
    }

    @Test
    fun `when run completes with launch search result then submits search query`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.Initial,
            transition = { LinearOnboardingTransition.Advance },
            result = { NewUserOnboardingResult.LaunchSearch(query = "weather") },
        )
        testee.commands.test {
            advanceUntilIdle()
            orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.FinishAndSubmitSearchQuery(query = "weather"), awaitItem())
        }
    }

    @Test
    fun `when run completes with launch chat result then submits chat prompt`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false),
            transition = { LinearOnboardingTransition.Advance },
            result = { NewUserOnboardingResult.LaunchChat(prompt = "cats") },
        )
        testee.commands.test {
            advanceUntilIdle()
            orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true))
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.FinishAndSubmitChatPrompt(prompt = "cats"), awaitItem())
        }
    }

    @Test
    fun `when run aborted then sends onboarding skipped`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.SkipNewUserOnboardingOption,
            transition = { LinearOnboardingTransition.AbortPlan },
        )
        testee.commands.test {
            advanceUntilIdle()
            orchestrator.onEvent(NewUserOnboardingEvent.SkipConfirmed) // -> AbortPlan -> run skipped
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.OnboardingSkipped, awaitItem())
        }
    }

    @Test
    fun `when custom ai onboarding flow then view state enables custom ai flow`() = runTest {
        whenever(customAiOnboardingStore.isEnabled()).thenReturn(true)
        val testee = startAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        assertTrue(testee.viewState.value.isCustomAiOnboardingFlow)
    }

    @Test
    fun `when ai comparison chart then view state shows ai comparison chart`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.AiComparisonChart, id = "ai_comparison_chart")
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.AI_COMPARISON_CHART, testee.viewState.value.currentDialog)
    }

    @Test
    fun `when ai comparison chart continue then emits continue clicked`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.AiComparisonChart, id = "ai_comparison_chart")
        advanceUntilIdle()

        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.ContinueClicked), recordedEvents)
    }

    @Test
    fun `when input screen preview then view state derives its step number from plan position`() = runTest {
        // Two indicator steps with the preview 2nd. The run starts on the AI comparison chart and advances to the
        // preview, so the VM derives "2 of 2" from plan position (not the dialog).
        val plan = LinearOnboardingPlan(
            id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
            steps = listOf(
                NewUserOnboardingActivityStep(
                    id = "ai_comparison_chart",
                    showsStepIndicator = true,
                    transition = { LinearOnboardingTransition.Advance },
                    resolveDialog = { NewUserOnboardingActivityDialog.AiComparisonChart },
                ),
                NewUserOnboardingActivityStep(
                    id = "input_screen_preview",
                    showsStepIndicator = true,
                    transition = recordAndStay,
                    resolveDialog = { NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false) },
                ),
            ),
        )
        orchestrator.startPlan(plan)
        val testee = createViewModel()
        advanceUntilIdle()
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // advance to the preview step
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.INPUT_SCREEN_PREVIEW, testee.viewState.value.currentDialog)
        assertEquals(2, testee.viewState.value.currentPageNumber)
        assertEquals(2, testee.viewState.value.maxPageCount)
    }

    // Starts the real orchestrator on a single BrowserActivity-hosted step and builds the view model, which enters
    // orchestrator mode and immediately hands off because the current step is hosted by the browser, not the activity.
    private suspend fun startAtBrowserStep(): BrandDesignUpdatePageViewModel {
        val browserStep = NewUserBrowserActivityStep(
            id = "duck_ai_demo",
            transition = { LinearOnboardingTransition.Stay },
            resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo("x") },
        )
        orchestrator.startPlan(LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(browserStep)))
        return createViewModel()
    }

    @Test
    fun `when current step hosted by browser activity then hands off to browser`() = runTest {
        val testee = startAtBrowserStep()

        testee.commands.test {
            assertEquals(BrandDesignUpdatePageViewModel.Command.HandOffToBrowserActivity, awaitItem())
        }
    }
}
