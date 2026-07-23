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

import com.duckduckgo.app.onboarding.CustomAiOnboardingPixelName
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingActivityDialog
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_INTRO_SHOWN_UNIQUE
import com.duckduckgo.app.pixels.AppPixelName.PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import javax.inject.Inject

/**
 * Single authority for the legacy once-ever "dialog shown" pixels, keyed on the actual dialog being applied
 * rather than on [com.duckduckgo.app.onboarding.ui.page.PreOnboardingDialogType] (legacy's
 * `fireDialogShownPixel`, ported 1:1). Exhaustive `when` with no `else`: a new dialog fails to compile here
 * until it's given an explicit mapping (even if that mapping is [Unit]).
 */
class OnboardingDialogShownPixels @Inject constructor(private val pixel: Pixel) {

    fun fireFor(dialog: NewUserOnboardingActivityDialog) {
        when (dialog) {
            NewUserOnboardingActivityDialog.SyncRestore -> pixel.fire(PREONBOARDING_SYNC_RESTORE_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InitialReinstallUser -> pixel.fire(PREONBOARDING_INTRO_REINSTALL_USER_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.Initial -> pixel.fire(PREONBOARDING_INTRO_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.ComparisonChart -> pixel.fire(PREONBOARDING_COMPARISON_CHART_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.AiComparisonChart -> pixel.fire(CustomAiOnboardingPixelName.AI_COMPARISON_SCREEN_SHOW, type = Unique())
            is NewUserOnboardingActivityDialog.AddressBarPosition -> pixel.fire(PREONBOARDING_ADDRESS_BAR_POSITION_SHOWN_UNIQUE, type = Unique())
            NewUserOnboardingActivityDialog.InputScreen -> pixel.fire(PREONBOARDING_CHOOSE_SEARCH_EXPERIENCE_IMPRESSIONS_UNIQUE, type = Unique())
            is NewUserOnboardingActivityDialog.InputScreenPreview,
            is NewUserOnboardingActivityDialog.QuickSetup,
            NewUserOnboardingActivityDialog.AddToDock,
            NewUserOnboardingActivityDialog.WidgetPrompt,
            is NewUserOnboardingActivityDialog.IntroAnimation,
            NewUserOnboardingActivityDialog.NotificationPermission,
            NewUserOnboardingActivityDialog.DefaultBrowserPrompt,
            NewUserOnboardingActivityDialog.AddWidget,
            -> Unit
        }
    }
}
