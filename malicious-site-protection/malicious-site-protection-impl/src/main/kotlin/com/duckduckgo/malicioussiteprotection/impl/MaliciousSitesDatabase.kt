/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

@Database(
    exportSchema = true,
    entities = [RevisionEntity::class, HashPrefixEntity::class, FilterEntity::class],
    version = 1,
)
abstract class MaliciousSitesDatabase : RoomDatabase() {
    abstract fun maliciousSiteDao(): MaliciousSiteDao

    companion object {
        val ALL_MIGRATIONS = arrayOf<Migration>()
    }
}
