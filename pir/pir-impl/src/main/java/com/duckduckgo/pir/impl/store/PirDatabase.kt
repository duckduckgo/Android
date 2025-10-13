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

package com.duckduckgo.pir.impl.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import com.duckduckgo.pir.impl.store.db.BrokerDao
import com.duckduckgo.pir.impl.store.db.BrokerEntity
import com.duckduckgo.pir.impl.store.db.BrokerJsonDao
import com.duckduckgo.pir.impl.store.db.BrokerJsonEtag
import com.duckduckgo.pir.impl.store.db.BrokerOptOut
import com.duckduckgo.pir.impl.store.db.BrokerScan
import com.duckduckgo.pir.impl.store.db.BrokerSchedulingConfigEntity
import com.duckduckgo.pir.impl.store.db.EmailConfirmationJobRecordEntity
import com.duckduckgo.pir.impl.store.db.EmailConfirmationLogDao
import com.duckduckgo.pir.impl.store.db.ExtractedProfileDao
import com.duckduckgo.pir.impl.store.db.JobSchedulingDao
import com.duckduckgo.pir.impl.store.db.MirrorSiteEntity
import com.duckduckgo.pir.impl.store.db.OptOutActionLog
import com.duckduckgo.pir.impl.store.db.OptOutCompletedBroker
import com.duckduckgo.pir.impl.store.db.OptOutJobRecordEntity
import com.duckduckgo.pir.impl.store.db.OptOutResultsDao
import com.duckduckgo.pir.impl.store.db.PirBrokerScanLog
import com.duckduckgo.pir.impl.store.db.PirEmailConfirmationLog
import com.duckduckgo.pir.impl.store.db.PirEventLog
import com.duckduckgo.pir.impl.store.db.ScanCompletedBroker
import com.duckduckgo.pir.impl.store.db.ScanJobRecordEntity
import com.duckduckgo.pir.impl.store.db.ScanLogDao
import com.duckduckgo.pir.impl.store.db.ScanResultsDao
import com.duckduckgo.pir.impl.store.db.StoredExtractedProfile
import com.duckduckgo.pir.impl.store.db.UserProfile
import com.duckduckgo.pir.impl.store.db.UserProfileDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

@Database(
    exportSchema = true,
    version = 13,
    entities = [
        BrokerJsonEtag::class,
        BrokerEntity::class,
        BrokerOptOut::class,
        BrokerScan::class,
        BrokerSchedulingConfigEntity::class,
        UserProfile::class,
        PirEventLog::class,
        PirBrokerScanLog::class,
        ScanCompletedBroker::class,
        OptOutCompletedBroker::class,
        OptOutActionLog::class,
        StoredExtractedProfile::class,
        ScanJobRecordEntity::class,
        OptOutJobRecordEntity::class,
        MirrorSiteEntity::class,
        EmailConfirmationJobRecordEntity::class,
        PirEmailConfirmationLog::class,
    ],
)
@TypeConverters(PirDatabaseConverters::class)
abstract class PirDatabase : RoomDatabase() {
    abstract fun brokerJsonDao(): BrokerJsonDao

    abstract fun brokerDao(): BrokerDao

    abstract fun scanResultsDao(): ScanResultsDao

    abstract fun userProfileDao(): UserProfileDao

    abstract fun scanLogDao(): ScanLogDao

    abstract fun emailConfirmationLogDao(): EmailConfirmationLogDao

    abstract fun optOutResultsDao(): OptOutResultsDao

    abstract fun jobSchedulingDao(): JobSchedulingDao

    abstract fun extractedProfileDao(): ExtractedProfileDao

    companion object {
        val ALL_MIGRATIONS: List<Migration> = emptyList()
    }
}

object PirDatabaseConverters {
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    @TypeConverter
    @JvmStatic
    fun toStringList(value: String): List<String> = stringListAdapter.fromJson(value)!!

    @TypeConverter
    @JvmStatic
    fun fromStringList(value: List<String>): String = stringListAdapter.toJson(value)
}
