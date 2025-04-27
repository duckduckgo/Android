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

package com.duckduckgo.brokensite.impl

import android.net.Uri
import com.duckduckgo.brokensite.api.DetectedRefreshPattern
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

interface BrokenSiteRefreshesInMemoryStore {
    fun resetRefreshCount()
    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<DetectedRefreshPattern>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSiteRefreshesInMemoryStore @Inject constructor() : BrokenSiteRefreshesInMemoryStore {

    private var refreshes: LastRefreshedUrl? = null

    override fun resetRefreshCount() {
        this.refreshes = null
    }

    override fun addRefresh(
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        refreshes.let {
            refreshes = if (it == null || it.url != url) {
                LastRefreshedUrl(url, mutableListOf(localDateTime))
            } else {
                it.copy(time = it.time.plus(localDateTime))
            }
        }
    }

    override fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<DetectedRefreshPattern> {
        val currentRefreshes = refreshes ?: return emptySet()
        val prunedRefreshes = pruneOldRefreshes(currentDateTime, currentRefreshes)

        val twiceRefreshCount = countPattern(
            refreshEvents = prunedRefreshes.time,
            pattern = RefreshPattern.TWICE_IN_12_SECONDS,
            window = TWICE_REFRESH_WINDOW_IN_SECS,
        )
        val thriceRefreshCount = countPattern(
            refreshEvents = prunedRefreshes.time,
            pattern = RefreshPattern.THRICE_IN_20_SECONDS,
            window = THRICE_REFRESH_WINDOW_IN_SECS,
        )

        val detectedPatterns = mutableSetOf<DetectedRefreshPattern>()
        if (twiceRefreshCount > 0) {
            detectedPatterns.add(DetectedRefreshPattern(RefreshPattern.TWICE_IN_12_SECONDS, twiceRefreshCount))
        }
        if (thriceRefreshCount > 0) {
            detectedPatterns.add(DetectedRefreshPattern(RefreshPattern.THRICE_IN_20_SECONDS, thriceRefreshCount))
        }
        return detectedPatterns
    }

    private fun pruneOldRefreshes(
        currentTime: LocalDateTime,
        refreshes: LastRefreshedUrl,
    ): LastRefreshedUrl {
        val cutoffTime = currentTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS)
        val newTimes = refreshes.time.filter { it.isAfter(cutoffTime) }
        return refreshes.copy(time = newTimes)
    }

    private fun countPattern(refreshEvents: List<LocalDateTime>, pattern: RefreshPattern, window: Long): Int {
        var count = 0
        var i = 0

        while (i <= refreshEvents.size - pattern.number) {
            val startTime = refreshEvents[i]
            val endTime = refreshEvents[i + pattern.number - 1]
            val diffSeconds = Duration.between(startTime, endTime).seconds

            if (diffSeconds < window) {
                count++
                i += pattern.number
            } else {
                i++
            }
        }
        return count
    }

    companion object {
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
    }
}

data class LastRefreshedUrl(
    val url: Uri,
    val time: List<LocalDateTime>,
)
