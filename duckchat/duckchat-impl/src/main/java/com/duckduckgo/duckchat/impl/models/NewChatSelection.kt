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

/** The model and reasoning a new chat is sent with. */
internal data class NewChatSelection(
    val modelId: String?,
    val available: List<AvailableReasoningMode>,
    val mode: ReasoningMode?,
) {
    val effort: ReasoningEffort? get() = ReasoningResolver.effortFor(mode, available)
}

/**
 * The tab's pending pick where it is still usable, otherwise the global selection. A pick that is gone from
 * the list or no longer accessible is ignored rather than cleared, as it may come back on the next fetch.
 */
internal fun ModelState.newChatSelection(pickedModelId: String?, pickedReasoningMode: String?): NewChatSelection {
    val picked = models.firstOrNull { it.id == pickedModelId && it.isAccessible }
    val available = if (picked == null) {
        availableReasoningModes
    } else {
        ReasoningResolver.availableModes(
            supported = picked.supportedReasoningEfforts,
            effortAccess = picked.reasoningEffortAccess,
            isEligible = isSubscriptionEligible,
        )
    }
    val pickedMode = ReasoningMode.from(pickedReasoningMode)?.takeIf { mode -> available.any { it.mode == mode && it.isAccessible } }
    return NewChatSelection(
        modelId = picked?.id ?: selectedModelId,
        available = available,
        mode = ReasoningResolver.resolveMode(pickedMode ?: selectedReasoningMode, available),
    )
}

internal fun ModelState.newChatSelection(state: NativeInputState): NewChatSelection =
    newChatSelection(state.newChatModelId, state.newChatReasoningMode)
