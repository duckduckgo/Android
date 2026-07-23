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

package com.duckduckgo.promptscoordinator.api

/**
 * The kinds of app-originated prompts arbitrated by the [PromptsCoordinator].
 *
 * @property cooldownMinutes the quiet gap since the last prompt was shown before this type may
 * claim the shared surface again.
 */
enum class PromptType(val cooldownMinutes: Double) {

    /** A Modal Coordinator prompt: bottom sheets and modal activities. */
    MODAL(cooldownMinutes = 24 * 60.0),

    /** An inline card on the New Tab Page, today the RMF card. */
    NTP_CARD(cooldownMinutes = 10.0),
}
