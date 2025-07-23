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

import com.duckduckgo.app.browser.defaultbrowsing.prompts.DefaultBrowserPromptsFeatureToggles.AdditionalPromptsCohortName
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.CONVERTED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.NOT_ENROLLED
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_1
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STAGE_2
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsDataStore.ExperimentStage.STOPPED
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

data class DefaultBrowserPromptsExperimentStageAction(
    val showMessageDialog: Boolean,
    val showSetAsDefaultPopupMenuItem: Boolean,
    val highlightPopupMenu: Boolean,
) {
    companion object {
        val disableAll = DefaultBrowserPromptsExperimentStageAction(
            showMessageDialog = false,
            showSetAsDefaultPopupMenuItem = false,
            highlightPopupMenu = false,
        )
    }
}

interface DefaultBrowserPromptsExperimentStageEvaluator {
    val targetCohort: AdditionalPromptsCohortName
    suspend fun evaluate(newStage: ExperimentStage): DefaultBrowserPromptsExperimentStageAction
}

@ContributesBinding(AppScope::class)
class DefaultBrowserPromptsExperimentStageEvaluatorImpl @Inject constructor() : DefaultBrowserPromptsExperimentStageEvaluator {

    override val targetCohort = AdditionalPromptsCohortName.VARIANT_3

    override suspend fun evaluate(newStage: ExperimentStage): DefaultBrowserPromptsExperimentStageAction =
        when (newStage) {
            NOT_ENROLLED -> DefaultBrowserPromptsExperimentStageAction.disableAll

            ENROLLED -> DefaultBrowserPromptsExperimentStageAction.disableAll

            STAGE_1 -> DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = true,
                highlightPopupMenu = true,
            )

            STAGE_2 -> DefaultBrowserPromptsExperimentStageAction(
                showMessageDialog = true,
                showSetAsDefaultPopupMenuItem = true,
                highlightPopupMenu = true,
            )

            STOPPED -> DefaultBrowserPromptsExperimentStageAction.disableAll

            CONVERTED -> DefaultBrowserPromptsExperimentStageAction.disableAll
        }
}
