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

package com.duckduckgo.site.permissions.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao

@Database(
    exportSchema = true,
    version = 6,
    entities = [
        SitePermissionsEntity::class,
        SitePermissionAllowedEntity::class,
    ],
)
abstract class SitePermissionsDatabase : RoomDatabase() {
    abstract fun sitePermissionsDao(): SitePermissionsDao
    abstract fun sitePermissionsAllowedDao(): SitePermissionsAllowedDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE 'site_permissions' ADD COLUMN 'askDrmSetting' TEXT NOT NULL DEFAULT 'ASK_EVERY_TIME'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE 'site_permissions' ADD COLUMN 'askLocationSetting' TEXT NOT NULL DEFAULT 'ASK_EVERY_TIME'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS `drm_block_exceptions`")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Rows used to be created for one-time grants with every setting defaulted to ASK_EVERY_TIME, which can't be told
        // apart from an explicit choice, so all of them become "not set" and sites left with nothing are dropped.
        database.execSQL(
            "CREATE TABLE `site_permissions_new` (`domain` TEXT NOT NULL, `askCameraSetting` TEXT, `askMicSetting` TEXT, " +
                "`askDrmSetting` TEXT, `askLocationSetting` TEXT, PRIMARY KEY(`domain`))",
        )
        database.execSQL(
            "INSERT INTO `site_permissions_new` SELECT `domain`, NULLIF(`askCameraSetting`, 'ASK_EVERY_TIME'), " +
                "NULLIF(`askMicSetting`, 'ASK_EVERY_TIME'), NULLIF(`askDrmSetting`, 'ASK_EVERY_TIME'), " +
                "NULLIF(`askLocationSetting`, 'ASK_EVERY_TIME') FROM `site_permissions`",
        )
        database.execSQL("DROP TABLE `site_permissions`")
        database.execSQL("ALTER TABLE `site_permissions_new` RENAME TO `site_permissions`")
        database.execSQL(
            "DELETE FROM `site_permissions` WHERE `askCameraSetting` IS NULL AND `askMicSetting` IS NULL " +
                "AND `askDrmSetting` IS NULL AND `askLocationSetting` IS NULL",
        )
    }
}

val ALL_MIGRATIONS = listOf(MIGRATION_1_2, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
