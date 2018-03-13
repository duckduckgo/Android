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

import android.arch.persistence.db.SupportSQLiteDatabase
import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import android.arch.persistence.room.migration.Migration
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomain
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSelectionEntity
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker

@Database(exportSchema = true, version = 2, entities = [
    HttpsUpgradeDomain::class,
    DisconnectTracker::class,
    NetworkLeaderboardEntry::class,
    AppConfigurationEntity::class,
    TabEntity::class,
    TabSelectionEntity::class,
    BookmarkEntity::class
])

abstract class AppDatabase : RoomDatabase() {

    abstract fun httpsUpgradeDomainDao(): HttpsUpgradeDomainDao
    abstract fun trackerDataDao(): TrackerDataDao
    abstract fun networkLeaderboardDao(): NetworkLeaderboardDao
    abstract fun appConfigurationDao(): AppConfigurationDao
    abstract fun tabsDao(): TabsDao
    abstract fun bookmarksDao(): BookmarksDao

    companion object {
        val MIGRATION_1_TO_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE `tabs` (`tabId` TEXT NOT NULL, `url` TEXT, `title` TEXT, PRIMARY KEY(`tabId`))")
                database.execSQL("CREATE INDEX `index_tabs_tabId` on `tabs` (tabId)")
                database.execSQL("CREATE TABLE `tab_selection` (`id` INTEGER NOT NULL, `tabId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`tabId`) REFERENCES `tabs`(`tabId`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                database.execSQL("CREATE INDEX `index_tab_selection_tabId` on `tab_selection` (tabId)")
            }
        }
    }
}