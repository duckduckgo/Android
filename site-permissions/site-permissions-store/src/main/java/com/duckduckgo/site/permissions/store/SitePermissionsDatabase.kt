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
import com.duckduckgo.site.permissions.store.drmblock.DrmBlockDao
import com.duckduckgo.site.permissions.store.drmblock.DrmBlockExceptionEntity
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsDao
import com.duckduckgo.site.permissions.store.sitepermissions.SitePermissionsEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionAllowedEntity
import com.duckduckgo.site.permissions.store.sitepermissionsallowed.SitePermissionsAllowedDao

@Database(
    exportSchema = true,
    version = 3,
    entities = [
        SitePermissionsEntity::class,
        SitePermissionAllowedEntity::class,
        DrmBlockExceptionEntity::class,
    ],
)

abstract class SitePermissionsDatabase : RoomDatabase() {
    abstract fun sitePermissionsDao(): SitePermissionsDao
    abstract fun sitePermissionsAllowedDao(): SitePermissionsAllowedDao
    abstract fun drmBlockDao(): DrmBlockDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE 'site_permissions' ADD COLUMN 'askDrmSetting' TEXT NOT NULL DEFAULT 'ASK_EVERY_TIME'")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
