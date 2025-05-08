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
    private val reactivateUsersExperiment: ReactivateUsersExperiment,
    private val userBrowserProperties: UserBrowserProperties,
    private val dispatchers: DispatcherProvider,
) : DaxPrompts {

    override suspend fun evaluate(): ActionType {
        return withContext(dispatchers.io()) {
            if (!isEligible()) {
                return@withContext ActionType.NONE
            }

            if (reactivateUsersExperiment.isControl()) {
                ActionType.SHOW_CONTROL
            } else if (reactivateUsersExperiment.isDuckPlayerPrompt()) {
                if (shouldShowDuckPlayerPrompt()) ActionType.SHOW_VARIANT_1 else ActionType.NONE
            } else if (reactivateUsersExperiment.isBrowserComparisonPrompt()) {
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
        daxPromptsRepository.getDaxPromptsShowBrowserComparison()
    }
}
