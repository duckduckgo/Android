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

import com.duckduckgo.duckchat.store.impl.DuckAiChat

enum class ReasoningEffort(val rawValue: String) {
    NONE("none"),
    MINIMAL("minimal"),
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high"),
    ;

    companion object {
        fun from(raw: String?): ReasoningEffort? = entries.firstOrNull { it.rawValue == raw }
    }
}

enum class ReasoningMode(
    val rawValue: String,
    internal val candidateEfforts: List<ReasoningEffort>,
) {
    FAST("fast", listOf(ReasoningEffort.NONE, ReasoningEffort.MINIMAL)),
    REASONING("reasoning", listOf(ReasoningEffort.LOW)),
    EXTENDED_REASONING("extended_reasoning", listOf(ReasoningEffort.HIGH, ReasoningEffort.MEDIUM)),
    ;

    companion object {
        fun from(raw: String?): ReasoningMode? = entries.firstOrNull { it.rawValue == raw }
    }
}

data class AvailableReasoningMode(
    val mode: ReasoningMode,
    val effort: ReasoningEffort,
    val access: ReasoningEffortAccess? = null,
) {
    val isAccessible: Boolean get() = access == null || access.isAccessible
}

data class ReasoningEffortAccess(
    val effort: ReasoningEffort,
    val accessTier: List<String>,
    val isAccessible: Boolean,
) {
    val requiredTier: UserTier?
        get() = lowestRequiredTier(accessTier, isAccessible)
}

object ReasoningResolver {

    /**
     * For each [ReasoningMode], picks the first candidate effort that is supported and accessible.
     * If no candidate is accessible, falls back to the first supported one so the row still renders
     * for upsell routing. If no candidate is supported, the mode is omitted.
     *
     * When [effortAccess] does not contain an entry for a chosen effort, the mode is treated as accessible.
     */
    fun availableModes(
        supported: List<ReasoningEffort>,
        effortAccess: List<ReasoningEffortAccess> = emptyList(),
    ): List<AvailableReasoningMode> {
        val accessByEffort = effortAccess.associateBy { it.effort }
        return ReasoningMode.entries.mapNotNull { mode ->
            val supportedCandidates = mode.candidateEfforts.filter { it in supported }
            if (supportedCandidates.isEmpty()) return@mapNotNull null
            val chosen = supportedCandidates.firstOrNull { effort ->
                val access = accessByEffort[effort]
                access == null || access.isAccessible
            } ?: supportedCandidates.first()
            AvailableReasoningMode(mode, chosen, accessByEffort[chosen])
        }
    }

    fun resolveMode(
        persisted: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningMode? {
        val accessible = available.filter { it.isAccessible }
        return when {
            accessible.isEmpty() -> null
            persisted != null && accessible.any { it.mode == persisted } -> persisted
            else -> accessible.first().mode
        }
    }

    fun effortFor(
        mode: ReasoningMode?,
        available: List<AvailableReasoningMode>,
    ): ReasoningEffort? = available.firstOrNull { it.mode == mode }?.effort

    /** Reasoning data for [chat]; `null` if its model isn't in [modelState] (callers fall back to global). */
    internal fun forChat(chat: DuckAiChat, modelState: ModelState): ChatReasoningResolution? {
        val chatModel = modelState.models.firstOrNull { it.id == chat.model } ?: return null
        val available = availableModes(
            supported = chatModel.supportedReasoningEfforts,
            effortAccess = chatModel.reasoningEffortAccess,
        )
        val mode = modelState.chatScopedReasoningMode ?: ReasoningMode.from(chat.reasoningMode)
        return ChatReasoningResolution(available = available, mode = mode)
    }
}

/** Resolved reasoning data for an existing chat: the rows that apply and the mode to use. */
internal data class ChatReasoningResolution(
    val available: List<AvailableReasoningMode>,
    val mode: ReasoningMode?,
)
