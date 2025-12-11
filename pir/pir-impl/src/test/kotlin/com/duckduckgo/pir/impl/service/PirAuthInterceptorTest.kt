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

package com.duckduckgo.pir.impl.service

import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import retrofit2.Invocation
import java.io.IOException

class PirAuthInterceptorTest {
    private lateinit var testee: PirAuthInterceptor
    private val mockSubscriptions: Subscriptions = mock()
    private val mockChain: Interceptor.Chain = mock()
    private val mockResponse: Response = mock()

    @Before
    fun setUp() {
        testee = PirAuthInterceptor(mockSubscriptions)
        whenever(mockChain.proceed(any())).thenReturn(mockResponse)
    }

    @Test
    fun whenGetInterceptorThenReturnsSelf() {
        assertEquals(testee, testee.getInterceptor())
    }

    @Test
    fun whenRequestWithoutAuthRequiredAnnotationThenProceedsWithoutAddingHeader() = runTest {
        val request = Request.Builder()
            .url("https://example.com")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        verify(mockChain).proceed(request)
    }

    @Test
    fun whenRequestWithAuthRequiredAnnotationAndValidTokenThenAddsAuthHeader() = runTest {
        val testToken = "test-access-token"
        whenever(mockSubscriptions.getAccessToken()).thenReturn(testToken)

        val mockMethod = TestInterface::class.java.getMethod("authRequiredMethod")
        val invocation = Invocation.of(mockMethod, emptyList<Any>())
        val request = Request.Builder()
            .url("https://example.com")
            .tag(Invocation::class.java, invocation)
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertNotNull(capturedRequest.header("Authorization"))
        assertEquals("bearer $testToken", capturedRequest.header("Authorization"))
    }

    @Test(expected = IOException::class)
    fun whenRequestWithAuthRequiredAnnotationAndNullTokenThenThrowsIOException() = runTest {
        whenever(mockSubscriptions.getAccessToken()).thenReturn(null)

        val mockMethod = TestInterface::class.java.getMethod("authRequiredMethod")
        val invocation = Invocation.of(mockMethod, emptyList<Any>())
        val request = Request.Builder()
            .url("https://example.com")
            .tag(Invocation::class.java, invocation)
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)
    }

    @Test
    fun whenRequestWithoutInvocationTagThenProceedsWithoutAddingHeader() = runTest {
        val request = Request.Builder()
            .url("https://example.com")
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertNull(capturedRequest.header("Authorization"))
    }

    @Test
    fun whenRequestWithNonAuthRequiredMethodThenProceedsWithoutAddingHeader() = runTest {
        val mockMethod = TestInterface::class.java.getMethod("nonAuthRequiredMethod")
        val invocation = Invocation.of(mockMethod, emptyList<Any>())
        val request = Request.Builder()
            .url("https://example.com")
            .tag(Invocation::class.java, invocation)
            .build()

        whenever(mockChain.request()).thenReturn(request)

        testee.intercept(mockChain)

        val requestCaptor = org.mockito.kotlin.argumentCaptor<Request>()
        verify(mockChain).proceed(requestCaptor.capture())

        val capturedRequest = requestCaptor.firstValue
        assertNull(capturedRequest.header("Authorization"))
    }

    interface TestInterface {
        @PirAuthRequired
        fun authRequiredMethod()

        fun nonAuthRequiredMethod()
    }
}
