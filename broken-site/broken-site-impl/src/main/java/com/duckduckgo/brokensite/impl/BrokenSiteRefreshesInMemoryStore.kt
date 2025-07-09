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
import com.duckduckgo.brokensite.api.RefreshPattern
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDateTime
import javax.inject.Inject

interface BrokenSiteRefreshesInMemoryStore {
    fun resetRefreshCount(pattern: RefreshPattern)
    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern>
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSiteRefreshesInMemoryStore @Inject constructor() : BrokenSiteRefreshesInMemoryStore {

    private var doubleRefreshes: LastRefreshedUrl? = null
    private var tripleRefreshes: LastRefreshedUrl? = null

    override fun resetRefreshCount(pattern: RefreshPattern) {
        when (pattern) {
            RefreshPattern.TWICE_IN_12_SECONDS -> this.doubleRefreshes = null
            RefreshPattern.THRICE_IN_20_SECONDS -> this.tripleRefreshes = null
        }
    }

    override fun addRefresh(
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        doubleRefreshes = updateRefreshes(doubleRefreshes, url, localDateTime)
        tripleRefreshes = updateRefreshes(tripleRefreshes, url, localDateTime)
    }

    override fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern> {
        pruneOldRefreshes(currentDateTime)

        if (doubleRefreshes == null && tripleRefreshes == null) {
            return emptySet()
        }
        val thriceRefreshCount = tripleRefreshes?.time?.count {
            it.isAfter(currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS))
        } ?: 0

        val twiceRefreshCount = doubleRefreshes?.time?.count {
            it.isAfter(currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS))
        } ?: 0

        val detectedPatterns = mutableSetOf<RefreshPattern>()
        addPatternsAndResetCount(detectedPatterns, twiceRefreshCount, TWICE_REFRESH_THRESHOLD, RefreshPattern.TWICE_IN_12_SECONDS)
        addPatternsAndResetCount(detectedPatterns, thriceRefreshCount, THRICE_REFRESH_THRESHOLD, RefreshPattern.THRICE_IN_20_SECONDS)

        return detectedPatterns
    }

    private fun addPatternsAndResetCount(
        detectedPatterns: MutableSet<RefreshPattern>,
        refreshCount: Int,
        threshold: Int,
        pattern: RefreshPattern,
    ) {
        if (refreshCount >= threshold) {
            detectedPatterns.add(pattern)
            resetRefreshCount(pattern)
        }
    }

    private fun pruneOldRefreshes(
        currentTime: LocalDateTime,
    ) {
        val doubleTimes = doubleRefreshes?.time
        val tripleTimes = tripleRefreshes?.time

        if (doubleTimes != null) {
            val cutoffTime = currentTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS)
            val newDoubleTimes = doubleTimes.filter { it.isAfter(cutoffTime) }
            this.doubleRefreshes = this.doubleRefreshes?.copy(time = newDoubleTimes)
        }

        if (tripleTimes != null) {
            val cutoffTime = currentTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS)
            val newTripleTimes = tripleTimes.filter { it.isAfter(cutoffTime) }
            this.tripleRefreshes = this.tripleRefreshes?.copy(time = newTripleTimes)
        }
    }

    private fun updateRefreshes(
        currentRefreshes: LastRefreshedUrl?,
        url: Uri,
        localDateTime: LocalDateTime,
    ): LastRefreshedUrl {
        return if (currentRefreshes == null || currentRefreshes.url != url) {
            LastRefreshedUrl(url, mutableListOf(localDateTime))
        } else {
            currentRefreshes.copy(time = currentRefreshes.time.plus(localDateTime))
        }
    }

    companion object {
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
        private const val TWICE_REFRESH_THRESHOLD = 2
        private const val THRICE_REFRESH_THRESHOLD = 3
    }
}

data class LastRefreshedUrl(
    val url: Uri,
    val time: List<LocalDateTime>,
)
