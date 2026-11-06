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

package com.duckduckgo.duckchat.impl.pixel

/** How a Duck.ai input session ended. */
enum class InputOutcome(val value: String) {
    PROMPT_SUBMITTED("prompt_submitted"),
    VOICE_STARTED("voice_started"),

    /** The input closed, collapsed or switched to Search without a prompt or a voice chat. */
    ABANDONED("abandoned"),

    /** A suggested prompt or quick action was picked in the contextual sheet. */
    SUGGESTED_PROMPT("suggested_prompt"),
}

/** What the user did to send a prompt. Enter only counts when the keyboard really submits; a newline is not a submit. */
enum class InputSubmitMethod(val value: String) {
    BUTTON("button"),
    ENTER("enter"),

    /** Quick actions, suggested prompts, Paste and Go, dictation and anything else that is not the button or Enter. */
    OTHER("other"),
}
