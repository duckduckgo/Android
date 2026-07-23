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
 * Arbitrates the shared New Tab Page prompt surface between the Modal Coordinator and the
 * Remote Messaging Framework (RMF) inline card, enforcing:
 *
 * 1. No overlap: two app-originated prompts are never on screen together. The surface is acquired
 *    with an atomic claim ([tryClaim]) held until the prompt is dismissed ([onClaimDone]) or the
 *    claim turns out not to materialize ([onClaimCancelled]).
 * 2. RMF priority: a non-RMF claim is refused while an RMF message is active (eligible to render).
 * 3. Quiet gaps between prompts, measured from when the previous prompt was dismissed, sized by
 *    the type of the prompt about to show.
 *
 * All gating is behind the `promptsCoordinator` remote kill-switch: when the feature is disabled,
 * [tryClaim] always returns true and the reporting methods are no-ops, restoring each system's
 * independent behaviour (callers that need the fallback distinction can consult [isEnabled]).
 */
interface PromptsCoordinator {

    /**
     * @return true when the `promptsCoordinator` remote feature is enabled and this coordinator
     * owns prompt gating. The Modal Coordinator uses this to fall back to its internal 24h window
     * when disabled.
     */
    suspend fun isEnabled(): Boolean

    /**
     * Atomically claims the shared prompt surface for [type].
     *
     * @return false when the surface is already claimed, an RMF message is active (and [type] is
     * not [PromptType.RMF]), or the quiet gap for [type] since the last prompt has not elapsed.
     * Callers must not show their prompt and should re-evaluate on their next natural trigger.
     * When the claim never materializes into a shown prompt, release it with [onClaimCancelled].
     */
    suspend fun tryClaim(type: PromptType): Boolean

    /**
     * Reports that the claimed prompt was shown and has now left the screen: stamps the gap
     * timestamp and frees the surface. Ignored unless [type] holds the current claim.
     */
    fun onClaimDone(type: PromptType)

    /**
     * Reports that the claim never materialized (nothing was shown): frees the surface without
     * stamping the gap timestamp, so an idle claim cannot delay other prompts. Ignored unless
     * [type] holds the current claim.
     */
    fun onClaimCancelled(type: PromptType)
}
