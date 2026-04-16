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

package com.duckduckgo.app.browser.menu

import kotlinx.coroutines.flow.Flow

/**
 * Plugin that contributes a highlight source for the browser menu icon blue dot.
 *
 * Implementations are collected via [PluginPoint] and aggregated by [BrowserMenuHighlight].
 * Each plugin declares which view modes it applies to via the `showIn*` flags.
 */
interface BrowserMenuHighlightPlugin {
    /**
     * Flow that emits `true` when this source wants the blue dot shown.
     */
    fun isHighlighted(): Flow<Boolean>

    /**
     * The set of view modes this highlight applies to.
     * Defaults to all modes.
     */
    val compatibleModes: Set<BrowserViewMode>
        get() = BrowserViewMode.entries.toSet()
}
