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
import okhttp3.*
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class ApiRequestInterceptorTest {

    private lateinit var testee: ApiRequestInterceptor

    private val fakeChain: Interceptor.Chain = FakeChain()

    @Before
    fun before() {
        testee = ApiRequestInterceptor(InstrumentationRegistry.getInstrumentation().context)
    }

    @Test
    fun whenAPIRequestIsMadeThenUserAgentIsAdded() {
        val packageName = InstrumentationRegistry.getInstrumentation().context.applicationInfo.packageName

        val response = testee.intercept(fakeChain)

        val regex = "ddg_android/.*\\($packageName; Android API .*\\)".toRegex()
        val result = response.request.header(Header.USER_AGENT)!!
        assertTrue(result.matches(regex))
    }

    // implement just request and proceed methods
    private class FakeChain : Interceptor.Chain {
        override fun call(): Call {
            TODO("Not yet implemented")
        }

        override fun connectTimeoutMillis(): Int {
            TODO("Not yet implemented")
        }

        override fun connection(): Connection? {
            TODO("Not yet implemented")
        }

        override fun proceed(request: Request): Response {
            return Response.Builder().request(request).protocol(Protocol.HTTP_2).code(200).message("").build()
        }

        override fun readTimeoutMillis(): Int {
            TODO("Not yet implemented")
        }

        override fun request(): Request {
            return Request.Builder().url("http://example.com").build()
        }

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            TODO("Not yet implemented")
        }

        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            TODO("Not yet implemented")
        }

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain {
            TODO("Not yet implemented")
        }

        override fun writeTimeoutMillis(): Int {
            TODO("Not yet implemented")
        }
    }
}
