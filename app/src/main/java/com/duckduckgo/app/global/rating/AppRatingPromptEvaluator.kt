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

package com.duckduckgo.app.global.rating

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.promptscoordinator.api.ModalEvaluator
import com.duckduckgo.promptscoordinator.api.ModalTrigger
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Brings the app enjoyment / rating prompt under the prompts coordinator so it stops competing with
 * the other modals. Gated by [AppRatingPromptModalFeature]: while that flag is off this evaluator
 * always skips and [AppEnjoymentAppCreationObserver] retains the old app-start behaviour.
 *
 * Uses [ModalTrigger.NTP_RENDER] so the prompt is only offered while the browser is on a clean New
 * Tab Page. The dialog is hosted by BrowserActivity, and an [ModalTrigger.APP_RESUME] pass can land
 * on another screen, where the dialog is silently dropped after the coordinator has already recorded
 * the modal as shown.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class AppRatingPromptEvaluator @Inject constructor(
    private val promptTypeDecider: PromptTypeDecider,
    private val appEnjoymentPromptEmitter: AppEnjoymentPromptEmitter,
    private val appRatingPromptModalFeature: AppRatingPromptModalFeature,
    private val dispatchers: DispatcherProvider,
) : ModalEvaluator {

    override val priority: Int = PRIORITY
    override val evaluatorId: String = "app_rating_prompt"
    override val trigger: ModalTrigger = ModalTrigger.NTP_RENDER

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (!appRatingPromptModalFeature.self().isEnabled()) {
            logcat { "AppRatingPromptEvaluator: skipped, feature disabled so the app-start observer owns the prompt" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        val promptType = promptTypeDecider.determineInitialPromptType()
        if (promptType == AppEnjoymentPromptOptions.ShowNothing) {
            logcat { "AppRatingPromptEvaluator: skipped, decider selected no prompt" }
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }

        delay(MODAL_DISPLAY_DELAY)
        withContext(dispatchers.main()) {
            appEnjoymentPromptEmitter.promptType.value = promptType
        }
        logcat { "AppRatingPromptEvaluator: emitted $promptType" }
        ModalEvaluator.EvaluationResult.ModalShown
    }

    companion object {
        private const val PRIORITY = 6
        private const val MODAL_DISPLAY_DELAY = 250L
    }
}
