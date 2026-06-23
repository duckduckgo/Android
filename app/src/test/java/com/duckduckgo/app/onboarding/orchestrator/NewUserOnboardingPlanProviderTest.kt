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
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager
import com.duckduckgo.app.onboarding.DuckAiOnboardingExperimentManager.DuckAiOnboardingExperimentVariant
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.BrandDesignOnboardingPixelSender
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager
import com.duckduckgo.app.onboardingquicksetup.OnboardingQuickSetupExperimentManager.QuickSetupExperimentVariant
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_RESUME_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE
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
    private val duckAiExperiment: DuckAiOnboardingExperimentManager = mock()
    private val quickSetupExperiment: OnboardingQuickSetupExperimentManager = mock()
    private val brandDesignOnboardingPixelSender: BrandDesignOnboardingPixelSender = mock()
    private val inputScreenOnboardingWideEvent: InputScreenOnboardingWideEvent = mock()
    private val defaultBrowserDetector: DefaultBrowserDetector = mock()
    private val widgetCapabilities: WidgetCapabilities = mock()
    private val pixel: Pixel = mock()
    private val splitOmnibarToggle: Toggle = mock()
    private val splitOmnibarWelcomeToggle: Toggle = mock()
    private val dismissedCtaDao: DismissedCtaDao = mock()

    private lateinit var provider: NewUserOnboardingPlanProvider
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    @Before
    fun setup() {
        whenever(androidBrowserConfigFeature.splitOmnibar()).thenReturn(splitOmnibarToggle)
        whenever(androidBrowserConfigFeature.splitOmnibarWelcomePage()).thenReturn(splitOmnibarWelcomeToggle)
        whenever(splitOmnibarToggle.isEnabled()).thenReturn(false)
        whenever(splitOmnibarWelcomeToggle.isEnabled()).thenReturn(false)
        whenever(defaultRoleBrowserDialog.shouldShowDialog()).thenReturn(true)
        whenever(defaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(false)
        runBlocking {
            whenever(syncAutoRestore.canRestore()).thenReturn(false)
            whenever(appBuildConfig.isAppReinstall()).thenReturn(false)
            whenever(duckAiExperiment.enroll()).thenReturn(DuckAiOnboardingExperimentVariant.CONTROL)
            whenever(quickSetupExperiment.enroll()).thenReturn(QuickSetupExperimentVariant.CONTROL)
        }
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(false)
        provider = NewUserOnboardingPlanProvider(
            syncAutoRestore = syncAutoRestore,
            appBuildConfig = appBuildConfig,
            defaultRoleBrowserDialog = defaultRoleBrowserDialog,
            settingsDataStore = settingsDataStore,
            onboardingStore = onboardingStore,
            duckChat = duckChat,
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            duckAiOnboardingExperimentManager = duckAiExperiment,
            onboardingQuickSetupExperimentManager = quickSetupExperiment,
            brandDesignOnboardingPixelSender = brandDesignOnboardingPixelSender,
            inputScreenOnboardingWideEvent = inputScreenOnboardingWideEvent,
            defaultBrowserDetector = defaultBrowserDetector,
            widgetCapabilities = widgetCapabilities,
            pixel = pixel,
            dispatchers = coroutineRule.testDispatcherProvider,
            dismissedCtaDao = dismissedCtaDao,
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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
    }

    @Test
    fun `when can restore then sync restore shown and reinstall still evaluated for side effect`() = runTest {
        whenever(syncAutoRestore.canRestore()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.SYNC_RESTORE)
        // isAppReinstall must run even when Sync Restore wins (side-effecting).
        verify(appBuildConfig).isAppReinstall()
        orchestrator.onEvent(NewUserOnboardingEvent.RestoreRequested)
        verify(pixel).fire(PREONBOARDING_SYNC_RESTORE_TAPPED_UNIQUE, type = Unique())
        verify(syncAutoRestore).restoreSyncAccount()
        // Advances past the mutually-exclusive reinstall/initial steps to comparison chart.
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
    }

    @Test
    fun `when skip from reinstall and quick setup treatment then switches to quick setup`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(quickSetupExperiment.enroll()).thenReturn(QuickSetupExperimentVariant.TREATMENT)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        verify(pixel).fire(PREONBOARDING_SKIP_ONBOARDING_PRESSED)
        assertStep(NewUserOnboardingStepIds.QUICK_SETUP)
        orchestrator.onEvent(NewUserOnboardingEvent.QuickSetupConfirmed(OmnibarType.SINGLE_TOP, withAi = true))
        verify(brandDesignOnboardingPixelSender).fireQuickSetupClicked(
            isReinstallUser = true,
            addressBarPosition = OmnibarType.SINGLE_TOP,
            inputScreenSelected = true,
        )
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when skip from reinstall and quick setup control then shows skip option`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        whenever(quickSetupExperiment.enroll()).thenReturn(QuickSetupExperimentVariant.CONTROL)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.SKIP_ONBOARDING_OPTION)
    }

    @Test
    fun `when skip confirmed then skipped and input screen user setting enabled`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.SKIP_ONBOARDING_OPTION)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipConfirmed)
        verify(pixel).fire(PREONBOARDING_CONFIRM_SKIP_ONBOARDING_PRESSED)
        verify(duckChat).setInputScreenUserSetting(true)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when resume from skip option then returns to comparison chart`() = runTest {
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        orchestrator.onEvent(NewUserOnboardingEvent.ResumeRequested)
        verify(pixel).fire(PREONBOARDING_RESUME_ONBOARDING_PRESSED)
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
    }

    @Test
    fun `when input mode ai and treatment search default then shows preview with search default`() = runTest {
        whenever(duckAiExperiment.enroll()).thenReturn(DuckAiOnboardingExperimentVariant.TREATMENT_WITH_SEARCH_DEFAULT)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
    fun `when input mode ai but control then preview skipped and completes`() = runTest {
        whenever(duckAiExperiment.enroll()).thenReturn(DuckAiOnboardingExperimentVariant.CONTROL)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when demo search query submitted on preview then completes with launch search result`() = runTest {
        whenever(duckAiExperiment.enroll()).thenReturn(DuckAiOnboardingExperimentVariant.TREATMENT_WITH_SEARCH_DEFAULT)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)

        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "weather", isChat = false))

        assertEquals(
            Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID, result = NewUserOnboardingResult.LaunchSearch(query = "weather")),
            orchestrator.state.value,
        )
    }

    @Test
    fun `when demo chat query submitted on preview then completes with launch chat result`() = runTest {
        whenever(duckAiExperiment.enroll()).thenReturn(DuckAiOnboardingExperimentVariant.TREATMENT_WITH_SEARCH_DEFAULT)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = true))

        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "why is the sky blue", isChat = true))

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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.SKIP_ONBOARDING_OPTION)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when onboarding path then custom ai plan walks to completed`() = runTest {
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(true)
        start()

        assertStep(NewUserOnboardingStepIds.INTRO_ANIMATION)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        assertStep(NewUserOnboardingStepIds.NOTIFICATION_PERMISSION)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.INITIAL)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
        assertStepProgress(current = 1, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.INPUT_SCREEN_PREVIEW)
        assertStepProgress(current = 2, total = 4)
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true))
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
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()

        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked)
        assertStep(NewUserOnboardingStepIds.AI_COMPARISON_CHART)
    }

    @Test
    fun `when onboarding path then input screen preview is chat only`() = runTest {
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
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
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // ai_comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.InputDemoQuerySubmitted(query = "best privacy tips", isChat = true))
        orchestrator.onEvent(NewUserOnboardingEvent.DuckAiFireCompleted)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(onboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when custom ai onboarding skipped then arms open input on duck ai tab`() = runTest {
        whenever(onboardingStore.isCustomAiOnboardingFlow()).thenReturn(true)
        whenever(appBuildConfig.isAppReinstall()).thenReturn(true)
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        assertStep(NewUserOnboardingStepIds.INITIAL_REINSTALL_USER)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipRequested)
        assertStep(NewUserOnboardingStepIds.SKIP_ONBOARDING_OPTION)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipConfirmed)

        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(onboardingStore).setOpenInputOnDuckAiTab()
    }

    @Test
    fun `when default onboarding completed then does not arm open input on duck ai tab`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        orchestrator.onEvent(NewUserOnboardingEvent.AddressBarConfirmed(OmnibarType.SINGLE_TOP))
        orchestrator.onEvent(NewUserOnboardingEvent.InputModeConfirmed(withAi = false))

        assertEquals(Completed(rootPlanId = NewUserOnboardingPlanProvider.ROOT_PLAN_ID), orchestrator.state.value)
        verify(onboardingStore, never()).setOpenInputOnDuckAiTab()
    }
}
