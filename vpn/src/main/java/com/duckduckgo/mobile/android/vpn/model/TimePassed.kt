/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.model

import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import org.threeten.bp.LocalDateTime

fun dateOfPreviousMidnight(): String {
    val midnight = LocalDateTime.now().toLocalDate().atStartOfDay()
    return DatabaseDateFormatter.timestamp(midnight)
}

fun dateOfLastDay(): String {
    val day = LocalDateTime.now().minusHours(24)
    return DatabaseDateFormatter.timestamp(day)
}

fun dateOfLastHour(): String {
    val midnight = LocalDateTime.now().minusHours(1)
    return DatabaseDateFormatter.timestamp(midnight)
}

fun dateOfLastWeek(): String {
    val midnight = LocalDateTime.now().minusDays(7)
    return DatabaseDateFormatter.timestamp(midnight)
}

data class TimePassed(
    val hours: Long,
    val minutes: Long,
    val seconds: Long
) {

    fun shortFormat(): String {
        val sb = StringBuilder()

        if (hours > 0) {
            sb.append(hours)
            sb.append("h ago")
            return sb.toString()
        }

        if (minutes > 2) {
            sb.append(minutes)
            sb.append("m ago")
            return sb.toString()
        }

        sb.append("just now")
        return sb.toString()
    }

    fun format(
        alwaysShowHours: Boolean = true,
        alwaysShowMinutes: Boolean = true,
        alwaysShowSeconds: Boolean = true
    ): String {
        val sb = StringBuilder()

        if (hours > 0 || alwaysShowHours) {
            sb.append(hours)
            sb.append(" hr")
        }

        if (minutes > 0 || alwaysShowMinutes) {
            sb.append(" ")
            sb.append(minutes)
            sb.append(" min")
        }

        if (alwaysShowSeconds) {
            sb.append(" ")
            sb.append(seconds)
            sb.append(" sec")
        }

        return sb.toString()
    }

    companion object {

        fun between(
            currentMillis: Long,
            oldMillis: Long
        ): TimePassed {
            return fromMilliseconds(currentMillis - oldMillis)
        }

        fun fromMilliseconds(millis: Long): TimePassed {
            val seconds = (millis / 1000) % 60
            val minutes = (millis / (1000 * 60) % 60)
            val hours = (millis / (1000) / 3600)

            return TimePassed(hours, minutes, seconds)
        }
    }
}
