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

package com.duckduckgo.downloads.impl

import android.webkit.CookieManager
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.cookies.api.CookieManagerProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.Executors

class CookieManagerWrapperImplTest {

    private val mainExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "cmw-test-main") }
    private val ioExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "cmw-test-io") }

    private val mockCookieManager: CookieManager = mock<CookieManager>().apply {
        whenever(getCookie(URL)).thenReturn("session=abc123")
    }

    // Mirrors DefaultCookieManagerProvider: the Fire CookieManager can only be resolved on the main
    // thread and returns null off it.
    private val mainThreadOnlyProvider = object : CookieManagerProvider {
        override fun forMode(mode: BrowserMode): CookieManager? =
            if (Thread.currentThread().name.contains("cmw-test-main")) mockCookieManager else null
    }

    private val dispatchers = object : DispatcherProvider {
        override fun io(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
        override fun main(): CoroutineDispatcher = mainExecutor.asCoroutineDispatcher()
        override fun computation(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
        override fun unconfined(): CoroutineDispatcher = ioExecutor.asCoroutineDispatcher()
    }

    private val wrapper = CookieManagerWrapperImpl(mainThreadOnlyProvider, dispatchers)

    @After
    fun tearDown() {
        mainExecutor.shutdown()
        ioExecutor.shutdown()
    }

    @Test
    fun whenCookieResolvableOnlyOnMainThreadThenGetCookieCalledFromWorkerThreadStillReturnsCookie() {
        // Downloads run on a worker thread; resolving the Fire CookieManager there would otherwise miss it.
        val result = ioExecutor.submit<String?> { wrapper.getCookie(URL, BrowserMode.FIRE) }.get()

        assertEquals("session=abc123", result)
    }

    companion object {
        private const val URL = "https://example.com/file.txt"
    }
}
