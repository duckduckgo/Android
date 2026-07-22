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

package com.duckduckgo.remote.messaging.internal.feature

import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.remote.messaging.internal.setting.RmfConfigMode
import com.duckduckgo.remote.messaging.internal.setting.RmfConfigSourceStore
import org.junit.Assert.assertEquals
import org.junit.Test

class RmfStagingEnvInterceptorTest {

    private val store = FakeRmfConfigSourceStore()
    private val interceptor = RmfStagingEnvInterceptor(RealRmfConfigUrlResolver(store))

    @Test
    fun whenStagingModeThenRewritesToStagingEndpoint() {
        store.mode = RmfConfigMode.STAGING

        val response = interceptor.intercept(FakeChain(RMF_URL_V1))

        assertEquals(RMF_STAGING_URL, response.request.url.toString())
    }

    @Test
    fun whenPrNumberModeThenRewritesToPrStagingEndpoint() {
        store.mode = RmfConfigMode.PR_NUMBER
        store.prNumber = "387"

        val response = interceptor.intercept(FakeChain(RMF_URL_V1))

        assertEquals(RMF_STAGING_PR_URL, response.request.url.toString())
    }

    @Test
    fun whenCustomUrlModeThenRewritesToCustomUrl() {
        store.mode = RmfConfigMode.CUSTOM_URL
        store.customUrl = CUSTOM_URL

        val response = interceptor.intercept(FakeChain(RMF_URL_V1))

        assertEquals(CUSTOM_URL, response.request.url.toString())
    }

    @Test
    fun whenProductionModeThenNoop() {
        store.mode = RmfConfigMode.PRODUCTION

        val response = interceptor.intercept(FakeChain(RMF_URL_V1))

        assertEquals(RMF_URL_V1, response.request.url.toString())
    }

    @Test
    fun whenUnknownEndpointThenNoop() {
        store.mode = RmfConfigMode.STAGING

        val response = interceptor.intercept(FakeChain(UNKNOWN_URL))

        assertEquals(UNKNOWN_URL, response.request.url.toString())
    }

    private class FakeRmfConfigSourceStore : RmfConfigSourceStore {
        override var mode: RmfConfigMode = RmfConfigMode.PRODUCTION
        override var prNumber: String = ""
        override var customUrl: String = ""
    }
}

private const val RMF_URL_V1 = "https://staticcdn.duckduckgo.com/remotemessaging/config/v1/android-config.json"
private const val RMF_STAGING_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/android-config.json"
private const val RMF_STAGING_PR_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/387/android-config.json"
private const val CUSTOM_URL = "https://example.com/whatever/config.json"
private const val UNKNOWN_URL = "https://unknown.com/remotemessaging/config/v1/android-config.json"
