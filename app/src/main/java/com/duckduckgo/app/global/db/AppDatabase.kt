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

import android.arch.persistence.room.Database
import android.arch.persistence.room.RoomDatabase
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomain
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardDao
import com.duckduckgo.app.privacymonitor.db.NetworkLeaderboardEntry
import com.duckduckgo.app.settings.db.AppConfigurationDao
import com.duckduckgo.app.settings.db.AppConfigurationEntity
import com.duckduckgo.app.trackerdetection.db.TrackerDataDao
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker

@Database(exportSchema = true, version = 1, entities = [
    HttpsUpgradeDomain::class,
    DisconnectTracker::class,
    NetworkLeaderboardEntry::class,
    AppConfigurationEntity::class,
    BookmarkEntity::class
])
abstract class AppDatabase : RoomDatabase() {

    abstract fun httpsUpgradeDomainDao(): HttpsUpgradeDomainDao
    abstract fun trackerDataDao(): TrackerDataDao
    abstract fun networkLeaderboardDao(): NetworkLeaderboardDao
    abstract fun appConfigurationDao(): AppConfigurationDao
    abstract fun bookmarksDao(): BookmarksDao

}