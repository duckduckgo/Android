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

import android.content.Context
import com.duckduckgo.app.global.R
import com.duckduckgo.app.global.formatters.time.model.TimePassed
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import org.threeten.bp.Duration
import org.threeten.bp.LocalDateTime
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

interface TimeDiffFormatter {
    fun formatTimePassedInDays(
        endLocalDateTime: LocalDateTime = LocalDateTime.now(),
        startLocalDateTime: LocalDateTime
    ): String

    fun formatTimePassed(
        endLocalDateTime: LocalDateTime = LocalDateTime.now(),
        startLocalDateTime: LocalDateTime
    ): String

}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealTimeDiffFormatter @Inject constructor(private val context: Context) : TimeDiffFormatter {

    override fun formatTimePassedInDays(
        endLocalDateTime: LocalDateTime,
        startLocalDateTime: LocalDateTime
    ): String {
        val startDate = DatabaseDateFormatter.timestamp(startLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }
        val endDate = DatabaseDateFormatter.timestamp(endLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }

        val diff = (endDate.time - startDate.time).run { TimeUnit.DAYS.convert(this, TimeUnit.MILLISECONDS) }

        return when (diff) {
            0L -> context.getString(R.string.common_Today)
            1L -> context.getString(R.string.common_Yesterday)
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(startDate)
        }
    }

    override fun formatTimePassed(
        endLocalDateTime: LocalDateTime,
        startLocalDateTime: LocalDateTime
    ): String {
        val timeDifferenceMillis = Duration.between(startLocalDateTime, endLocalDateTime).toMillis()
        val startDate = DatabaseDateFormatter.timestamp(startLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }
        val endDate = DatabaseDateFormatter.timestamp(endLocalDateTime).substringBefore("T")
            .run { FORMATTER_DAYS.parse(this) }

        val timeDifferenceDate = (endDate.time - startDate.time).run { TimeUnit.DAYS.convert(this, TimeUnit.MILLISECONDS) }

        return when (timeDifferenceDate) {
            0L, 1L -> TimePassed.fromMilliseconds(timeDifferenceMillis).shortFormat()
            else -> context.getString(R.string.common_DaysAgo, timeDifferenceDate)
        }
    }

    companion object {
        private val FORMATTER_DAYS = SimpleDateFormat("yyyy-MM-dd")
    }
}
