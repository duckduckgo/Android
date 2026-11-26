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

package com.duckduckgo.duckchat.impl.models

import com.duckduckgo.duckchat.api.nativeinput.NativeInputState

/**
 * Where a new chat starts when `resetModelForNewChats` is on. Hardcoded until the models endpoint
 * supplies a default per tier.
 */
internal object NewChatDefaults {

    /** The tier's default model, or the first accessible one when the default is missing or locked. */
    fun modelId(tier: UserTier, models: List<AIChatModel>): String? {
        val preferred = tierDefaultModelId(tier)
        return models.firstOrNull { it.id == preferred && it.isAccessible }?.id
            ?: models.firstOrNull { it.isAccessible }?.id
    }

    /** The tier's default model id, unchecked against the models endpoint. */
    fun tierDefaultModelId(tier: UserTier): String = when (tier) {
        UserTier.FREE -> FREE_DEFAULT_MODEL_ID
        UserTier.PLUS, UserTier.PRO -> PAID_DEFAULT_MODEL_ID
    }

    /** [preferred] while [available] offers it, else fast, else the first accessible mode. */
    fun reasoningMode(
        preferred: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningMode? {
        val accessible = available.filter { it.isAccessible }.map { it.mode }
        return preferred?.takeIf { it in accessible }
            ?: ReasoningMode.FAST.takeIf { it in accessible }
            ?: accessible.firstOrNull()
    }

    private const val FREE_DEFAULT_MODEL_ID = "gpt-6-luna"
    private const val PAID_DEFAULT_MODEL_ID = "gpt-5.6-terra"
}

/**
 * The selection a new chat in a tab sends with: the tab's pick while it is still accessible, else the
 * global selection. Without a pick this is the receiver unchanged.
 */
internal fun ModelState.forNewChat(
    pickedModelId: String?,
    pickedReasoningMode: String?,
): ModelState {
    val pickedModel = models.firstOrNull { it.id == pickedModelId && it.isAccessible }
    val pickedMode = ReasoningMode.from(pickedReasoningMode)
    if (pickedModel == null && pickedMode == null) return this
    val available = pickedModel?.let {
        ReasoningResolver.availableModes(it.supportedReasoningEfforts, it.reasoningEffortAccess, isSubscriptionEligible)
    } ?: availableReasoningModes
    return copy(
        selectedModelId = pickedModel?.id ?: selectedModelId,
        selectedModelShortName = pickedModel?.shortName ?: selectedModelShortName,
        selectedReasoningMode = NewChatDefaults.reasoningMode(pickedMode, available),
        availableReasoningModes = available,
    )
}

/** [forNewChat] for a tab with no chat yet; an existing chat keeps the global selection. */
internal fun ModelState.forTab(state: NativeInputState): ModelState =
    if (state.chatId == null) forNewChat(state.newChatModelId, state.newChatReasoningMode) else this
