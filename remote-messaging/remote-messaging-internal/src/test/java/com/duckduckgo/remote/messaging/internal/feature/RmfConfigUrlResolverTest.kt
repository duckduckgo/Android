/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.remote.messaging.internal.setting.RmfConfigMode
import com.duckduckgo.remote.messaging.internal.setting.RmfConfigSourceStore
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RmfConfigUrlResolverTest {

    private val store = FakeRmfConfigSourceStore()
    private val resolver = RealRmfConfigUrlResolver(store)

    @Test
    fun whenProductionModeThenReturnsNull() {
        store.mode = RmfConfigMode.PRODUCTION

        assertNull(resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenNonRmfUrlThenReturnsNull() {
        store.mode = RmfConfigMode.STAGING

        assertNull(resolver.resolve(NON_RMF_URL.toHttpUrl()))
    }

    @Test
    fun whenRequestUrlAlreadyStagingThenReturnsNull() {
        store.mode = RmfConfigMode.STAGING

        assertNull(resolver.resolve(RMF_STAGING_DEFAULT_URL.toHttpUrl()))
    }

    @Test
    fun whenStagingModeThenRewritesToStagingDefault() {
        store.mode = RmfConfigMode.STAGING

        assertEquals(RMF_STAGING_DEFAULT_URL, resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenPrNumberModeThenRewritesToPrStagingUrl() {
        store.mode = RmfConfigMode.PR_NUMBER
        store.prNumber = "387"

        assertEquals(RMF_STAGING_PR_URL, resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenPrNumberModeButBlankThenFallsBackToStagingDefault() {
        store.mode = RmfConfigMode.PR_NUMBER
        store.prNumber = ""

        assertEquals(RMF_STAGING_DEFAULT_URL, resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenCustomUrlModeThenReturnsCustomUrlVerbatim() {
        store.mode = RmfConfigMode.CUSTOM_URL
        store.customUrl = CUSTOM_URL

        assertEquals(CUSTOM_URL, resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenCustomUrlModeButBlankThenReturnsNull() {
        store.mode = RmfConfigMode.CUSTOM_URL
        store.customUrl = ""

        assertNull(resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    @Test
    fun whenCustomUrlModeButNotHttpThenReturnsNull() {
        store.mode = RmfConfigMode.CUSTOM_URL
        store.customUrl = "ftp://example.com/android-config.json"

        assertNull(resolver.resolve(RMF_PROD_URL.toHttpUrl()))
    }

    private class FakeRmfConfigSourceStore : RmfConfigSourceStore {
        override var mode: RmfConfigMode = RmfConfigMode.PRODUCTION
        override var prNumber: String = ""
        override var customUrl: String = ""
    }
}

private const val RMF_PROD_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/v1/android-config.json"
private const val RMF_STAGING_DEFAULT_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/android-config.json"
private const val RMF_STAGING_PR_URL = "https://staticcdn.duckduckgo.com/remotemessaging/config/staging/387/android-config.json"
private const val NON_RMF_URL = "https://unknown.com/remotemessaging/config/v1/android-config.json"
private const val CUSTOM_URL = "https://example.com/whatever/config.json"
