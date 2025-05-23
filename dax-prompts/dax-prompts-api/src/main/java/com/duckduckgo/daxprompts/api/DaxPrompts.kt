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

package com.duckduckgo.daxprompts.api

/**
 * DaxPrompts interface provides a set of methods for controlling the display for various Dax Prompts in the app.
 */
interface DaxPrompts {

    suspend fun evaluate(): ActionType

    enum class ActionType {
        SHOW_CONTROL,
        SHOW_VARIANT_DUCKPLAYER,
        SHOW_VARIANT_BROWSER_COMPARISON,
        NONE,
    }
}
