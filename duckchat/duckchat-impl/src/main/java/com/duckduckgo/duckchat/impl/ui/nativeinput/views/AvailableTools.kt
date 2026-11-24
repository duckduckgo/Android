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

package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.impl.models.Tool
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModel
import com.duckduckgo.duckchat.impl.nativeinput.EffectiveModelProvider
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelSurface
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The tools the active tab's model supports, read by both the tools menu and the merged attach menu. */
class AvailableTools @Inject constructor(
    nativeInputStateProvider: NativeInputStateProvider,
    modelManager: DuckAiModelManager,
    effectiveModelProvider: EffectiveModelProvider,
) {
    /**
     * Every tool while that model is unknown, which is what the pre-plugin code fell back to, and what keeps a
     * switch from briefly hiding a supported tool.
     */
    val tools: Flow<Set<Tool>> = combine(
        nativeInputStateProvider.state,
        modelManager.modelState,
        effectiveModelProvider.effectiveModel,
    ) { state, modelState, effective ->
        val model = effective.modelFor(state.chatId)?.let { id -> modelState.models.firstOrNull { it.id == id } }
        model?.let { Tool.entries.filterTo(mutableSetOf()) { tool -> it.supportsTool(tool) } } ?: Tool.entries.toSet()
    }
}

/** The model id only when it was resolved for [chatId]; a model resolved for another chat is stale here. */
internal fun EffectiveModel.modelFor(chatId: String?): String? =
    (this as? EffectiveModel.Resolved)?.takeIf { it.chatId == chatId }?.modelId

internal fun DuckChatPixels.fireToolSelected(tool: Tool, surface: DuckChatPixelSurface) {
    when (tool) {
        Tool.IMAGE_GENERATION -> fireImageGenerationSelected(surface)
        Tool.WEB_SEARCH -> fireWebSearchSelected(surface)
    }
}

internal data class ToolMenuItem(
    val iconRes: Int,
    val titleRes: Int,
    val subtitleRes: Int,
    val tool: Tool,
)

/** The tools in menu order, shared by the tools menu and the merged attach menu. */
internal val toolMenuItems = listOf(
    ToolMenuItem(
        iconRes = R.drawable.ic_images_24,
        titleRes = R.string.duckChatOptionsMenuCreateImage,
        subtitleRes = R.string.duckChatOptionsMenuCreateImageSubtitle,
        tool = Tool.IMAGE_GENERATION,
    ),
    ToolMenuItem(
        iconRes = com.duckduckgo.mobile.android.R.drawable.ic_globe_24,
        titleRes = R.string.duckChatOptionsMenuWebSearch,
        subtitleRes = R.string.duckChatOptionsMenuWebSearchSubtitle,
        tool = Tool.WEB_SEARCH,
    ),
)
