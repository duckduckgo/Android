/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.reddit

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.net.URI
import java.net.URISyntaxException

class RedditBlockWorkaroundTest {
    @get:Rule var coroutineRule = CoroutineTestRule()
    private val networkProtectionState: NetworkProtectionState = mock()
    private val lifecycleOwner: LifecycleOwner = TestLifecycleOwner()

    private val cookieManager = FakeCookieManagerWrapper()

    private val redditBlockWorkaround = RedditBlockWorkaround(networkProtectionState, coroutineRule.testDispatcherProvider, cookieManager)

    @Test
    fun `on resume with netp disabled removes the reddit_session dummy`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        redditBlockWorkaround.onResume(lifecycleOwner)

        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        redditBlockWorkaround.onResume(lifecycleOwner)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=; Expires=Wed, 21 Oct 2015 07:28:00 GMT", cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled adds reddit_session dummy if not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=;", cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp disabled noops if reddit_session not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertNull(cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled noops if reddit_session present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        cookieManager.setCookie(".reddit.com", "reddit_session=value;")

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=value;", cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp disabled noops if reddit_session present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        cookieManager.setCookie(".reddit.com", "reddit_session=value;")

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=value;", cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on pause expires the reddit_session dummy if present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        cookieManager.setCookie(".reddit.com", "reddit_session=;")
        redditBlockWorkaround.onPause(lifecycleOwner)
        assertEquals("reddit_session=; Expires=Wed, 21 Oct 2015 07:28:00 GMT", cookieManager.getCookie(".reddit.com"))
    }

    @Test
    fun `on pause noops if reddit_session dummy if not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        redditBlockWorkaround.onPause(lifecycleOwner)
        assertNull(cookieManager.getCookie(".reddit.com"))
    }
}

class FakeCookieManagerWrapper() : CookieManagerWrapper {

    val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()
    // private val cookieStore: MutableMap<String, MutableList<String>> = mutableMapOf()

    // Function to get cookies for a specific URL
    override fun getCookie(url: String): String? {
        val host = getCookieHost(url)
        val cookies = cookieStore[host] ?: return null
        return cookies.joinToString(";") { it.toString() }
    }

    // Function to set cookies for a specific URL or domain
    override fun setCookie(url: String, cookieString: String) {
        val uri = URI(url)
        val domain = runCatching { if (uri.host.startsWith(".")) uri.host.substring(1) else uri.host }.getOrElse { url }
        val cookie = Cookie.fromString(cookieString)

        // Add cookie to the specific domain
        val cookies = cookieStore.computeIfAbsent(domain) { mutableListOf() }.filter { it.name != cookie.name }.toMutableList().apply {
            add(cookie)
        }

        cookieStore[domain] = cookies
    }

    private fun getCookieHost(url: String): String? {
        if (url.startsWith(".")) return url

        var url = url
        if (!(url.startsWith("http") || url.startsWith("https"))) {
            url = "http" + url
        }
        return try {
            URI(url).host
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("wrong URL : $url", e)
        }
    }

    data class Cookie(
        val name: String,
        val value: String,
        val expires: String,
    ) {
        companion object {
            fun fromString(cookieString: String): Cookie {
                val name = cookieString.substringBefore((";")).substringBefore("=")
                val value = cookieString.substringBefore((";")).substringAfter("=")
                val expires = if (cookieString.contains("Expires=")) {
                    cookieString.substringAfter("Expires=")
                } else {
                    ""
                }
                return Cookie(name, value, expires)
            }
        }

        override fun toString(): String {
            return if (expires.isNotEmpty()) {
                "$name=$value; Expires=$expires"
            } else {
                "$name=$value;"
            }
        }
    }
}
