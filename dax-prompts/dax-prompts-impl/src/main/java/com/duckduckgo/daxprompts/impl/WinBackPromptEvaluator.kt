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

package com.duckduckgo.daxprompts.impl

import android.content.Context
import android.content.Intent
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.OnboardingFlowChecker
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonNoParams
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.modalcoordinator.api.ModalEvaluator
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface WinBackPromptEvaluator

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = WinBackPromptEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class WinBackPromptEvaluatorImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val applicationContext: Context,
    private val userBrowserProperties: UserBrowserProperties,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val daxPromptsRepository: DaxPromptsRepository,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val reactivateUsersToggles: ReactivateUsersToggles,
    private val onboardingFlowChecker: OnboardingFlowChecker,
) : ModalEvaluator, WinBackPromptEvaluator {

    override val priority: Int = 1

    override val evaluatorId: String = "win_back_prompt"

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (isEnabled() && isEligible()) {
            val intent = globalActivityStarter
                .startIntent(applicationContext, DaxPromptBrowserComparisonNoParams) ?: return@withContext ModalEvaluator.EvaluationResult.Skipped

            delay(MODAL_DISPLAY_DELAY)
            appCoroutineScope.launch(dispatchers.main()) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                applicationContext.startActivity(intent)
            }

            return@withContext ModalEvaluator.EvaluationResult.ModalShown
        } else {
            return@withContext ModalEvaluator.EvaluationResult.Skipped
        }
    }

    private fun isEnabled() = reactivateUsersToggles.self().isEnabled() && reactivateUsersToggles.defaultBrowserWinBackPrompt().isEnabled()

    private suspend fun isEligible() = onboardingFlowChecker.isOnboardingComplete() &&
        userBrowserProperties.wasEverDefaultBrowser() &&
        !daxPromptsRepository.getDaxPromptsBrowserComparisonShown() &&
        !defaultBrowserDetector.isDefaultBrowser()

    companion object {
        private const val MODAL_DISPLAY_DELAY = 1500L
    }
}
