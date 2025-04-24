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
import timber.log.Timber

interface BrokenSiteRefreshesInMemoryStore {
    fun resetRefreshCount()
    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern>
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

    override fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern> {
        val currentRefreshes = refreshes ?: return emptySet()
        val detectedPatterns = mutableSetOf<RefreshPattern>()

        val twiceWindowSecondsAgo = currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS)
        val thriceWindowSecondsAgo = currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS)

        val refreshesWithinTwiceWindow = currentRefreshes.time.filter {
            it.isAfter(twiceWindowSecondsAgo) && it.isBefore(currentDateTime)
        }

        if (refreshesWithinTwiceWindow.size >= TWICE_REFRESH) {
            detectedPatterns.add(RefreshPattern.TWICE_IN_12_SECONDS)
            Timber.d("KateTest-> Detected 2 refreshes in 12 seconds for URL: ${currentRefreshes.url}")
        }

        val refreshesWithinThriceWindow = currentRefreshes.time.filter {
            it.isAfter(thriceWindowSecondsAgo) && it.isBefore(currentDateTime)
        }

        if (refreshesWithinThriceWindow.size >= THRICE_REFRESH) {
            detectedPatterns.add(RefreshPattern.THRICE_IN_20_SECONDS)
            Timber.d("KateTest-> Detected 3 refreshes in 20 seconds for URL: ${currentRefreshes.url}")
        }

        // Discard timestamps before the most recent longest window
        refreshes = currentRefreshes.copy(time = refreshesWithinThriceWindow)

        return detectedPatterns
    }

    companion object {
        private const val TWICE_REFRESH = 2
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH = 3
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
    }
}

data class LastRefreshedUrl(
    val url: Uri,
    val time: List<LocalDateTime>,
)
