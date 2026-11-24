/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.app.trackerdetection.model.ActionTypeConverter
import com.duckduckgo.app.trackerdetection.model.CategoriesTypeConverter
import com.duckduckgo.app.trackerdetection.model.RuleTypeConverter
import com.duckduckgo.app.trackerdetection.model.TdsCnameEntity
import com.duckduckgo.app.trackerdetection.model.TdsDomainEntity
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TdsMetadata
import com.duckduckgo.app.trackerdetection.model.TdsTracker

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        TdsTracker::class,
        TdsEntity::class,
        TdsDomainEntity::class,
        TdsCnameEntity::class,
        TdsMetadata::class,
        WebTrackerBlocked::class,
        LegacyAppDbImport::class,
    ],
)
@TypeConverters(
    ActionTypeConverter::class,
    RuleTypeConverter::class,
    CategoriesTypeConverter::class,
)
abstract class TrackerDetectionDatabase : RoomDatabase() {
    abstract fun tdsTrackerDao(): TdsTrackerDao
    abstract fun tdsEntityDao(): TdsEntityDao
    abstract fun tdsDomainEntityDao(): TdsDomainEntityDao
    abstract fun tdsCnameEntityDao(): TdsCnameEntityDao
    abstract fun tdsMetadataDao(): TdsMetadataDao
    abstract fun webTrackersBlockedDao(): WebTrackersBlockedDao

    companion object {
        /**
         * `web_trackers_blocked` moved here from app.db. It starts empty: WebTrackersBlockedLegacyImporter copies the old rows.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `web_trackers_blocked` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`trackerUrl` TEXT NOT NULL, `trackerCompany` TEXT NOT NULL, `timestamp` TEXT NOT NULL)",
                )
                db.execSQL("CREATE TABLE IF NOT EXISTS `legacy_app_db_imports` (`name` TEXT NOT NULL, PRIMARY KEY(`name`))")
            }
        }

        val ALL_MIGRATIONS = listOf(MIGRATION_1_2)
    }
}
