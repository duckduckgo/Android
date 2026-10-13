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

package com.duckduckgo.app.widget

import android.app.Activity

/**
 * Launches the system pin-widget prompt where supported, otherwise manual add-widget instructions.
 * Pin prompts default to Search & Favorites; the simple and search-only flags select alternatives, with simple taking precedence.
 * The Duck.ai-only flag is currently ignored. A null activity does nothing.
 */
interface AddWidgetLauncher {
    fun launchAddWidget(
        activity: Activity?,
        simpleWidgetPrompt: Boolean = false,
        searchOnlyWidgetPrompt: Boolean = false,
        duckAiOnlyWidgetPrompt: Boolean = false,
    )
}
