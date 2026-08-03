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

import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class DialogConfigResolverTest {

    private val searchOptions = listOf(DaxDialogIntroOption(optionText = "search", iconRes = 0, link = "how to fix a bike"))
    private val chatSuggestions = listOf(DaxDialogIntroOption(optionText = "chat", iconRes = 0, link = "explain quantum computing"))
    private val onboardingStore: OnboardingStore = mock {
        on { getSearchOptions() } doReturn searchOptions
        on { getChatSuggestions() } doReturn chatSuggestions
    }

    private val testee = DialogConfigResolver(onboardingStore)

    @Test
    fun `resolves the comparison chart with the browser chart config`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.ComparisonChart, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.ComparisonChart, config.background)
        assertEquals(Embellishment.BottomWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        val expectedChart = ComparisonChartConfig.Browser(isCustomAiCopy = false)
        assertEquals(ContentConfig.ComparisonChart(TextConfig.Resource(expectedChart.titleRes), expectedChart), config.content)
        assertEquals(
            CtaConfig(TextConfig.Resource(expectedChart.primaryCtaTextRes), CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked)),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the comparison chart with custom ai copy in the custom ai flow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.ComparisonChart, isCustomAiFlow = true)!!

        val expectedChart = ComparisonChartConfig.Browser(isCustomAiCopy = true)
        assertEquals(ContentConfig.ComparisonChart(TextConfig.Resource(expectedChart.titleRes), expectedChart), config.content)
    }

    @Test
    fun `resolves the ai comparison chart with the ai chart config`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.AiComparisonChart, isCustomAiFlow = true)!!

        assertEquals(
            ContentConfig.ComparisonChart(TextConfig.Resource(ComparisonChartConfig.Ai.titleRes), ComparisonChartConfig.Ai),
            config.content,
        )
    }

    @Test
    fun `resolves the address bar position with a submitting cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = true), isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.AddressBar, config.background)
        assertEquals(Embellishment.BobbingDax, config.embellishment)
        assertEquals(
            ContentConfig.AddressBar(
                title = TextConfig.Resource(R.string.preOnboardingAddressBarTitle),
                initialPosition = OmnibarType.SINGLE_TOP,
                showSplitOption = true,
            ),
            config.content,
        )
        assertEquals(CtaAction.Submit, config.primaryCta!!.action)
    }

    @Test
    fun `resolves no config for a dialog that has no config-driven screen yet`() {
        assertNull(testee.resolve(NewUserOnboardingActivityDialog.NotificationPermission, isCustomAiFlow = false))
    }

    @Test
    fun `resolves the add to dock dialog with no decoration and no arrow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.AddToDock, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.AddToDock, config.background)
        assertEquals(Embellishment.None, config.embellishment)
        assertEquals(CardArrowConfig.Hidden, config.cardArrow)
        assertEquals(
            ContentConfig.AddToDock(
                title = TextConfig.Resource(R.string.preOnboardingDockStepTitle),
                body = TextConfig.Resource(R.string.preOnboardingAddToDockBody),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingAddToDockPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the initial welcome dialog with a continue cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.Welcome, config.background)
        assertEquals(Embellishment.WalkingDax, config.embellishment)
        assertEquals(CardArrowConfig.AtStart, config.cardArrow)
        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
                body1AsHtml = false,
                body2 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the initial welcome dialog with single line html copy in the custom ai flow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = true)!!

        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBodyCustomAi),
                body1AsHtml = true,
                body2 = null,
            ),
            config.content,
        )
    }

    @Test
    fun `resolves the reinstall welcome dialog with a skip secondary cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InitialReinstallUser, isCustomAiFlow = false)!!

        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
                body1 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody1),
                body1AsHtml = false,
                body2 = TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingDaxDialog1SecondaryButton),
                CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
            config.secondaryCta,
        )
    }

    @Test
    fun `resolves the sync restore dialog with its own copy and restore cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.SyncRestore, isCustomAiFlow = true)!!

        assertEquals(OnboardingBackgroundStep.Welcome, config.background)
        assertEquals(
            ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle),
                body1 = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignBody1),
                body1AsHtml = true,
                body2 = null,
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.syncRestoreDialogPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.RestoreRequested),
            ),
            config.primaryCta,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.syncRestoreDialogSecondaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
            config.secondaryCta,
        )
    }

    @Test
    fun `resolves the input screen with a submitting cta and ai preselected`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreen, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.InputType, config.background)
        assertEquals(Embellishment.LeftWing, config.embellishment)
        assertEquals(CardArrowConfig.AtStart, config.cardArrow)
        val content = config.content as ContentConfig.InputScreen
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputScreenTitleUpdated), content.title)
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputScreenDescription), content.description)
        assertEquals(InputScreenContentState(withAi = true), content.initialState())
        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingInputScreenButton), CtaAction.Submit),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the widget prompt dialog with add and skip ctas`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.WidgetPrompt, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.AddWidget, config.background)
        assertEquals(Embellishment.LeftWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        assertEquals(
            ContentConfig.WidgetPrompt(
                title = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogTitle),
                body = TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogSubTitle),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.preOnboardingWidgetPromptPrimaryCta),
                CtaAction.Emit(NewUserOnboardingEvent.AddWidgetRequested),
            ),
            config.primaryCta,
        )
        assertEquals(
            CtaConfig(
                TextConfig.Resource(R.string.experimentHomeScreenWidgetBottomSheetDialogGhostButton),
                CtaAction.Emit(NewUserOnboardingEvent.WidgetPromptSkipped),
            ),
            config.secondaryCta,
        )
    }

    @Test
    fun `resolves the input screen preview with store suggestions and no cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = true), isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.InputType, config.background)
        assertEquals(Embellishment.None, config.embellishment)
        assertEquals(CardArrowConfig.Hidden, config.cardArrow)
        assertEquals(
            ContentConfig.InputScreenPreview(
                title = TextConfig.Resource(R.string.preOnboardingInputModeDemoTitle),
                isSearchDefault = true,
                showModeToggle = true,
                searchSuggestions = searchOptions,
                chatSuggestions = chatSuggestions,
            ),
            config.content,
        )
        assertNull(config.primaryCta)
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the input screen preview without a mode toggle in the custom ai flow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InputScreenPreview(isSearchDefault = false), isCustomAiFlow = true)!!

        val content = config.content as ContentConfig.InputScreenPreview
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputModeDemoTitleCustomAi), content.title)
        assertFalse(content.showModeToggle)
        assertEquals(InputScreenPreviewContentState(isSearchSelected = false), content.initialState())
    }
}
