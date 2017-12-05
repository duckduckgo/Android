/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.global

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test


class UriExtensionTest {

    @Test
    fun whenUriDoesNotHaveASchemeThenWithSchemeAppendsHttp() {
        val url = "someurl"
        assertEquals("http://$url", Uri.parse(url).withScheme().toString())
    }

    @Test
    fun whenUriHasASchemeThenWithSchemeHasNoEffect() {
        val url = "http://someurl"
        assertEquals(url, Uri.parse(url).withScheme().toString())
    }

    @Test
    fun whenUriBeginsWithWwwThenBaseHostReturnsWithoutWww() {
        val url = "http://www.example.com"
        assertEquals("example.com", Uri.parse(url).baseHost)
    }

    @Test
    fun whenUriDoesNotBeginWithWwwThenBaseHosReturnsWithSameHost() {
        val url = "http://example.com"
        assertEquals("example.com", Uri.parse(url).baseHost)
    }

    @Test
    fun whenUriDoesNotHaveASchemeThenBaseHostStillResolvesHost() {
        val url = "www.example.com"
        assertEquals("example.com", Uri.parse(url).baseHost)
    }

    @Test
    fun whenUriContainsInvalidHostThenBaseHostIsNull() {
        val url = "about:blank"
        assertNull(Uri.parse(url).baseHost)
    }

    @Test
    fun whenUriIsHttpIrrespectiveOfCaseThenIsHttpIsTrue() {
        assertTrue(Uri.parse("http://example.com").isHttp)
        assertTrue(Uri.parse("HTTP://example.com").isHttp)
    }

    @Test
    fun whenUriIsHttpsThenIsHttpIsFalse() {
        assertFalse(Uri.parse("https://example.com").isHttp)
    }

    @Test
    fun whenUriIsMalformedThenIsHttpIsFalse() {
        assertFalse(Uri.parse("[example com]").isHttp)
    }

    @Test
    fun whenUriIsHttpsIrrespectiveOfCaseThenIsHttpsIsTrue() {
        assertTrue(Uri.parse("https://example.com").isHttps)
        assertTrue(Uri.parse("HTTPS://example.com").isHttps)
    }

    @Test
    fun whenUriIsHttpThenIsHttpsIsFalse() {
        assertFalse(Uri.parse("http://example.com").isHttps)
    }

    @Test
    fun whenUriIsMalformedThenIsHtpsIsFalse() {
        assertFalse(Uri.parse("[example com]").isHttps)
    }
}