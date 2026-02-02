/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.app.tabs.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import java.time.LocalDateTime

@Entity(
    tableName = "tabs",
    foreignKeys = [
        ForeignKey(
            entity = TabEntity::class,
            parentColumns = ["tabId"],
            childColumns = ["sourceTabId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("tabId"),
    ],
)
data class TabEntity(
    @PrimaryKey val tabId: String,
    val url: String? = null,
    val title: String? = null,
    val skipHome: Boolean = false,
    val viewed: Boolean = true,
    val position: Int = 0,
    val tabPreviewFile: String? = null,
    val sourceTabId: String? = null,
    val deletable: Boolean = false,
    val lastAccessTime: LocalDateTime? = null,
)

val TabEntity.isBlank: Boolean
    get() = title == null && url == null

val TabEntity.isAboutBlank: Boolean
    get() = title?.equals("about:blank", ignoreCase = true) == true

class LocalDateTimeTypeConverter {
    @TypeConverter
    fun convertForDb(date: LocalDateTime): String = DatabaseDateFormatter.timestamp(date)

    @TypeConverter
    fun convertFromDb(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it) }
}
