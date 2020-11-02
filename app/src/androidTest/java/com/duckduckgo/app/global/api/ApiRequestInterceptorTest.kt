/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.api

import androidx.test.platform.app.InstrumentationRegistry
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import timber.log.Timber

class ApiRequestInterceptorTest {

    private lateinit var testee: ApiRequestInterceptor

    @Mock
    private lateinit var mockChain: Interceptor.Chain

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        testee = ApiRequestInterceptor(InstrumentationRegistry.getInstrumentation().context)
    }

    @Test
    fun whenAPIRequestIsMadeThenUserAgentIsAdded() {
        whenever(mockChain.request()).thenReturn(request())
        whenever(mockChain.proceed(any())).thenReturn(response())

        val packageName = InstrumentationRegistry.getInstrumentation().context.packageName

        val captor = ArgumentCaptor.forClass(Request::class.java)
        testee.intercept(mockChain)
        verify(mockChain).proceed(captor.capture())

        val regex = "ddg_android/.*\\($packageName; Android API .*\\)".toRegex()

        val result = captor.value.header(Header.USER_AGENT)!!
        Timber.v("$result")
        assertTrue(result.matches(regex))
    }

    private fun request(): Request {
        return Request.Builder().url("http://example.com").build()
    }

    private fun response(): Response {
        return Response.Builder().request(request()).protocol(Protocol.HTTP_2).code(200).message("").build()
    }
}
