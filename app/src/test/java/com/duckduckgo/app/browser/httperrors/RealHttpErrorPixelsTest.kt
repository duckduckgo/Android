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

package com.duckduckgo.app.browser.httperrors

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import java.time.Instant
import java.util.concurrent.TimeUnit
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

@RunWith(AndroidJUnit4::class)
class RealHttpErrorPixelsTest {

    private lateinit var testee: HttpErrorPixels
    private lateinit var prefs: SharedPreferences

    private val mockPixel: Pixel = mock()
    private val mockContext: Context = mock()

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        whenever(mockContext.getSharedPreferences("com.duckduckgo.app.browser.httperrors", 0)).thenReturn(prefs)
        testee = RealHttpErrorPixels(mockPixel, mockContext)
    }

    @Test
    fun whenUpdateCountPixelCalledThenSharedPrefUpdated() {
        val key = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_count"
        assertEquals(0, prefs.getInt(key, 0))

        testee.updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        testee.updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)

        assertEquals(2, prefs.getInt(key, 0))
    }

    @Test
    fun whenFireCountPixelCalledForZeroCountThenPixelNotSent() {
        val key = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_count"
        assertEquals(0, prefs.getInt(key, 0))

        testee.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)

        verify(mockPixel, never()).fire(
            pixel = eq(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY),
            parameters = any(),
            encodedParameters = any(),
            type = eq(Count),
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeNotSetThenPixelSent() {
        val key = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_count"
        testee.updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        assertEquals(1, prefs.getInt(key, 0))

        testee.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)

        verify(mockPixel).fire(
            pixel = eq(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY),
            parameters = eq(mapOf(HttpErrorPixelParameters.HTTP_ERROR_CODE_COUNT to "1")),
            encodedParameters = any(),
            type = eq(Count),
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeBeforeTimestampThenPixelNotSent() {
        val key = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_count"
        val timestampKey = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_timestamp"
        val now = Instant.now().toEpochMilli()
        testee.updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        assertEquals(1, prefs.getInt(key, 0))
        prefs.edit { putLong(timestampKey, now.plus(TimeUnit.HOURS.toMillis(1))) }

        testee.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)

        verify(mockPixel, never()).fire(
            pixel = eq(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY),
            parameters = any(),
            encodedParameters = any(),
            type = eq(Count),
        )
    }

    @Test
    fun whenFireCountPixelCalledForNonZeroCountAndCurrentTimeAfterTimestampThenPixelSent() {
        val key = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_count"
        val timestampKey = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY.pixelName}_timestamp"
        val now = Instant.now().toEpochMilli()
        testee.updateCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)
        assertEquals(1, prefs.getInt(key, 0))
        prefs.edit { putLong(timestampKey, now.minus(TimeUnit.HOURS.toMillis(1))) }

        testee.fireCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY)

        verify(mockPixel).fire(
            pixel = eq(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_400_DAILY),
            parameters = eq(mapOf(HttpErrorPixelParameters.HTTP_ERROR_CODE_COUNT to "1")),
            encodedParameters = any(),
            type = eq(Count),
        )
    }
}
