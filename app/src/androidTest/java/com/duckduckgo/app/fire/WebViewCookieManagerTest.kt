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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Suppress("RemoveExplicitTypeArguments")
class WebViewCookieManagerTest {

    private lateinit var testee: WebViewCookieManager

    private val cookieManager: CookieManager = CookieManager.getInstance()

    @Before
    fun setup() = runBlocking {
        removeExistingCookies()
        testee = WebViewCookieManager(cookieManager, host)
    }

    private suspend fun removeExistingCookies() {
        withContext(Dispatchers.Main) {
            suspendCoroutine<Unit> { continuation ->
                cookieManager.removeAllCookies { continuation.resume(Unit) }
            }
        }
    }

    @Test
    fun whenExternalCookiesClearedThenInternalCookiesRecreated() = runBlocking<Unit> {
        cookieManager.setCookie(host, "da=abc")
        cookieManager.setCookie(externalHost, "dz=zyx")

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        val actualCookies = cookieManager.getCookie(host)?.split(";").orEmpty()
        assertEquals(1, actualCookies.size)
        assertTrue(actualCookies.contains("da=abc"))
    }

    @Test
    fun whenExternalCookiesClearedThenExternalCookiesAreNotRecreated() = runBlocking<Unit> {
        cookieManager.setCookie(host, "da=abc")
        cookieManager.setCookie(externalHost, "dz=zyx")

        withContext(Dispatchers.Main) {
            testee.removeExternalCookies()
        }

        val actualCookies = cookieManager.getCookie(externalHost)?.split(";").orEmpty()
        assertEquals(0, actualCookies.size)
    }

    companion object {
        private const val host = "duckduckgo.com"
        private const val externalHost = "example.com"
    }
}