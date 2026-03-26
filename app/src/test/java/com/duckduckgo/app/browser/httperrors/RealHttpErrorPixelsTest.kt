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
import com.duckduckgo.app.browser.httperrors.RealHttpErrorPixels.Companion.PIXEL_5XX_KEYS_SET
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.api.InMemorySharedPreferences
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.network.ExternalVpnDetector
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealHttpErrorPixelsTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private lateinit var testee: HttpErrorPixels
    private lateinit var prefs: SharedPreferences

    private val mockPixel: Pixel = mock()
    private val mockContext: Context = mock()
    private val mockWebViewVersionProvider: WebViewVersionProvider = mock()
    private val mockNetworkProtectionState: NetworkProtectionState = mock()
    private val mockExternalVpnDetector: ExternalVpnDetector = mock()
    private var fakeAndroidConfigBrowserFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        whenever(mockContext.getSharedPreferences("com.duckduckgo.app.browser.httperrors", 0)).thenReturn(prefs)
        testee = RealHttpErrorPixels(
            mockPixel,
            mockContext,
            mockWebViewVersionProvider,
            mockNetworkProtectionState,
            mockExternalVpnDetector,
            fakeAndroidConfigBrowserFeature,
        )
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

    @Test
    fun whenUpdate5xxCountPixelCalledThenSharedPrefUpdated() = runTest {
        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = true))
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
        whenever(mockExternalVpnDetector.isExternalVpnDetected()).thenReturn(false)
        whenever(mockWebViewVersionProvider.getFullVersion()).thenReturn("123.45.67.89")

        // The pixelKey format is: pixelName|statusCode|pProVpnConnected|externalVpnConnected|webViewVersion|_count
        val expectedKey = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY.pixelName}|503|true|false|123.45.67.89|_count"

        testee.update5xxCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY, 503)

        assertEquals(1, prefs.getInt(expectedKey, 0))
        assertTrue(prefs.getStringSet(PIXEL_5XX_KEYS_SET, emptySet())!!.contains(expectedKey))
    }

    @Test
    fun whenUpdate5xxCountPixelCalledMultipleTimesThenCounterIncremented() = runTest {
        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = true))
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
        whenever(mockExternalVpnDetector.isExternalVpnDetected()).thenReturn(false)
        whenever(mockWebViewVersionProvider.getFullVersion()).thenReturn("123.45.67.89")

        // The pixelKey format is: pixelName|statusCode|pProVpnConnected|externalVpnConnected|webViewVersion|_count
        val expectedKey = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY.pixelName}|503|true|false|123.45.67.89|_count"

        testee.update5xxCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY, 503)
        testee.update5xxCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY, 503)

        assertEquals(2, prefs.getInt(expectedKey, 0))
    }

    @Test
    fun whenUpdate5xxCountPixelCalledWirhFeatureFlagDisabledThenSharedPrefNotUpdated() = runTest {
        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = false))
        whenever(mockNetworkProtectionState.isRunning()).thenReturn(true)
        whenever(mockExternalVpnDetector.isExternalVpnDetected()).thenReturn(false)
        whenever(mockWebViewVersionProvider.getFullVersion()).thenReturn("123.45.67.89")

        // The pixelKey format is: pixelName|statusCode|pProVpnConnected|externalVpnConnected|webViewVersion|_count
        val expectedKey = "${HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY.pixelName}|503|true|false|123.45.67.89|_count"

        testee.update5xxCountPixel(HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY, 503)

        assertEquals(0, prefs.getInt(expectedKey, 0))
        assertFalse(prefs.getStringSet(PIXEL_5XX_KEYS_SET, emptySet())!!.contains(expectedKey))
    }

    @Test
    fun whenFire5xxCountPixelCalledWithNonZeroCountPixelSent() = runTest {
        val pixelName = HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY
        val pixelKey = "${pixelName.pixelName}|503|true|false|123.45.67.89|_count"
        val pixelKeys = mutableSetOf(pixelKey)

        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = true))
        prefs.edit {
            putStringSet(PIXEL_5XX_KEYS_SET, pixelKeys)
            putInt(pixelKey, 5)
        }

        testee.fire5xxCountPixels()

        verify(mockPixel).fire(
            pixel = eq(pixelName),
            parameters = eq(
                mapOf(
                    HttpErrorPixelParameters.HTTP_ERROR_CODE_COUNT to "5",
                    "error_code" to "503",
                    "ppro_user" to "true",
                    "vpn_user" to "false",
                    "webview_version" to "123.45.67.89",
                ),
            ),
            encodedParameters = any(),
            type = any(),
        )

        assertEquals(0, prefs.getInt(pixelKey, -1))
    }

    @Test
    fun whenFire5xxCountPixelCalledWithZeroCountPixelNotSent() {
        val pixelName = HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY
        val pixelKey = "${pixelName.pixelName}|503|true|false|123.45.67.89|_count"
        val pixelKeys = mutableSetOf(pixelKey)

        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = true))
        prefs.edit {
            putStringSet(PIXEL_5XX_KEYS_SET, pixelKeys)
            putInt(pixelKey, 0)
        }

        testee.fire5xxCountPixels()

        verify(mockPixel, never()).fire(
            pixel = eq(pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun whenFire5xxCountPixelsCalledBeforeTimeWindowThenPixelNotSent() {
        val pixelName = HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY
        val pixelKey = "${pixelName.pixelName}|503|true|false|123.45.67.89|_count"
        val pixelKeys = mutableSetOf(pixelKey)
        val now = Instant.now().toEpochMilli()

        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = true))
        prefs.edit {
            putStringSet(PIXEL_5XX_KEYS_SET, pixelKeys)
            putInt(pixelKey, 5)
            putLong("${pixelKey}_timestamp", now + TimeUnit.HOURS.toMillis(1)) // 1 hour in future
        }

        testee.fire5xxCountPixels()

        verify(mockPixel, never()).fire(
            pixel = eq(pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )

        assertEquals(5, prefs.getInt(pixelKey, -1))
    }

    @Test
    fun whenFire5xxCountPixelsCalledWithTheFeatureFlagDisabledThenPixelNotSent() {
        val pixelName = HttpErrorPixelName.WEBVIEW_RECEIVED_HTTP_ERROR_5XX_DAILY
        val pixelKey = "${pixelName.pixelName}|503|true|false|123.45.67.89|_count"

        fakeAndroidConfigBrowserFeature.self().setRawStoredState(State(enable = true))
        fakeAndroidConfigBrowserFeature.httpError5xxPixel().setRawStoredState(State(enable = false))

        testee.fire5xxCountPixels()

        verify(mockPixel, never()).fire(
            pixel = eq(pixelName),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )

        assertEquals(-1, prefs.getInt(pixelKey, -1))
    }
}
