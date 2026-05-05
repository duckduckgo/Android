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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.featureflags.OkHttpInterceptorRefactorFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
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
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockChain: Interceptor.Chain = mock()
    private val mockResponse: Response = mock()

    @Before
    fun setUp() {
        testee = PirPixelInterceptor(
            mockAppBuildConfig,
            FakeFeatureToggleFactory.create(OkHttpInterceptorRefactorFeature::class.java),
        )
        whenever(mockChain.proceed(any())).thenReturn(mockResponse)
        whenever(mockAppBuildConfig.sdkInt).thenReturn(30)
        whenever(mockAppBuildConfig.manufacturer).thenReturn("Samsung")
    }

    @Test
    fun whenGetInterceptorThenReturnsSelf() {
        assertEquals(testee, testee.getInterceptor())
    }

    @Test
    fun whenInterceptNonPirPixelThenDoesNotAddAnyParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/some_other_pixel")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val man = capturedRequest.url.queryParameter("manufacturer")
        assertNull(man)
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
        val man = capturedRequest.url.queryParameter("manufacturer")
        assertEquals("samsung", man)
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
        assertEquals("samsung", capturedRequest.url.queryParameter("manufacturer"))
    }

    @Test
    fun whenManufacturerIsCommonButMixedCaseThenLowercased() = runTest {
        whenever(mockAppBuildConfig.manufacturer).thenReturn("OnePlus")
        val request = Request.Builder()
            .url("https://example.com/m_dbp_initial_scan_duration")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertEquals("oneplus", capturedRequest.url.queryParameter("manufacturer"))
    }

    @Test
    fun whenManufacturerIsNotCommonThenReportedAsOther() = runTest {
        whenever(mockAppBuildConfig.manufacturer).thenReturn("ObscureBrand")
        val request = Request.Builder()
            .url("https://example.com/m_dbp_initial_scan_duration")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertEquals("other", capturedRequest.url.queryParameter("manufacturer"))
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
    fun whenInterceptLowMemoryPixelThenAddsManufacturerParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_low-memory")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val man = capturedRequest.url.queryParameter("manufacturer")
        assertEquals("samsung", man)
    }

    @Test
    fun whenInterceptStartFailedPixelThenAddsManufacturerParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_foreground-run_start-failed")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val man = capturedRequest.url.queryParameter("manufacturer")
        assertEquals("samsung", man)
    }

    @Test
    fun whenInterceptInitialScanIncompletePixelThenAddsManufacturerParameter() = runTest {
        val request = Request.Builder()
            .url("https://example.com/m_dbp_initial-scan_incomplete")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        val man = capturedRequest.url.queryParameter("manufacturer")
        assertEquals("samsung", man)
    }
}
