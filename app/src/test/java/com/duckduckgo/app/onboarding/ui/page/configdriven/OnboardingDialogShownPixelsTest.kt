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
import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions

class OnboardingDialogShownPixelsTest {

    private val pixel: Pixel = mock()
    private val testee = OnboardingDialogShownPixels(pixel)

    @Test
    fun `fires the intro shown pixel once ever for the initial dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.Initial)

        verify(pixel).fire(AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the reinstall intro shown pixel for the reinstall dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.InitialReinstallUser)

        verify(pixel).fire(AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the sync restore shown pixel for the sync restore dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.SyncRestore)

        verify(pixel).fire(AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the comparison chart shown pixel for the comparison chart`() {
        testee.fireFor(NewUserOnboardingActivityDialog.ComparisonChart)

        verify(pixel).fire(AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the ai comparison shown pixel for the ai comparison chart`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AiComparisonChart)

        verify(pixel).fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
    }

    @Test
    fun `fires the address bar shown pixel for the address bar dialog`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AddressBarPosition(showSplitOption = false))

        verify(pixel).fire(AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
    }

    @Test
    fun `fires the search experience shown pixel for the input screen`() {
        testee.fireFor(NewUserOnboardingActivityDialog.InputScreen)

        verify(pixel).fire(AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
    }

    @Test
    fun `fires nothing for the dialogs legacy had no shown pixel for`() {
        testee.fireFor(NewUserOnboardingActivityDialog.AddToDock)
        testee.fireFor(NewUserOnboardingActivityDialog.WidgetPrompt)
        testee.fireFor(
            NewUserOnboardingActivityDialog.InputScreenPreview(
                isSearchDefault = true,
                showModeToggle = true,
                titleRes = R.string.preOnboardingInputModeDemoTitle,
            ),
        )
        testee.fireFor(NewUserOnboardingActivityDialog.DownloadReason)
        testee.fireFor(NewUserOnboardingActivityDialog.SegmentedComparisonChart(ComparisonChartConfig.SegmentedSearchPath))
        testee.fireFor(
            NewUserOnboardingActivityDialog.PreferenceSelector(
                titleRes = R.string.searchPathPreferenceSelectorTitle,
                rows = listOf(
                    ContentConfig.PreferenceSelector.Row(
                        preference = OnboardingPreference.SEARCH_HISTORY,
                        iconRes = null,
                        primaryText = TextConfig.Literal("history"),
                        secondaryText = null,
                        initiallyEnabled = true,
                    ),
                ),
            ),
        )
        testee.fireFor(
            NewUserOnboardingActivityDialog.QuickSetup(
                showSplitOption = true,
                hideSetDefaultBrowserRow = false,
                hideAddWidgetRow = false,
                hideAddressBarRow = false,
                isReinstallUser = false,
            ),
        )

        verifyNoInteractions(pixel)
    }
}
