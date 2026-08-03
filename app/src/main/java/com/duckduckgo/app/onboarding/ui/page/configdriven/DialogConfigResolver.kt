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
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundStep
import javax.inject.Inject

class DialogConfigResolver @Inject constructor() {

    fun resolve(
        dialog: NewUserOnboardingActivityDialog,
        isCustomAiFlow: Boolean,
    ): DialogConfig? = when (dialog) {
        NewUserOnboardingActivityDialog.ComparisonChart -> comparisonChart(ComparisonChartConfig.Browser(isCustomAiCopy = isCustomAiFlow))

        NewUserOnboardingActivityDialog.AiComparisonChart -> comparisonChart(ComparisonChartConfig.Ai)

        is NewUserOnboardingActivityDialog.AddressBarPosition -> DialogConfig(
            background = OnboardingBackgroundStep.AddressBar,
            embellishment = Embellishment.BobbingDax,
            cardArrow = CardArrowConfig.AtEnd,
            content = ContentConfig.AddressBar(
                title = TextConfig.Resource(R.string.preOnboardingAddressBarTitle),
                initialPosition = OmnibarType.SINGLE_TOP,
                showSplitOption = dialog.showSplitOption,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingAddressBarOkButton),
                action = CtaAction.Submit,
            ),
        )

        NewUserOnboardingActivityDialog.Initial -> welcome(
            content = welcomeContent(isCustomAiFlow),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
        )

        NewUserOnboardingActivityDialog.InitialReinstallUser -> welcome(
            content = welcomeContent(isCustomAiFlow),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1ButtonBrandDesign),
                action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
            ),
            secondaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingDaxDialog1SecondaryButton),
                action = CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
        )

        NewUserOnboardingActivityDialog.SyncRestore -> welcome(
            content = ContentConfig.Welcome(
                title = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignTitle),
                body1 = TextConfig.Resource(R.string.syncRestoreDialogBrandDesignBody1),
                body1AsHtml = true,
                body2 = null,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.syncRestoreDialogPrimaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.RestoreRequested),
            ),
            secondaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.syncRestoreDialogSecondaryCta),
                action = CtaAction.Emit(NewUserOnboardingEvent.SkipRequested),
            ),
        )

        NewUserOnboardingActivityDialog.InputScreen -> DialogConfig(
            background = OnboardingBackgroundStep.InputType,
            embellishment = Embellishment.LeftWing,
            cardArrow = CardArrowConfig.AtStart,
            content = ContentConfig.InputScreen(
                title = TextConfig.Resource(R.string.preOnboardingInputScreenTitleUpdated),
                description = TextConfig.Resource(R.string.preOnboardingInputScreenDescription),
                initialWithAi = true,
            ),
            primaryCta = CtaConfig(
                text = TextConfig.Resource(R.string.preOnboardingInputScreenButton),
                action = CtaAction.Submit,
            ),
        )

        is NewUserOnboardingActivityDialog.IntroAnimation,
        NewUserOnboardingActivityDialog.NotificationPermission,
        NewUserOnboardingActivityDialog.DefaultBrowserPrompt,
        NewUserOnboardingActivityDialog.AddWidget,
        NewUserOnboardingActivityDialog.AddToDock,
        NewUserOnboardingActivityDialog.WidgetPrompt,
        is NewUserOnboardingActivityDialog.InputScreenPreview,
        is NewUserOnboardingActivityDialog.QuickSetup,
        -> null // to be implemented in following tasks
    }

    private fun comparisonChart(chart: ComparisonChartConfig) = DialogConfig(
        background = OnboardingBackgroundStep.ComparisonChart,
        embellishment = Embellishment.BottomWing,
        cardArrow = CardArrowConfig.AtEnd,
        content = ContentConfig.ComparisonChart(title = TextConfig.Resource(chart.titleRes), config = chart),
        primaryCta = CtaConfig(
            text = TextConfig.Resource(chart.primaryCtaTextRes),
            action = CtaAction.Emit(NewUserOnboardingEvent.ContinueClicked),
        ),
    )

    private fun welcome(
        content: ContentConfig.Welcome,
        primaryCta: CtaConfig,
        secondaryCta: CtaConfig? = null,
    ) = DialogConfig(
        background = OnboardingBackgroundStep.Welcome,
        embellishment = Embellishment.WalkingDax,
        cardArrow = CardArrowConfig.AtStart,
        content = content,
        primaryCta = primaryCta,
        secondaryCta = secondaryCta,
    )

    private fun welcomeContent(isCustomAiFlow: Boolean) = ContentConfig.Welcome(
        title = TextConfig.Resource(R.string.preOnboardingWelcomeDialogTitle),
        body1 = TextConfig.Resource(
            if (isCustomAiFlow) R.string.preOnboardingWelcomeDialogBodyCustomAi else R.string.preOnboardingWelcomeDialogBody1,
        ),
        body1AsHtml = isCustomAiFlow,
        body2 = if (isCustomAiFlow) null else TextConfig.Resource(R.string.preOnboardingWelcomeDialogBody2),
    )
}
