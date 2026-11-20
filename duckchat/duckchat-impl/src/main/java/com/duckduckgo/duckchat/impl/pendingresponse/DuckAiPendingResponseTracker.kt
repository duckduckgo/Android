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

package com.duckduckgo.duckchat.impl.pendingresponse

import com.duckduckgo.browsermode.api.BrowserMode

/**
 * Tracks which Duck.ai responses are still generating and whether the user is looking at them, awaits the ones the user left,
 * and keeps the app alive while any of them is generating or awaited. Tracking is in memory only, so it ends with the process.
 */
interface DuckAiPendingResponseTracker {
    fun onNativePromptSubmitted(tabId: String, browserMode: BrowserMode)
    fun onResponseState(tabId: String, status: String, browserMode: BrowserMode)
    fun onStopTapped(tabId: String)
}
