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
    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(): Set<RefreshPattern>
    fun isRefreshPatternDetectionValid(url: Uri, currentDateTime: LocalDateTime): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSiteRefreshesInMemoryStore @Inject constructor() : BrokenSiteRefreshesInMemoryStore {

    private var lastRefreshedUrl: Uri? = null
    private var doubleRefreshTimes = emptyList<LocalDateTime>()
    private var tripleRefreshTimes = emptyList<LocalDateTime>()
    private var pendingDetection: RefreshPatternDetection? = null

    @Synchronized
    override fun addRefresh(
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        resetIfUrlChanged(url)
        doubleRefreshTimes = doubleRefreshTimes.plus(localDateTime)
        tripleRefreshTimes = tripleRefreshTimes.plus(localDateTime)
        detectPatterns(url, localDateTime)
    }

    @Synchronized
    override fun getRefreshPatterns(): Set<RefreshPattern> {
        val detection = pendingDetection ?: return emptySet()
        pendingDetection = detection.copy(patterns = emptySet())
        return detection.patterns
    }

    @Synchronized
    override fun isRefreshPatternDetectionValid(
        url: Uri,
        currentDateTime: LocalDateTime,
    ): Boolean {
        val detection = pendingDetection ?: return false
        return detection.url == url && !detection.detectedAt.isBefore(currentDateTime.minusSeconds(DETECTION_STALENESS_IN_SECS))
    }

    private fun resetIfUrlChanged(url: Uri) {
        if (lastRefreshedUrl != url) {
            lastRefreshedUrl = url
            doubleRefreshTimes = emptyList()
            tripleRefreshTimes = emptyList()
            pendingDetection = null
        }
    }

    private fun detectPatterns(
        url: Uri,
        currentDateTime: LocalDateTime,
    ) {
        pruneOldRefreshes(currentDateTime)
        val detectedPatterns = buildSet {
            if (doubleRefreshTimes.size >= TWICE_REFRESH_THRESHOLD) {
                add(RefreshPattern.TWICE_IN_12_SECONDS)
                doubleRefreshTimes = emptyList()
            }
            if (tripleRefreshTimes.size >= THRICE_REFRESH_THRESHOLD) {
                add(RefreshPattern.THRICE_IN_20_SECONDS)
                tripleRefreshTimes = emptyList()
            }
        }

        if (detectedPatterns.isNotEmpty()) {
            pendingDetection = RefreshPatternDetection(
                patterns = pendingDetection?.patterns.orEmpty() + detectedPatterns,
                url = url,
                detectedAt = currentDateTime,
            )
        }
    }

    private fun pruneOldRefreshes(currentDateTime: LocalDateTime) {
        doubleRefreshTimes = doubleRefreshTimes.filter { it.isAfter(currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS)) }
        tripleRefreshTimes = tripleRefreshTimes.filter { it.isAfter(currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS)) }
    }

    companion object {
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
        private const val DETECTION_STALENESS_IN_SECS = 60L
        private const val TWICE_REFRESH_THRESHOLD = 2
        private const val THRICE_REFRESH_THRESHOLD = 3
    }
}

private data class RefreshPatternDetection(
    val patterns: Set<RefreshPattern>,
    val url: Uri,
    val detectedAt: LocalDateTime,
)
