/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.adclick.impl.pixels

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.global.api.InMemorySharedPreferences
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.threeten.bp.Instant
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RealAdClickPixelsTest {

    private lateinit var testee: AdClickPixels
    private lateinit var prefs: SharedPreferences

    private val mockPixel: Pixel = mock()
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        whenever(mockContext.getSharedPreferences("com.duckduckgo.adclick.impl.pixels", 0)).thenReturn(prefs)
        testee = RealAdClickPixels(mockPixel, mockContext)
    }

    @Test
    fun whenUpdateCountPixelCalledThenSharedPrefUpdated() {
        val key = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_count"
        assertEquals(0, prefs.getInt(key, 0))

        testee.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        testee.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)

        assertEquals(2, prefs.getInt(key, 0))
    }

    @Test
    fun whenFireCountPixelCalledForZeroCountThenPixelNotSent() {
        val key = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_count"
        assertEquals(0, prefs.getInt(key, 0))

        testee.fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)

        verify(mockPixel, never()).fire(
            pixel = eq(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION),
            parameters = any(),
            encodedParameters = any()
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeNotSetThenPixelSent() {
        val key = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_count"
        testee.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertEquals(1, prefs.getInt(key, 0))

        testee.fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)

        verify(mockPixel).fire(
            pixel = eq(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION),
            parameters = eq(mapOf(AdClickPixelParameters.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION_COUNT to "1")),
            encodedParameters = any()
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeBeforeTimestampThenPixelNotSent() {
        val key = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_count"
        val timestampKey = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_timestamp"
        val now = Instant.now().toEpochMilli()
        testee.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertEquals(1, prefs.getInt(key, 0))
        prefs.edit { putLong(timestampKey, now.plus(TimeUnit.HOURS.toMillis(1))) }

        testee.fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)

        verify(mockPixel, never()).fire(
            pixel = eq(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION),
            parameters = any(),
            encodedParameters = any()
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeAfterTimestampThenPixelSent() {
        val key = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_count"
        val timestampKey = "${AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION.pixelName}_timestamp"
        val now = Instant.now().toEpochMilli()
        testee.updateCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)
        assertEquals(1, prefs.getInt(key, 0))
        prefs.edit { putLong(timestampKey, now.minus(TimeUnit.HOURS.toMillis(1))) }

        testee.fireCountPixel(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION)

        verify(mockPixel).fire(
            pixel = eq(AdClickPixelName.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION),
            parameters = eq(mapOf(AdClickPixelParameters.AD_CLICK_PAGELOADS_WITH_AD_ATTRIBUTION_COUNT to "1")),
            encodedParameters = any()
        )
    }
}
