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

import androidx.annotation.StringRes
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.OnboardingPreferencePresentation
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option

/**
 * What the [com.duckduckgo.app.onboarding.ui.OnboardingActivity] renderer should present for the current step.
 */
sealed interface NewUserOnboardingActivityDialog {
    data class IntroAnimation(val withDuckAi: Boolean = false) : NewUserOnboardingActivityDialog
    data object NotificationPermission : NewUserOnboardingActivityDialog
    data object SyncRestore : NewUserOnboardingActivityDialog
    data object InitialReinstallUser : NewUserOnboardingActivityDialog
    data object Initial : NewUserOnboardingActivityDialog
    data object ComparisonChart : NewUserOnboardingActivityDialog
    data object DownloadReason : NewUserOnboardingActivityDialog
    data object AiComparisonChart : NewUserOnboardingActivityDialog
    data class SegmentedComparisonChart(val chart: ComparisonChartConfig) : NewUserOnboardingActivityDialog
    data object DefaultBrowserPrompt : NewUserOnboardingActivityDialog
    data object AddToDock : NewUserOnboardingActivityDialog
    data object WidgetPrompt : NewUserOnboardingActivityDialog

    data object AddWidget : NewUserOnboardingActivityDialog
    data class AddressBarPosition(val showSplitOption: Boolean) : NewUserOnboardingActivityDialog
    data object InputScreen : NewUserOnboardingActivityDialog

    /**
     * @param isSearchDefault when true, the search tab is pre-selected, otherwise, the Duck.ai tab is pre-selected.
     * @param showModeToggle when true, the toggle is visible and allows changing the pre-selected input method ([isSearchDefault]).
     */
    data class InputScreenPreview(
        val isSearchDefault: Boolean,
        val showModeToggle: Boolean,
        @get:StringRes val titleRes: Int,
    ) : NewUserOnboardingActivityDialog

    data class QuickSetup(
        val showSplitOption: Boolean,
        val hideSetDefaultBrowserRow: Boolean,
        val hideAddWidgetRow: Boolean,
        val hideAddressBarRow: Boolean,
        val isReinstallUser: Boolean,
    ) : NewUserOnboardingActivityDialog

    /** [offered] holds only the preferences to offer, in row order, each against how to seed and render its row. */
    data class PreferenceSelector(
        @get:StringRes val titleRes: Int,
        val offered: Map<OnboardingPreference, Offered>,
        @get:StringRes val caption: Int? = null,
    ) : NewUserOnboardingActivityDialog {

        data class Offered(
            val initiallyEnabled: Boolean,
            /** Set only for preferences whose owning module supplies its own copy and icon. */
            val presentation: OnboardingPreferencePresentation? = null,
        )
    }

    data class SingleChoice(
        @field:StringRes val title: Int,
        @field:StringRes val body: Int,
        val options: List<Option>,
    ) : NewUserOnboardingActivityDialog

    data class TogglePosition(val options: List<Option>) : NewUserOnboardingActivityDialog

    data class DuckAiState(val options: List<Option>) : NewUserOnboardingActivityDialog
}
