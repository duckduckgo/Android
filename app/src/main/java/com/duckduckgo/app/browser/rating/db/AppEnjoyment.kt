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

private const val TYPE_PROVIDED_RATING = 1
private const val TYPE_DECLINED_RATING = 2
private const val TYPE_PROVIDED_FEEDBACK = 3
private const val TYPE_DECLINED_FEEDBACK = 4

@Dao
interface AppEnjoymentDao {

    @Insert
    fun insertEvent(event: AppEnjoymentEntity)

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_PROVIDED_RATING")
    fun hasUserProvidedRating(): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_DECLINED_RATING")
    fun hasUserDeclinedRating(): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_PROVIDED_FEEDBACK")
    fun hasUserProvidedFeedback(): Boolean

    @Query("SELECT * from app_enjoyment WHERE eventType = $TYPE_DECLINED_FEEDBACK")
    fun hasUserDeclinedFeedback(): Boolean

}

@Entity(tableName = "app_enjoyment")
data class AppEnjoymentEntity(
    val eventType: AppEnjoymentEventType,
    val timestamp: Long = System.currentTimeMillis(), @PrimaryKey(autoGenerate = true) val primaryKey: Int = 0
)

enum class AppEnjoymentEventType(val value: Int) {

    USER_PROVIDED_RATING(TYPE_PROVIDED_RATING),
    USER_DECLINED_RATING(TYPE_DECLINED_RATING),
    USER_PROVIDED_FEEDBACK(TYPE_PROVIDED_FEEDBACK),
    USER_DECLINED_FEEDBACK(TYPE_DECLINED_FEEDBACK);

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