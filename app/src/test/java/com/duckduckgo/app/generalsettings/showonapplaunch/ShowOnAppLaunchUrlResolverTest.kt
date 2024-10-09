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

package com.duckduckgo.app.generalsettings.showonapplaunch

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.generalsettings.showonapplaunch.store.ShowOnAppLaunchOptionDataStore.Companion.DEFAULT_SPECIFIC_PAGE_URL
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowOnAppLaunchUrlResolverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val fakeUrlFetcher = FakeUrlFetcher()
    private val testee = ShowOnAppLaunchUrlResolver(fakeUrlFetcher)

    @Test
    fun whenUrlIsNullThenShouldReturnDefaultUrl() = runTest {
        val result = testee.resolve(null)
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlIsEmptyThenShouldReturnDefaultUrl() = runTest {
        val result = testee.resolve("")
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlIsBlankThenShouldReturnDefaultUrl() = runTest {
        val result = testee.resolve(" ")
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlHasNoSchemeThenHttpSchemeIsAdded() = runTest {
        val result = testee.resolve("www.example.com")
        assertEquals("http://www.example.com", result)
    }

    @Test
    fun whenUrlHasNoSchemeAndSubdomainThenHttpSchemeIsAdded() = runTest {
        val result = testee.resolve("example.com")
        assertEquals("http://example.com", result)
    }

    @Test
    fun whenUrlDoesNotHaveAPathThenForwardSlashIsAdded() = runTest {
        val result = testee.resolve("https://www.example.com")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasASchemeThenShouldReturnTheSameUrl() = runTest {
        val result = testee.resolve("https://www.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasDifferentSchemeThenShouldReturnTheSameUrl() = runTest {
        val result = testee.resolve("ftp://www.example.com/")
        assertEquals("ftp://www.example.com/", result)
    }

    @Test
    fun whenUrlHasSpecialCharactersThenShouldReturnTheSameUrl() = runTest {
        val result = testee.resolve("https://www.example.com/path?query=param&another=param")
        assertEquals("https://www.example.com/path?query=param&another=param", result)
    }

    @Test
    fun whenUrlHasPortThenShouldReturnTheSameUrl() = runTest {
        val result = testee.resolve("https://www.example.com:8080/")
        assertEquals("https://www.example.com:8080/", result)
    }

    @Test
    fun whenUrlHasPathAndQueryParametersThenShouldReturnTheSameUrl() = runTest {
        val result = testee.resolve("https://www.example.com/path/to/resource?query=param")
        assertEquals("https://www.example.com/path/to/resource?query=param", result)
    }

    @Test
    fun whenUrlHasUppercaseProtocolThenShouldLowercaseProtocol() = runTest {
        val result = testee.resolve("HTTPS://www.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseSubdomainThenShouldLowercaseSubdomain() = runTest {
        val result = testee.resolve("https://WWW.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseDomainThenShouldLowercaseDomain() = runTest {
        val result = testee.resolve("https://www.EXAMPLE.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseTopLevelDomainThenShouldLowercaseTopLevelDomain() = runTest {
        val result = testee.resolve("https://www.example.COM/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasMixedCaseThenOnlyProtocolSubdomainDomainAndTldAreLowercased() = runTest {
        val result = testee.resolve("HTTPS://WWW.EXAMPLE.COM/Path?Query=Param#Fragment")
        assertEquals("https://www.example.com/Path?Query=Param#Fragment", result)
    }

    @Test
    fun whenUrlIsNotAValidUrlReturnsInvalidUrlWithHttpScheme() = runTest {
        val result = testee.resolve("example")
        assertEquals("http://example", result)
    }

    @Test
    fun whenUrlIsFetchedThenResolvedUrlIsReturned() = runTest {
        val resolvedUrl = "http://www.example.com"
        fakeUrlFetcher.fakeFetchedUrl = resolvedUrl

        val result = testee.resolve("example.com")
        assertEquals(resolvedUrl, result)
    }

    private class FakeUrlFetcher : UrlFetcher {

        var fakeFetchedUrl: String? = null

        override suspend fun fetchUrl(url: String): String = fakeFetchedUrl ?: url
    }
}
