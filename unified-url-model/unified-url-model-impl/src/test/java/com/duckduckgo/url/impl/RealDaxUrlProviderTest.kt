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
    fun whenNewDomainThenTldPlusOneIsCached() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val domain = provider.getDomain("sub.example.com")!!
        assertSame(domain.toTldPlusOne(), domain.toTldPlusOne())
    }

    @Test
    fun whenLegacyDomainThenTldPlusOneIsRecomputed() {
        whenever(toggle.isEnabled()).thenReturn(false)
        val domain = provider.getDomain("sub.example.com")!!
        assertNotSame(domain.toTldPlusOne(), domain.toTldPlusOne())
        assertEquals(domain.toTldPlusOne(), domain.toTldPlusOne())
    }

    @Test
    fun whenBuildingThenComponentsAreAssembledInOrder() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.HTTPS)
            .encodedHost("example.com")
            .encodedPath("/a/b")
            .encodedQuery("q=1")
            .build()

        assertEquals("https://example.com/a/b?q=1", (url as RealDaxUrl).raw)
    }

    @Test
    fun whenBuildingThenUnsetComponentsAreOmitted() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.HTTP)
            .encodedHost("example.com")
            .encodedPath("/")
            .encodedQuery(null)
            .build()

        assertEquals("http://example.com/", (url as RealDaxUrl).raw)
    }

    @Test
    fun whenSchemeIsOtherThenNoSchemeIsWritten() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.OTHER)
            .encodedHost("example.com")
            .encodedPath("/a")
            .build()

        assertEquals("//example.com/a", (url as RealDaxUrl).raw)
    }

    // The verbatim contract: a component the caller already percent-encoded must survive the round
    // trip unchanged, because consumers hash and regex-match the result (see
    // :malicious-site-protection-impl). Re-encoding here would silently shift those bytes.
    @Test
    fun whenComponentsArePercentEncodedThenTheyAreNotEncodedAgain() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.HTTPS)
            .encodedHost("example.com")
            .encodedPath("/a%20b%23c")
            .encodedQuery("q=%2F")
            .build()

        assertEquals("https://example.com/a%20b%23c?q=%2F", (url as RealDaxUrl).raw)
    }

    @Test
    fun whenBuildingThenHostIsNotNormalizedButDomainStillIs() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.builder()
            .scheme(DaxScheme.HTTPS)
            .encodedHost("www.example.com")
            .encodedPath("/a")
            .build()

        assertEquals("https://www.example.com/a", (url as RealDaxUrl).raw)
        assertEquals("example.com", url.domain?.toString())
    }

    @Test
    fun whenRegexFoundAnywhereThenContainsMatch() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val url = provider.getUrl("https://example.com/login?next=/account")

        assertTrue(url.containsMatch(Regex("/login")))
        assertTrue(url.containsMatch(Regex("^https://example\\.com/")))
        assertFalse(url.containsMatch(Regex("^/login")))
    }

    @Test
    fun whenBuiltFromSameStringThenUrlsAreEqualRegardlessOfMode() {
        whenever(toggle.isEnabled()).thenReturn(true)
        val memoized = provider.getUrl("https://example.com/a")
        val recomputing = RealDaxUrl("https://example.com/a", memoize = false)

        assertEquals(memoized, recomputing)
        assertEquals(memoized.hashCode(), recomputing.hashCode())
        assertNotEquals(memoized, provider.getUrl("https://example.com/b"))
    }

    @Test
    fun whenBuildingThenMemoizationFollowsTheFlag() {
        whenever(toggle.isEnabled()).thenReturn(false)
        val url = provider.builder().scheme(DaxScheme.HTTPS).encodedHost("example.com").build()
        assertFalse((url as RealDaxUrl).memoize)
    }
}
