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

import app.cash.turbine.test
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.CustomDuckAiOnboardingFeature
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingResult
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.LinearOnboardingEvent
import com.duckduckgo.onboarding.api.LinearOnboardingOrchestrator
import com.duckduckgo.onboarding.api.LinearOnboardingPlan
import com.duckduckgo.onboarding.api.LinearOnboardingState
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingStep
import com.duckduckgo.onboarding.api.LinearOnboardingTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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

class BrandDesignUpdatePageViewModelOrchestratorTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixel: Pixel = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val customDuckAiOnboardingFeature: CustomDuckAiOnboardingFeature = mock()
    private val customAiToggle: Toggle = mock()
    private val fakeOrchestrator = FakeOrchestrator()

    private class FakeOrchestrator : LinearOnboardingOrchestrator {
        val stateFlow = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
        override val state: StateFlow<LinearOnboardingState> = stateFlow
        val events = mutableListOf<LinearOnboardingEvent>()
        override suspend fun startPlan(plan: LinearOnboardingPlan) = Unit
        override suspend fun onEvent(event: LinearOnboardingEvent) {
            events.add(event)
        }
    }

    @Before
    fun setup() {
        whenever(customDuckAiOnboardingFeature.introAnimation()).thenReturn(customAiToggle)
        whenever(customAiToggle.isEnabled()).thenReturn(false)
        whenever(onboardingStore.getSearchOptions()).thenReturn(emptyList())
        whenever(onboardingStore.getChatSuggestions()).thenReturn(emptyList())
    }

    private fun step(dialog: NewUserOnboardingActivityDialog, id: String = "step"): LinearOnboardingStep =
        NewUserOnboardingActivityStep(id = id, transition = { LinearOnboardingTransition.Stay }, resolveDialog = { dialog })

    private fun inProgressWith(dialog: NewUserOnboardingActivityDialog, id: String = "step"): InProgress {
        val plan = LinearOnboardingPlan(id = "test_plan", steps = listOf(step(dialog, id)))
        return InProgress(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, currentPlan = plan, currentStepIndex = 0)
    }

    // Constructs the VM in orchestrator mode. Seeds the orchestrator at the given dialog first, because the VM both
    // derives its mode (orchestrator.state != NotStarted) and renders the current dialog from the observed state.
    private fun createViewModelAt(
        startingDialog: NewUserOnboardingActivityDialog,
        id: String = "step",
    ): BrandDesignUpdatePageViewModel {
        fakeOrchestrator.stateFlow.value = inProgressWith(startingDialog, id)
        return BrandDesignUpdatePageViewModel(
            defaultRoleBrowserDialog = defaultRoleBrowserDialog,
            context = mock(),
            pixel = pixel,
            appInstallStore = mock<AppInstallStore>(),
            settingsDataStore = mock<SettingsDataStore>(),
            dispatchers = coroutineRule.testDispatcherProvider,
            appBuildConfig = mock<AppBuildConfig>(),
            onboardingStore = onboardingStore,
            androidBrowserConfigFeature = mock<AndroidBrowserConfigFeature>(),
            duckChat = mock<DuckChat>(),
            inputScreenOnboardingWideEvent = mock<InputScreenOnboardingWideEvent>(),
            duckAiOnboardingExperimentManager = mock<DuckAiOnboardingExperimentManager>(),
            onboardingQuickSetupExperimentManager = mock<OnboardingQuickSetupExperimentManager>(),
            defaultBrowserDetector = mock<DefaultBrowserDetector>(),
            widgetCapabilities = mock<WidgetCapabilities>(),
            syncAutoRestore = mock(),
            quickSetupPixelSender = mock(),
            customDuckAiOnboardingFeature = customDuckAiOnboardingFeature,
            orchestrator = fakeOrchestrator,
        )
    }

    @Test
    fun `when state is initial then view state shows initial and fires shown pixel`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.INITIAL, testee.viewState.value.currentDialog)
        verify(pixel).fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `when state is comparison chart then view state shows comparison chart`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.COMPARISON_CHART, testee.viewState.value.currentDialog)
        verify(pixel).fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `when intro animation dialog then plays intro animation`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = false), id = "intro_animation")
        testee.commands.test {
            advanceUntilIdle()
            assertEquals(
                BrandDesignUpdatePageViewModel.Command.PlayIntroAnimation(withDuckAi = false),
                awaitItem(),
            )
        }
    }

    @Test
    fun `when continue methods then emit continue clicked`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()
        testee.onPrimaryCtaClicked()

        fakeOrchestrator.stateFlow.value = inProgressWith(NewUserOnboardingActivityDialog.ComparisonChart)
        advanceUntilIdle()
        testee.onPrimaryCtaClicked()

        fakeOrchestrator.stateFlow.value = inProgressWith(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false))
        advanceUntilIdle()
        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                NewUserOnboardingEvent.ContinueClicked,
                NewUserOnboardingEvent.ContinueClicked,
                NewUserOnboardingEvent.ContinueClicked,
            ),
            fakeOrchestrator.events,
        )
    }

    @Test
    fun `when address bar confirmed then emits with selected type`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = false))
        advanceUntilIdle()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_BOTTOM)), fakeOrchestrator.events)
    }

    @Test
    fun `when input mode confirmed then emits with selection`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.InputScreen)
        advanceUntilIdle()
        testee.onInputScreenOptionSelected(withAi = false)
        testee.onPrimaryCtaClicked()
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.InputModeConfirmed(withAi = false)), fakeOrchestrator.events)
    }

    @Test
    fun `when skip resume restore methods then emit matching events`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.SyncRestore)
        advanceUntilIdle()
        testee.onPrimaryCtaClicked()
        testee.onSecondaryCtaClicked()

        fakeOrchestrator.stateFlow.value = inProgressWith(NewUserOnboardingActivityDialog.InitialReinstallUser)
        advanceUntilIdle()
        testee.onSecondaryCtaClicked()

        fakeOrchestrator.stateFlow.value = inProgressWith(NewUserOnboardingActivityDialog.SkipNewUserOnboardingOption)
        advanceUntilIdle()
        testee.onPrimaryCtaClicked()
        testee.onSecondaryCtaClicked()
        advanceUntilIdle()

        assertEquals(
            listOf(
                NewUserOnboardingEvent.RestoreRequested,
                NewUserOnboardingEvent.SkipRequested,
                NewUserOnboardingEvent.SkipRequested,
                NewUserOnboardingEvent.SkipConfirmed,
                NewUserOnboardingEvent.ResumeRequested,
            ),
            fakeOrchestrator.events,
        )
    }

    @Test
    fun `when completed with no result then sends plain finish`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.Initial)
        testee.commands.test {
            fakeOrchestrator.stateFlow.value = LinearOnboardingState.Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID)
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.Finish, awaitItem())
        }
    }

    @Test
    fun `when completed with launch search result then submits search query`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.Initial)
        testee.commands.test {
            fakeOrchestrator.stateFlow.value = LinearOnboardingState.Completed(
                rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
                result = NewUserOnboardingResult.LaunchSearch(query = "weather"),
            )
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.FinishAndSubmitSearchQuery(query = "weather"), awaitItem())
        }
    }

    @Test
    fun `when skipped then sends onboarding skipped`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.Initial)
        testee.commands.test {
            fakeOrchestrator.stateFlow.value = LinearOnboardingState.Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID)
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.OnboardingSkipped, awaitItem())
        }
    }

    @Test
    fun `when demo query submitted then forwards event and completed result drives finish`() = runTest {
        val testee = createViewModelAt(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false))
        testee.commands.test {
            testee.onInputModeDemoQuerySubmitted(query = "cats", isChat = true)
            advanceUntilIdle()

            // The VM forwards the query as an event; the orchestrator surfaces it on Completed.result.
            fakeOrchestrator.stateFlow.value = LinearOnboardingState.Completed(
                rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
                result = NewUserOnboardingResult.LaunchChat(prompt = "cats"),
            )
            advanceUntilIdle()
            assertEquals(BrandDesignUpdatePageViewModel.Command.FinishAndSubmitChatPrompt(prompt = "cats"), awaitItem())
        }
        assertTrue(fakeOrchestrator.events.contains(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true)))
    }
}
