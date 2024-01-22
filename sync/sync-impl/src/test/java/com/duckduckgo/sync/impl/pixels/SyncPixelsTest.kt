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

package com.duckduckgo.sync.impl.pixels

import android.content.SharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.sync.impl.stats.DailyStats
import com.duckduckgo.sync.impl.stats.SyncStatsRepository
import com.duckduckgo.sync.store.SharedPrefsProvider
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SyncPixelsTest {

    private var pixel: Pixel = mock()
    private var syncStatsRepository: SyncStatsRepository = mock()
    private var sharedPrefsProv: SharedPrefsProvider = mock()

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var testee: RealSyncPixels

    @Before
    fun setUp() {
        sharedPreferences = InMemorySharedPreferences()
        whenever(
            sharedPrefsProv.getSharedPrefs(eq("com.duckduckgo.sync.pixels.v1")),
        ).thenReturn(sharedPreferences)

        testee = RealSyncPixels(
            pixel,
            syncStatsRepository,
            sharedPrefsProv,
        )
    }

    @Test
    fun whenDailyPixelCalledThenPixelFired() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailyPixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats)

        verify(pixel).fire(SyncPixelName.SYNC_DAILY_PIXEL, payload)
    }

    @Test
    fun whenDailyPixelCalledTwiceThenPixelFiredOnce() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailyPixel()
        testee.fireDailyPixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats).plus(dailyStats.operationErrorStats)

        verify(pixel, times(1)).fire(SyncPixelName.SYNC_DAILY_PIXEL, payload)
    }

    @Test
    fun whenLoginPixelCalledThenPixelFired() {
        testee.fireLoginPixel()

        verify(pixel).fire(SyncPixelName.SYNC_LOGIN)
    }

    @Test
    fun whenSignupDirectPixelCalledThenPixelFired() {
        testee.fireSignupDirectPixel()

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_DIRECT)
    }

    @Test
    fun whenSignupConnectPixelCalledThenPixelFired() {
        testee.fireSignupConnectPixel()

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_CONNECT)
    }

    private fun givenSomeDailyStats(): DailyStats {
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()
        val dailyStats = DailyStats("1", date, emptyMap())
        whenever(syncStatsRepository.getYesterdayDailyStats()).thenReturn(dailyStats)

        return dailyStats
    }
}
