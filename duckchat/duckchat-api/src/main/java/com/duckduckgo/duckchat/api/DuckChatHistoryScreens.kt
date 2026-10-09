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

import com.duckduckgo.navigation.api.GlobalActivityStarter

/**
 * Use this model to launch the Duck.ai chat history screen.
 *
 * @param source the entry point the screen is opened from
 */
data class DuckChatHistoryParams(
    val source: DuckChatHistorySource,
) : GlobalActivityStarter.ActivityParams

/**
 * Entry points from which the Duck.ai chat history screen can be opened.
 */
enum class DuckChatHistorySource {
    BROWSER_MENU,
    ADDRESS_BAR,
    CONTEXTUAL_CHAT,
    SIDE_BAR,
}
