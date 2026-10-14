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

package com.duckduckgo.duckchat.impl.nativeinput.footer.usagelimits

import com.duckduckgo.duckchat.impl.models.AIChatModel
import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterDraft
import javax.inject.Inject

/** The page's CTA turned into something native can run, or null when there is nothing runnable. */
sealed class ResolvedUsageCta {
    /** [model] is the first candidate the user can switch to; [candidateIds] are all of them, for retiring the card on a manual pick. */
    data class SwitchModel(
        val model: AIChatModel,
        val candidateIds: List<String>,
    ) : ResolvedUsageCta()

    data class StartUsingWeeklyLimit(val putEntries: List<UsageCtaPutEntry>) : ResolvedUsageCta()

    data class Subscribe(val freeTrialEligible: Boolean) : ResolvedUsageCta()
}

/**
 * Turns the page's CTA into an action native can run.
 *
 * For a model switch the page sends two lists of target models: one for the model the page is on and a table with a list per model the native picker might be on.
 * We use the table entry for the picker's current model when there is one, else the page's list. Only accessible models that can support the current draft qualify.
 * If nothing qualifies there is no CTA button.
 */
class UsageLimitCtaResolver @Inject constructor() {

    fun resolve(
        cta: UsageCta?,
        modelState: ModelState,
        draft: NativeInputFooterDraft,
        freeTrialEligible: Boolean,
    ): ResolvedUsageCta? {
        if (cta == null) return null
        return when (cta.id) {
            UsageCtaId.SWITCH_TO_CHEAPER, UsageCtaId.SWITCH_TO_FREE -> resolveSwitch(cta, modelState, draft)
            UsageCtaId.BYPASS_WEEKLY -> ResolvedUsageCta.StartUsingWeeklyLimit(cta.putEntries)
            UsageCtaId.SUBSCRIBE -> if (modelState.isSubscriptionEligible) ResolvedUsageCta.Subscribe(freeTrialEligible) else null
        }
    }

    fun candidateIds(
        cta: UsageCta,
        selectedModelId: String?,
    ): List<String> {
        val targets = selectedModelId?.let { cta.byModelId[it] }
        val ids = if (targets != null) {
            listOfNotNull(targets.modelId) + targets.modelIds
        } else {
            listOfNotNull(cta.modelId) + cta.modelIds
        }
        return ids.distinct().filterNot { it == selectedModelId }
    }

    private fun resolveSwitch(
        cta: UsageCta,
        modelState: ModelState,
        draft: NativeInputFooterDraft,
    ): ResolvedUsageCta? {
        val candidateIds = candidateIds(cta, modelState.selectedModelId)
        val model = candidateIds
            .mapNotNull { id -> modelState.models.firstOrNull { it.id == id } }
            .firstOrNull { it.isAccessible && it.supports(draft) }
            ?: return null
        return ResolvedUsageCta.SwitchModel(model = model, candidateIds = candidateIds)
    }

    private fun AIChatModel.supports(draft: NativeInputFooterDraft): Boolean {
        if (draft.hasImages && !supportsImageUpload) return false
        if (draft.fileMimeTypes.any { it !in supportedFileTypes }) return false
        val tool = draft.selectedTool?.let { Tool.from(it) }
        return tool == null || tool in supportedTools
    }
}
