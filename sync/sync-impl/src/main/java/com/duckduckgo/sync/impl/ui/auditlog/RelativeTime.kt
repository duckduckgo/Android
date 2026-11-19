/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.ui.auditlog

import android.content.res.Resources
import com.duckduckgo.sync.impl.R
import java.time.Duration
import java.time.Instant

sealed interface RelativeTime {
    data object JustNow : RelativeTime
    data class Minutes(val count: Int) : RelativeTime
    data class Hours(val count: Int) : RelativeTime
    data class Days(val count: Int) : RelativeTime
    data class Months(val count: Int) : RelativeTime
    data class Years(val count: Int) : RelativeTime

    companion object {
        /** Same buckets as the desktop apps, so every platform shows the same text for one entry. */
        fun between(occurredAt: Instant, now: Instant): RelativeTime {
            val elapsed = Duration.between(occurredAt, now)
            val days = elapsed.toDays().toInt()
            return when {
                elapsed < Duration.ofMinutes(1) -> JustNow
                elapsed < Duration.ofHours(1) -> Minutes(elapsed.toMinutes().toInt())
                elapsed < Duration.ofDays(1) -> Hours(elapsed.toHours().toInt())
                days < DAYS_PER_MONTH -> Days(days)
                days < DAYS_PER_YEAR -> Months(days / DAYS_PER_MONTH)
                else -> Years(days / DAYS_PER_YEAR)
            }
        }

        private const val DAYS_PER_MONTH = 30
        private const val DAYS_PER_YEAR = 365
    }
}

fun RelativeTime.format(resources: Resources): String = when (this) {
    RelativeTime.JustNow -> resources.getString(R.string.syncAuditLogJustNow)
    is RelativeTime.Minutes -> resources.getQuantityString(R.plurals.syncAuditLogMinutesAgo, count, count)
    is RelativeTime.Hours -> resources.getQuantityString(R.plurals.syncAuditLogHoursAgo, count, count)
    is RelativeTime.Days -> resources.getQuantityString(R.plurals.syncAuditLogDaysAgo, count, count)
    is RelativeTime.Months -> resources.getQuantityString(R.plurals.syncAuditLogMonthsAgo, count, count)
    is RelativeTime.Years -> resources.getQuantityString(R.plurals.syncAuditLogYearsAgo, count, count)
}
