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

package com.duckduckgo.duckchat.api

import androidx.fragment.app.Fragment

/**
 * Duck.ai asks the user before its client tools read or change the browser. The prompt belongs to the chat that asked,
 * so the screen hosting that chat has to show it.
 */
interface DuckAiToolPermissionPrompts {
    /**
     * Shows the tool permission prompts of the Duck.ai chat in [tabId] over [fragment], while that tab is the selected one.
     * Call it once, from [Fragment.onCreate] or later. The prompts follow the fragment's view and stop when it is destroyed.
     */
    fun register(
        fragment: Fragment,
        tabId: String,
    )
}
