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
    private lateinit var lifecycleOwner: LifecycleOwner

    // Two independent cookie jars, mirroring a Regular profile and a Fire profile.
    private val regularJar = FakeCookieJar()
    private val fireJar = FakeCookieJar()
    private val cookieManager = FakeCookieManagerWrapper(listOf(regularJar, fireJar))

    private lateinit var redditBlockWorkaround: RedditBlockWorkaround

    @org.junit.Before
    fun setUp() {
        lifecycleOwner = TestLifecycleOwner()
        redditBlockWorkaround = RedditBlockWorkaround(networkProtectionState, coroutineRule.testDispatcherProvider, cookieManager)
    }

    @Test
    fun `on resume with netp disabled removes the reddit_session dummy`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        redditBlockWorkaround.onResume(lifecycleOwner)

        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        redditBlockWorkaround.onResume(lifecycleOwner)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=; Expires=Wed, 21 Oct 2015 07:28:00 GMT", regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled adds reddit_session dummy if not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=;", regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp disabled noops if reddit_session not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertNull(regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled noops if reddit_session present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        regularJar.setCookie(".reddit.com", "reddit_session=value;")

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=value;", regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp disabled noops if reddit_session present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        regularJar.setCookie(".reddit.com", "reddit_session=value;")

        redditBlockWorkaround.onResume(lifecycleOwner)
        assertEquals("reddit_session=value;", regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled adds the dummy to every resolved profile`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)

        redditBlockWorkaround.onResume(lifecycleOwner)

        assertEquals("reddit_session=;", regularJar.getCookie(".reddit.com"))
        assertEquals("reddit_session=;", fireJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on resume with netp enabled does not clobber a real session in another profile`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        // The Fire profile holds a real reddit login while the Regular profile is logged out.
        fireJar.setCookie(".reddit.com", "reddit_session=realtoken;")

        redditBlockWorkaround.onResume(lifecycleOwner)

        // Regular gets the dummy, but Fire's real session must be left untouched.
        assertEquals("reddit_session=;", regularJar.getCookie(".reddit.com"))
        assertEquals("reddit_session=realtoken;", fireJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on pause with netp enabled does not clobber a real session in another profile`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        fireJar.setCookie(".reddit.com", "reddit_session=realtoken;")

        redditBlockWorkaround.onPause(lifecycleOwner)

        assertEquals("reddit_session=realtoken;", fireJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on pause expires the reddit_session dummy if present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        regularJar.setCookie(".reddit.com", "reddit_session=;")
        redditBlockWorkaround.onPause(lifecycleOwner)
        assertEquals("reddit_session=; Expires=Wed, 21 Oct 2015 07:28:00 GMT", regularJar.getCookie(".reddit.com"))
    }

    @Test
    fun `on pause noops if reddit_session dummy if not present`() = runTest {
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        redditBlockWorkaround.onPause(lifecycleOwner)
        assertNull(regularJar.getCookie(".reddit.com"))
    }
}

class FakeCookieManagerWrapper(private val profiles: List<FakeCookieJar>) : CookieManagerWrapper {
    override fun resolvedProfiles(): List<CookieJar> = profiles
}

class FakeCookieJar : CookieJar {

    private val cookieStore: MutableMap<String, MutableList<Cookie>> = mutableMapOf()

    override fun getCookie(url: String): String? {
        val host = getCookieHost(url)
        val cookies = cookieStore[host] ?: return null
        return cookies.joinToString(";") { it.toString() }
    }

    override fun setCookie(url: String, cookieString: String) {
        val uri = URI(url)
        val domain = runCatching { if (uri.host.startsWith(".")) uri.host.substring(1) else uri.host }.getOrElse { url }
        val cookie = Cookie.fromString(cookieString)

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
