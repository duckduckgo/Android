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

package com.duckduckgo.autofill.store.engagement

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import java.time.LocalDate

@Database(
    exportSchema = true,
    version = 1,
    entities = [
        AutofillEngagementEntity::class,
    ],
)
abstract class AutofillEngagementDatabase : RoomDatabase() {
    abstract fun autofillEngagementDao(): AutofillEngagementDao

    companion object {
        val ALL_MIGRATIONS = emptyArray<Migration>()
    }
}

@Entity(tableName = "autofill_engagement")
data class AutofillEngagementEntity(
    @PrimaryKey val date: String,
    val autofilled: Boolean,
    val searched: Boolean,
) {
    fun isToday(): Boolean {
        return date == LocalDate.now().toString()
    }
}
