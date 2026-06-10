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
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShowOnAppLaunchUrlConverterImplTest {

    private val urlConverter = ShowOnAppLaunchUrlConverterImpl()

    @Test
    fun whenUrlIsNullThenShouldReturnDefaultUrl() {
        val result = urlConverter.convertUrl(null)
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlIsEmptyThenShouldReturnDefaultUrl() {
        val result = urlConverter.convertUrl("")
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlIsBlankThenShouldReturnDefaultUrl() {
        val result = urlConverter.convertUrl(" ")
        assertEquals(DEFAULT_SPECIFIC_PAGE_URL, result)
    }

    @Test
    fun whenUrlHasNoSchemeThenHttpSchemeIsAdded() {
        val result = urlConverter.convertUrl("www.example.com")
        assertEquals("http://www.example.com", result)
    }

    @Test
    fun whenUrlHasNoSchemeAndSubdomainThenHttpSchemeIsAdded() {
        val result = urlConverter.convertUrl("example.com")
        assertEquals("http://example.com", result)
    }

    @Test
    fun whenUrlDoesNotHaveAPathThenForwardSlashIsAdded() {
        val result = urlConverter.convertUrl("https://www.example.com")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasASchemeThenShouldReturnTheSameUrl() {
        val result = urlConverter.convertUrl("https://www.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasDifferentSchemeThenShouldReturnTheSameUrl() {
        val result = urlConverter.convertUrl("ftp://www.example.com/")
        assertEquals("ftp://www.example.com/", result)
    }

    @Test
    fun whenUrlHasSpecialCharactersThenShouldReturnTheSameUrl() {
        val result = urlConverter.convertUrl("https://www.example.com/path?query=param&another=param")
        assertEquals("https://www.example.com/path?query=param&another=param", result)
    }

    @Test
    fun whenUrlHasPortThenShouldReturnTheSameUrl() {
        val result = urlConverter.convertUrl("https://www.example.com:8080/")
        assertEquals("https://www.example.com:8080/", result)
    }

    @Test
    fun whenUrlHasPathAndQueryParametersThenShouldReturnTheSameUrl() {
        val result = urlConverter.convertUrl("https://www.example.com/path/to/resource?query=param")
        assertEquals("https://www.example.com/path/to/resource?query=param", result)
    }

    @Test
    fun whenUrlHasUppercaseProtocolThenShouldLowercaseProtocol() {
        val result = urlConverter.convertUrl("HTTPS://www.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseSubdomainThenShouldLowercaseSubdomain() {
        val result = urlConverter.convertUrl("https://WWW.example.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseDomainThenShouldLowercaseDomain() {
        val result = urlConverter.convertUrl("https://www.EXAMPLE.com/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasUppercaseTopLevelDomainThenShouldLowercaseTopLevelDomain() {
        val result = urlConverter.convertUrl("https://www.example.COM/")
        assertEquals("https://www.example.com/", result)
    }

    @Test
    fun whenUrlHasMixedCaseThenOnlyProtocolSubdomainDomainAndTldAreLowercased() {
        val result = urlConverter.convertUrl("HTTPS://WWW.EXAMPLE.COM/Path?Query=Param#Fragment")
        assertEquals("https://www.example.com/Path?Query=Param#Fragment", result)
    }

    @Test
    fun whenUrlIsNotAValidUrlReturnsInvalidUrlWithHttpScheme() {
        val result = urlConverter.convertUrl("example")
        assertEquals("http://example", result)
    }

    @Test
    fun whenUrlHasADifferentSchemeThenSameUrlReturned() {
        val result = urlConverter.convertUrl("ftp://example.com/")
        assertEquals("ftp://example.com/", result)
    }

    @Test
    fun whenUrlHasADifferentSchemeAndNoTrailingSlashThenTrailingSlashAdded() {
        val result = urlConverter.convertUrl("ftp://example.com")
        assertEquals("ftp://example.com/", result)
    }
}
