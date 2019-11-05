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

private const val SHOULD_INTERCEPT_REQUEST_VALUE = 1
private const val ON_PAGE_STARTED_VALUE = 2


@Dao
abstract class UncaughtWebViewExceptionDao {

    @Insert
    abstract fun add(uncaughtException: UncaughtWebViewExceptionEntity)

    @Query("SELECT COUNT(1) FROM UncaughtWebViewExceptionEntity")
    abstract fun count(): Long

    @Query("SELECT * FROM UncaughtWebViewExceptionEntity")
    abstract fun all(): List<UncaughtWebViewExceptionEntity>

    @Query("DELETE FROM UncaughtWebViewExceptionEntity WHERE id=:id")
    abstract fun delete(id: Long)

}

enum class UncaughtWebViewExceptionSource(val value: Int) {

    SHOULD_INTERCEPT_REQUEST(SHOULD_INTERCEPT_REQUEST_VALUE),
    ON_PAGE_STARTED(ON_PAGE_STARTED_VALUE);

    companion object {
        private val map = UncaughtWebViewExceptionSource.values().associateBy(UncaughtWebViewExceptionSource::value)
        fun fromValue(value: Int) = map[value]
    }

}

class UncaughtWebViewExceptionSourceConverter {

    @TypeConverter
    fun convertForDb(event: UncaughtWebViewExceptionSource): Int = event.value

    @TypeConverter
    fun convertFromDb(value: Int): UncaughtWebViewExceptionSource? = UncaughtWebViewExceptionSource.fromValue(value)

}