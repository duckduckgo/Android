/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing

import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles
import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles.AdditionalPromptsCohorts
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DefaultBrowserPromptsExperimentImpl(
    private val defaultBrowserPromptsFeatureToggles: DefaultBrowserPromptsFeatureToggles,
) {
    private val _showOverflowMenuDot = MutableStateFlow(false)
    private val _showOverflowMenuItem = MutableStateFlow(false)
    val showOverflowMenuDot: StateFlow<Boolean> = _showOverflowMenuDot.asStateFlow()
    val showOverflowMenuItem: StateFlow<Boolean> = _showOverflowMenuItem.asStateFlow()

    init {
        when {
            defaultBrowserPromptsFeatureToggles.additionalPrompts().isEnabled(AdditionalPromptsCohorts.VARIANT_1) -> {
            }
        }
        defaultBrowserPromptsFeatureToggles.additionalPrompts().getCohort()
    }
}
