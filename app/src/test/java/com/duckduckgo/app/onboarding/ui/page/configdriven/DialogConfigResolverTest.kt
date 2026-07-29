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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialogConfigResolverTest {

    private val testee = DialogConfigResolver()

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
        assertNull(testee.resolve(NewUserOnboardingActivityDialog.Initial, isCustomAiFlow = false))
        assertNull(testee.resolve(NewUserOnboardingActivityDialog.NotificationPermission, isCustomAiFlow = false))
        assertNull(testee.resolve(NewUserOnboardingActivityDialog.AddToDock, isCustomAiFlow = false))
    }
}
