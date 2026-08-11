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
import java.util.WeakHashMap
import javax.inject.Inject

interface BrokenSiteRefreshesInMemoryStore {
    fun addRefresh(owner: RefreshPatternOwner, url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(owner: RefreshPatternOwner): Set<RefreshPattern>
    fun isRefreshPatternDetectionValid(url: Uri, currentDateTime: LocalDateTime): Boolean
}

class RefreshPatternOwner

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSiteRefreshesInMemoryStore @Inject constructor() : BrokenSiteRefreshesInMemoryStore {

    private val refreshStates = WeakHashMap<RefreshPatternOwner, RefreshPatternState>()

    @Synchronized
    override fun addRefresh(
        owner: RefreshPatternOwner,
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        val state = refreshStates.getOrPut(owner, ::RefreshPatternState)
        resetIfUrlChanged(state, url)
        state.doubleRefreshTimes = state.doubleRefreshTimes.plus(localDateTime)
        state.tripleRefreshTimes = state.tripleRefreshTimes.plus(localDateTime)
        detectPatterns(state, url, localDateTime)
    }

    @Synchronized
    override fun getRefreshPatterns(owner: RefreshPatternOwner): Set<RefreshPattern> {
        val state = refreshStates[owner] ?: return emptySet()
        val detection = state.pendingDetection ?: return emptySet()
        state.pendingDetection = detection.copy(patterns = emptySet())
        return detection.patterns
    }

    @Synchronized
    override fun isRefreshPatternDetectionValid(
        url: Uri,
        currentDateTime: LocalDateTime,
    ): Boolean {
        return refreshStates.values.any { state ->
            state.pendingDetection?.let { detection ->
                detection.url == url && !detection.detectedAt.isBefore(currentDateTime.minusSeconds(DETECTION_STALENESS_IN_SECS))
            } == true
        }
    }

    private fun resetIfUrlChanged(
        state: RefreshPatternState,
        url: Uri,
    ) {
        if (state.lastRefreshedUrl != url) {
            state.lastRefreshedUrl = url
            state.doubleRefreshTimes = emptyList()
            state.tripleRefreshTimes = emptyList()
            state.pendingDetection = null
        }
    }

    private fun detectPatterns(
        state: RefreshPatternState,
        url: Uri,
        currentDateTime: LocalDateTime,
    ) {
        pruneOldRefreshes(state, currentDateTime)
        val detectedPatterns = buildSet {
            if (state.doubleRefreshTimes.size >= TWICE_REFRESH_THRESHOLD) {
                add(RefreshPattern.TWICE_IN_12_SECONDS)
                state.doubleRefreshTimes = emptyList()
            }
            if (state.tripleRefreshTimes.size >= THRICE_REFRESH_THRESHOLD) {
                add(RefreshPattern.THRICE_IN_20_SECONDS)
                state.tripleRefreshTimes = emptyList()
            }
        }

        if (detectedPatterns.isNotEmpty()) {
            state.pendingDetection = RefreshPatternDetection(
                patterns = state.pendingDetection?.patterns.orEmpty() + detectedPatterns,
                url = url,
                detectedAt = currentDateTime,
            )
        }
    }

    private fun pruneOldRefreshes(
        state: RefreshPatternState,
        currentDateTime: LocalDateTime,
    ) {
        state.doubleRefreshTimes =
            state.doubleRefreshTimes.filter { it.isAfter(currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS)) }
        state.tripleRefreshTimes =
            state.tripleRefreshTimes.filter { it.isAfter(currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS)) }
    }

    companion object {
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
        private const val DETECTION_STALENESS_IN_SECS = 60L
        private const val TWICE_REFRESH_THRESHOLD = 2
        private const val THRICE_REFRESH_THRESHOLD = 3
    }
}

private class RefreshPatternState(
    var lastRefreshedUrl: Uri? = null,
    var doubleRefreshTimes: List<LocalDateTime> = emptyList(),
    var tripleRefreshTimes: List<LocalDateTime> = emptyList(),
    var pendingDetection: RefreshPatternDetection? = null,
)

private data class RefreshPatternDetection(
    val patterns: Set<RefreshPattern>,
    val url: Uri,
    val detectedAt: LocalDateTime,
)
