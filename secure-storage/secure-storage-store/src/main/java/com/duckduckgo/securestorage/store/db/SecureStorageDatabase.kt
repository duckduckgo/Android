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

package com.duckduckgo.securestorage.store.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    version = 3,
    entities = [WebsiteLoginCredentialsEntity::class],
)
abstract class SecureStorageDatabase : RoomDatabase() {
    abstract fun websiteLoginCredentialsDao(): WebsiteLoginCredentialsDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `website_login_credentials` ADD COLUMN `notes` TEXT")
        database.execSQL("ALTER TABLE `website_login_credentials` ADD COLUMN `domainTitle` TEXT")
        database.execSQL("ALTER TABLE `website_login_credentials` ADD COLUMN `lastUpdatedInMillis` INTEGER")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("UPDATE `website_login_credentials` SET `notes` = null")
        database.execSQL("ALTER TABLE `website_login_credentials` ADD COLUMN `notesIv` TEXT")
        database.execSQL("ALTER TABLE `website_login_credentials` RENAME COLUMN `iv` to `passwordIv`")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3)
