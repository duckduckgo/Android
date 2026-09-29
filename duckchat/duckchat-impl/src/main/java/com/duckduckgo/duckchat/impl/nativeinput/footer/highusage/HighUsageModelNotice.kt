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

package com.duckduckgo.duckchat.impl.nativeinput.footer.highusage

import com.duckduckgo.duckchat.impl.models.ModelState
import com.duckduckgo.duckchat.impl.nativeinput.footer.NativeInputFooterContext
import javax.inject.Inject

data class HighUsageModelNotice(
    val modelId: String,
    val modelShortName: String,
)

class HighUsageModelNoticeResolver @Inject constructor() {
    fun resolve(
        modelState: ModelState,
        footerContext: NativeInputFooterContext,
        dismissedModelIds: Set<String>,
    ): HighUsageModelNotice? {
        if (!footerContext.isDuckAiSelected || footerContext.isEditing || footerContext.isFireMode || !footerContext.isInputFocused) return null
        val modelId = modelState.selectedModelId?.takeIf { it in HIGH_USAGE_MODEL_IDS } ?: return null
        if (modelId in dismissedModelIds) return null

        val modelShortName = modelState.selectedModelShortName ?: return null
        return HighUsageModelNotice(
            modelId = modelId,
            modelShortName = modelShortName,
        )
    }

    private companion object {
        val HIGH_USAGE_MODEL_IDS = setOf("claude-opus-4-8")
    }
}
