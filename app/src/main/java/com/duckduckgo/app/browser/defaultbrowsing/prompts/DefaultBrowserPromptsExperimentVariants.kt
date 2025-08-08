/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.defaultbrowsing.prompts

import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.CONVERTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.NOT_ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_1
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_2
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STAGE_3
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.Stage.STOPPED
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class DefaultBrowserPromptsFlowStageAction(
    val showMessageDialog: Boolean,
    val showSetAsDefaultPopupMenuItem: Boolean,
    val highlightPopupMenu: Boolean,
    val showMessage: Boolean,
) {
    companion object {
        val disableAll = DefaultBrowserPromptsFlowStageAction(
            showMessageDialog = false,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
            showMessage = false,
        )
    }
}

interface DefaultBrowserPromptsFlowStageEvaluator {
    suspend fun evaluate(newStage: Stage): DefaultBrowserPromptsFlowStageAction
}

@ContributesBinding(AppScope::class)
class DefaultBrowserPromptsFlowStageEvaluatorImpl @Inject constructor() : DefaultBrowserPromptsFlowStageEvaluator {

    override suspend fun evaluate(newStage: Stage): DefaultBrowserPromptsFlowStageAction =
        when (newStage) {
            NOT_ENROLLED -> DefaultBrowserPromptsFlowStageAction.disableAll

            ENROLLED -> DefaultBrowserPromptsFlowStageAction.disableAll

            STAGE_1 -> DefaultBrowserPromptsFlowStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = true,
                highlightPopupMenu = true,
                showMessage = false,
            )

            STAGE_2 -> DefaultBrowserPromptsFlowStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = true,
                highlightPopupMenu = true,
                showMessage = false,
            )

            STAGE_3 -> DefaultBrowserPromptsFlowStageAction(
                showMessageDialog = false,
                showSetAsDefaultPopupMenuItem = false,
                highlightPopupMenu = false,
                showMessage = true,
            )

            STOPPED -> DefaultBrowserPromptsFlowStageAction.disableAll

            CONVERTED -> DefaultBrowserPromptsFlowStageAction.disableAll
        }
}
