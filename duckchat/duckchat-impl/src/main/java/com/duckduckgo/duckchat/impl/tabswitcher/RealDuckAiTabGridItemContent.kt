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

package com.duckduckgo.duckchat.impl.tabswitcher

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.duckchat.api.tabswitcher.DuckAiTabGridItemContent

sealed class RealDuckAiTabGridItemContent : DuckAiTabGridItemContent {
    /** [title] is null for a tab that has no chat yet. */
    data class Empty(val title: String?) : RealDuckAiTabGridItemContent()

    data class Chat(val title: String, val snippet: String) : RealDuckAiTabGridItemContent()

    /** [mode] selects the store [fileRef] is read from when the thumbnail loads. */
    data class ImageChat(val title: String, val fileRef: String, val mode: BrowserMode) : RealDuckAiTabGridItemContent()

    data object ActiveVoice : RealDuckAiTabGridItemContent()
}
