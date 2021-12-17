/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.threeten.bp.LocalDateTime
import org.threeten.bp.format.DateTimeFormatter

@Entity(tableName = "web_trackers_blocked")
data class WebTrackerBlocked(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val trackerUrl: String,
    val trackerCompany: String,
    val timestamp: String = timestamp()
) {

    companion object {
        private val FORMATTER_SECONDS: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun timestamp(date: LocalDateTime = LocalDateTime.now()): String {
            return FORMATTER_SECONDS.format(date)
        }
    }
}
