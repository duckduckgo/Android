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
 * Arbitrates the shared New Tab Page prompt surface between the Modal Coordinator and the Remote
 * Messaging Framework (RMF) inline card, enforcing:
 *
 * 1. No overlap: two app-originated prompts are never on screen together. The surface is acquired
 *    with a claim ([tryClaim]) held until the prompt is done ([onClaimDone]) or the claim turns out
 *    not to materialize ([onClaimCancelled]).
 * 2. Quiet gaps between prompts, measured from when the previous prompt was shown or dismissed,
 *    sized by the type of the prompt about to show.
 *
 * Claims must never be speculative: claim only when the prompt is definitely about to show, so a
 * busy surface always means another prompt is genuinely showing and refusal is a final answer.
 *
 * All gating sits behind the `promptsCoordinator` kill-switch: when disabled, [tryClaim] always
 * returns true and the reports are no-ops (see [isEnabled]).
 */
interface PromptsCoordinator {

    /**
     * @return true when this coordinator owns prompt gating. The Modal Coordinator uses it to fall
     * back to its internal 24h window when disabled.
     */
    suspend fun isEnabled(): Boolean

    /**
     * Claims the shared prompt surface for [type]. Call it only when the prompt is definitely about
     * to show — never to reserve the surface while still deciding.
     *
     * @return false when the surface is held by another prompt or the gap for [type] has not
     * elapsed. Callers must not show their prompt and should re-evaluate on their next natural
     * trigger. A claim whose prompt then fails to show must be released with [onClaimCancelled].
     */
    suspend fun tryClaim(type: PromptType): Boolean

    /**
     * Reports that the claimed prompt reached the user: stamps the gap and frees the surface.
     * Reported once showing is certain rather than on dismissal, so a prompt that never reports
     * leaving the screen cannot strand the claim. Ignored unless [type] holds the claim.
     *
     * Suspends until the release is applied, so reporting and then claiming again cannot have the
     * second claim undone by the first report landing late.
     */
    suspend fun onClaimDone(type: PromptType)

    /**
     * Reports that the claim never became a prompt: frees the surface without stamping the gap.
     * Ignored unless [type] holds the claim.
     */
    suspend fun onClaimCancelled(type: PromptType)
}
