/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.brokensite.impl

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.global.sha256
import com.duckduckgo.brokensite.store.BrokenSiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.threeten.bp.Instant
import org.threeten.bp.LocalDateTime
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter

interface BrokenSiteReportRepository {
    suspend fun getLastSentDay(hostname: String): String?
    fun setLastSentDay(hostname: String)

    fun cleanupOldEntries()
}

class RealBrokenSiteReportRepository constructor(
    private val database: BrokenSiteDatabase,
    private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : BrokenSiteReportRepository {

    override suspend fun getLastSentDay(hostname: String): String? {
        if (hostname.isEmpty()) return null

        return withContext(dispatcherProvider.io()) {
            val hostnameHashPrefix = hostname.sha256.take(PREFIX_LENGTH)
            val lastSeenTimestamp = database.brokenSiteDao().getBrokenSiteReport(hostnameHashPrefix)?.lastSentTimestamp
            if (lastSeenTimestamp != null) {
                convertToShortDate(lastSeenTimestamp)
            } else {
                null
            }
        }
    }

    override fun setLastSentDay(hostname: String) {
        if (hostname.isEmpty()) return

        coroutineScope.launch(dispatcherProvider.io()) {
            val hostnameHashPrefix = hostname.sha256.take(PREFIX_LENGTH)
            database.brokenSiteDao().upsertBrokenSiteReport(hostnameHashPrefix)
        }
    }

    override fun cleanupOldEntries() {
        coroutineScope.launch(dispatcherProvider.io()) {
            val expiryTime = getUTCDate30DaysAgo()
            database.brokenSiteDao().cleanupBrokenSiteReport(expiryTime)
        }
    }

    private fun convertToShortDate(dateString: String): String {
        val inputFormatter = DateTimeFormatter.ISO_INSTANT
        val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val instant = Instant.from(inputFormatter.parse(dateString))
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC)

        return localDateTime.format(outputFormatter)
    }

    private fun getUTCDate30DaysAgo(): String {
        val currentDate = OffsetDateTime.now()
        val daysAgo = currentDate.minusDays(THIRTY_DAYS_AGO)
        return DatabaseDateFormatter.iso8601(daysAgo)
    }

    companion object {
        private const val PREFIX_LENGTH = 6
        private const val THIRTY_DAYS_AGO = 30L
    }
}
