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

package com.duckduckgo.browsermode.api

import kotlinx.coroutines.flow.StateFlow

/**
 * Source of truth for the user's currently-active [BrowserMode]. The current value is used
 * when creating new tabs and display the correct UI state.
 */
interface BrowserModeStateHolder {
    /**
     * Returns current [BrowserMode].
     */
    val currentMode: StateFlow<BrowserMode>

    /**
     * Sets the active [BrowserMode]. Used when user explicitly changes the mode or automatically
     * when a mode change is required for opening a link.
     */
    fun switchTo(mode: BrowserMode)
}
