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

package com.duckduckgo.app.onboarding.orchestrator

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.CustomAiOnboardingResolver
import com.duckduckgo.app.onboarding.CustomAiOnboardingStore
import com.duckduckgo.app.onboarding.DuckAiOnboardingAvailability
import com.duckduckgo.app.onboarding.DuckAiOnboardingDemo
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelSender
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_AI_INTRO
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_FIRE_BUTTON
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_NOTIFICATIONS
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_QUICK_SETUP
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_EXPERIENCE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SET_DEFAULT
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_WELCOME
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.app.widget.ui.WidgetCapabilities
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.wideevents.InputScreenOnboardingWideEvent
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import com.duckduckgo.sync.api.SyncAutoRestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewUserOnboardingPlanProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val syncAutoRestore: SyncAutoRestore = mock()
    private val appBuildConfig: AppBuildConfig = mock()
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val onboardingStore: OnboardingStore = mock()
    private val duckChat: DuckChat = mock()
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val duckAiAvailability: DuckAiOnboardingAvailability = mock()
    private val onboardingPixelSender: OnboardingPixelSender = mock()
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val widgetCapabilities: WidgetCapabilities = mock()
    private val pixel: Pixel = mock()
    private val splitOmnibarToggle: Toggle = mock()
    private val splitOmnibarWelcomeToggle: Toggle = mock()
    private val singleTabFireDialogToggle: Toggle = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()
    private val customAiOnboardingStore: CustomAiOnboardingStore = mock()
    private val customAiOnboardingResolver: CustomAiOnboardingResolver = mock()
    private val duckAiOnboardingDemo: DuckAiOnboardingDemo = mock()

    private lateinit var provider: NewUserOnboardingPlanProvider
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    @Before
    fun setup() {
        whenever(androidBrowserConfigFeature.splitOmnibar()).thenReturn(splitOmnibarToggle)
        whenever(androidBrowserConfigFeature.splitOmnibarWelcomePage()).thenReturn(splitOmnibarWelcomeToggle)
        whenever(androidBrowserConfigFeature.singleTabFireDialog()).thenReturn(singleTabFireDialogToggle)
        whenever(splitOmnibarToggle.isEnabled()).thenReturn(false)
        whenever(splitOmnibarWelcomeToggle.isEnabled()).thenReturn(false)
        whenever(singleTabFireDialogToggle.isEnabled()).thenReturn(true)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)
        runBlocking {
            whenever(syncAutoRestore.canRestore()).thenReturn(false)
            whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
            whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(false)
            whenever(customAiOnboardingResolver.resolve()).thenReturn(false)
        }
        provider = NewUserOnboardingPlanProvider(
            syncAutoRestore = syncAutoRestore,
            appBuildConfig = appBuildConfig,
            defaultRoleBrowserDialog = defaultRoleBrowserDialog,
            settingsDataStore = settingsDataStore,
            onboardingStore = onboardingStore,
            duckChat = duckChat,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            duckAiOnboardingAvailability = duckAiAvailability,
            onboardingPixelSender = onboardingPixelSender,
            inputScreenOnboardingWideEvent = inputScreenOnboardingWideEvent,
            defaultBrowserDetector = defaultBrowserDetector,
            widgetCapabilities = widgetCapabilities,
            pixel = pixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            dismissedCtaDao = dismissedCtaDao,
            customAiOnboardingStore = customAiOnboardingStore,
            customAiOnboardingResolver = customAiOnboardingResolver,
            duckAiOnboardingDemo = duckAiOnboardingDemo,
        )
    }

    private suspend fun start() {
        orchestrator.startPlan(provider.buildRootPlan(onCompleted = {}, onSkipped = {}))
    }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
    }

    private fun assertStepProgress(current: Int, total: Int) {
        assertEquals(StepProgress(current = current, total = total), (orchestrator.state.value as InProgress).stepIndicatorProgress())
    }

    @Test
    fun `when initial user then walks full happy path to completed`() = runTest {
        start()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        verify(pixel).fire(PREONBOARDING_CHOOSE_BROWSER_PRESSED, mapOf(PixelParameter.DEFAULT_BROWSER to "false"))
        assertStep(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT)
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        assertStep(NewUserOnboardingStepIds.ADDRESS_BAR_POSITION)
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_BOTTOM))
        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_BOTTOM
        verify(pixel).fire(PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE)
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN)
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = false))
        // input_screen_preview precondition is false (not AI) -> plan exhausts.
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when reinstall user then shows reinstall dialog after preamble`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
    }

    @Test
    fun `when can restore then sync restore shown and reinstall still evaluated for side effect`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.SYNC_RESTORE)
        // isAppReinstall must run even when Sync Restore wins (side-effecting).
        verify(appBuildConfig).isAppReinstall()
        orchestrator.onEvent(NewUserOnboardingEvent.RestoreRequested)
        verify(pixel).fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
        verify(syncAutoRestore).restoreSyncAccount()
        // Advances past the mutually-exclusive reinstall/initial steps to comparison chart.
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        // The custom-AI "returning sync user ignored" pixel must not leak into the default plan
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
    }

    @Test
    fun `when skip from reinstall then switches to quick setup`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        verify(pixel).fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))
        verify(onboardingPixelSender).fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SINGLE_TOP,
                inputScreenSelected = true,
            ),
        )
        verify(duckChat, never()).setInputScreenUserSetting(true)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when custom ai path and quick setup confirmed with ai then forces input screen user setting on`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))
        // Skipping Custom AI onboarding via quick setup must force the input screen user setting on.
        verify(duckChat).setInputScreenUserSetting(true)
        verify(onboardingPixelSender).fire(
            ONBOARDING_QUICK_SETUP,
            OnboardingPixelAction.QuickSetupClicked(
                addressBarPosition = OmnibarType.SINGLE_TOP,
                inputScreenSelected = true,
            ),
        )
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when input mode ai and duck ai onboarding enabled then shows preview with search default`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN)
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        verify(pixel).fire(PREONBOARDING_AICHAT_SELECTED)
        verify(inputScreenOnboardingWideEvent).onInputScreenEnabledDuringOnboarding(reinstallUser = false)
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        val step = (orchestrator.state.value as InProgress).currentStep as NewUserOnboardingActivityStep
        assertEquals(
            NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = true),
            step.resolveDialog(),
        )
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when input mode ai but duck ai onboarding disabled then preview skipped and completes`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(false)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when demo search query submitted on preview then completes with launch search result`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)

        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "weather", isChat = false, fromSuggestion = false))

        assertEquals(
            Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, result = NewUserOnboardingResult.LaunchSearch(query = "weather")),
            orchestrator.state.value,
        )
    }

    @Test
    fun `when demo chat query submitted on preview then completes with launch chat result`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))

        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "why is the sky blue", isChat = true, fromSuggestion = false))

        assertEquals(
            Completed(
                rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID,
                result = NewUserOnboardingResult.LaunchChat(prompt = "why is the sky blue"),
            ),
            orchestrator.state.value,
        )
    }

    @Test
    fun `when default browser dialog not needed then still continues to address bar`() = runTest {
        // Intentional deviation from legacy: no early finish when already default.
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(false)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        verify(pixel).fire(PREONBOARDING_CHOOSE_BROWSER_PRESSED, mapOf(PixelParameter.DEFAULT_BROWSER to "true"))
        // default_browser_prompt precondition false -> skipped -> address bar still shown.
        assertStep(NewUserOnboardingStepIds.ADDRESS_BAR_POSITION)
    }

    @Test
    fun `when address bar split selected but split disabled then resolves to single top`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SPLIT))
        verify(settingsDataStore).omnibarType = OmnibarType.SINGLE_TOP
    }

    @Test
    fun `when dev skip from first step then aborts to skipped`() = runTest {
        start()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when dev skip mid flow then aborts to skipped`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when dev skip from side plan then aborts to skipped`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when onboarding path then custom ai plan walks to completed`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()

        // Custom-AI plan arms the in-context Duck.ai demo up front (in buildRootPlan).
        verify(duckAiOnboardingDemo).arm()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        assertStepProgress(current = 1, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        assertStepProgress(current = 2, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)

        val step = (orchestrator.state.value as InProgress).currentStep as NewUserBrowserActivityStep
        assertEquals(NewUserBrowserActivityAction.RunDuckAiOnboardingDemo("best privacy tips"), step.resolveAction())

        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        assertStepProgress(current = 3, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT)
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        assertStep(NewUserOnboardingStepIds.ADDRESS_BAR_POSITION)
        assertStepProgress(current = 4, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when onboarding path and reinstall then reinstall dialog replaces initial`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()

        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
    }

    @Test
    fun `when onboarding path then input screen preview is chat only`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)

        val state = orchestrator.state.value as InProgress
        val dialog = (state.currentStep as NewUserOnboardingActivityStep).resolveDialog() as NewUserOnboardingActivityDialog.InputScreenPreview
        // Step number is derived from the step's position in the plan, not carried on the dialog.
        assertEquals(StepProgress(current = 2, total = 4), state.stepIndicatorProgress())
        assertFalse(dialog.isSearchDefault)
    }

    @Test
    fun `when custom ai onboarding completed then arms open input on duck ai tab`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true, fromSuggestion = false))
        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when custom ai onboarding skipped then arms open input on duck ai tab`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))

        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when default onboarding completed then does not arm open input on duck ai tab`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = false))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(customAiOnboardingStore, never()).setOpenInputOnDuckAiTab()
        verify(duckAiOnboardingDemo, never()).arm()
    }

    @Test
    fun `when custom ai path and single tab fire dialog disabled then preview and demo steps skipped`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        whenever(singleTabFireDialogToggle.isEnabled()).thenReturn(false)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)

        // Continue from the comparison chart skips both gated steps (precondition false) and lands on the next satisfied step.
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
    }

    @Test
    fun `when custom ai path then fires plan started pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        verify(pixel).fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())
    }

    @Test
    fun `when default path then does not fire plan started pixel`() = runTest {
        start()
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.PLAN_STARTED, type = Unique())
    }

    @Test
    fun `when custom ai path and can restore then shows reinstall dialog and fires returning sync user ignored pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        // The custom-AI plan has no sync-restore step, so a restore-capable user gets the reinstall dialog instead.
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        verify(pixel).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
        // No restore is offered: the sync-restore step does not exist on this plan.
        verify(syncAutoRestore, never()).restoreSyncAccount()
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
    }

    @Test
    fun `when custom ai path and initial user then does not fire returning sync user ignored pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        verify(pixel, never()).fire(CustomAiOnboardingPixelName.RETURNING_SYNC_USER_IGNORED, type = Unique())
    }

    // region Shown pixel tests

    @Test
    fun `when notification permission step presented then fires NotificationsShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        // Now at NOTIFICATION_PERMISSION — emit Presented
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when intro animation step presented then fires no shown pixel`() = runTest {
        start()
        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender, never()).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when sync restore step presented then fires SyncRestoreShown pixel`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.SYNC_RESTORE)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when welcome step presented then fires WelcomeShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when comparison chart step presented then fires SetDefaultShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when default browser prompt step presented then fires no shown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        assertStep(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        // pixelName = null for this step; sender should not be called for a shown event
        verify(onboardingPixelSender, never()).fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = true))
        verify(onboardingPixelSender, never()).fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = false))
    }

    @Test
    fun `when address bar position step presented then fires AddressBarPositionShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        assertStep(NewUserOnboardingStepIds.ADDRESS_BAR_POSITION)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_ADDRESS_BAR_POSITION, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when input screen step presented then fires SearchExperienceShown pixel`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_SEARCH_EXPERIENCE, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when quick setup step presented then fires QuickSetupShown pixel`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_QUICK_SETUP, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when ai comparison chart step presented then fires AiIntroShown pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when custom ai input screen preview step presented then fires SearchChatToggleShown pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_SEARCH_CHAT_TOGGLE, OnboardingPixelAction.Shown)
    }

    @Test
    fun `when duck ai demo step presented then fires AiChatShown pixel`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)
        orchestrator.onEvent(NewUserOnboardingEvent.Presented)
        verify(onboardingPixelSender).fire(ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Shown)
    }

    // endregion

    // region Clicked/confirmed pixel tests

    @Test
    fun `when notification permission granted then fires NotificationsConfirmed with granted true`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = true))
        verify(onboardingPixelSender).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.NotificationsConfirmed(granted = true))
    }

    @Test
    fun `when notification permission denied then fires NotificationsConfirmed with granted false`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = false))
        verify(onboardingPixelSender).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.NotificationsConfirmed(granted = false))
    }

    @Test
    fun `when notification permission sdk less than 33 then does not fire NotificationsConfirmed`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        verify(onboardingPixelSender, never()).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.NotificationsConfirmed(granted = true))
        verify(onboardingPixelSender, never()).fire(ONBOARDING_NOTIFICATIONS, OnboardingPixelAction.NotificationsConfirmed(granted = false))
    }

    @Test
    fun `when sync restore accepted then fires SyncRestoreClicked with engaged true`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.RestoreRequested)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = true))
    }

    @Test
    fun `when sync restore skipped then fires SyncRestoreClicked with engaged false`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = false))
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
    }

    @Test
    fun `when welcome continue clicked then fires WelcomeClicked with engaged true`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = true))
    }

    @Test
    fun `when reinstall welcome skip clicked then fires WelcomeClicked with engaged false`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        verify(onboardingPixelSender).fire(ONBOARDING_WELCOME, OnboardingPixelAction.Clicked(engaged = false))
    }

    @Test
    fun `when comparison chart continue clicked then fires SetDefaultClicked`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        verify(onboardingPixelSender).fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.Clicked(engaged = true))
    }

    @Test
    fun `when default browser set confirmed then fires SetDefaultConfirmed with isDdgDefault true`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = true))
        verify(onboardingPixelSender).fire(ONBOARDING_SET_DEFAULT, OnboardingPixelAction.SetDefaultConfirmed(isDdgDefault = true))
    }

    @Test
    fun `when address bar position bottom confirmed then fires AddressBarPositionClicked with bottom`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_BOTTOM))
        verify(
            onboardingPixelSender,
        ).fire(ONBOARDING_ADDRESS_BAR_POSITION, OnboardingPixelAction.AddressBarClicked(position = OmnibarType.SINGLE_BOTTOM))
    }

    @Test
    fun `when input screen search only confirmed then fires SearchExperienceClicked with withAi false`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = false))
        verify(onboardingPixelSender).fire(ONBOARDING_SEARCH_EXPERIENCE, OnboardingPixelAction.SearchExperienceClicked(withAi = false))
    }

    @Test
    fun `when input screen preview suggestion search submitted then fires TryInputClicked with suggestion and search`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = false, fromSuggestion = true))
        verify(
            onboardingPixelSender,
        ).fire(ONBOARDING_SEARCH_CHAT_TOGGLE, OnboardingPixelAction.TryInputClicked(fromSuggestion = true, isChat = false))
    }

    @Test
    fun `when input screen preview submitted then sets search onboarding variant`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "cats", isChat = false, fromSuggestion = false))
        verify(onboardingPixelSender).searchBranchSelected()
    }

    @Test
    fun `when input screen preview chat submitted then sets chat onboarding variant`() = runTest {
        whenever(duckAiAvailability.isDuckAiOnboardingEnabled()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        verify(onboardingPixelSender).chatBranchSelected()
    }

    @Test
    fun `when ai comparison chart continue clicked then fires AiComparisonClicked`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        verify(onboardingPixelSender).fire(ONBOARDING_AI_INTRO, OnboardingPixelAction.Clicked(engaged = true))
    }

    @Test
    fun `when custom ai input screen preview demo submitted then sets chat variant and fires TryInputClicked`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        // Chat-only preview always records the chat branch, regardless of the submitted mode.
        verify(onboardingPixelSender).chatBranchSelected()
        verify(onboardingPixelSender).fire(
            ONBOARDING_SEARCH_CHAT_TOGGLE,
            OnboardingPixelAction.TryInputClicked(fromSuggestion = false, isChat = true),
        )
    }

    @Test
    fun `when duck ai demo fire completed then fires AiChatClicked`() = runTest {
        whenever(customAiOnboardingResolver.resolve()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "hello", isChat = true, fromSuggestion = false))
        assertStep(NewUserOnboardingStepIds.DUCK_AI_DEMO)
        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        verify(onboardingPixelSender).fire(ONBOARDING_FIRE_BUTTON, OnboardingPixelAction.Clicked(engaged = true))
    }

    // endregion
}
