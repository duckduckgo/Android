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

/**
 * Where the model and effort of a new chat's first prompt came from, as reported in `model_source`.
 * [USER_DEFAULT] and [USER_DEFAULT_UNAVAILABLE] match the pixel definition but aren't sent until Duck.ai
 * has a user default setting.
 */
enum class ModelSource(val pixelValue: String) {
    DEFAULT("default"),
    USER_DEFAULT("user_default"),
    PICKED("picked"),
    ONBOARDING("onboarding"),
    FALLBACK("fallback"),
    USER_DEFAULT_UNAVAILABLE("user_default_unavailable"),
}

/**
 * Classifies what a new chat actually sent against where it started: the onboarding model on its default
 * effort when [onboardingModelId] is set and still usable, else the [global] selection. Only the sent
 * values count: a model or effort that differs from the starting point is [ModelSource.PICKED], and a pick
 * changed back to the starting point is not.
 *
 * Null when `resetModelForNewChats` is off or no model is sent, so the param is omitted.
 */
internal fun modelSourceFor(
    isResetEnabled: Boolean,
    global: ModelState,
    onboardingModelId: String?,
    sentModelId: String?,
    sentReasoningEffort: String?,
): ModelSource? {
    if (!isResetEnabled || sentModelId == null) return null
    val onboardingModel = global.models.firstOrNull { it.id == onboardingModelId && it.isAccessible }
    val (startModelId, startEffort, startSource) = if (onboardingModel != null) {
        val available = ReasoningResolver.availableModes(
            onboardingModel.supportedReasoningEfforts,
            onboardingModel.reasoningEffortAccess,
            global.isSubscriptionEligible,
        )
        val effort = ReasoningResolver.effortFor(NewChatDefaults.reasoningMode(null, available), available)
        Triple(onboardingModel.id, effort?.rawValue, ModelSource.ONBOARDING)
    } else {
        val effort = ReasoningResolver.effortFor(global.selectedReasoningMode, global.availableReasoningModes)
        Triple(global.selectedModelId, effort?.rawValue, global.globalSource())
    }
    return if (sentModelId != startModelId || sentReasoningEffort != startEffort) ModelSource.PICKED else startSource
}

private fun ModelState.globalSource(): ModelSource {
    val isTierDefault = models.isNotEmpty() &&
        selectedModelId == NewChatDefaults.tierDefaultModelId(userTier) &&
        (availableReasoningModes.isEmpty() || selectedReasoningMode == ReasoningMode.FAST)
    return if (isTierDefault) ModelSource.DEFAULT else ModelSource.FALLBACK
}
