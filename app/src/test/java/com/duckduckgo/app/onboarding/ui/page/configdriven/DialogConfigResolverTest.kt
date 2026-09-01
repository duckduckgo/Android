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
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.TestOption
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
import com.duckduckgo.mobile.android.R as CommonR

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
    fun `resolves the segmented comparison chart without an embellishment or card arrow`() {
        val config = testee.resolve(
            NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedSearchPath),
            isCustomAiFlow = false,
        )!!

        assertEquals(OnboardingBackgroundStep.ComparisonChart, config.background)
        assertEquals(Embellishment.None, config.embellishment)
        assertEquals(CardArrowConfig.Hidden, config.cardArrow)
        val expectedChart = ComparisonChartConfig.SegmentedSearchPath
        assertEquals(ContentConfig.ComparisonChart(TextConfig.Resource(expectedChart.titleRes), expectedChart), config.content)
        assertEquals(
            CtaConfig(TextConfig.Resource(expectedChart.primaryCtaTextRes), CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked)),
            config.primaryCta,
        )
    }

    @Test
    fun `resolves the preference selector by passing its rows through against a submitting cta`() {
        val rows = listOf(
            row(OnboardingPreference.SEARCH_HISTORY, initiallyEnabled = true),
            row(OnboardingPreference.SAFE_SEARCH, initiallyEnabled = false),
        )

        val config = testee.resolve(
            NewUserOnboardingActivityDialog.PreferenceSelector(
                titleRes = R.string.noAiPathPreferenceSelectorTitle,
                rows = rows,
            ),
            isCustomAiFlow = false,
        )!!

        assertEquals(OnboardingBackgroundStep.PreferenceSelector, config.background)
        assertEquals(Embellishment.LeftWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        val content = config.content as ContentConfig.PreferenceSelector
        assertEquals(TextConfig.Resource(R.string.noAiPathPreferenceSelectorTitle), content.title)
        assertEquals(rows, content.rows)
        assertNull(content.caption)
        assertEquals(
            PreferenceSelectorContentState(
                mapOf(
                    OnboardingPreference.SEARCH_HISTORY to true,
                    OnboardingPreference.SAFE_SEARCH to false,
                ),
            ),
            content.initialState(),
        )
        assertEquals(CtaAction.Submit, config.primaryCta!!.action)
    }

    @Test
    fun `resolves the preference selector caption when one is given`() {
        val config = testee.resolve(
            NewUserOnboardingActivityDialog.PreferenceSelector(
                titleRes = R.string.blockAdsPathPreferenceSelectorTitle,
                rows = listOf(row(OnboardingPreference.BLOCK_ADS, initiallyEnabled = true)),
                caption = R.string.preferenceChangeInSettingsCaption,
            ),
            isCustomAiFlow = false,
        )!!

        assertEquals(
            TextConfig.Resource(R.string.preferenceChangeInSettingsCaption),
            (config.content as ContentConfig.PreferenceSelector).caption,
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
    fun `resolves the download reason with a submitting cta`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.DownloadReason, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.ComparisonChart, config.background)
        assertEquals(Embellishment.BottomWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        assertEquals(
            ContentConfig.DownloadReason(
                title = TextConfig.Resource(R.string.downloadReasonTitle),
                body = TextConfig.Resource(R.string.downloadReasonBody),
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.downloadReasonPrimaryCta), CtaAction.Submit),
            config.primaryCta,
        )
        assertNull(config.secondaryCta)
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
    fun `resolves the initial welcome dialog with a card entry that follows the background`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = false)!!

        assertEquals(CardEntry.AfterBackgroundTransition, config.cardEntry)
    }

    @Test
    fun `resolves the reinstall welcome dialog with a card entry that follows the background`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.InitialReinstallUser, isCustomAiFlow = false)!!

        assertEquals(CardEntry.AfterBackgroundTransition, config.cardEntry)
    }

    @Test
    fun `resolves the sync restore dialog with a card entry that follows the background`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.SyncRestore, isCustomAiFlow = false)!!

        assertEquals(CardEntry.AfterBackgroundTransition, config.cardEntry)
    }

    @Test
    fun `resolves a mid-flow dialog with an immediate card entry`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.ComparisonChart, isCustomAiFlow = false)!!

        assertEquals(CardEntry.Immediate, config.cardEntry)
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
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
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
    fun `resolves the input screen preview with store suggestions and no cta`() {
        val config = testee.resolve(
            NewUserOnboardingActivityDialog.InputScreenPreview(
                isSearchDefault = true,
                showModeToggle = true,
                titleRes = R.string.preOnboardingInputModeDemoTitle,
            ),
            isCustomAiFlow = false,
        )!!

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
    fun `resolves the input screen preview title and mode toggle from the dialog`() {
        val config = testee.resolve(
            NewUserOnboardingActivityDialog.InputScreenPreview(
                isSearchDefault = false,
                showModeToggle = false,
                titleRes = R.string.preOnboardingInputModeDemoTitleCustomAi,
            ),
            isCustomAiFlow = true,
        )!!

        val content = config.content as ContentConfig.InputScreenPreview
        assertEquals(TextConfig.Resource(R.string.preOnboardingInputModeDemoTitleCustomAi), content.title)
        assertFalse(content.showModeToggle)
        assertEquals(InputScreenPreviewContentState(isSearchSelected = false), content.initialState())
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
    fun `resolves quick setup with the plan's row visibility and a submitting cta`() {
        val dialog = NewUserOnboardingActivityDialog.QuickSetup(
            showSplitOption = true,
            hideSetDefaultBrowserRow = true,
            hideAddWidgetRow = false,
            hideAddressBarRow = true,
            isReinstallUser = true,
        )

        val config = testee.resolve(dialog, isCustomAiFlow = false)!!

        assertEquals(OnboardingBackgroundStep.QuickSetup, config.background)
        assertEquals(Embellishment.BottomWing, config.embellishment)
        assertEquals(CardArrowConfig.AtEnd, config.cardArrow)
        assertEquals(
            ContentConfig.QuickSetup(
                title = TextConfig.Resource(R.string.preOnboardingReinstallQuickSetupTitle),
                showSplitOption = true,
                hideSetDefaultBrowserRow = true,
                hideAddWidgetRow = false,
                hideAddressBarRow = true,
                initialAddressBarPosition = OmnibarType.SINGLE_TOP,
                initialWithAi = true,
            ),
            config.content,
        )
        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingReinstallStartBrowsing), CtaAction.Submit),
            config.primaryCta,
        )
    }

    @Test
    fun `resolves quick setup with custom ai cta copy in the custom ai flow`() {
        val dialog = NewUserOnboardingActivityDialog.QuickSetup(
            showSplitOption = false,
            hideSetDefaultBrowserRow = false,
            hideAddWidgetRow = false,
            hideAddressBarRow = false,
            isReinstallUser = false,
        )

        val config = testee.resolve(dialog, isCustomAiFlow = true)!!

        assertEquals(
            CtaConfig(TextConfig.Resource(R.string.preOnboardingDaxDialog3ButtonCustomAi), CtaAction.Submit),
            config.primaryCta,
        )
    }

    @Test
    fun `resolves the toggle position dialog with the plan's options and no ctas`() {
        val options = listOf(TestOption("duckAI", label = "Open tabs with AI chat"), TestOption("lastUsed", label = "Not Now"))

        val config = testee.resolve(NewUserOnboardingActivityDialog.TogglePosition(options), isCustomAiFlow = true)!!

        assertEquals(
            ContentConfig.TogglePosition(
                title = TextConfig.Resource(R.string.aiPathTogglePositionTitle),
                pictogramLightRes = CommonR.drawable.toggle_ai_chat_default_lighttheme,
                pictogramDarkRes = CommonR.drawable.toggle_ai_chat_default_darktheme,
                pictogramCaption = TextConfig.Resource(R.string.aiPathTogglePositionPictogramCaption),
                options = options,
            ),
            config.content,
        )
        assertNull(config.primaryCta)
        assertNull(config.secondaryCta)
    }

    @Test
    fun `resolves the import passwords dialog with a right wing and a mirrored arrow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.ImportPasswords, isCustomAiFlow = false)!!

        assertEquals(Embellishment.RightWing, config.embellishment)
        assertEquals(CardArrowConfig.AtStartMirrored, config.cardArrow)
    }

    @Test
    fun `resolves the import complete dialog with a right wing and a mirrored arrow`() {
        val config = testee.resolve(NewUserOnboardingActivityDialog.ImportComplete, isCustomAiFlow = false)!!

        assertEquals(Embellishment.RightWing, config.embellishment)
        assertEquals(CardArrowConfig.AtStartMirrored, config.cardArrow)
    }

    private fun row(
        preference: OnboardingPreference,
        initiallyEnabled: Boolean,
    ) = ContentConfig.PreferenceSelector.Row(
        preference = preference,
        iconRes = null,
        primaryText = TextConfig.Literal(preference.name),
        secondaryText = null,
        initiallyEnabled = initiallyEnabled,
    )
}
