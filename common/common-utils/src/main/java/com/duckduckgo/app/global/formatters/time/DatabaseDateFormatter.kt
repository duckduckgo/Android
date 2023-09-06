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

package com.duckduckgo.app.global.formatters.time

import org.threeten.bp.Duration
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

class DatabaseDateFormatter {

    companion object {
        private val FORMATTER_SECONDS: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        fun bucketByHour(date: LocalDateTime = LocalDateTime.now()): String {
            val byHour = date
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
            return FORMATTER_SECONDS.format(byHour)
        }

        fun timestamp(date: LocalDateTime = LocalDateTime.now()): String {
            return FORMATTER_SECONDS.format(date)
        }

        fun iso8601(date: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): String {
            return date.format(DateTimeFormatter.ISO_INSTANT)
        }

        fun iso8601Date(timestamp: String): LocalDate {
            return OffsetDateTime.parse(timestamp).toLocalDate()
        }

        fun millisIso8601(date: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)): Long {
            return date.toInstant().toEpochMilli()
        }

        fun parseMillisIso8601(offsetDateMillis: Long): String {
            val odt = OffsetDateTime.ofInstant(Instant.ofEpochMilli(offsetDateMillis), ZoneOffset.UTC)
            return odt.format(DateTimeFormatter.ISO_INSTANT)
        }

        fun parseIso8601ToMillis(dateTime: String): Long {
            return Instant.parse(dateTime).toEpochMilli()
        }

        fun duration(
            start: String,
            end: String = FORMATTER_SECONDS.format(LocalDateTime.now()),
        ): Duration {
            val startTime = LocalDateTime.parse(start, FORMATTER_SECONDS)
            val endTime = LocalDateTime.parse(end, FORMATTER_SECONDS)
            return Duration.between(startTime, endTime)
        }
    }
}
