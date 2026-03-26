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

import android.net.Uri
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.brokensite.store.BrokenSiteDatabase
import com.duckduckgo.brokensite.store.BrokenSiteLastSentReportEntity
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.common.utils.sha256
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface BrokenSiteReportRepository {
    suspend fun getLastSentDay(hostname: String): String?
    fun setLastSentDay(hostname: String)

    fun cleanupOldEntries()

    suspend fun setMaxDismissStreak(maxDismissStreak: Int)
    suspend fun getMaxDismissStreak(): Int

    suspend fun setDismissStreakResetDays(days: Int)
    suspend fun getDismissStreakResetDays(): Int

    suspend fun setCoolDownDays(days: Long)
    suspend fun getCoolDownDays(): Long

    suspend fun setBrokenSitePromptRCSettings(maxDismissStreak: Int, dismissStreakResetDays: Int, coolDownDays: Int)

    suspend fun setNextShownDate(nextShownDate: LocalDateTime?)
    suspend fun getNextShownDate(): LocalDateTime?

    suspend fun addDismissal(dismissal: LocalDateTime)
    suspend fun clearAllDismissals()
    suspend fun getDismissalCountBetween(t1: LocalDateTime, t2: LocalDateTime): Int

    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern>
}

class RealBrokenSiteReportRepository(
    private val database: BrokenSiteDatabase,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val brokenSitePromptDataStore: BrokenSitePromptDataStore,
    private val brokenSiteRefreshesInMemoryStore: BrokenSiteRefreshesInMemoryStore,
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
            database.brokenSiteDao().insertBrokenSiteReport(BrokenSiteLastSentReportEntity(hostnameHashPrefix))
        }
    }

    override fun cleanupOldEntries() {
        coroutineScope.launch(dispatcherProvider.io()) {
            val expiryTime = getUTCDate30DaysAgo()
            database.brokenSiteDao().deleteAllExpiredReports(expiryTime)
            brokenSitePromptDataStore.deleteAllExpiredDismissals(expiryTime, ZoneId.systemDefault())
        }
    }

    override suspend fun setMaxDismissStreak(maxDismissStreak: Int) {
        brokenSitePromptDataStore.setMaxDismissStreak(maxDismissStreak)
    }

    override suspend fun getMaxDismissStreak(): Int =
        brokenSitePromptDataStore.getMaxDismissStreak()

    override suspend fun setDismissStreakResetDays(days: Int) {
        brokenSitePromptDataStore.setDismissStreakResetDays(days)
    }

    override suspend fun getDismissStreakResetDays(): Int =
        brokenSitePromptDataStore.getDismissStreakResetDays()

    override suspend fun setCoolDownDays(days: Long) {
        brokenSitePromptDataStore.setCoolDownDays(days)
    }

    override suspend fun getCoolDownDays(): Long =
        brokenSitePromptDataStore.getCoolDownDays()

    override suspend fun setBrokenSitePromptRCSettings(
        maxDismissStreak: Int,
        dismissStreakResetDays: Int,
        coolDownDays: Int,
    ) {
        setMaxDismissStreak(maxDismissStreak)
        setDismissStreakResetDays(dismissStreakResetDays)
        setCoolDownDays(coolDownDays.toLong())
    }

    override suspend fun setNextShownDate(nextShownDate: LocalDateTime?) {
        brokenSitePromptDataStore.setNextShownDate(nextShownDate)
    }

    override suspend fun getNextShownDate(): LocalDateTime? {
        return brokenSitePromptDataStore.getNextShownDate()
    }

    override suspend fun addDismissal(dismissal: LocalDateTime) {
        brokenSitePromptDataStore.addDismissal(dismissal)
    }

    override suspend fun clearAllDismissals() {
        brokenSitePromptDataStore.clearAllDismissals()
    }

    override suspend fun getDismissalCountBetween(t1: LocalDateTime, t2: LocalDateTime): Int {
        return brokenSitePromptDataStore.getDismissalCountBetween(t1, t2)
    }

    override fun addRefresh(url: Uri, localDateTime: LocalDateTime) {
        brokenSiteRefreshesInMemoryStore.addRefresh(url, localDateTime)
    }

    override fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern> {
        return brokenSiteRefreshesInMemoryStore.getRefreshPatterns(currentDateTime)
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
