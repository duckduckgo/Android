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

package com.duckduckgo.app.global.exception

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.TypeConverter

@Dao
abstract class UncaughtExceptionDao {

    @Insert
    abstract fun add(uncaughtException: UncaughtExceptionEntity)

    @Query("SELECT COUNT(1) FROM UncaughtExceptionEntity")
    abstract fun count(): Long

    @Query("SELECT * FROM UncaughtExceptionEntity")
    abstract fun all(): List<UncaughtExceptionEntity>

    @Query("DELETE FROM UncaughtExceptionEntity WHERE id=:id")
    abstract fun delete(id: Long)
}

enum class UncaughtExceptionSource {
    GLOBAL,
    SHOULD_INTERCEPT_REQUEST,
    ON_PAGE_STARTED,
    ON_PAGE_FINISHED,
    SHOULD_OVERRIDE_REQUEST,
    ON_HTTP_AUTH_REQUEST,
    SHOW_CUSTOM_VIEW,
    HIDE_CUSTOM_VIEW,
    ON_PROGRESS_CHANGED,
    RECEIVED_PAGE_TITLE,
    SHOW_FILE_CHOOSER
}

class UncaughtExceptionSourceConverter {

    @TypeConverter
    fun convertForDb(event: UncaughtExceptionSource): String = event.name

    @TypeConverter
    fun convertFromDb(value: String): UncaughtExceptionSource? = UncaughtExceptionSource.valueOf(value)
}
