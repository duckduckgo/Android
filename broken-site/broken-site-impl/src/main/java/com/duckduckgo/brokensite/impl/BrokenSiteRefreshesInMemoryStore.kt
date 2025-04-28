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
        Timber.d("KateTest--> addRefresh called with url: $url, localDateTime: $localDateTime")

        doubleRefreshes.let {
            doubleRefreshes = if (it == null || it.url != url) {
                LastRefreshedUrl(url, mutableListOf(localDateTime))
            } else {
                it.copy(time = it.time.plus(localDateTime))
            }
            Timber.d("KateTest--> doubleRefreshes updated to: ${doubleRefreshes!!.url} with ${doubleRefreshes!!.time.size} timestamps")
        }

        tripleRefreshes.let {
            tripleRefreshes = if (it == null || it.url != url) {
                LastRefreshedUrl(url, mutableListOf(localDateTime))
            } else {
                it.copy(time = it.time.plus(localDateTime))
            }
            Timber.d("KateTest--> tripleRefreshes updated to: ${tripleRefreshes!!.url} with ${tripleRefreshes!!.time.size} timestamps")
        }
        // refreshes.let {
        //     refreshes = if (it == null || it.url != url) {
        //         LastRefreshedUrl(url, mutableListOf(localDateTime))
        //     } else {
        //         it.copy(time = it.time.plus(localDateTime))
        //     }
        // }
    }

    override fun getRefreshPatterns(currentDateTime: LocalDateTime): Set<RefreshPattern> {
        Timber.d("KateTest--> getRefreshPatterns called with currentDateTime: $currentDateTime")
        Timber.d("KateTest--> doubleRefreshes: $doubleRefreshes, tripleRefreshes: $tripleRefreshes")

        var twiceRefreshCount = 0
        var thriceRefreshCount = 0

        pruneOldRefreshes(currentDateTime)

        Timber.d(
            "KateTest--> prunedDoubleRefreshes.size: ${doubleRefreshes?.time?.size}," +
                "prunedTripleRefreshes.size: ${tripleRefreshes?.time?.size}",
        )

        when {
            doubleRefreshes == null && tripleRefreshes == null -> return emptySet()
            doubleRefreshes == null -> {
                thriceRefreshCount = tripleRefreshes!!.time.filter {
                    it.isAfter(currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS))
                }.size
            }
            tripleRefreshes == null -> {
                twiceRefreshCount = doubleRefreshes!!.time.filter {
                    it.isAfter(currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS))
                }.size
            }
            else -> {
                thriceRefreshCount = tripleRefreshes!!.time.filter {
                    it.isAfter(currentDateTime.minusSeconds(THRICE_REFRESH_WINDOW_IN_SECS))
                }.size
                twiceRefreshCount = doubleRefreshes!!.time.filter {
                    it.isAfter(currentDateTime.minusSeconds(TWICE_REFRESH_WINDOW_IN_SECS))
                }.size
            }
        }

        val detectedPatterns = mutableSetOf<RefreshPattern>()
        if (twiceRefreshCount > 1) {
            detectedPatterns.add(RefreshPattern.TWICE_IN_12_SECONDS)
            resetRefreshCount(RefreshPattern.TWICE_IN_12_SECONDS)
        }
        if (thriceRefreshCount > 2) {
            detectedPatterns.add(RefreshPattern.THRICE_IN_20_SECONDS)
            resetRefreshCount(RefreshPattern.THRICE_IN_20_SECONDS)
        }
        Timber.d("KateTest--> detectedPatterns: $detectedPatterns")
        return detectedPatterns
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

    companion object {
        private const val TWICE_REFRESH_WINDOW_IN_SECS = 12L
        private const val THRICE_REFRESH_WINDOW_IN_SECS = 20L
    }
}

data class LastRefreshedUrl(
    val url: Uri,
    val time: List<LocalDateTime>,
)
