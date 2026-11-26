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

/** The model and effort a new chat starts on before checking them against the models endpoint. */
data class NewChatDefault(val modelId: String, val mode: ReasoningMode)

/**
 * Where a new chat starts when `resetModelForNewChats` is on: the models endpoint's `newChatDefault`, with the
 * hardcoded tier default as the fallback when the endpoint sends none or has not loaded.
 */
internal object NewChatDefaults {

    /** The server's default when it names a model, an unknown or missing effort counting as fast; else the tier default on fast. */
    fun preferred(tier: UserTier, remote: RemoteNewChatDefault?): NewChatDefault {
        val modelId = remote?.modelId?.takeUnless { it.isBlank() } ?: return NewChatDefault(tierDefaultModelId(tier), ReasoningMode.FAST)
        val mode = ReasoningEffort.from(remote.reasoningEffort)?.let { ReasoningMode.forEffort(it) } ?: ReasoningMode.FAST
        return NewChatDefault(modelId, mode)
    }

    /** [preferred]'s model, else the tier default, else the first accessible model, skipping any that are missing or locked. */
    fun modelId(tier: UserTier, preferred: NewChatDefault, models: List<AIChatModel>): String? =
        models.firstOrNull { it.id == preferred.modelId && it.isAccessible }?.id
            ?: models.firstOrNull { it.id == tierDefaultModelId(tier) && it.isAccessible }?.id
            ?: models.firstOrNull { it.isAccessible }?.id

    /** [preferred]'s mode only on [preferred]'s own model, since the effort was chosen for it; else as [reasoningMode] with no preference. */
    fun reasoningMode(
        preferred: NewChatDefault,
        selectedModelId: String?,
        available: List<AvailableReasoningMode>,
    ): ReasoningMode? = reasoningMode(preferred.mode.takeIf { selectedModelId == preferred.modelId }, available)

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

    private fun tierDefaultModelId(tier: UserTier): String = when (tier) {
        UserTier.FREE -> FREE_DEFAULT_MODEL_ID
        UserTier.PLUS, UserTier.PRO -> PAID_DEFAULT_MODEL_ID
    }

    private const val FREE_DEFAULT_MODEL_ID = "gpt-6-luna"
    private const val PAID_DEFAULT_MODEL_ID = "gpt-5.6-terra"
}

/**
 * The selection a new chat in a tab sends with: the tab's pick while it is still accessible, else the
 * global selection. A model picked without an effort starts on the new-chat default's effort when it is that
 * default's model, else on fast. Without a pick this is the receiver unchanged.
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
    val mode = pickedMode ?: newChatDefault?.takeIf { it.modelId == pickedModel?.id }?.mode
    return copy(
        selectedModelId = pickedModel?.id ?: selectedModelId,
        selectedModelShortName = pickedModel?.shortName ?: selectedModelShortName,
        selectedReasoningMode = NewChatDefaults.reasoningMode(mode, available),
        availableReasoningModes = available,
    )
}

/** [forNewChat] for a tab with no chat yet; an existing chat keeps the global selection. */
internal fun ModelState.forTab(state: NativeInputState): ModelState =
    if (state.chatId == null) forNewChat(state.newChatModelId, state.newChatReasoningMode) else this
