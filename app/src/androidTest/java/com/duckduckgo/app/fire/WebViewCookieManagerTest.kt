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
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Test

private data class Cookie(val url: String, val value: String)

class WebViewCookieManagerTest {

    private lateinit var testee: WebViewCookieManager
    private val selectiveCookieRemover = mock<CookieRemover>()
    private val cookieManagerRemover = mock<CookieRemover>()
    private val cookieManager = mock<CookieManager>()
    private val ddgCookie = Cookie(DDG_HOST, "da=abc")
    private val externalHostCookie = Cookie("example.com", "dz=zyx")

    @Before
    fun setup() {
        whenever(cookieManager.setCookie(any(), any(), any())).then {
            (it.getArgument(2) as ValueCallback<Boolean>).onReceiveValue(true)
        }
        testee = WebViewCookieManager(cookieManager, DDG_HOST, cookieManagerRemover, selectiveCookieRemover)
    }

    @Test
    fun whenSelectiveCookieRemoverSucceedsThenInternalCookiesRecreated() = runBlocking {
        givenCookieManagerWithCookies(
            ddgCookie,
            externalHostCookie
        )
        selectiveCookieRemover.succeeds()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(cookieManager, times(1)).setCookie(eq(ddgCookie.url), eq(ddgCookie.value), any())
    }

    @Test
    fun whenCookieManagerRemoverSucceedsThenInternalCookiesRecreated() = runBlocking {
        givenCookieManagerWithCookies(
            ddgCookie,
            externalHostCookie
        )
        selectiveCookieRemover.fails()
        cookieManagerRemover.succeeds()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(cookieManager, times(1)).setCookie(eq(ddgCookie.url), eq(ddgCookie.value), any())
    }

    @Test
    fun whenCookiesStoredThenSelectiveCookieRemoverExecuted() = runBlocking<Unit> {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)
        selectiveCookieRemover.succeeds()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(selectiveCookieRemover).removeCookies()
    }

    @Test
    fun whenCookiesStoredThenFlushBeforeAndAfterInteractingWithCookieManager() = runBlocking<Unit> {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)
        selectiveCookieRemover.succeeds()

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
    fun whenCookiesStoredAndelectiveCookieRemoverFailsThenCookieManagerRemoverExecuted() = runBlocking<Unit> {
        givenCookieManagerWithCookies(ddgCookie, externalHostCookie)
        selectiveCookieRemover.fails()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verify(cookieManagerRemover).removeCookies()
    }

    @Test
    fun whenNoCookiesThenRemoveProcessNotExecuted() = runBlocking {
        givenCookieManagerWithCookies()

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        verifyZeroInteractions(selectiveCookieRemover)
        verifyZeroInteractions(cookieManagerRemover)
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

    private suspend fun CookieRemover.succeeds() {
        whenever(this.removeCookies()).thenReturn(true)
    }

    private suspend fun CookieRemover.fails() {
        whenever(this.removeCookies()).thenReturn(false)
    }

    companion object {
        private const val DDG_HOST = "duckduckgo.com"
    }
}