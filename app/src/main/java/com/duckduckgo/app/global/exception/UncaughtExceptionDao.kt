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

private const val GLOBAL_VALUE = 0
private const val SHOULD_INTERCEPT_REQUEST_VALUE = 1
private const val ON_PAGE_STARTED_VALUE = 2
private const val ON_PAGE_FINISHED_VALUE = 3
private const val SHOULD_OVERRIDE_REQUEST_VALUE = 4
private const val ON_HTTP_AUTH_REQUEST_VALUE = 5
private const val SHOW_CUSTOM_VIEW_VALUE = 6
private const val HIDE_CUSTOM_VIEW_VALUE = 7
private const val ON_PROGRESS_CHANGED_VALUE = 8
private const val RECEIVED_PAGE_TITLE_VALUE = 9
private const val SHOW_FILE_CHOOSER_VALUE = 10


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

enum class UncaughtExceptionSource(val value: Int) {

    GLOBAL(GLOBAL_VALUE),
    SHOULD_INTERCEPT_REQUEST(SHOULD_INTERCEPT_REQUEST_VALUE),
    ON_PAGE_STARTED(ON_PAGE_STARTED_VALUE),
    ON_PAGE_FINISHED(ON_PAGE_FINISHED_VALUE),
    ON_HTTP_AUTH_REQUEST(ON_HTTP_AUTH_REQUEST_VALUE),
    SHOULD_OVERRIDE_REQUEST(SHOULD_OVERRIDE_REQUEST_VALUE),
    SHOW_CUSTOM_VIEW(SHOW_CUSTOM_VIEW_VALUE),
    HIDE_CUSTOM_VIEW(HIDE_CUSTOM_VIEW_VALUE),
    ON_PROGRESS_CHANGED(ON_PROGRESS_CHANGED_VALUE),
    RECEIVED_PAGE_TITLE(RECEIVED_PAGE_TITLE_VALUE),
    SHOW_FILE_CHOOSER(SHOW_FILE_CHOOSER_VALUE);

    companion object {
        private val map = values().associateBy(UncaughtExceptionSource::value)
        fun fromValue(value: Int) = map[value]
    }

}

class UncaughtExceptionSourceConverter {

    @TypeConverter
    fun convertForDb(event: UncaughtExceptionSource): Int = event.value

    @TypeConverter
    fun convertFromDb(value: Int): UncaughtExceptionSource? = UncaughtExceptionSource.fromValue(value)

}