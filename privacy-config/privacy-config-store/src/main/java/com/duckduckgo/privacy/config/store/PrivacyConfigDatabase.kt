/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store

import androidx.room.Database

import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.privacy.config.store.features.autofill.AutofillDao
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingDao
import com.duckduckgo.privacy.config.store.features.drm.DrmDao
import com.duckduckgo.privacy.config.store.features.gpc.GpcExceptionsDao
import com.duckduckgo.privacy.config.store.features.gpc.GpcHeadersDao
import com.duckduckgo.privacy.config.store.features.https.HttpsDao
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistDao
import com.duckduckgo.privacy.config.store.features.amplinks.AmpLinksDao
import com.duckduckgo.privacy.config.store.features.trackingparameters.TrackingParametersDao
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryDao

@TypeConverters(
    RuleTypeConverter::class,
)
@Database(
    exportSchema = true, version = 8,
    entities = [
        TrackerAllowlistEntity::class,
        UnprotectedTemporaryEntity::class,
        HttpsExceptionEntity::class,
        GpcExceptionEntity::class,
        GpcHeaderEnabledSiteEntity::class,
        ContentBlockingExceptionEntity::class,
        DrmExceptionEntity::class,
        PrivacyConfig::class,
        AmpLinkFormatEntity::class,
        AmpKeywordEntity::class,
        AmpLinkExceptionEntity::class,
        TrackingParameterEntity::class,
        TrackingParameterExceptionEntity::class,
        AutofillExceptionEntity::class
    ]
)
abstract class PrivacyConfigDatabase : RoomDatabase() {
    abstract fun trackerAllowlistDao(): TrackerAllowlistDao
    abstract fun unprotectedTemporaryDao(): UnprotectedTemporaryDao
    abstract fun httpsDao(): HttpsDao
    abstract fun gpcExceptionsDao(): GpcExceptionsDao
    abstract fun gpcHeadersDao(): GpcHeadersDao
    abstract fun contentBlockingDao(): ContentBlockingDao
    abstract fun privacyConfigDao(): PrivacyConfigDao
    abstract fun drmDao(): DrmDao
    abstract fun ampLinksDao(): AmpLinksDao
    abstract fun trackingParametersDao(): TrackingParametersDao
    abstract fun autofillDao(): AutofillDao
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE toggles")
        database.execSQL("DELETE FROM privacy_config")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_2_3)
