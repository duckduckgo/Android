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
import com.duckduckgo.sync.api.engine.SyncableType
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result.Error
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

class RealSyncPixelsTest {

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

        testee.fireDailySuccessRatePixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats)

        verify(pixel).fire(SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL, payload)
    }

    @Test
    fun whenDailyPixelCalledTwiceThenPixelFiredOnce() {
        val dailyStats = givenSomeDailyStats()

        testee.fireDailySuccessRatePixel()
        testee.fireDailySuccessRatePixel()

        val payload = mapOf(
            SyncPixelParameters.COUNT to dailyStats.attempts,
            SyncPixelParameters.DATE to dailyStats.date,
        ).plus(dailyStats.apiErrorStats).plus(dailyStats.operationErrorStats)

        verify(pixel, times(1)).fire(SyncPixelName.SYNC_DAILY_SUCCESS_RATE_PIXEL, payload)
    }

    @Test
    fun whenLoginPixelCalledThenPixelFired() {
        testee.fireLoginPixel()

        verify(pixel).fire(SyncPixelName.SYNC_LOGIN)
    }

    @Test
    fun whenSignupDirectPixelCalledWithNoSourceThenPixelFired() {
        testee.fireSignupDirectPixel(source = null)

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_DIRECT)
    }

    @Test
    fun whenSignupDirectPixelCalledWithSourceThenPixelFiredIncludesSource() {
        testee.fireSignupDirectPixel(source = "foo")
        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_DIRECT, mapOf("source" to "foo"))
    }

    @Test
    fun whenSignupConnectPixelCalledWithNoSourceThenPixelFired() {
        testee.fireSignupConnectPixel(source = null)

        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_CONNECT)
    }

    @Test
    fun whenSignupConnectPixelCalledWithSourceThenPixelFiredIncludesSource() {
        testee.fireSignupConnectPixel(source = "foo")
        verify(pixel).fire(SyncPixelName.SYNC_SIGNUP_CONNECT, mapOf("source" to "foo"))
    }

    @Test
    fun whenfireDailyApiErrorForObjectLimitExceededThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.COUNT_LIMIT.code))

        verify(pixel).fire("m_sync_bookmarks_object_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForRequestSizeLimitExceededThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.CONTENT_TOO_LARGE.code))

        verify(pixel).fire("m_sync_bookmarks_request_size_limit_exceeded_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForValidationErrorThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.VALIDATION_ERROR.code))

        verify(pixel).fire("m_sync_bookmarks_validation_error_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenfireDailyApiErrorForTooManyRequestsThenPixelSent() {
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_1.code))
        testee.fireDailySyncApiErrorPixel(SyncableType.BOOKMARKS, Error(code = API_CODE.TOO_MANY_REQUESTS_2.code))

        verify(pixel, times(2)).fire("m_sync_bookmarks_too_many_requests_daily", emptyMap(), emptyMap(), type = Pixel.PixelType.Daily())
    }

    @Test
    fun whenFireSyncAccountErrorPixelForRescopeTokenThenPixelSent() {
        val error = Error(code = 401, reason = "unauthorized")

        testee.fireSyncAccountErrorPixel(error, SyncAccountOperation.RESCOPE_TOKEN)

        verify(pixel).fire(
            SyncPixelName.SYNC_RESCOPE_TOKEN_FAILURE,
            mapOf(
                SyncPixelParameters.ERROR_CODE to "401",
                SyncPixelParameters.ERROR_REASON to "unauthorized",
            ),
        )
    }

    private fun givenSomeDailyStats(): DailyStats {
        val date = DatabaseDateFormatter.getUtcIsoLocalDate()
        val dailyStats = DailyStats("1", date, emptyMap())
        whenever(syncStatsRepository.getYesterdayDailyStats()).thenReturn(dailyStats)

        return dailyStats
    }
}
