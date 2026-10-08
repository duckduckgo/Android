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

package com.duckduckgo.app.trackerdetection.di

import androidx.room.RoomDatabase
import com.duckduckgo.app.trackerdetection.db.TdsCnameEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsDomainEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsEntityDao
import com.duckduckgo.app.trackerdetection.db.TdsMetadataDao
import com.duckduckgo.app.trackerdetection.db.TdsTrackerDao
import com.duckduckgo.app.trackerdetection.db.TrackerDetectionDatabase
import com.duckduckgo.data.store.api.DatabaseProvider
import com.duckduckgo.data.store.api.RoomDatabaseConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn

@Module
@ContributesTo(AppScope::class)
object TrackerDetectionDatabaseModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideTrackerDetectionDatabase(databaseProvider: DatabaseProvider): TrackerDetectionDatabase {
        return databaseProvider.buildRoomDatabase(
            TrackerDetectionDatabase::class.java,
            "tracker_detection.db",
            config = RoomDatabaseConfig(
                // Written from both the main and :pir processes; RoomDatabaseConfig defaults this to false.
                enableMultiInstanceInvalidation = true,
                // Matches app.db, which these tables lived in and which is also shared with :pir.
                journalMode = RoomDatabase.JournalMode.TRUNCATE,
                migrations = TrackerDetectionDatabase.ALL_MIGRATIONS,
            ),
        )
    }

    @Provides
    fun provideTdsTrackerDao(database: TrackerDetectionDatabase): TdsTrackerDao = database.tdsTrackerDao()

    @Provides
    fun provideTdsEntityDao(database: TrackerDetectionDatabase): TdsEntityDao = database.tdsEntityDao()

    @Provides
    fun provideTdsDomainEntityDao(database: TrackerDetectionDatabase): TdsDomainEntityDao = database.tdsDomainEntityDao()

    @Provides
    fun provideTdsCnameEntityDao(database: TrackerDetectionDatabase): TdsCnameEntityDao = database.tdsCnameEntityDao()

    @Provides
    fun provideTdsMetadataDao(database: TrackerDetectionDatabase): TdsMetadataDao = database.tdsMetadataDao()
}
