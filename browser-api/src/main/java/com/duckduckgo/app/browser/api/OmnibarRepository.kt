/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.api

import com.duckduckgo.app.browser.omnibar.OmnibarType

/**
 * Repository exposing split omnibar availability checks (feature flags / user preference).
 */
interface OmnibarRepository {
    /**
     * The currently selected omnibar variant.
     *
     * This property represents which omnibar layout the app should attempt to display
     * (for example, top omnibar, bottom omnibar, or split omnibar).
     * The concrete values are defined by [com.duckduckgo.app.browser.omnibar.OmnibarType]
     * (e.g. SINGLE_TOP, SINGLE_BOTTOM, SPLIT).
     */
    val omnibarType: OmnibarType

    /**
     * True when the split omnibar option is available (both the unified omnibar layout and split omnibar
     * feature flags are enabled).
     */
    val isSplitOmnibarAvailable: Boolean

    /**
     * True when the unified omnibar UI feature flag is enabled.
     *
     * A precondition for the split omnibar to be available.
     */
    val isUnifiedOmnibarLayoutEnabled: Boolean
}
