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

import androidx.annotation.DrawableRes
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.OnboardingPreference
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig
import com.duckduckgo.onboarding.api.OnboardingSingleChoiceDataPlugin.Option

/** A screen with working state the user edits before submitting. */
interface Stateful<S : Any> {
    fun initialState(): S
}

sealed interface ContentConfig {

    val title: TextConfig

    data class Welcome(
        override val title: TextConfig,
        val body1: TextConfig,
        val body2: TextConfig?,
    ) : ContentConfig

    data class ComparisonChart(
        override val title: TextConfig,
        val config: ComparisonChartConfig,
    ) : ContentConfig

    data class AddressBar(
        override val title: TextConfig,
        val initialPosition: OmnibarType,
        val showSplitOption: Boolean,
    ) : ContentConfig, Stateful<AddressBarContentState> {
        override fun initialState() = AddressBarContentState(position = initialPosition)
    }

    data class InputScreen(
        override val title: TextConfig,
        val description: TextConfig,
        val initialWithAi: Boolean,
    ) : ContentConfig, Stateful<InputScreenContentState> {
        override fun initialState() = InputScreenContentState(withAi = initialWithAi)
    }

    data class AddToDock(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig

    data class WidgetPrompt(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig

    data class InputScreenPreview(
        override val title: TextConfig,
        val isSearchDefault: Boolean,
        val showModeToggle: Boolean,
        val searchSuggestions: List<DaxDialogIntroOption>,
        val chatSuggestions: List<DaxDialogIntroOption>,
    ) : ContentConfig, Stateful<InputScreenPreviewContentState> {
        override fun initialState() = InputScreenPreviewContentState(isSearchSelected = isSearchDefault)
    }

    data class QuickSetup(
        override val title: TextConfig,
        val showSplitOption: Boolean,
        val hideSetDefaultBrowserRow: Boolean,
        val hideAddWidgetRow: Boolean,
        val hideAddressBarRow: Boolean,
        val initialAddressBarPosition: OmnibarType,
        val initialWithAi: Boolean,
    ) : ContentConfig, Stateful<QuickSetupContentState> {
        override fun initialState() = QuickSetupContentState(
            defaultBrowserChecked = false,
            widgetChecked = false,
            addressBarPosition = initialAddressBarPosition,
            withAi = initialWithAi,
        )
    }

    data class DownloadReason(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig, Stateful<DownloadReasonContentState> {
        override fun initialState() = DownloadReasonContentState(selection = null)
    }

    data class PreferenceSelector(
        override val title: TextConfig,
        val rows: List<Row>,
    ) : ContentConfig, Stateful<PreferenceSelectorContentState> {

        data class Row(
            val preference: OnboardingPreference,
            @DrawableRes val iconRes: Int,
            val primaryText: TextConfig,
            val secondaryText: TextConfig,
            val initiallyEnabled: Boolean,
        )

        override fun initialState() = PreferenceSelectorContentState(rows.associate { it.preference to it.initiallyEnabled })
    }

    data class SingleChoice(
        override val title: TextConfig,
        val body: TextConfig,
        val rows: List<Option>,
    ) : ContentConfig, Stateful<SingleChoiceContentState> {

        init {
            require(rows.isNotEmpty()) { "A single-choice screen needs at least one row" }
        }

        override fun initialState() = SingleChoiceContentState(selected = rows.first())
    }

    data class DuckAiState(
        override val title: TextConfig,
        val body: TextConfig,
        val options: List<Option>,
    ) : ContentConfig {

        init {
            require(options.isNotEmpty()) { "A Duck.ai state screen needs at least one option" }
        }
    }

    data class TogglePosition(
        override val title: TextConfig,
        @field:DrawableRes val pictogramLightRes: Int,
        @field:DrawableRes val pictogramDarkRes: Int,
        val pictogramCaption: TextConfig,
        val options: List<Option>,
    ) : ContentConfig {

        init {
            require(options.isNotEmpty()) { "A toggle position screen needs at least one option" }
        }
    }

    data class ImportPasswords(
        override val title: TextConfig,
        val body: TextConfig,
    ) : ContentConfig

    data class ImportComplete(
        override val title: TextConfig,
        val parsingTitle: TextConfig,
        val parsingBody: TextConfig,
        val failedTitle: TextConfig,
        val failedRow: TextConfig,
    ) : ContentConfig, Stateful<ImportCompleteContentState> {
        override fun initialState(): ImportCompleteContentState = ImportCompleteContentState.Parsing
    }
}

/**
 * The outcome card is entered as soon as the import web flow returns, before the imported credentials have
 * been counted, so its content is state rather than config.
 */
sealed interface ImportCompleteContentState {
    data object Parsing : ImportCompleteContentState
    data class Finished(val imported: Int, val skipped: Int) : ImportCompleteContentState

    /** The import returned successfully but never reported counts, so there is no outcome to show. */
    data object Failed : ImportCompleteContentState
}

data class AddressBarContentState(val position: OmnibarType)

data class InputScreenContentState(val withAi: Boolean)

data class InputScreenPreviewContentState(val isSearchSelected: Boolean)

data class QuickSetupContentState(
    val defaultBrowserChecked: Boolean,
    val widgetChecked: Boolean,
    val addressBarPosition: OmnibarType,
    val withAi: Boolean,
)

data class DownloadReasonContentState(val selection: DownloadReasonSelection?)

data class PreferenceSelectorContentState(val enabled: Map<OnboardingPreference, Boolean>)

data class SingleChoiceContentState(val selected: Option)

enum class DownloadReasonSelection {
    SEARCH,
    AI_CHAT,
    NO_AI,
    BLOCK_ADS,
}
