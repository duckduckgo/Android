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

package com.duckduckgo.mobile.android.vpn.time

import android.content.Context
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.model.TimePassed
import com.duckduckgo.mobile.android.vpn.store.DatabaseDateFormatter
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TimeDiffFormatter @Inject constructor(private val context: Context) {

    fun formatTimePassedInDays(endLocalDateTime: LocalDateTime = LocalDateTime.now(), startLocalDateTime: LocalDateTime): String {
        val startDate = DatabaseDateFormatter.timestamp(startLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }
        val endDate = DatabaseDateFormatter.timestamp(endLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }

        val diff = (endDate.time - startDate.time).run { TimeUnit.DAYS.convert(this, TimeUnit.MILLISECONDS) }

        return when (diff) {
            0L -> context.getString(R.string.atp_ActivityToday)
            1L -> context.getString(R.string.atp_ActivityYesterday)
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate)
        }
    }

    fun formatTimePassed(endLocalDateTime: LocalDateTime = LocalDateTime.now(), startLocalDateTime: LocalDateTime): String {
        val timeDifferenceMillis = Duration.between(startLocalDateTime, endLocalDateTime).toMillis()
        val startDate = DatabaseDateFormatter.timestamp(startLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }
        val endDate = DatabaseDateFormatter.timestamp(endLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }

        val timeDifferenceDate = (endDate.time - startDate.time).run { TimeUnit.DAYS.convert(this, TimeUnit.MILLISECONDS) }

        return when (timeDifferenceDate) {
            0L, 1L -> TimePassed.fromMilliseconds(timeDifferenceMillis).shortFormat()
            else -> context.getString(R.string.atp_ActivityDaysAgo, timeDifferenceDate)
        }
    }

    companion object {
        private val FORMATTER_DAYS = SimpleDateFormat("yyyy-MM-dd")
    }
}
