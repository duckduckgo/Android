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

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.model.CtaId
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.onboarding.OnboardingPromptsExperimentManager.OnboardingPromptExperimentVariant
import com.duckduckgo.app.onboarding.ui.page.OnboardingPixelAction
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_AICHAT_SELECTED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_BOTTOM_ADDRESS_BAR_SELECTED_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_BROWSER_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SKIP_ONBOARDING_PRESSED
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_ADDRESS_BAR_POSITION
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_NOTIFICATIONS
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_QUICK_SETUP
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_CHAT_TOGGLE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SEARCH_EXPERIENCE
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_SET_DEFAULT
import com.duckduckgo.app.pixels.OnboardingPixelName.ONBOARDING_WELCOME
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.onboarding.api.LinearOnboardingState.Completed
import com.duckduckgo.onboarding.api.LinearOnboardingState.InProgress
import com.duckduckgo.onboarding.api.LinearOnboardingState.Skipped
import com.duckduckgo.onboarding.impl.LinearOnboardingOrchestratorImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DefaultOnboardingPlanBuilderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val h = NewUserOnboardingPlanHarness(coroutineRule.testDispatcherProvider)

    private val syncAutoRestore = h.syncAutoRestore
    private val appBuildConfig = h.appBuildConfig
    private val defaultRoleBrowserDialog = h.defaultRoleBrowserDialog
    private val settingsDataStore = h.settingsDataStore
    private val onboardingStore = h.onboardingStore
    private val duckChat = h.duckChat
    private val duckAiAvailability = h.duckAiAvailability
    private val onboardingPixelSender = h.onboardingPixelSender
    private val inputScreenOnboardingWideEvent = h.inputScreenOnboardingWideEvent
    private val widgetCapabilities = h.widgetCapabilities
    private val pixel = h.pixel
    private val dismissedCtaDao = h.dismissedCtaDao

    private val builder = h.defaultPlanBuilder()
    private val orchestrator = LinearOnboardingOrchestratorImpl()

    private suspend fun start(onboardingPromptExperimentVariant: OnboardingPromptExperimentVariant? = null) {
        orchestrator.startPlan(
            builder.build(
                onCompleted = {},
                onSkipped = {},
                onboardingPromptExperimentVariant = onboardingPromptExperimentVariant,
            ),
        )
    }

    private fun assertStep(id: String) {
        val state = orchestrator.state.value
        assertTrue("expected InProgress on '$id' but was $state", state is InProgress)
        assertEquals(id, (state as InProgress).currentStep.id)
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
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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
        assertEquals(Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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
            Completed(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID, result = NewUserOnboardingResult.LaunchSearch(query = "weather")),
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
                rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID,
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
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
    }

    @Test
    fun `when dev skip mid flow then aborts to skipped`() = runTest {
        start()
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        assertStep(NewUserOnboardingStepIds.COMPARISON_CHART)
        orchestrator.onEvent(NewUserOnboardingEvent.SkipNewUserOnboardingDevOptionClicked)
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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
        assertEquals(Skipped(rootPlanId = NewUserOnboardingPlans.ROOT_PLAN_ID), orchestrator.state.value)
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

    // endregion

    // region Home-screen prompts experiment composition

    private suspend fun stepIdsFor(onboardingPromptExperimentVariant: OnboardingPromptExperimentVariant?): List<String> {
        return builder.build(
            onCompleted = {},
            onSkipped = {},
            onboardingPromptExperimentVariant = onboardingPromptExperimentVariant,
        ).steps.map { it.id }
    }

    @Test
    fun whenControlThenNoNewPagesInPlan() = runTest {
        val ids = stepIdsFor(OnboardingPromptExperimentVariant.CONTROL)
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_TO_DOCK))
        assertFalse(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_WIDGET))
    }

    @Test
    fun whenDockOnlyThenOnlyAddToDockInsertedAfterDefaultBrowser() = runTest {
        val ids = stepIdsFor(OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY)
        assertTrue(ids.contains(NewUserOnboardingStepIds.ADD_TO_DOCK))
        assertFalse(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_WIDGET))
        assertEquals(
            ids.indexOf(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT) + 1,
            ids.indexOf(NewUserOnboardingStepIds.ADD_TO_DOCK),
        )
    }

    @Test
    fun whenWidgetOnlyThenWidgetPromptAndAddWidgetInserted() = runTest {
        val ids = stepIdsFor(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY)
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_TO_DOCK))
        assertTrue(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
        assertTrue(ids.contains(NewUserOnboardingStepIds.ADD_WIDGET))
        assertEquals(
            ids.indexOf(NewUserOnboardingStepIds.WIDGET_PROMPT) + 1,
            ids.indexOf(NewUserOnboardingStepIds.ADD_WIDGET),
        )
        assertEquals(
            ids.indexOf(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT) + 1,
            ids.indexOf(NewUserOnboardingStepIds.WIDGET_PROMPT),
        )
    }

    @Test
    fun whenWidgetPromptStepPresentedThenLinearPlanWidgetPromptShownStored() = runTest {
        start(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY)
        orchestrator.onEvent(NewUserOnboardingEvent.IntroAnimationFinished)
        orchestrator.onEvent(NewUserOnboardingEvent.NotificationPermissionFinished(granted = null))
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // initial
        orchestrator.onEvent(NewUserOnboardingEvent.ContinueClicked) // comparison_chart
        orchestrator.onEvent(NewUserOnboardingEvent.DefaultBrowserPromptFinished(isDefaultBrowser = false))
        assertStep(NewUserOnboardingStepIds.WIDGET_PROMPT)

        orchestrator.onEvent(NewUserOnboardingEvent.Presented)

        verify(onboardingStore).linearPlanWidgetPromptShown = true
        verify(dismissedCtaDao, never()).insert(DismissedCta(CtaId.ADD_WIDGET))
        assertStep(NewUserOnboardingStepIds.WIDGET_PROMPT)
    }

    @Test
    fun whenWidgetVariantButUserAlreadyHasWidgetThenWidgetStepsNotInserted() = runTest {
        whenever(widgetCapabilities.hasInstalledWidgets).thenReturn(true)
        val ids = stepIdsFor(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY)
        assertFalse(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_WIDGET))
    }

    @Test
    fun whenBothThenDockThenWidgetPromptThenAddWidget() = runTest {
        val ids = stepIdsFor(OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET)
        val dock = ids.indexOf(NewUserOnboardingStepIds.ADD_TO_DOCK)
        val prompt = ids.indexOf(NewUserOnboardingStepIds.WIDGET_PROMPT)
        val add = ids.indexOf(NewUserOnboardingStepIds.ADD_WIDGET)
        assertTrue(dock < prompt && prompt < add)
        assertEquals(ids.indexOf(NewUserOnboardingStepIds.DEFAULT_BROWSER_PROMPT) + 1, dock)
    }

    @Test
    fun whenNotEnrolledThenNoNewPagesInPlan() = runTest {
        val ids = stepIdsFor(onboardingPromptExperimentVariant = null)
        assertFalse(ids.contains(NewUserOnboardingStepIds.ADD_TO_DOCK))
        assertFalse(ids.contains(NewUserOnboardingStepIds.WIDGET_PROMPT))
    }

    // endregion

    // region Step-indicator regression guard

    private suspend fun indicatorCountFor(onboardingPromptExperimentVariant: OnboardingPromptExperimentVariant): Int {
        return builder.build(
            onCompleted = {},
            onSkipped = {},
            onboardingPromptExperimentVariant = onboardingPromptExperimentVariant,
        ).steps.count { (it as? NewUserOnboardingActivityStep)?.showsStepIndicator == true }
    }

    @Test
    fun stepIndicatorTotalsMatchCohort() = runTest {
        val control = indicatorCountFor(OnboardingPromptExperimentVariant.CONTROL)
        assertEquals(control + 1, indicatorCountFor(OnboardingPromptExperimentVariant.TREATMENT_DOCK_ONLY))
        assertEquals(control + 1, indicatorCountFor(OnboardingPromptExperimentVariant.TREATMENT_WIDGET_ONLY))
        assertEquals(control + 2, indicatorCountFor(OnboardingPromptExperimentVariant.TREATMENT_DOCK_AND_WIDGET))
    }

    // endregion
}
