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

import android.graphics.Bitmap

sealed class RealDuckAiTabGridItemContent {
    data object Empty : RealDuckAiTabGridItemContent()

    data class Chat(val title: String, val snippet: String) : RealDuckAiTabGridItemContent()

    /** A voice chat that is no longer active, previewed by its [transcript]. */
    data class VoiceTranscript(val title: String, val transcript: String) : RealDuckAiTabGridItemContent()

    /** A question has been sent and no reply text exists yet. */
    data class Thinking(val title: String) : RealDuckAiTabGridItemContent()

    /** [thumbnail] is null when the image is missing or can't be decoded. */
    data class ImageChat(val title: String, val fileRef: String, val thumbnail: Bitmap?) : RealDuckAiTabGridItemContent()

    data object ActiveVoice : RealDuckAiTabGridItemContent()

    /** The chat can't be read, so the card shows the host's screenshot of the tab instead. */
    data object Fallback : RealDuckAiTabGridItemContent()
}
