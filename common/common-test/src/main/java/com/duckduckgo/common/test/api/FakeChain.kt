/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.common.test.api

import okhttp3.*
import retrofit2.Invocation
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

open class FakeChain(
    private val url: String,
    private val expectedResponseCode: Int? = null,
    private val serviceMethod: Method? = null,
) : Interceptor.Chain {
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
        return Response.Builder()
            .request(request)
            .headers(request.headers) // echo the headers
            .protocol(Protocol.HTTP_2)
            .code(expectedResponseCode ?: 200)
            .message("")
            .build()
    }

    override fun readTimeoutMillis(): Int {
        TODO("Not yet implemented")
    }

    override fun request(): Request {
        return Request.Builder().apply {
            url(url)
            serviceMethod?.let {
                tag(Invocation::class.java, Invocation.of(it, emptyList<Unit>()))
            }
        }.build()
    }

    override fun withConnectTimeout(
        timeout: Int,
        unit: TimeUnit,
    ): Interceptor.Chain {
        TODO("Not yet implemented")
    }

    override fun withReadTimeout(
        timeout: Int,
        unit: TimeUnit,
    ): Interceptor.Chain {
        TODO("Not yet implemented")
    }

    override fun withWriteTimeout(
        timeout: Int,
        unit: TimeUnit,
    ): Interceptor.Chain {
        TODO("Not yet implemented")
    }

    override fun writeTimeoutMillis(): Int {
        TODO("Not yet implemented")
    }
}
