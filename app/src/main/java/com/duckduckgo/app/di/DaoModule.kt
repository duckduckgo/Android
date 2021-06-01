/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.di

import com.duckduckgo.app.global.db.AppDatabase
import dagger.Module
import dagger.Provides

@Module
class DaoModule {

    @Provides
    fun providesHttpsFalsePositivesDao(database: AppDatabase) = database.httpsFalsePositivesDao()

    @Provides
    fun provideHttpsBloomFilterSpecDao(database: AppDatabase) = database.httpsBloomFilterSpecDao()

    @Provides
    fun providesTdsTrackDao(database: AppDatabase) = database.tdsTrackerDao()

    @Provides
    fun providesTdsEntityDao(database: AppDatabase) = database.tdsEntityDao()

    @Provides
    fun providesTdsDomainEntityDao(database: AppDatabase) = database.tdsDomainEntityDao()

    @Provides
    fun providesTemporaryTrackingWhitelist(database: AppDatabase) = database.temporaryTrackingWhitelistDao()

    @Provides
    fun providesUserWhitelist(database: AppDatabase) = database.userWhitelistDao()

    @Provides
    fun providesNetworkLeaderboardDao(database: AppDatabase) = database.networkLeaderboardDao()

    @Provides
    fun providesBookmarksDao(database: AppDatabase) = database.bookmarksDao()

    @Provides
    fun providesFavoritesDao(database: AppDatabase) = database.favoritesDao()

    @Provides
    fun providesTabsDao(database: AppDatabase) = database.tabsDao()

    @Provides
    fun surveyDao(database: AppDatabase) = database.surveyDao()

    @Provides
    fun dismissedCtaDao(database: AppDatabase) = database.dismissedCtaDao()

    @Provides
    fun searchCountDao(database: AppDatabase) = database.searchCountDao()

    @Provides
    fun appDaysUsedDao(database: AppDatabase) = database.appsDaysUsedDao()

    @Provides
    fun notification(database: AppDatabase) = database.notificationDao()

    @Provides
    fun privacyProtectionCounts(database: AppDatabase) = database.privacyProtectionCountsDao()

    @Provides
    fun uncaughtExceptionDao(database: AppDatabase) = database.uncaughtExceptionDao()

    @Provides
    fun tdsDao(database: AppDatabase) = database.tdsDao()

    @Provides
    fun userStageDao(database: AppDatabase) = database.userStageDao()

    @Provides
    fun fireproofWebsiteDao(database: AppDatabase) = database.fireproofWebsiteDao()

    @Provides
    fun userEventsDao(database: AppDatabase) = database.userEventsDao()

    @Provides
    fun locationPermissionsDao(database: AppDatabase) = database.locationPermissionsDao()

    @Provides
    fun allowedDomainsDao(database: AppDatabase) = database.authCookiesAllowedDomainsDao()
}
