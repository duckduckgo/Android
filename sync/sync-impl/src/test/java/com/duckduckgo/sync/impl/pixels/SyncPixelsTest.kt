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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.api.InMemorySharedPreferences
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

    private lateinit var testee: RealSyncPixels

    @Before
    fun setUp() {
        val prefs = InMemorySharedPreferences()
        whenever(
            sharedPrefsProv.getSharedPrefs(eq("com.duckduckgo.sync.pixels.v1")),
        ).thenReturn(prefs)

        testee = RealSyncPixels(
            pixel,
            syncStatsRepository,
            sharedPrefsProv,
        )
    }

    @Test
    fun whenDailyPixelCalledThenPixelFired() {
        testee.fireDailyPixel()

        verify(pixel).fire(SyncPixelName.SYNC_DAILY_PIXEL)
    }

    @Test
    fun whenDailyPixelCalledTwiceThenPixelFiredOnce() {
        testee.fireDailyPixel()
        testee.fireDailyPixel()

        verify(pixel).fire(SyncPixelName.SYNC_DAILY_PIXEL)
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
}
