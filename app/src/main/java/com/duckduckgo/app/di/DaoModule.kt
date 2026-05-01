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

import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.browser.cookies.db.AuthCookiesAllowedDomainsDao
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.events.db.UserEventsDao
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.onboarding.store.UserStageDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.PrivacyProtectionCountDao
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.tabs.db.TabPageContextDao
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.app.trackerdetection.db.TdsTrackerDao
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.usage.app.AppDaysUsedDao
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import dagger.Module
import dagger.Provides

@Module
object DaoModule {

    @Provides
    fun providesTdsTrackDao(database: AppDatabase): TdsTrackerDao = database.tdsTrackerDao()

    @Provides
    fun providesTdsEntityDao(database: AppDatabase): TdsEntityDao = database.tdsEntityDao()

    @Provides
    fun providesTdsDomainEntityDao(database: AppDatabase): TdsDomainEntityDao = database.tdsDomainEntityDao()

    @Provides
    fun providesTdsCnameEntityDao(database: AppDatabase): TdsCnameEntityDao = database.tdsCnameEntityDao()

    @Provides
    fun providesUserAllowList(database: AppDatabase): UserAllowListDao = database.userAllowListDao()

    @Provides
    fun providesNetworkLeaderboardDao(database: AppDatabase): NetworkLeaderboardDao = database.networkLeaderboardDao()

    @Provides
    fun providesBookmarksDao(database: AppDatabase): BookmarksDao = database.bookmarksDao()

    @Provides
    fun providesFavoritesDao(database: AppDatabase): FavoritesDao = database.favoritesDao()

    @Provides
    fun providesBookmarkFoldersDao(database: AppDatabase): BookmarkFoldersDao = database.bookmarkFoldersDao()

    @Provides
    fun providesTabsDao(database: AppDatabase): TabsDao = database.tabsDao()

    @Provides
    fun providesTabPageContextDao(database: AppDatabase): TabPageContextDao = database.tabPageContextDao()

    @Provides
    fun surveyDao(database: AppDatabase): SurveyDao = database.surveyDao()

    @Provides
    fun dismissedCtaDao(database: AppDatabase): DismissedCtaDao = database.dismissedCtaDao()

    @Provides
    fun searchCountDao(database: AppDatabase): SearchCountDao = database.searchCountDao()

    @Provides
    fun appDaysUsedDao(database: AppDatabase): AppDaysUsedDao = database.appsDaysUsedDao()

    @Provides
    fun notification(database: AppDatabase): NotificationDao = database.notificationDao()

    @Provides
    fun privacyProtectionCounts(database: AppDatabase): PrivacyProtectionCountDao = database.privacyProtectionCountsDao()

    @Provides
    fun tdsDao(database: AppDatabase): TdsMetadataDao = database.tdsDao()

    @Provides
    fun userStageDao(database: AppDatabase): UserStageDao = database.userStageDao()

    @Provides
    fun fireproofWebsiteDao(database: AppDatabase): FireproofWebsiteDao = database.fireproofWebsiteDao()

    @Provides
    fun userEventsDao(database: AppDatabase): UserEventsDao = database.userEventsDao()

    @Provides
    fun locationPermissionsDao(database: AppDatabase): LocationPermissionsDao = database.locationPermissionsDao()

    @Provides
    fun webTrackersBlockedDao(database: AppDatabase): WebTrackersBlockedDao = database.webTrackersBlockedDao()

    @Provides
    fun allowedDomainsDao(database: AppDatabase): AuthCookiesAllowedDomainsDao = database.authCookiesAllowedDomainsDao()

    @Provides
    fun syncEntitiesDao(database: AppDatabase): SavedSitesEntitiesDao = database.syncEntitiesDao()

    @Provides
    fun syncRelationsDao(database: AppDatabase): SavedSitesRelationsDao = database.syncRelationsDao()
}
