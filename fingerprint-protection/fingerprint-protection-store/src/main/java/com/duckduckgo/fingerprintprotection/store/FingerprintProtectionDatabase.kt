/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.fingerprintprotection.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingbattery.FingerprintingBatteryDao
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingcanvas.FingerprintingCanvasDao
import com.duckduckgo.fingerprintprotection.store.features.fingerprintinghardware.FingerprintingHardwareDao
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingscreensize.FingerprintingScreenSizeDao
import com.duckduckgo.fingerprintprotection.store.features.fingerprintingtemporarystorage.FingerprintingTemporaryStorageDao

@Database(
    exportSchema = true,
    version = 2,
    entities = [
        FingerprintingBatteryEntity::class,
        FingerprintingCanvasEntity::class,
        FingerprintingHardwareEntity::class,
        FingerprintingScreenSizeEntity::class,
        FingerprintingTemporaryStorageEntity::class,
    ],
)
abstract class FingerprintProtectionDatabase : RoomDatabase() {
    abstract fun fingerprintingBatteryDao(): FingerprintingBatteryDao
    abstract fun fingerprintingCanvasDao(): FingerprintingCanvasDao
    abstract fun fingerprintingHardwareDao(): FingerprintingHardwareDao
    abstract fun fingerprintingScreenSizeDao(): FingerprintingScreenSizeDao
    abstract fun fingerprintingTemporaryStorageDao(): FingerprintingTemporaryStorageDao
}
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS `fingerprint_protection_seed`")
    }
}

val ALL_MIGRATIONS = listOf(
    MIGRATION_1_2,
)
