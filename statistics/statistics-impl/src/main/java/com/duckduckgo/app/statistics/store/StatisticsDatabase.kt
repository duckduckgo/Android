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

package com.duckduckgo.app.statistics.store

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.duckduckgo.app.statistics.model.DailyPixelFired
import com.duckduckgo.app.statistics.model.UniquePixelFired
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Database(
    exportSchema = true,
    version = 1,
    entities = [
        DailyPixelFired::class,
        UniquePixelFired::class,
    ],
)
@TypeConverters(LocalDateConverter::class)
abstract class StatisticsDatabase : RoomDatabase() {
    abstract fun dailyPixelFiredDao(): DailyPixelFiredDao
    abstract fun uniquePixelFiredDao(): UniquePixelFiredDao
}

object LocalDateConverter {
    private val formatter: DateTimeFormatter
        get() = DateTimeFormatter.ISO_LOCAL_DATE

    @TypeConverter
    fun fromLocalDate(localDate: LocalDate?): String? =
        localDate?.format(formatter)

    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? =
        dateString?.let { LocalDate.parse(it, formatter) }
}
