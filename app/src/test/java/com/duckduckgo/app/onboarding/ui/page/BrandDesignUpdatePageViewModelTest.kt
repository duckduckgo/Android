/*
 * Copyright (c) 2025 DuckDuckGo
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
import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityAction
import com.duckduckgo.app.onboarding.orchestrator.NewUserBrowserActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityStep
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanBootstrapper
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingPlanProvider
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingResult
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingStepIds
import com.duckduckgo.app.onboarding.orchestrator.StepProgress
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.BrandDesignUpdatePageViewModel.Command
import com.duckduckgo.app.onboardingbranddesignupdate.OnboardingBrandDesignUpdateToggles
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.NOTIFICATION_RUNTIME_PERMISSION_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
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
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class BrandDesignUpdatePageViewModelTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val mockDefaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val mockContext: Context = mock()
    private val pixel: Pixel = mock()
    private val mockAppInstallStore: AppInstallStore = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val mockDefaultBrowserDetector: DefaultBrowserDetector = mock()
    private val mockWidgetCapabilities: WidgetCapabilities = mock()
    private val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    private val newUserOnboardingPlanBootstrapper: NewUserOnboardingPlanBootstrapper = mock()
    private val fakeOnboardingBrandDesignUpdateToggles: OnboardingBrandDesignUpdateToggles = FakeFeatureToggleFactory.create(
        OnboardingBrandDesignUpdateToggles::class.java,
    )

    // Default harness: mock orchestrator left NotStarted, so the view model renders no dialog and emits no
    // commands on its own — the shared interaction tests drive a single method and assert exactly what it emits.
    private val orchestratorState = MutableStateFlow<LinearOnboardingState>(LinearOnboardingState.NotStarted)
    private val mockOrchestrator: LinearOnboardingOrchestrator = mock {
        on { state } doReturn orchestratorState
    }

    // Real orchestrator, used by the orchestrator-flow tests that need an actual plan/step rendered.
    private val realOrchestrator = LinearOnboardingOrchestratorImpl()

    private val recordedEvents = mutableListOf<LinearOnboardingEvent>()

    // Default step transition: record the event the view model emitted and stay on the same dialog.
    // Presented is the VM's "step rendered" signal (fires shown pixels); these tests assert the
    // action events the VM emits in response to CTAs, so Presented is filtered out as noise.
    private val recordAndStay: suspend (LinearOnboardingEvent) -> LinearOnboardingTransition = { event ->
        if (event !is NewUserOnboardingEvent.Presented) {
            recordedEvents.add(event)
        }
        LinearOnboardingTransition.Stay
    }

    @Before
    fun setUp() {
        whenever(onboardingStore.getSearchOptions()).thenReturn(emptyList())
        whenever(onboardingStore.getChatSuggestions()).thenReturn(emptyList())
        runBlocking { whenever(customAiOnboardingStore.isEnabled()).thenReturn(false) }
    }

    private fun createViewModel(orchestrator: LinearOnboardingOrchestrator = mockOrchestrator): BrandDesignUpdatePageViewModel =
        BrandDesignUpdatePageViewModel(
            defaultRoleBrowserDialog = mockDefaultRoleBrowserDialog,
            context = mockContext,
            pixel = pixel,
            appInstallStore = mockAppInstallStore,
            dispatchers = coroutineRule.testDispatcherProvider,
            onboardingStore = onboardingStore,
            defaultBrowserDetector = mockDefaultBrowserDetector,
            widgetCapabilities = mockWidgetCapabilities,
            orchestrator = orchestrator,
            newUserOnboardingPlanBootstrapper = newUserOnboardingPlanBootstrapper,
            customAiOnboardingStore = customAiOnboardingStore,
            onboardingBrandDesignUpdateToggles = fakeOnboardingBrandDesignUpdateToggles,
        )

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
            steps = listOf(NewUserOnboardingActivityStep(id = id, pixelName = null, transition = transition, resolveDialog = { dialog })),
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
        realOrchestrator.startPlan(planAt(dialog, id, transition, result))
        return createViewModel(realOrchestrator)
    }

    // Starts the real orchestrator on a single QUICK_SETUP step so the view model actually renders QUICK_SETUP.
    private suspend fun createViewModelAtQuickSetup(): BrandDesignUpdatePageViewModel {
        val plan = LinearOnboardingPlan(
            id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
            steps = listOf(
                NewUserOnboardingActivityStep(
                    id = "quick_setup",
                    pixelName = null,
                    transition = { LinearOnboardingTransition.Stay },
                    resolveDialog = {
                        NewUserOnboardingActivityDialog.QuickSetup(
                            showSplitOption = false,
                            hideSetDefaultBrowserRow = false,
                            hideAddWidgetRow = false,
                            isReinstallUser = false,
                        )
                    },
                ),
            ),
        )
        realOrchestrator.startPlan(plan)
        return createViewModel(realOrchestrator)
    }

    // Starts the real orchestrator on a single BrowserActivity-hosted step and builds the view model, which enters
    // orchestrator mode and immediately hands off because the current step is hosted by the browser, not the activity.
    private suspend fun startAtBrowserStep(): BrandDesignUpdatePageViewModel {
        val browserStep = NewUserBrowserActivityStep(
            id = "duck_ai_demo",
            pixelName = null,
            transition = { LinearOnboardingTransition.Stay },
            resolveAction = { NewUserBrowserActivityAction.RunDuckAiOnboardingDemo("x") },
        )
        realOrchestrator.startPlan(LinearOnboardingPlan(id = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, steps = listOf(browserStep)))
        return createViewModel(realOrchestrator)
    }

    // region Intro animation state

    @Test
    fun whenViewModelCreatedThenHasPlayedIntroAnimationIsFalse() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()
        assertFalse(testee.viewState.value.hasPlayedIntroAnimation)
    }

    @Test
    fun whenOnIntroAnimationStartedThenHasPlayedIntroAnimationIsTrue() = runTest {
        val testee = createViewModel()

        testee.onIntroAnimationStarted()

        assertTrue(testee.viewState.value.hasPlayedIntroAnimation)
    }

    // endregion

    // region Initial view state

    @Test
    fun whenViewModelCreatedThenInitialViewStateHasNullDialogAndDefaults() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()

        val state = testee.viewState.value
        assertNull(state.currentDialog)
        assertEquals(OmnibarType.SINGLE_TOP, state.selectedAddressBarPosition)
        assertTrue(state.inputScreenSelected)
        assertFalse(state.showSplitOption)
        assertFalse(state.isReinstallUser)
    }

    @Test
    fun whenViewModelCreatedThenHasAnimatedCurrentDialogIsFalse() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()
        assertFalse(testee.viewState.value.hasAnimatedCurrentDialog)
    }

    @Test
    fun whenOnDialogAnimationStartedThenHasAnimatedCurrentDialogIsTrue() = runTest {
        val testee = createViewModel()
        advanceUntilIdle()
        assertFalse(testee.viewState.value.hasAnimatedCurrentDialog)

        testee.onDialogAnimationStarted()

        assertTrue(testee.viewState.value.hasAnimatedCurrentDialog)
    }

    // endregion

    // region Address bar position and input screen selection

    @Test
    fun whenAddressBarPositionSelectedThenViewStateUpdates() = runTest {
        val testee = createViewModel()

        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)

        assertEquals(OmnibarType.SINGLE_BOTTOM, testee.viewState.value.selectedAddressBarPosition)
    }

    @Test
    fun whenInputScreenOptionSelectedThenViewStateUpdates() = runTest {
        val testee = createViewModel()

        testee.onInputScreenOptionSelected(withAi = false)

        assertFalse(testee.viewState.value.inputScreenSelected)
    }

    // endregion

    // region onScreenTapped

    @Test
    fun whenOnDialogTappedThenSkipDialogAnimationCommandSent() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onDialogTapped()
            assertTrue(awaitItem() is Command.SkipDialogAnimation)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOnBackgroundTappedThenSkipDialogAnimationCommandSent() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onBackgroundTapped()
            assertTrue(awaitItem() is Command.SkipDialogAnimation)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region Notification permissions

    @Test
    fun whenNotificationPermissionRequestedThenPixelFired() = runTest {
        val testee = createViewModel()
        testee.notificationRuntimePermissionRequested()
        advanceUntilIdle()
        verify(pixel).fire(NOTIFICATION_RUNTIME_PERMISSION_SHOWN)
    }

    @Test
    fun whenNotificationPermissionRequestedThenPresentedEventSentToOrchestrator() = runTest {
        val testee = createViewModel()
        testee.notificationRuntimePermissionRequested()
        advanceUntilIdle()
        verify(mockOrchestrator).onEvent(NewUserOnboardingEvent.Presented)
    }

    @Test
    fun whenNotificationPermissionGrantedThenPixelFired() = runTest {
        val testee = createViewModel()
        testee.notificationRuntimePermissionGranted()
        advanceUntilIdle()
        verify(pixel).fire(
            AppPixelName.NOTIFICATIONS_ENABLED,
            mapOf(PixelParameter.FROM_ONBOARDING to true.toString()),
        )
    }

    // endregion

    // region onboardingImprovementsV2Enabled

    @Test
    fun whenOnboardingImprovementsV2DisabledThenViewStateReflectsFalse() = runTest {
        fakeOnboardingBrandDesignUpdateToggles.onboardingImprovementsV2().setRawStoredState(Toggle.State(remoteEnableState = false))
        val testee = createViewModel()
        advanceUntilIdle()
        assertFalse(testee.viewState.value.onboardingImprovementsV2Enabled)
    }

    // endregion

    // region onDefaultBrowserSet / onDefaultBrowserNotSet

    @Test
    fun whenDefaultBrowserSetThenRecordsResultAndFiresSetPixel() = runTest {
        val testee = createViewModel()

        testee.onDefaultBrowserSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = true
        verify(pixel).fire(
            AppPixelName.DEFAULT_BROWSER_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    @Test
    fun whenDefaultBrowserNotSetThenRecordsResultAndFiresNotSetPixel() = runTest {
        val testee = createViewModel()

        testee.onDefaultBrowserNotSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = false
        verify(pixel).fire(
            AppPixelName.DEFAULT_BROWSER_NOT_SET,
            mapOf(PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()),
        )
    }

    // endregion

    // region Quick setup - edit click commands

    @Test
    fun whenQuickSetupAddressBarPositionEditClickedThenSendShowAddressBarPositionBottomSheetCommandWithDefaults() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupAddressBarPositionEditClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQuickSetupAddressBarPositionBottomSheet)
            command as Command.ShowQuickSetupAddressBarPositionBottomSheet
            assertEquals(OmnibarType.SINGLE_TOP, command.initialSelection)
            assertEquals(false, command.showSplitOption)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupAddressBarPositionEditClickedAfterPickingBottomThenCommandReflectsCurrentSelection() = runTest {
        val testee = createViewModel()
        testee.onAddressBarPositionOptionSelected(OmnibarType.SINGLE_BOTTOM)
        testee.commands.test {
            testee.onQuickSetupAddressBarPositionEditClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQuickSetupAddressBarPositionBottomSheet)
            assertEquals(
                OmnibarType.SINGLE_BOTTOM,
                (command as Command.ShowQuickSetupAddressBarPositionBottomSheet).initialSelection,
            )
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupSearchOptionsEditClickedThenSendShowSearchOptionsBottomSheetCommandWithDefaultWithAiTrue() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupSearchOptionsEditClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQuickSetupSearchOptionsBottomSheet)
            assertEquals(true, (command as Command.ShowQuickSetupSearchOptionsBottomSheet).initialWithAi)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupSearchOptionsEditClickedAfterPickingSearchOnlyThenCommandReflectsCurrentWithAi() = runTest {
        val testee = createViewModel()
        testee.onInputScreenOptionSelected(withAi = false)
        testee.commands.test {
            testee.onQuickSetupSearchOptionsEditClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQuickSetupSearchOptionsBottomSheet)
            assertEquals(false, (command as Command.ShowQuickSetupSearchOptionsBottomSheet).initialWithAi)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onQuickSetupSetAsDefaultClicked

    @Test
    fun whenQuickSetupSetAsDefaultClickedFirstTimeWithValidIntentThenSendShowQuickSetupDefaultBrowserDialog() = runTest {
        val mockIntent: Intent = mock()
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockContext)).thenReturn(mockIntent)
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupSetAsDefaultClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowQuickSetupDefaultBrowserDialog)
            assertEquals(mockIntent, (command as Command.ShowQuickSetupDefaultBrowserDialog).intent)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupSetAsDefaultClickedFirstTimeWithNullIntentThenSendOpenSystemSettings() = runTest {
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockContext)).thenReturn(null)
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupSetAsDefaultClicked()
            val command = awaitItem()
            assertTrue(command is Command.OpenDefaultBrowserSystemSettings)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupSetAsDefaultClickedSecondTimeAfterDialogShownThenSendOpenSystemSettings() = runTest {
        val mockIntent: Intent = mock()
        whenever(mockDefaultRoleBrowserDialog.createIntent(mockContext)).thenReturn(mockIntent)
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupSetAsDefaultClicked() // first time: dialog
            assertTrue(awaitItem() is Command.ShowQuickSetupDefaultBrowserDialog)
            testee.onQuickSetupSetAsDefaultClicked() // second time: settings
            val command = awaitItem()
            assertTrue(command is Command.OpenDefaultBrowserSystemSettings)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onQuickSetupSetAsDefaultUnchecked

    @Test
    fun whenQuickSetupSetAsDefaultUncheckedThenSendOpenDefaultBrowserSystemSettings() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupSetAsDefaultUnchecked()
            val command = awaitItem()
            assertTrue(command is Command.OpenDefaultBrowserSystemSettings)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region onQuickSetupDefaultBrowserSet / onQuickSetupDefaultBrowserNotSet

    @Test
    fun whenQuickSetupDefaultBrowserSetThenRecordsResultWithoutTelemetry() = runTest {
        val testee = createViewModel()

        testee.onQuickSetupDefaultBrowserSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = true
    }

    @Test
    fun whenQuickSetupDefaultBrowserNotSetThenRecordsResultWithoutTelemetry() = runTest {
        val testee = createViewModel()

        testee.onQuickSetupDefaultBrowserNotSet()

        verify(mockDefaultRoleBrowserDialog).dialogShown()
        verify(mockAppInstallStore).defaultBrowser = false
    }

    // endregion

    // region Widget switch commands

    @Test
    fun whenQuickSetupAddHomescreenWidgetClickedThenSendLaunchAddWidgetPrompt() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupAddHomescreenWidgetClicked()
            val command = awaitItem()
            assertTrue(command is Command.LaunchAddWidgetPrompt)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenQuickSetupRemoveHomescreenWidgetClickedThenSendShowRemoveWidgetBottomSheet() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.onQuickSetupRemoveHomescreenWidgetClicked()
            val command = awaitItem()
            assertTrue(command is Command.ShowRemoveWidgetBottomSheet)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region checkWidgetAddedState

    @Test
    fun whenCheckWidgetAddedStateAndWidgetInstalledThenSendSyncAddWidgetSwitchChecked() = runTest {
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        val testee = createViewModel()
        testee.commands.test {
            testee.checkWidgetAddedState()
            val command = awaitItem()
            assertTrue(command is Command.SyncAddWidgetSwitch)
            assertTrue((command as Command.SyncAddWidgetSwitch).isChecked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenCheckWidgetAddedStateAndWidgetNotInstalledThenSendSyncAddWidgetSwitchUnchecked() = runTest {
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        val testee = createViewModel()
        testee.commands.test {
            testee.checkWidgetAddedState()
            val command = awaitItem()
            assertTrue(command is Command.SyncAddWidgetSwitch)
            assertFalse((command as Command.SyncAddWidgetSwitch).isChecked)
            cancelAndConsumeRemainingEvents()
        }
    }

    // endregion

    // region checkQuickSetupSwitchesState

    @Test
    fun whenCheckQuickSetupSwitchesStateInQuickSetupThenSendCombinedSyncWithBothStates() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(false)
        val testee = createViewModelAtQuickSetup()
        advanceUntilIdle()
        assertEquals(PreOnboardingDialogType.QUICK_SETUP, testee.viewState.value.currentDialog)

        testee.commands.test {
            testee.checkQuickSetupSwitchesState()
            val command = awaitItem()
            assertTrue(command is Command.SyncQuickSetupSwitches)
            command as Command.SyncQuickSetupSwitches
            assertTrue(command.defaultBrowserChecked)
            assertFalse(command.widgetChecked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenCheckQuickSetupSwitchesStateInQuickSetupWithWidgetInstalledAndNotDefaultThenSendCombinedSync() = runTest {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockWidgetCapabilities.hasInstalledWidgets).thenReturn(true)
        val testee = createViewModelAtQuickSetup()
        advanceUntilIdle()
        assertEquals(PreOnboardingDialogType.QUICK_SETUP, testee.viewState.value.currentDialog)

        testee.commands.test {
            testee.checkQuickSetupSwitchesState()
            val command = awaitItem()
            assertTrue(command is Command.SyncQuickSetupSwitches)
            command as Command.SyncQuickSetupSwitches
            assertFalse(command.defaultBrowserChecked)
            assertTrue(command.widgetChecked)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenCheckQuickSetupSwitchesStateNotInQuickSetupThenNoCommandSent() = runTest {
        val testee = createViewModel()
        testee.commands.test {
            testee.checkQuickSetupSwitchesState()
            expectNoEvents()
            cancelAndConsumeRemainingEvents()
        }
        verifyNoInteractions(mockDefaultBrowserDetector)
        verifyNoInteractions(mockWidgetCapabilities)
    }

    // endregion

    // region Orchestrator flow - step rendering

    @Test
    fun `when intro animation step then plays intro animation`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.IntroAnimation(withDuckAi = false),
            id = NewUserOnboardingStepIds.INTRO_ANIMATION,
        )
        testee.commands.test {
            advanceUntilIdle()
            assertEquals(
                Command.PlayIntroAnimation(withDuckAi = false),
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
    fun `when ai comparison chart then view state shows ai comparison chart and fires shown pixel`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.AiComparisonChart, id = "ai_comparison_chart")
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.AI_COMPARISON_CHART, testee.viewState.value.currentDialog)
        verify(pixel).fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
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
                    pixelName = null,
                    showsStepIndicator = true,
                    transition = { LinearOnboardingTransition.Advance },
                    resolveDialog = { NewUserOnboardingActivityDialog.AiComparisonChart },
                ),
                NewUserOnboardingActivityStep(
                    id = "input_screen_preview",
                    pixelName = null,
                    showsStepIndicator = true,
                    transition = recordAndStay,
                    resolveDialog = { NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false) },
                ),
            ),
        )
        realOrchestrator.startPlan(plan)
        val testee = createViewModel(realOrchestrator)
        advanceUntilIdle()
        realOrchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // advance to the preview step
        advanceUntilIdle()

        assertEquals(PreOnboardingDialogType.INPUT_SCREEN_PREVIEW, testee.viewState.value.currentDialog)
        assertEquals(StepProgress(current = 2, total = 2), testee.viewState.value.stepIndicator)
    }

    @Test
    fun `when custom ai onboarding flow then view state enables custom ai flow`() = runTest {
        whenever(customAiOnboardingStore.isEnabled()).thenReturn(true)
        val testee = startAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        assertTrue(testee.viewState.value.isCustomAiOnboardingFlow)
    }

    @Test
    fun `when current step hosted by browser activity then hands off to browser`() = runTest {
        val testee = startAtBrowserStep()

        testee.commands.test {
            assertEquals(Command.HandOffToBrowserActivity, awaitItem())
        }
    }

    // endregion

    // region Orchestrator flow - CTA event translation

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
    fun `when ai comparison chart continue then emits continue clicked`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.AiComparisonChart, id = "ai_comparison_chart")
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
    fun `when demo query submitted then forwards event`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false))
        advanceUntilIdle()

        testee.onInputModeDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = false)
        advanceUntilIdle()

        assertEquals(listOf(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = false)), recordedEvents)
    }

    // endregion

    // region Orchestrator flow - run completion

    @Test
    fun `when run completes with no result then sends plain finish`() = runTest {
        val testee = startAt(NewUserOnboardingActivityDialog.Initial, transition = { LinearOnboardingTransition.Advance })
        testee.commands.test {
            advanceUntilIdle()
            realOrchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // -> Advance -> run completes
            advanceUntilIdle()
            assertEquals(Command.Finish, awaitItem())
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
            realOrchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
            advanceUntilIdle()
            assertEquals(Command.FinishAndSubmitSearchQuery(query = "weather"), awaitItem())
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
            realOrchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = true, fromSuggestion = false))
            advanceUntilIdle()
            assertEquals(Command.FinishAndSubmitChatPrompt(prompt = "cats"), awaitItem())
        }
    }

    @Test
    fun `when run aborted then sends onboarding skipped`() = runTest {
        val testee = startAt(
            NewUserOnboardingActivityDialog.QuickSetup(
                showSplitOption = false,
                hideSetDefaultBrowserRow = false,
                hideAddWidgetRow = false,
                isReinstallUser = false,
            ),
            transition = { LinearOnboardingTransition.AbortPlan },
        )
        testee.commands.test {
            advanceUntilIdle()
            // -> AbortPlan -> run skipped
            realOrchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))
            advanceUntilIdle()
            assertEquals(Command.OnboardingSkipped, awaitItem())
        }
    }

    // endregion

    // region Orchestrator flow - re-bootstrap on process-death restore

    @Test
    fun `when orchestrator not started then re-bootstraps the plan`() = runTest {
        // The orchestrator is left NotStarted, mimicking a process-death restore where the
        // OnboardingActivity is recreated without routing through LaunchViewModel.
        createViewModel(realOrchestrator)
        advanceUntilIdle()

        verifyBlocking(newUserOnboardingPlanBootstrapper) { startNewUserOnboardingPlan() }
    }

    @Test
    fun `when orchestrator already in progress then does not re-bootstrap`() = runTest {
        startAt(NewUserOnboardingActivityDialog.Initial)
        advanceUntilIdle()

        verifyNoInteractions(newUserOnboardingPlanBootstrapper)
    }

    // endregion
}
