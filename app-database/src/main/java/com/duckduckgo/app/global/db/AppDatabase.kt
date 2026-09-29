/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.duckduckgo.app.bookmarks.db.*
import com.duckduckgo.app.browser.cookies.db.AuthCookieAllowedDomainEntity
import com.duckduckgo.app.browser.cookies.db.AuthCookiesAllowedDomainsDao
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageDao
import com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageEntity
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelEntity
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelDao
import com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelEntity
import com.duckduckgo.app.browser.rating.db.*
import com.duckduckgo.app.browser.session.WebViewSessionDao
import com.duckduckgo.app.browser.session.WebViewSessionEntity
import com.duckduckgo.app.cta.db.DismissedCtaDao
import com.duckduckgo.app.cta.model.DismissedCta
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.global.events.db.UserEventEntity
import com.duckduckgo.app.global.events.db.UserEventTypeConverter
import com.duckduckgo.app.global.events.db.UserEventsDao
import com.duckduckgo.app.location.data.LocationPermissionEntity
import com.duckduckgo.app.location.data.LocationPermissionTypeConverter
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.notification.db.NotificationDao
import com.duckduckgo.app.notification.model.Notification
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.privacy.db.*
import com.duckduckgo.app.privacy.model.PrivacyProtectionCountsEntity
import com.duckduckgo.app.privacy.model.UserAllowListedDomain
import com.duckduckgo.app.statistics.model.PixelEntity
import com.duckduckgo.app.statistics.model.QueryParamsTypeConverter
import com.duckduckgo.app.statistics.store.PendingPixelDao
import com.duckduckgo.app.survey.db.SurveyDao
import com.duckduckgo.app.survey.model.Survey
import com.duckduckgo.app.tabs.db.DuckAiTabSessionDao
import com.duckduckgo.app.tabs.db.DuckAiTabSessionEntity
import com.duckduckgo.app.tabs.db.TabPageContextDao
import com.duckduckgo.app.tabs.db.TabPageContextEntity
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.LocalDateTimeTypeConverter
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.app.trackerdetection.db.*
import com.duckduckgo.app.trackerdetection.model.*
import com.duckduckgo.app.usage.app.AppDaysUsedDao
import com.duckduckgo.app.usage.app.AppDaysUsedEntity
import com.duckduckgo.app.usage.search.SearchCountDao
import com.duckduckgo.app.usage.search.SearchCountEntity
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityTypeConverter
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao

/**
 * Main application database.
 *
 * This database is legacy and must only ever shrink — no new tables or entities may be added to it.
 * New storage belongs in the owning feature's `-impl` module.
 *
 * [TabEntity] and [TabSelectionEntity] are also used by
 * [com.duckduckgo.app.fire.db.FireModeDatabase]. Any schema change to these entities must be
 * accompanied by a migration in BOTH databases — forgetting one will result in a runtime crash
 * for the affected users.
 */
@Database(
    exportSchema = true,
    version = 64,
    entities = [
        TdsTracker::class,
        TdsEntity::class,
        TdsDomainEntity::class,
        TdsCnameEntity::class,
        UserAllowListedDomain::class,
        NetworkLeaderboardEntry::class,
        SitesVisitedEntity::class,
        TabEntity::class,
        TabSelectionEntity::class,
        TabPageContextEntity::class,
        WebViewSessionEntity::class,
        BookmarkEntity::class,
        FavoriteEntity::class,
        BookmarkFolderEntity::class,
        Survey::class,
        DismissedCta::class,
        SearchCountEntity::class,
        AppDaysUsedEntity::class,
        AppEnjoymentEntity::class,
        Notification::class,
        PrivacyProtectionCountsEntity::class,
        TdsMetadata::class,
        UserStage::class,
        FireproofWebsiteEntity::class,
        UserEventEntity::class,
        LocationPermissionEntity::class,
        PixelEntity::class,
        PageLoadedPixelEntity::class,
        PagePaintedPixelEntity::class,
        WebTrackerBlocked::class,
        AuthCookieAllowedDomainEntity::class,
        Entity::class,
        Relation::class,
        DefaultBrowserPromptsAppUsageEntity::class,
        DuckAiTabSessionEntity::class,
    ],
)
@TypeConverters(
    Survey.StatusTypeConverter::class,
    DismissedCta.IdTypeConverter::class,
    AppEnjoymentTypeConverter::class,
    PromptCountConverter::class,
    ActionTypeConverter::class,
    RuleTypeConverter::class,
    CategoriesTypeConverter::class,
    StageTypeConverter::class,
    UserEventTypeConverter::class,
    LocationPermissionTypeConverter::class,
    QueryParamsTypeConverter::class,
    EntityTypeConverter::class,
    LocalDateTimeTypeConverter::class,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tdsTrackerDao(): TdsTrackerDao
    abstract fun tdsEntityDao(): TdsEntityDao
    abstract fun tdsDomainEntityDao(): TdsDomainEntityDao
    abstract fun tdsCnameEntityDao(): TdsCnameEntityDao
    abstract fun userAllowListDao(): UserAllowListDao
    abstract fun networkLeaderboardDao(): NetworkLeaderboardDao
    abstract fun tabsDao(): TabsDao
    abstract fun tabPageContextDao(): TabPageContextDao
    abstract fun duckAiTabSessionDao(): DuckAiTabSessionDao
    abstract fun webViewSessionDao(): WebViewSessionDao
    abstract fun bookmarksDao(): BookmarksDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun bookmarkFoldersDao(): BookmarkFoldersDao
    abstract fun surveyDao(): SurveyDao
    abstract fun dismissedCtaDao(): DismissedCtaDao
    abstract fun searchCountDao(): SearchCountDao
    abstract fun appsDaysUsedDao(): AppDaysUsedDao
    abstract fun appEnjoymentDao(): AppEnjoymentDao
    abstract fun notificationDao(): NotificationDao
    abstract fun privacyProtectionCountsDao(): PrivacyProtectionCountDao
    abstract fun tdsDao(): TdsMetadataDao
    abstract fun userStageDao(): UserStageDao
    abstract fun fireproofWebsiteDao(): FireproofWebsiteDao
    abstract fun locationPermissionsDao(): LocationPermissionsDao
    abstract fun userEventsDao(): UserEventsDao
    abstract fun pixelDao(): PendingPixelDao

    abstract fun pageLoadedPixelDao(): PageLoadedPixelDao
    abstract fun pagePaintedPixelDao(): PagePaintedPixelDao
    abstract fun authCookiesAllowedDomainsDao(): AuthCookiesAllowedDomainsDao
    abstract fun webTrackersBlockedDao(): WebTrackersBlockedDao

    abstract fun syncEntitiesDao(): SavedSitesEntitiesDao

    abstract fun syncRelationsDao(): SavedSitesRelationsDao

    abstract fun defaultBrowserPromptsAppUsageDao(): DefaultBrowserPromptsAppUsageDao
}
