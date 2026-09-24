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
 * Measures how often app-originated prompts reach the user.
 *
 * Measurement only: reporting has no arbitration effect, never claims the prompt surface and never
 * stamps any cooldown. Use [PromptsCoordinator] or [ModalShownReporter] for that.
 *
 * Both calls are fire-and-forget and safe to make from lifecycle callbacks.
 */
interface PromptExposureReporter {

    /**
     * Records that a prompt has just been shown to the user.
     *
     * @param promptId a bounded constant identifying the prompt, such as a [ModalEvaluator.evaluatorId]
     * or one of the ids registered in the `m_prompt_shown` pixel definition. Never a message,
     * notification or any other dynamic id.
     */
    fun reportPromptShown(promptId: String)

    /**
     * Records that the remote message card has just been shown on the New Tab Page.
     *
     * The card re-renders constantly and can sit on the page for days, so it counts at most once per
     * message per install-week. [messageId] is only used locally to deduplicate and is never sent.
     */
    fun reportNewTabPageCardShown(messageId: String)
}
