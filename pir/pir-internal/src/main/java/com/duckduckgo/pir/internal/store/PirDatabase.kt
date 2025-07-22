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

package com.duckduckgo.pir.internal.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.pir.internal.store.db.Broker
import com.duckduckgo.pir.internal.store.db.BrokerDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonDao
import com.duckduckgo.pir.internal.store.db.BrokerJsonEtag
import com.duckduckgo.pir.internal.store.db.BrokerOptOut
import com.duckduckgo.pir.internal.store.db.BrokerScan
import com.duckduckgo.pir.internal.store.db.BrokerSchedulingConfig
import com.duckduckgo.pir.internal.store.db.JobSchedulingDao
import com.duckduckgo.pir.internal.store.db.OptOutActionLog
import com.duckduckgo.pir.internal.store.db.OptOutCompletedBroker
import com.duckduckgo.pir.internal.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.internal.store.db.OptOutResultsDao
import com.duckduckgo.pir.internal.store.db.PirBrokerScanLog
import com.duckduckgo.pir.internal.store.db.PirEventLog
import com.duckduckgo.pir.internal.store.db.ScanCompletedBroker
import com.duckduckgo.pir.internal.store.db.ScanJobRecordEntity
import com.duckduckgo.pir.internal.store.db.ScanLogDao
import com.duckduckgo.pir.internal.store.db.ScanResultsDao
import com.duckduckgo.pir.internal.store.db.StoredExtractedProfile
import com.duckduckgo.pir.internal.store.db.UserProfile
import com.duckduckgo.pir.internal.store.db.UserProfileDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Database(
    exportSchema = true,
    version = 5,
    entities = [
        BrokerJsonEtag::class,
        Broker::class,
        BrokerOptOut::class,
        BrokerScan::class,
        BrokerSchedulingConfig::class,
        UserProfile::class,
        PirEventLog::class,
        PirBrokerScanLog::class,
        ScanCompletedBroker::class,
        OptOutCompletedBroker::class,
        OptOutActionLog::class,
        StoredExtractedProfile::class,
        ScanJobRecordEntity::class,
        OptOutJobRecordEntity::class,
    ],
)
@TypeConverters(PirDatabaseConverters::class)
abstract class PirDatabase : RoomDatabase() {
    abstract fun brokerJsonDao(): BrokerJsonDao
    abstract fun brokerDao(): BrokerDao
    abstract fun scanResultsDao(): ScanResultsDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun scanLogDao(): ScanLogDao
    abstract fun optOutResultsDao(): OptOutResultsDao
    abstract fun jobSchedulingDao(): JobSchedulingDao

    companion object {
        val ALL_MIGRATIONS: List<Migration>
            get() = listOf(
                MIGRATION_3_TO_4,
                MIGRATION_4_TO_5,
            )

        private val MIGRATION_3_TO_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `pir_scan_complete_brokers`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pir_scan_complete_brokers` (`brokerName` TEXT NOT NULL, " +
                        "`profileQueryId` INTEGER NOT NULL, `startTimeInMillis` INTEGER NOT NULL, `endTimeInMillis` INTEGER NOT NULL, " +
                        "`isSuccess` INTEGER NOT NULL, PRIMARY KEY(`brokerName`, `profileQueryId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `pir_extracted_profiles` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`profileQueryId` INTEGER NOT NULL, `brokerName` TEXT NOT NULL, `name` TEXT," +
                        " `alternativeNames` TEXT NOT NULL, `age` TEXT, `addresses` TEXT NOT NULL," +
                        " `phoneNumbers` TEXT NOT NULL, `relatives` TEXT NOT NULL, `profileUrl` TEXT," +
                        " `identifier` TEXT, `reportId` TEXT, `email` TEXT, `fullName` TEXT, `dateAddedInMillis` INTEGER NOT NULL," +
                        " `deprecated` INTEGER NOT NULL)",
                )
                db.execSQL("DROP TABLE IF EXISTS `pir_scan_extracted_profile`")
                db.execSQL("DROP TABLE IF EXISTS `pir_scan_navigate_results`")
                db.execSQL("DROP TABLE IF EXISTS `pir_scan_error`")
            }
        }

        private val MIGRATION_4_TO_5: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pir_scan_job_record` (`brokerName` TEXT NOT NULL, `userProfileId` INTEGER NOT NULL, 
                    `status` TEXT NOT NULL, `lastScanDateInMillis` INTEGER, PRIMARY KEY(`brokerName`, `userProfileId`))
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pir_optout_job_record` (`extractedProfileId` INTEGER NOT NULL, `brokerName` TEXT NOT NULL, 
                    `userProfileId` INTEGER NOT NULL, `status` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, `lastOptOutAttemptDate` INTEGER, 
                    `optOutRequestedDate` INTEGER NOT NULL, `optOutRemovedDate` INTEGER NOT NULL, PRIMARY KEY(`extractedProfileId`))
                    """.trimIndent(),
                )
            }
        }
    }
}

object PirDatabaseConverters {

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String): List<String> {
        return stringListAdapter.fromJson(value)!!
    }

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>): String {
        return stringListAdapter.toJson(value)
    }
}
