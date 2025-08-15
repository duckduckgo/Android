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

package com.duckduckgo.app.browser.defaultbrowsing.prompts.store

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class DefaultBrowserPromptsAppUsageRepositoryImpl @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val experimentAppUsageDao: ExperimentAppUsageDao,
) : DefaultBrowserPromptsAppUsageRepository {

    override suspend fun recordAppUsedNow() = withContext(dispatchers.io()) {
        val isoDateET = ZonedDateTime.now(ZoneId.of("America/New_York"))
            .truncatedTo(ChronoUnit.DAYS)
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

        experimentAppUsageDao.insert(ExperimentAppUsageEntity(isoDateET))
    }

    override suspend fun getActiveDaysUsedSinceStart(): Result<Long> = withContext(dispatchers.io()) {
        try {
            // dateETString is already a String like "2025-07-23"
            val dateETString = experimentAppUsageDao.getFirstDay()
            if (dateETString == null) {
                return@withContext Result.failure(IllegalStateException("Date is missing"))
            }
            val parsedDate = LocalDate.parse(dateETString, DateTimeFormatter.ISO_LOCAL_DATE)
            val isoDateET = parsedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            val daysUsed = experimentAppUsageDao.getNumberOfDaysAppUsedSinceDateET(isoDateET)
            Result.success(daysUsed)
        } catch (ex: DateTimeParseException) {
            // This catch block will now correctly handle if getFirstDay() returns
            // a string that is not a valid ISO_LOCAL_DATE.
            Result.failure(ex)
        } catch (e: Exception) {
            // Catch other potential exceptions from DAO or other operations
            Result.failure(e)
        }
    }
}

@Dao
abstract class ExperimentAppUsageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract fun insert(experimentAppUsageEntity: ExperimentAppUsageEntity)

    @Query("SELECT COUNT(*) from experiment_app_usage_entity WHERE isoDateET > :isoDateET")
    abstract fun getNumberOfDaysAppUsedSinceDateET(isoDateET: String): Long

    @Query("SELECT isoDateET FROM experiment_app_usage_entity ORDER BY isoDateET ASC LIMIT 1")
    abstract fun getFirstDay(): String?
}

@Entity(tableName = "experiment_app_usage_entity")
data class ExperimentAppUsageEntity(@PrimaryKey val isoDateET: String)
