/*
 * Copyright (c) 2022 DuckDuckGo
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
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.utils.absoluteString
import com.duckduckgo.common.utils.baseHost
import com.duckduckgo.common.utils.domain
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.faviconLocation
import com.duckduckgo.common.utils.hasIpHost
import com.duckduckgo.common.utils.isHttp
import com.duckduckgo.common.utils.isHttps
import com.duckduckgo.common.utils.isHttpsVersionOfUri
import com.duckduckgo.common.utils.isLocalUrl
import com.duckduckgo.common.utils.isMobileSite
import com.duckduckgo.common.utils.toDesktopUri
import com.duckduckgo.common.utils.toStringDropScheme
import com.duckduckgo.common.utils.withScheme
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [29]) // Ensure Uri properly parses IPv6 addresses
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
    fun whenUriIsHttpsAndOtherIsHttpButOtherwiseIdenticalThenIsHttpsVersionOfOtherIsTrue() {
        val uri = Uri.parse("https://example.com")
        val other = Uri.parse("http://example.com")
        assertTrue(uri.isHttpsVersionOfUri(other))
    }

    @Test
    fun whenUriIsHttpsAndOtherIsHttpButNotOtherwiseIdenticalThenIsHttpsVersionOfOtherIsFalse() {
        val uri = Uri.parse("https://example.com")
        val other = Uri.parse("http://example.com/path")
        assertFalse(uri.isHttpsVersionOfUri(other))
    }

    @Test
    fun whenUriIsHttpThenIsHttpsVersionOfOtherIsFalse() {
        val uri = Uri.parse("http://example.com")
        val other = Uri.parse("http://example.com")
        assertFalse(uri.isHttpsVersionOfUri(other))
    }

    @Test
    fun whenUriIsHttpsAndOtherIsHttpsThenIsHttpsVersionOfOtherIsFalse() {
        val uri = Uri.parse("https://example.com")
        val other = Uri.parse("https://example.com")
        assertFalse(uri.isHttpsVersionOfUri(other))
    }

    @Test
    fun whenUriIsMalformedThenIsHtpsIsFalse() {
        assertFalse(Uri.parse("[example com]").isHttps)
    }

    @Test
    fun whenIpUriThenHasIpHostIsTrue() {
        assertTrue(Uri.parse("https://54.229.105.203/something").hasIpHost)
        assertTrue(Uri.parse("54.229.105.203/something").hasIpHost)
    }

    @Test
    fun whenIpWithPortUriThenHasIpHostIsTrue() {
        assertTrue(Uri.parse("https://54.229.105.203:999/something").hasIpHost)
        assertTrue(Uri.parse("54.229.105.203:999/something").hasIpHost)
    }

    @Test
    fun whenIpWithPortUriThenPortNumberParsedSuccessfully() {
        assertEquals(999, Uri.parse("https://54.229.105.203:999/something").port)
    }

    @Test
    fun whenValidIpAddressWithPortParsedWithSchemeThenPortNumberParsedSuccessfully() {
        assertEquals(999, Uri.parse("121.33.2.11:999").withScheme().port)
    }

    @Test
    fun whenStandardUriThenHasIpHostIsFalse() {
        assertFalse(Uri.parse("http://example.com").hasIpHost)
    }

    @Test
    fun whenUrlStartsMDotThenIdentifiedAsMobileSite() {
        assertTrue(Uri.parse("https://m.example.com").isMobileSite)
    }

    @Test
    fun whenUrlStartsMobileDotThenIdentifiedAsMobileSite() {
        assertTrue(Uri.parse("https://mobile.example.com").isMobileSite)
    }

    @Test
    fun whenUrlSubdomainEndsWithMThenNotIdentifiedAsMobileSite() {
        assertFalse(Uri.parse("https://adam.example.com").isMobileSite)
    }

    @Test
    fun whenUrlDoesNotStartWithMDotThenNotIdentifiedAsMobileSite() {
        assertFalse(Uri.parse("https://example.com").isMobileSite)
    }

    @Test
    fun whenConvertingMobileSiteToDesktopSiteThenShortMobilePrefixStripped() {
        val converted = Uri.parse("https://m.example.com").toDesktopUri()
        assertEquals("https://example.com", converted.toString())
    }

    @Test
    fun whenConvertingMobileSiteToDesktopSiteThenLongMobilePrefixStripped() {
        val converted = Uri.parse("https://mobile.example.com").toDesktopUri()
        assertEquals("https://example.com", converted.toString())
    }

    @Test
    fun whenConvertingMobileSiteToDesktopSiteThenMultipleMobilePrefixesStripped() {
        val converted = Uri.parse("https://mobile.m.example.com").toDesktopUri()
        assertEquals("https://example.com", converted.toString())
    }

    @Test
    fun whenConvertingDesktopSiteToDesktopSiteThenUrlUnchanged() {
        val converted = Uri.parse("https://example.com").toDesktopUri()
        assertEquals("https://example.com", converted.toString())
    }

    @Test
    fun whenGettingAbsoluteStringThenDoNotReturnQueryParameters() {
        val absoluteString = Uri.parse("https://example.com/test?q=example/#1/anotherrandomcode").absoluteString
        assertEquals("https://example.com/test", absoluteString)
    }

    @Test
    fun whenNullUrlThenNullFaviconUrl() {
        assertNull("".toUri().faviconLocation())
    }

    @Test
    fun whenHttpRequestThenFaviconLocationAlsoHttp() {
        val favicon = "http://example.com".toUri().faviconLocation()
        assertTrue(favicon!!.isHttp)
    }

    @Test
    fun whenHttpsRequestThenFaviconLocationAlsoHttps() {
        val favicon = "https://example.com".toUri().faviconLocation()
        assertTrue(favicon!!.isHttps)
    }

    @Test
    fun whenUrlContainsASubdomainThenSubdomainReturnedInFavicon() {
        val favicon = "https://sub.example.com".toUri().faviconLocation()
        assertEquals("https://sub.example.com/favicon.ico", favicon.toString())
    }

    @Test
    fun whenUrlIsIpAddressThenIpReturnedInFaviconUrl() {
        val favicon = "https://192.168.1.0".toUri().faviconLocation()
        assertEquals("https://192.168.1.0/favicon.ico", favicon.toString())
    }

    @Test
    fun whenUrlDoesNotHaveSchemeReturnNull() {
        assertNull("www.example.com".toUri().domain())
    }

    @Test
    fun whenUrlHasSchemeReturnDomain() {
        assertEquals("www.example.com", "http://www.example.com".toUri().domain())
    }

    @Test
    fun whenUriHasResourceNameThenDropSchemeReturnResourceName() {
        assertEquals("www.foo.com", "https://www.foo.com".toUri().toStringDropScheme())
        assertEquals("www.foo.com", "http://www.foo.com".toUri().toStringDropScheme())
    }

    @Test
    fun whenUriHasResourceNameAndPathThenDropSchemeReturnResourceNameAndPath() {
        assertEquals("www.foo.com/path/to/foo", "https://www.foo.com/path/to/foo".toUri().toStringDropScheme())
        assertEquals("www.foo.com/path/to/foo", "http://www.foo.com/path/to/foo".toUri().toStringDropScheme())
    }

    @Test
    fun whenUriHasResourceNamePathAndParamsThenDropSchemeReturnResourceNamePathAndParams() {
        assertEquals("www.foo.com/path/to/foo?key=value", "https://www.foo.com/path/to/foo?key=value".toUri().toStringDropScheme())
        assertEquals("www.foo.com/path/to/foo?key=value", "http://www.foo.com/path/to/foo?key=value".toUri().toStringDropScheme())
    }

    @Test
    fun whenUriExtractDomainThenReturnDomainOnly() {
        assertEquals("www.foo.com", "https://www.foo.com/path/to/foo?key=value".extractDomain())
        assertEquals("www.foo.com", "www.foo.com/path/to/foo?key=value".extractDomain())
        assertEquals("foo.com", "foo.com/path/to/foo?key=value".extractDomain())
        assertEquals("foo.com", "http://foo.com/path/to/foo?key=value".extractDomain())
    }

    @Test
    fun whenHostIsLocalhostThenIsLocalUrlReturnsTrue() {
        assertTrue("http://localhost".toUri().isLocalUrl)
        assertTrue("https://localhost".toUri().isLocalUrl)
        assertTrue("http://localhost:8080".toUri().isLocalUrl)
        assertTrue("http://LOCALHOST".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsLoopbackIpThenIsLocalUrlReturnsTrue() {
        assertTrue("http://127.0.0.1".toUri().isLocalUrl)
        assertTrue("http://127.0.0.1:3000".toUri().isLocalUrl)
        assertTrue("http://127.1.2.3".toUri().isLocalUrl)
        assertTrue("http://127.255.255.255".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsPrivateNetworkClass10ThenIsLocalUrlReturnsTrue() {
        assertTrue("http://10.0.0.1".toUri().isLocalUrl)
        assertTrue("http://10.255.255.255".toUri().isLocalUrl)
        assertTrue("http://10.1.2.3:8080".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsPrivateNetworkClass172ThenIsLocalUrlReturnsTrue() {
        assertTrue("http://172.16.0.1".toUri().isLocalUrl)
        assertTrue("http://172.31.255.255".toUri().isLocalUrl)
        assertTrue("http://172.20.10.5".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsPrivateNetworkClass192ThenIsLocalUrlReturnsTrue() {
        assertTrue("http://192.168.0.1".toUri().isLocalUrl)
        assertTrue("http://192.168.255.255".toUri().isLocalUrl)
        assertTrue("http://192.168.1.100:3000".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsPublicIpThenIsLocalUrlReturnsFalse() {
        assertFalse("http://8.8.8.8".toUri().isLocalUrl)
        assertFalse("http://1.1.1.1".toUri().isLocalUrl)
        assertFalse("http://93.184.216.34".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsNormalDomainThenIsLocalUrlReturnsFalse() {
        assertFalse("http://example.com".toUri().isLocalUrl)
        assertFalse("https://duckduckgo.com".toUri().isLocalUrl)
        assertFalse("http://www.google.com".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsEdgeCaseFor172ThenIsLocalUrlReturnsCorrectly() {
        assertFalse("http://172.15.0.1".toUri().isLocalUrl)
        assertFalse("http://172.32.0.1".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsEdgeCaseFor192ThenIsLocalUrlReturnsCorrectly() {
        assertFalse("http://192.167.0.1".toUri().isLocalUrl)
        assertFalse("http://192.169.0.1".toUri().isLocalUrl)
    }

    @Test
    fun whenSchemeIsFileThenIsLocalUrlReturnsTrue() {
        assertTrue("file:///path/to/file".toUri().isLocalUrl)
    }

    @Test
    fun whenHostIsNullOrEmptyThenIsLocalUrlReturnsFalse() {
        assertFalse("about:blank".toUri().isLocalUrl)
    }

    // IPv6 loopback tests
    @Test
    fun whenHostIsIPv6LoopbackThenIsLocalUrlReturnsTrue() {
        assertTrue("http://[::1]".toUri().isLocalUrl)
        assertTrue("http://[::1]:8080".toUri().isLocalUrl)
        assertTrue("https://[0:0:0:0:0:0:0:1]".toUri().isLocalUrl)
    }

    // IPv6 link-local tests (fe80::/10)
    @Test
    fun whenHostIsIPv6LinkLocalThenIsLocalUrlReturnsTrue() {
        assertTrue("http://[fe80::1]".toUri().isLocalUrl)
        assertTrue("http://[fe80::abcd:ef01:2345:6789]".toUri().isLocalUrl)
    }

    // IPv6 unique local addresses (fc00::/7)
    @Test
    fun whenHostIsIPv6UniqueLocalThenIsLocalUrlReturnsTrue() {
        assertTrue("http://[fc00::1]".toUri().isLocalUrl)
        assertTrue("http://[fd12:3456:789a:bcde::1]".toUri().isLocalUrl)
    }

    // IPv6 global addresses (should be false)
    @Test
    fun whenHostIsIPv6GlobalAddressThenIsLocalUrlReturnsFalse() {
        assertFalse("http://[2001:db8::1]".toUri().isLocalUrl)
        assertFalse("https://[2606:2800:220:1:248:1893:25c8:1946]".toUri().isLocalUrl)
    }

    // Security vulnerability tests - domain suffixes that look like IPs
    @Test
    fun whenHostIsIPWithDomainSuffixThenIsLocalUrlReturnsFalse() {
        assertFalse("http://127.0.0.1.evil.com".toUri().isLocalUrl)
        assertFalse("http://10.1.evil.com".toUri().isLocalUrl)
        assertFalse("http://192.168.1.1.attacker.com".toUri().isLocalUrl)
        assertFalse("http://localhost.evil.com".toUri().isLocalUrl)
    }

    // .local domain test (documenting current behavior - NOT treated as local)
    @Test
    fun whenHostIsDotLocalDomainThenIsLocalUrlReturnsFalse() {
        assertFalse("http://myserver.local".toUri().isLocalUrl)
        assertFalse("http://printer.local:631".toUri().isLocalUrl)
    }

    // Malformed IP tests
    @Test
    fun whenHostIsMalformedIPThenIsLocalUrlReturnsFalse() {
        assertFalse("http://999.999.999.999".toUri().isLocalUrl)
        assertFalse("http://192.168.1.1.1".toUri().isLocalUrl)
    }

    // Abbreviated IPv4 tests - parseNumericAddress normalizes these
    @Test
    fun whenHostIsAbbreviatedPrivateIpThenIsLocalUrlReturnsTrue() {
        // 192.168.1 is parsed as 192.168.0.1 by parseNumericAddress
        assertTrue("http://192.168.1".toUri().isLocalUrl)
    }
}
