/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.impl.pixels

import android.content.Context
import android.os.PowerManager
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PirPixelInterceptorTest {
    private lateinit var testee: PirPixelInterceptor
    private val mockContext: Context = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockChain: Interceptor.Chain = mock()
    private val mockResponse: Response = mock()
    private val mockPowerManager: PowerManager = mock()

    @Before
    fun setUp() {
        testee = PirPixelInterceptor(mockContext, mockAppBuildConfig)
        whenever(mockChain.proceed(any())).thenReturn(mockResponse)
        whenever(mockContext.packageName).thenReturn("com.test.app")
        whenever(mockContext.getSystemService(Context.POWER_SERVICE)).thenReturn(mockPowerManager)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(30)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("TestManufacturer")
        whenever(mockPowerManager.isIgnoringBatteryOptimizations(any())).thenReturn(true)
    }

    @Test
    fun whenGetInterceptorThenReturnsSelf() {
        assertEquals(testee, testee.getInterceptor())
    }

    @Test
    fun whenInterceptAllowListedPixelThenAddsMetadataParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)
    }

    @Test
    fun whenInterceptNonPirPixelThenDoesNotAddMetadataParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/some_other_pixel")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNull(metadataParam)
    }

    @Test
    fun whenInterceptPirAllowListedPixelThenMetadataContainsCorrectFields() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_completed")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)

        // Decode Base64
        val decodedBytes = Base64.decode(metadataParam, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val decodedJson = String(decodedBytes)
        val jsonObject = JSONObject(decodedJson)

        assertEquals("false", jsonObject.getString("batteryOptimizations"))
        assertEquals("TestManufacturer", jsonObject.getString("man"))
    }

    @Test
    fun whenInterceptPirAllowlistedPixelWithBatteryOptimizationsDisabledThenMetadataShowsTrue() = runTest {
        whenever(mockPowerManager.isIgnoringBatteryOptimizations(any())).thenReturn(false)

        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)

        val decodedBytes = Base64.decode(metadataParam, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val decodedJson = String(decodedBytes)
        val jsonObject = JSONObject(decodedJson)

        assertEquals("true", jsonObject.getString("batteryOptimizations"))
    }

    @Test
    fun whenInterceptPirAllowListedPixelThenPreservesOtherQueryParameters() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started?existing=param&another=value")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertEquals("param", capturedRequest.url.queryParameter("existing"))
        assertEquals("value", capturedRequest.url.queryParameter("another"))
        assertNotNull(capturedRequest.url.queryParameter("metadata"))
    }

    @Test
    fun whenInterceptPirAllowListedThenMetadataIsBase64Encoded() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)

        val decodedBytes = Base64.decode(metadataParam, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        assertNotNull(decodedBytes)
        assert(decodedBytes.isNotEmpty())
    }

    @Test
    fun whenInterceptNonPixelUrlThenDoesNotModifyRequest() = runTest {
        val originalUrl = "https://example.com/api/endpoint"
        val request = Request.Builder()
            .url(originalUrl)
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertEquals(originalUrl, capturedRequest.url.toString())
    }

    @Test
    fun whenBatteryOptimizationCheckThrowsExceptionThenDefaultsToFalse() = runTest {
        whenever(mockPowerManager.isIgnoringBatteryOptimizations(any())).thenThrow(RuntimeException("Test exception"))

        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)

        val decodedBytes = Base64.decode(metadataParam, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val decodedJson = String(decodedBytes)
        val jsonObject = JSONObject(decodedJson)

        // when exception occurs, should default to false (meaning battery optimizations are enabled)
        assertEquals("true", jsonObject.getString("batteryOptimizations"))
    }

    @Test
    fun whenPackageNameIsNullThenBatteryOptimizationCheckReturnsFalse() = runTest {
        whenever(mockContext.packageName).thenReturn(null)

        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_started")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val metadataParam = capturedRequest.url.queryParameter("metadata")
        assertNotNull(metadataParam)

        val decodedBytes = Base64.decode(metadataParam, Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE)
        val decodedJson = String(decodedBytes)
        val jsonObject = JSONObject(decodedJson)

        assertEquals("true", jsonObject.getString("batteryOptimizations"))
    }
}
