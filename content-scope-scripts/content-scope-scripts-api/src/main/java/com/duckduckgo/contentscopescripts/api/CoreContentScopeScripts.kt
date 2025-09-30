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

package com.duckduckgo.contentscopescripts.api

import com.duckduckgo.feature.toggles.api.Toggle

interface CoreContentScopeScripts {
    fun getScript(
        isDesktopMode: Boolean?,
        activeExperiments: List<Toggle>,
    ): String

    fun isEnabled(): Boolean

    val secret: String
    val javascriptInterface: String
    val callbackName: String
}
