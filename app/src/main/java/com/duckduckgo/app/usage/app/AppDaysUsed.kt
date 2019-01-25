/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.usage.app

import androidx.room.*
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Singleton

private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

@Dao
@Singleton
abstract class AppDaysUsedDao {

    @Query("SELECT COUNT(*) from app_days_used where date <= :dateToday ORDER BY date ASC")
    abstract fun getNumberOfDaysAppUsed(dateToday: String = formatter.format(Date())): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(appUsedEntity: AppDaysUsedEntity)

    fun recordAppUsedToday() {
        insert(AppDaysUsedEntity())
    }
}

@Entity(tableName = "app_days_used")
data class AppDaysUsedEntity(
    @PrimaryKey val date: String = formatter.format((Date()))
)


