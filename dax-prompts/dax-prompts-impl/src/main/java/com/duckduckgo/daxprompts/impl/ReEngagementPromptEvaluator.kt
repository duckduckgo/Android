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
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.daxprompts.api.DaxPromptBrowserComparisonParams
import com.duckduckgo.daxprompts.api.LaunchSource
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
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface ReEngagementPromptEvaluator

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = ModalEvaluator::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = ReEngagementPromptEvaluator::class,
)
@SingleInstanceIn(scope = AppScope::class)
class ReEngagementPromptEvaluatorImpl @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val applicationContext: Context,
    private val userBrowserProperties: UserBrowserProperties,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val daxPromptsRepository: DaxPromptsRepository,
    private val globalActivityStarter: GlobalActivityStarter,
    private val dispatchers: DispatcherProvider,
    private val reactivateUsersToggles: ReactivateUsersToggles,
) : ModalEvaluator, ReEngagementPromptEvaluator {

    override val priority: Int = 2

    override val evaluatorId: String = "re_engagement_prompt"

    override suspend fun evaluate(): ModalEvaluator.EvaluationResult = withContext(dispatchers.io()) {
        if (isEnabled() && isEligible()) {
            val intent = globalActivityStarter
                .startIntent(applicationContext, DaxPromptBrowserComparisonParams(LaunchSource.REACTIVATE_USERS))
                ?: return@withContext ModalEvaluator.EvaluationResult.Skipped

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

    private fun isEnabled() = reactivateUsersToggles.self().isEnabled() && reactivateUsersToggles.browserComparisonPrompt().isEnabled()

    private suspend fun isEligible(): Boolean {
        if (daysSinceInstall() < EXISTING_USER_DAY_COUNT_THRESHOLD) {
            return false
        }

        val sevenDaysAgo = Date(Date().time - EXISTING_USER_DAYS_INACTIVE_MILLIS)
        if (userBrowserProperties.daysUsedSince(sevenDaysAgo) > MAX_DAYS_USED_IN_INACTIVE_WINDOW) {
            return false
        }
        if (defaultBrowserDetector.isDefaultBrowser()) {
            return false
        }
        return !daxPromptsRepository.getDaxPromptsBrowserComparisonShown()
    }

    private fun daysSinceInstall(): Long {
        val firstInstallTime = applicationContext.packageManager
            .getPackageInfo(applicationContext.packageName, 0).firstInstallTime
        return TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - firstInstallTime)
    }

    companion object {
        private const val MODAL_DISPLAY_DELAY = 1500L
        private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28
        private const val EXISTING_USER_DAYS_INACTIVE_MILLIS = 7 * 24 * 60 * 60 * 1000L
        private const val MAX_DAYS_USED_IN_INACTIVE_WINDOW = 1L
    }
}
