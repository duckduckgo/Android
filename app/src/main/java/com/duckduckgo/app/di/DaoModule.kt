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
    fun providesHttpsWhitelistDao(database: AppDatabase) = database.httpsWhitelistedDao()

    @Provides
    fun provideHttpsBloomFilterSpecDao(database: AppDatabase) = database.httpsBloomFilterSpecDao()

    @Provides
    fun providesDisconnectTrackDao(database: AppDatabase) = database.trackerDataDao()

    @Provides
    fun providesNetworkLeaderboardDao(database: AppDatabase) = database.networkLeaderboardDao()

    @Provides
    fun providesBookmarksDao(database: AppDatabase) = database.bookmarksDao()

    @Provides
    fun providesTabsDao(database: AppDatabase) = database.tabsDao()

    @Provides
    fun appConfigurationDao(database: AppDatabase) = database.appConfigurationDao()

    @Provides
    fun networkEntityDao(database: AppDatabase) = database.networkEntityDao()

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
}