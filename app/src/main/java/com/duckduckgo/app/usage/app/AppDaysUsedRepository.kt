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

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

private val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)

interface AppDaysUsedRepository {

    suspend fun getNumberOfDaysAppUsed(): Long

    suspend fun recordAppUsedToday()
    suspend fun getNumberOfDaysAppUsedSinceDate(date: Date): Long
}

class AppDaysUsedDatabaseRepository(private val appDaysUsedDao: AppDaysUsedDao) : AppDaysUsedRepository {

    private val singleThreadedDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override suspend fun recordAppUsedToday() = withContext(singleThreadedDispatcher) {
        appDaysUsedDao.insert(AppDaysUsedEntity())
    }

    override suspend fun getNumberOfDaysAppUsed(): Long {
        return withContext(singleThreadedDispatcher) {
            return@withContext appDaysUsedDao.getNumberOfDaysAppUsed()
        }
    }

    override suspend fun getNumberOfDaysAppUsedSinceDate(date: Date): Long {
        return withContext(singleThreadedDispatcher) {
            return@withContext appDaysUsedDao.getNumberOfDaysAppUsedSince(formatter.format(date))
        }
    }
}

@Entity(tableName = "app_days_used")
data class AppDaysUsedEntity(
    @PrimaryKey val date: String = formatter.format((Date()))
)