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

import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DefaultRoleBrowserDialog
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
    private val reactivateUsersToggles: ReactivateUsersToggles,
    private val userBrowserProperties: UserBrowserProperties,
    private val defaultBrowserDetector: DefaultBrowserDetector,
    private val defaultRoleBrowserDialog: DefaultRoleBrowserDialog,
    private val dispatchers: DispatcherProvider,
) : DaxPrompts {

    override suspend fun evaluate(): ActionType {
        return if (isEnabled() && isEligible() && shouldShowBrowserComparisonPrompt()) {
            ActionType.SHOW_VARIANT_BROWSER_COMPARISON
        } else {
            ActionType.NONE
        }
    }

    private suspend fun isEnabled(): Boolean {
        return withContext(dispatchers.io()) {
            reactivateUsersToggles.self().isEnabled() && reactivateUsersToggles.browserComparisonPrompt().isEnabled()
        }
    }

    private suspend fun isEligible(): Boolean {
        return withContext(dispatchers.io()) {
            if (userBrowserProperties.daysSinceInstalled() < EXISTING_USER_DAY_COUNT_THRESHOLD) {
                return@withContext false
            }

            val sevenDaysAgo = Date(Date().time - EXISTING_USER_DAYS_INACTIVE_MILLIS)
            if (userBrowserProperties.daysUsedSince(sevenDaysAgo) > 0L) {
                return@withContext false
            }

            if (defaultBrowserDetector.isDefaultBrowser()) {
                return@withContext false
            }

            defaultRoleBrowserDialog.shouldShowDialog()
        }
    }

    private suspend fun shouldShowBrowserComparisonPrompt(): Boolean = withContext(dispatchers.io()) {
        daxPromptsRepository.getDaxPromptsShowBrowserComparison()
    }
}
