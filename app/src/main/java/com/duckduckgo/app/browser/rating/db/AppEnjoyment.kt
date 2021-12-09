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

package com.duckduckgo.app.browser.rating.db

import androidx.room.*
import com.duckduckgo.app.global.rating.PromptCount
import com.duckduckgo.app.location.data.LocationPermissionType

private const val TYPE_PROVIDED_RATING = 1
private const val TYPE_DECLINED_RATING = 2
private const val TYPE_PROVIDED_FEEDBACK = 3
private const val TYPE_DECLINED_FEEDBACK = 4
private const val TYPE_DECLINED_TO_PARTICIPATE = 5

@Dao
interface AppEnjoymentDao {

    @Insert
    fun insertEvent(event: AppEnjoymentEntity)

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_PROVIDED_RATING")
    fun hasUserProvidedRating(): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_DECLINED_RATING AND promptCount = :promptCount")
    fun hasUserDeclinedRating(promptCount: Int): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_PROVIDED_FEEDBACK")
    fun hasUserProvidedFeedback(): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_DECLINED_FEEDBACK AND promptCount = :promptCount")
    fun hasUserDeclinedFeedback(promptCount: Int): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_DECLINED_TO_PARTICIPATE AND promptCount = :promptCount")
    fun hasUserDeclinedToSayWhetherEnjoying(promptCount: Int): Boolean

    @Query("SELECT timestamp FROM app_enjoyment WHERE eventType=$TYPE_DECLINED_RATING OR eventType=$TYPE_DECLINED_FEEDBACK OR eventType=$TYPE_DECLINED_TO_PARTICIPATE ORDER BY timestamp DESC LIMIT 1")
    fun latestDateUserDeclinedRatingOrFeedback(): Long?

}

@Entity(tableName = "app_enjoyment")
data class AppEnjoymentEntity(
    val eventType: AppEnjoymentEventType,
    val promptCount: PromptCount,
    val timestamp: Long = System.currentTimeMillis(),
    @PrimaryKey(autoGenerate = true) val primaryKey: Int = 0
)

enum class AppEnjoymentEventType(val value: Int) {

    USER_PROVIDED_RATING(TYPE_PROVIDED_RATING),
    USER_DECLINED_RATING(TYPE_DECLINED_RATING),
    USER_PROVIDED_FEEDBACK(TYPE_PROVIDED_FEEDBACK),
    USER_DECLINED_FEEDBACK(TYPE_DECLINED_FEEDBACK),
    USER_DECLINED_TO_SAY_WHETHER_ENJOYING(TYPE_DECLINED_TO_PARTICIPATE);

    companion object {
        private val map = AppEnjoymentEventType.values().associateBy(AppEnjoymentEventType::value)
        fun fromValue(value: Int) = map[value]
    }

}

class AppEnjoymentTypeConverter {

    @TypeConverter
    fun convertForDb(event: AppEnjoymentEventType): Int = event.value

    @TypeConverter
    fun convertFromDb(value: Int): AppEnjoymentEventType? = AppEnjoymentEventType.fromValue(value)

}

class PromptCountConverter {

    @TypeConverter
    fun convertForDb(promptCount: PromptCount): Int = promptCount.value

    @TypeConverter
    fun convertFromDb(promptCount: Int): PromptCount = PromptCount(promptCount)

}

class LocationPermissionTypeConverter {

    @TypeConverter
    fun convertForDb(event: LocationPermissionType): Int = event.value

    @TypeConverter
    fun convertFromDb(value: Int): LocationPermissionType? = LocationPermissionType.fromValue(value)

}
