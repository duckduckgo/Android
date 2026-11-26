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

package com.duckduckgo.url.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.url.DaxDomainType
import com.duckduckgo.url.DaxScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealDaxUrlProviderTest {

    private val feature: UnifiedUrlModelFeature = mock()
    private val toggle: Toggle = mock()
    private lateinit var provider: RealDaxUrlProvider

    @Before
    fun setup() {
        whenever(feature.useUnifiedUrl()).thenReturn(toggle)
        provider = RealDaxUrlProvider(lazy { feature })
    }

    @Test
    fun whenFlagEnabledThenReturnsMemoizingModel() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue((provider.getUrl("example.com") as RealDaxUrl).memoize)
    }

    @Test
    fun whenFlagDisabledThenReturnsRecomputingModel() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertFalse((provider.getUrl("example.com") as RealDaxUrl).memoize)
    }

    @Test
    fun whenSameDomainRequestedTwiceThenReturnsNewInstance() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertNotSame(provider.getUrl("example.com"), provider.getUrl("example.com"))
    }

    @Test
    fun whenMatchingThenAppliesSameOrSubdomainSemantics() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val exception = provider.getUrl("example.com")
        assertTrue(provider.getUrl("https://example.com").matches(exception))
        assertTrue(provider.getUrl("https://sub.example.com").matches(exception))
        assertFalse(provider.getUrl("https://other.com").matches(exception))
        assertFalse(provider.getUrl("https://notexample.com").matches(exception))
    }

    @Test
    fun whenDomainExtractedThenNormalizedWwwStripped() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("example.com", provider.getUrl("https://www.example.com/path?q=1").domain?.toString())
    }

    @Test
    fun whenUrlHasNoResolvableHostThenDomainIsNull() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertNull(provider.getDomain(""))
        assertNull(provider.getUrl("mailto:someone").domain)
    }

    @Test
    fun whenDaxDomainSameOrSubdomainThenTruthTableHolds() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val parent = provider.getDomain("example.com")!!

        assertTrue(parent.sameOrSubdomain(provider.getDomain("example.com")!!))
        assertTrue(parent.sameOrSubdomain(provider.getDomain("sub.example.com")!!))
        assertFalse(parent.sameOrSubdomain(provider.getDomain("other.com")!!))
        assertFalse(parent.sameOrSubdomain(provider.getDomain("notexample.com")!!))
    }

    @Test
    fun whenSchemeReadThenReflectsRawUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals(DaxScheme.HTTPS, provider.getUrl("https://example.com").scheme)
        assertTrue(provider.getUrl("https://example.com").isHttps)
        assertEquals(DaxScheme.HTTP, provider.getUrl("http://example.com").scheme)
        assertFalse(provider.getUrl("http://example.com").isHttps)
        assertEquals(DaxScheme.OTHER, provider.getUrl("example.com").scheme)
    }

    @Test
    fun whenWithSchemeThenReturnsUrlWithRewrittenScheme() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val https = provider.withScheme(provider.getUrl("http://example.com"), DaxScheme.HTTPS)
        assertEquals(DaxScheme.HTTPS, https.scheme)
        assertEquals("example.com", https.domain?.toString())
    }

    @Test
    fun whenWithSchemeGivenIpPortThenHostPreserved() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val https = provider.withScheme("255.255.255.255:9999", DaxScheme.HTTPS)
        assertEquals(DaxScheme.HTTPS, https.scheme)
        assertEquals("255.255.255.255", https.domain?.toString())
    }

    @Test
    fun whenMatchingAgainstDaxDomainThenAppliesSameOrSubdomainSemantics() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val exception = provider.getUrl("example.com")
        val visited = provider.getUrl("https://sub.example.com/path")

        assertTrue(visited.matches(exception))
        assertTrue(visited.matches(exception.domain!!))
        assertFalse(provider.getUrl("https://other.com").matches(exception))
    }

    @Test
    fun whenHttpOrHttpsUrlThenIsWebUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue(provider.getUrl("https://example.com").isWebUrl)
        assertTrue(provider.getUrl("http://sub.example.com/path").isWebUrl)
        assertTrue(provider.getUrl("example.com").isWebUrl)
    }

    @Test
    fun whenNotWebUrlThenIsNotWebUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertFalse(provider.getUrl("not a url").isWebUrl)
        assertFalse(provider.getUrl("duck://player").isWebUrl)
    }

    @Test
    fun whenLocalOrPrivateHostThenIsLocalUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue(provider.getUrl("http://localhost").isLocalUrl)
        assertTrue(provider.getUrl("http://127.0.0.1").isLocalUrl)
        assertTrue(provider.getUrl("file:///sdcard/page.html").isLocalUrl)
        assertFalse(provider.getUrl("https://example.com").isLocalUrl)
    }

    @Test
    fun whenPathReadThenReflectsUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val model = provider.getUrl("https://example.com/foo/bar")
        assertEquals("/foo/bar", model.path)
        assertEquals(listOf("foo", "bar"), model.pathSegments)
        assertEquals("bar", model.lastPathSegment)
    }

    @Test
    fun whenPortFragmentQueryReadThenReflectUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val model = provider.getUrl("https://example.com:8443/path?q=cats#section")
        assertEquals(8443, model.port)
        assertEquals("q=cats", model.query)
        assertEquals("section", model.fragment)
    }

    @Test
    fun whenNoPortThenPortIsNull() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertNull(provider.getUrl("https://example.com/path").port)
    }

    @Test
    fun whenQueryParametersReadThenReflectUrl() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val model = provider.getUrl("https://example.com/search?q=cats&lang=en")
        assertEquals("cats", model.queryParameter("q"))
        assertEquals("en", model.queryParameter("lang"))
        assertNull(model.queryParameter("missing"))
        assertEquals(setOf("q", "lang"), model.queryParameterNames)
    }

    @Test
    fun whenOpaqueUrlThenQueryParametersAreEmpty() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val model = provider.getUrl("mailto:someone@example.com")
        assertNull(model.queryParameter("q"))
        assertTrue(model.queryParameterNames.isEmpty())
    }

    @Test
    fun whenMobileSiteThenIsMobileSite() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue(provider.getUrl("https://m.example.com").isMobileSite)
        assertTrue(provider.getUrl("https://mobile.example.com/path").isMobileSite)
        assertFalse(provider.getUrl("https://example.com").isMobileSite)
    }

    @Test
    fun whenFlagEnabledThenDomainIsMemoizing() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue((provider.getDomain("example.com") as RealDaxDomain).memoize)
    }

    @Test
    fun whenFlagDisabledThenDomainIsRecomputing() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertFalse((provider.getDomain("example.com") as RealDaxDomain).memoize)
    }

    @Test
    fun whenTldPlusOneComputedThenReturnsRegistrableDomain() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("example.com", provider.getDomain("sub.example.com")!!.toTldPlusOne()!!.toString())
    }

    @Test
    fun whenHostHasNoRegistrableDomainThenTldPlusOneIsNullButOrSelfReturnsSelf() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val ip = provider.getDomain("127.0.0.1")!!
        assertNull(ip.toTldPlusOne())
        assertEquals("127.0.0.1", ip.toTldPlusOneOrSelf().toString())
    }

    @Test
    fun whenHostIsLocalhostThenDomainStillBuiltAndMatches() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val localhost = provider.getDomain("localhost")!!
        assertEquals("localhost", localhost.toString())
        assertTrue(localhost.sameOrSubdomain(provider.getDomain("http://localhost")!!))
    }

    @Test
    fun whenHostIsIpThenDomainStillBuiltAndMatches() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val ip = provider.getDomain("127.0.0.1")!!
        assertEquals("127.0.0.1", ip.toString())
        assertTrue(ip.sameOrSubdomain(provider.getDomain("https://127.0.0.1/path")!!))
    }

    @Test
    fun whenClassifyingDomainsThenTypeReflectsHostKind() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals(DaxDomainType.REGISTRABLE_DOMAIN, provider.getDomain("sub.example.com")!!.type)
        assertEquals(DaxDomainType.IP, provider.getDomain("127.0.0.1")!!.type)
        assertEquals(DaxDomainType.LOCALHOST, provider.getDomain("localhost")!!.type)
        assertEquals(DaxDomainType.OTHER, provider.getDomain("co.uk")!!.type)
    }

    @Test
    fun whenMemoizingDomainThenTldPlusOneIsCached() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val domain = provider.getDomain("sub.example.com")!!
        assertSame(domain.toTldPlusOne(), domain.toTldPlusOne())
    }

    @Test
    fun whenRecomputingDomainThenTldPlusOneIsRecomputed() {
        whenever(toggle.isEnabled()).thenReturn(false)
        val domain = provider.getDomain("sub.example.com")!!
        assertNotSame(domain.toTldPlusOne(), domain.toTldPlusOne())
        assertEquals(domain.toTldPlusOne(), domain.toTldPlusOne())
    }

    @Test
    fun whenBuilderGivenComponentsThenBuildsUrlVerbatim() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.HTTPS)
            .encodedHost("WWW.Example.com")
            .encodedPath("/a%2Fb/c")
            .encodedQuery("q=a%20b")
            .build() as RealDaxUrl

        assertEquals("https://WWW.Example.com/a%2Fb/c?q=a%20b", url.raw)
    }

    @Test
    fun whenBuilderGivenOtherSchemeThenSchemeIsUnset() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder().scheme(DaxScheme.OTHER).encodedHost("example.com").build()

        assertEquals(DaxScheme.OTHER, url.scheme)
        assertEquals("//example.com", (url as RealDaxUrl).raw)
    }

    @Test
    fun whenBuilderUsedThenModeFollowsFlag() {
        whenever(toggle.isEnabled()).thenReturn(false)
        assertFalse((provider.builder().encodedHost("example.com").build() as RealDaxUrl).memoize)
    }

    @Test
    fun whenHostHasUppercaseThenDomainIsLowercased() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("example.com", provider.getDomain("Example.COM").toString())
        assertEquals(provider.getDomain("example.com"), provider.getDomain("EXAMPLE.com"))
    }

    @Test
    fun whenHostHasUppercaseWwwThenWwwIsStripped() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("example.com", provider.getUrl("https://WWW.Example.com/Path").domain.toString())
    }

    @Test
    fun whenHostsDifferOnlyInCaseThenSameOrSubdomainMatches() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertTrue(provider.getUrl("https://Sub.EXAMPLE.com").matches(provider.getDomain("example.COM")!!))
    }

    @Test
    fun whenUnicodeAndPunycodeHostsThenDomainsAreEqual() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals(provider.getDomain("xn--a-5fa.com"), provider.getDomain("ça.com"))
        assertEquals(provider.getDomain("https://xn--a-5fa.com/path"), provider.getUrl("https://www.ça.com/path").domain)
    }

    @Test
    fun whenUnicodeHostThenConvertedNontransitionally() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("xn--fa-hia.de", provider.getDomain("faß.de").toString())
        assertNotEquals(provider.getDomain("fass.de"), provider.getDomain("faß.de"))
    }

    @Test
    fun whenAsciiHostThenLeftUntouched() {
        whenever(toggle.isEnabled()).thenReturn(true)
        assertEquals("_dmarc.example.com", provider.getDomain("_dmarc.example.com").toString())
        assertEquals("xn--a-5fa.com", provider.getDomain("xn--a-5fa.com").toString())
    }

    @Test
    fun whenUnicodeHostRejectedByIdnaThenKeptAsIs() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val tooLongLabel = "ç" + "a".repeat(70)
        assertEquals("$tooLongLabel.com", provider.getDomain("$tooLongLabel.COM").toString())
    }

    @Test
    fun whenUnicodeParentAndPunycodeChildThenSameOrSubdomainMatches() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val exception = provider.getDomain("ça.com")!!
        assertTrue(provider.getUrl("https://sub.xn--a-5fa.com").matches(exception))
    }

    @Test
    fun whenFlagOnOrOffThenEveryUrlMemberIsEqual() {
        PARITY_INPUTS.forEach { input ->
            val memoized = RealDaxUrl(input, memoize = true)
            val recomputed = RealDaxUrl(input, memoize = false)

            assertEquals(input, memoized.domain, recomputed.domain)
            assertEquals(input, memoized.scheme, recomputed.scheme)
            assertEquals(input, memoized.isHttps, recomputed.isHttps)
            assertEquals(input, memoized.isWebUrl, recomputed.isWebUrl)
            assertEquals(input, memoized.isLocalUrl, recomputed.isLocalUrl)
            assertEquals(input, memoized.isMobileSite, recomputed.isMobileSite)
            assertEquals(input, memoized.path, recomputed.path)
            assertEquals(input, memoized.pathSegments, recomputed.pathSegments)
            assertEquals(input, memoized.lastPathSegment, recomputed.lastPathSegment)
            assertEquals(input, memoized.port, recomputed.port)
            assertEquals(input, memoized.query, recomputed.query)
            assertEquals(input, memoized.fragment, recomputed.fragment)
            assertEquals(input, memoized.queryParameterNames, recomputed.queryParameterNames)
            assertEquals(input, memoized.queryParameter("q"), recomputed.queryParameter("q"))
        }
    }

    @Test
    fun whenFlagOnOrOffThenEveryDomainMemberIsEqual() {
        PARITY_INPUTS.forEach { input ->
            val memoized = RealDaxUrl(input, memoize = true).domain ?: return@forEach
            val recomputed = RealDaxUrl(input, memoize = false).domain!!

            assertEquals(input, memoized, recomputed)
            assertEquals(input, memoized.type, recomputed.type)
            assertEquals(input, memoized.toTldPlusOne(), recomputed.toTldPlusOne())
            assertEquals(input, memoized.toTldPlusOneOrSelf(), recomputed.toTldPlusOneOrSelf())
            assertEquals(input, memoized.sameOrSubdomain(recomputed), recomputed.sameOrSubdomain(memoized))
        }
    }

    private companion object {
        val PARITY_INPUTS = listOf(
            "https://www.example.com/path/to?q=cats&lang=en#frag",
            "http://sub.example.co.uk:8080/a/b",
            "example.com",
            "www.example.com",
            "https://m.example.com",
            "http://localhost:3000",
            "http://127.0.0.1/path",
            "http://192.168.1.1",
            "file:///sdcard/page.html",
            "co.uk",
            "duck://player/123",
            "mailto:someone@example.com",
            "about:blank",
            "not a url",
            "https://www.ça.com/path",
            "faß.de",
            "HTTPS://WWW.Example.COM/Path",
            "",
        )
    }
}
