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
        /** The plain welcome copy keeps its raw line breaks; the sync-restore and custom-AI variants carry markup. */
        val body1AsHtml: Boolean,
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
}

data class AddressBarContentState(val position: OmnibarType)

data class InputScreenContentState(val withAi: Boolean)
