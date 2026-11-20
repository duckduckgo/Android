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
package com.duckduckgo.duckchat.impl.responseready

import com.duckduckgo.browsermode.api.RegularMode

interface DuckAiResponseReadyMonitor {

    /**
     * Suspends until a response the user left is ready, the wait stops due to API or internal errors, or it times out.
     *
     * [tabId] is the tab the response was left in.
     * If [chatId] is `null`, the monitor observes the tab's URL and when chat ID appears, hands it to [onChatIdFound].
     *
     * Only compatible with [RegularMode] tabs.
     */
    suspend fun await(
        tabId: String,
        chatId: String?,
        settings: ResponseReadyNotificationsSettings,
        onChatIdFound: suspend (String) -> Unit,
    ): WaitResult
}

sealed interface WaitResult {
    data object Ready : WaitResult
    data object Stopped : WaitResult
    data object TimedOut : WaitResult
}
