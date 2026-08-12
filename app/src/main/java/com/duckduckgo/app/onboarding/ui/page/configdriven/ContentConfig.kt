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

import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.cta.ui.DaxBubbleCta.DaxDialogIntroOption
import com.duckduckgo.app.onboarding.ui.page.ComparisonChartConfig

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

enum class DownloadReasonSelection {
    SEARCH,
    AI_CHAT,
    NO_AI,
    BLOCK_ADS,
}
