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

package com.duckduckgo.app.fire

import android.webkit.CookieManager
import android.webkit.ValueCallback
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private data class Cookie(val url: String, val value: String)

@ExperimentalCoroutinesApi
class WebViewCookieManagerTest {
    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val removeCookieStrategy = mock<RemoveCookiesStrategy>()
    private val cookieManager = mock<CookieManager>()
    private val ddgCookie = Cookie(DDG_HOST, "da=abc")
    private val externalHostCookie = Cookie("example.com", "dz=zyx")
    private val testee: WebViewCookieManager = WebViewCookieManager(
        cookieManager,
        DDG_HOST,
        removeCookieStrategy,
        coroutineRule.testDispatcherProvider
    )

    @Before
    fun setup() {
        whenever(cookieManager.setCookie(any(), any(), any())).then {
            (it.getArgument(2) as ValueCallback<Boolean>).onReceiveValue(true)
        }
    }

    @Test
    fun whenCookiesRemovedThenInternalCookiesRecreated() = coroutineRule.runBlocking {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(cookieManager).setCookie(eq(ddgCookie.url), eq(ddgCookie.value), any())
    }

    @Test
    fun whenCookiesStoredThenRemoveCookiesExecuted() = coroutineRule.runBlocking {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(removeCookieStrategy).removeCookies()
    }

    @Test
    fun whenCookiesStoredThenFlushBeforeAndAfterInteractingWithCookieManager() = coroutineRule.runBlocking {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        cookieManager.inOrder {
            verify().flush()
            verify().getCookie(DDG_HOST)
            verify().hasCookies()
            verify().setCookie(eq(DDG_HOST), any(), any())
            verify().flush()
        }
    }

    @Test
    fun whenNoCookiesThenRemoveProcessNotExecuted() = coroutineRule.runBlocking {
        givenCookieManagerWithCookies()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verifyZeroInteractions(removeCookieStrategy)
    }

    private fun givenCookieManagerWithCookies(vararg cookies: Cookie) {
        if (cookies.isEmpty()) {
            whenever(cookieManager.hasCookies()).thenReturn(false)
        } else {
            whenever(cookieManager.hasCookies()).thenReturn(true)
            cookies.forEach { cookie ->
                whenever(cookieManager.getCookie(cookie.url)).thenReturn(cookie.value)
            }
        }
    }

    companion object {
        private const val DDG_HOST = "duckduckgo.com"
    }
}
