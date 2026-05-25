/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.pixels.campaign.params

import com.duckduckgo.app.fire.FireproofRepository
import com.duckduckgo.app.firebutton.FireButtonStore
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.daxOnboardingActive
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.usage.app.AppDaysUsedRepository
import com.duckduckgo.browser.api.UserBrowserProperties
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AppOnboardingCompletedAdditionalPixelParamPlugin @Inject constructor(
    private val userStageStore: UserStageStore,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "appOnboardingCompleted",
        "${!userStageStore.daxOnboardingActive()}",
    )
}

@ContributesMultibinding(AppScope::class)
class EmailEnabledAdditionalPixelParamPlugin @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "emailEnabled",
        "${userBrowserProperties.emailEnabled()}",
    )
}

@ContributesMultibinding(AppScope::class)
class WidgetAddedAdditionalPixelParamPlugin @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "widgetAdded",
        "${userBrowserProperties.widgetAdded()}",
    )
}

@ContributesMultibinding(AppScope::class)
class FireProofingUsedAdditionalPixelParamPlugin @Inject constructor(
    private val fireproofRepository: FireproofRepository,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "fireproofingUsed",
        "${fireproofRepository.fireproofWebsites().isNotEmpty()}",
    )
}

@ContributesMultibinding(AppScope::class)
class FrequentUserAdditionalPixelParamPlugin @Inject constructor(
    private val appDaysUsedRepository: AppDaysUsedRepository,
    private val currentTimeProvider: CurrentTimeProvider,
) : AdditionalPixelParamPlugin {
    private val formatter by lazy {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    override suspend fun params(): Pair<String, String> = Pair(
        "frequentUser",
        "${daysSinceLastUse() < 2}",
    )

    private suspend fun daysSinceLastUse(): Long {
        val lastActiveDate = formatter.parse(appDaysUsedRepository.getLastActiveDay())?.time ?: 0
        return TimeUnit.MILLISECONDS.toDays(currentTimeProvider.currentTimeMillis() - lastActiveDate)
    }
}

@ContributesMultibinding(AppScope::class)
class LongTermUserAdditionalPixelParamPlugin @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "longTermUser",
        "${userBrowserProperties.daysSinceInstalled() > 30}",
    )
}

@ContributesMultibinding(AppScope::class)
class SearchUserAdditionalPixelParamPlugin @Inject constructor(
    private val userBrowserProperties: UserBrowserProperties,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "searchUser",
        "${userBrowserProperties.searchCount() > 50}",
    )
}

@ContributesMultibinding(AppScope::class)
class ValidOpenTabsCountAdditionalPixelParamPlugin @Inject constructor(
    private val tabsDao: TabsDao,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "validOpenTabsCount",
        "${tabsDao.tabs().size > 3}",
    )
}

@ContributesMultibinding(AppScope::class)
class FireButtonUsedAdditionalPixelParamPlugin @Inject constructor(
    private val fireButtonStore: FireButtonStore,
) : AdditionalPixelParamPlugin {
    override suspend fun params(): Pair<String, String> = Pair(
        "fireButtonUsed",
        "${fireButtonStore.fireButttonUseCount > 5}",
    )
}
