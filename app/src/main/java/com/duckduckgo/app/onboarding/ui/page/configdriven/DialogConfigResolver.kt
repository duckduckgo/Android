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

        is NewUserOnboardingActivityDialog.IntroAnimation,
        NewUserOnboardingActivityDialog.NotificationPermission,
        NewUserOnboardingActivityDialog.DefaultBrowserPrompt,
        NewUserOnboardingActivityDialog.AddWidget,
        NewUserOnboardingActivityDialog.SyncRestore,
        NewUserOnboardingActivityDialog.InitialReinstallUser,
        NewUserOnboardingActivityDialog.Initial,
        NewUserOnboardingActivityDialog.AddToDock,
        NewUserOnboardingActivityDialog.WidgetPrompt,
        NewUserOnboardingActivityDialog.InputScreen,
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
}
