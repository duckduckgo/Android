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

package com.duckduckgo.daxprompts.impl

import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.daxprompts.api.DaxPrompts
import com.duckduckgo.daxprompts.api.DaxPrompts.ActionType
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.CONTROL
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.VARIANT_1
import com.duckduckgo.daxprompts.impl.ReactivateUsersToggles.Cohorts.VARIANT_2
import com.duckduckgo.daxprompts.impl.repository.DaxPromptsRepository
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.withContext

private const val EXISTING_USER_DAY_COUNT_THRESHOLD = 28
private const val EXISTING_USER_DAYS_INACTIVE_MILLIS = 7 * 24 * 60 * 60 * 1000 // 7 days

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DaxPrompts::class)
class RealDaxPrompts @Inject constructor(
    private val daxPromptsRepository: DaxPromptsRepository,
    private val reactivateUsersToggles: ReactivateUsersToggles,
    private val userBrowserProperties: UserBrowserProperties,
    private val dispatchers: DispatcherProvider,
) : DaxPrompts {

    override suspend fun evaluate(): ActionType {
        return withContext(dispatchers.io()) {
            if (!reactivateUsersToggles.reactivateUsersExperimentMay25().isEnrolled() && !isEligible()) {
                return@withContext ActionType.NONE
            }

            if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnabled(CONTROL)) {
                ActionType.SHOW_CONTROL
            } else if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnabled(VARIANT_1)) {
                if (shouldShowDuckPlayerPrompt()) ActionType.SHOW_VARIANT_1 else ActionType.NONE
            } else if (reactivateUsersToggles.reactivateUsersExperimentMay25().isEnabled(VARIANT_2)) {
                if (shouldShowBrowserComparisonPrompt()) ActionType.SHOW_VARIANT_2 else ActionType.NONE
            } else {
                ActionType.NONE
            }
        }
    }

    private suspend fun isEligible(): Boolean {
        return withContext(dispatchers.io()) {
            val sevenDaysAgo = Date(Date().time - EXISTING_USER_DAYS_INACTIVE_MILLIS)
            userBrowserProperties.daysSinceInstalled() >= EXISTING_USER_DAY_COUNT_THRESHOLD && userBrowserProperties.daysUsedSince(sevenDaysAgo) == 0L
        }
    }

    private suspend fun shouldShowDuckPlayerPrompt(): Boolean = withContext(dispatchers.io()) {
        daxPromptsRepository.getDaxPromptsShowDuckPlayer()
    }

    private suspend fun shouldShowBrowserComparisonPrompt(): Boolean = withContext(dispatchers.io()) {
        // TODO ANA: add here a check that the browser is not default already
        daxPromptsRepository.getDaxPromptsShowBrowserComparison()
    }
}
