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

package com.duckduckgo.app.browser.weblocalstorage

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.FileUtilities
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WebLocalStorageSettingsJsonParserImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val testee = WebLocalStorageSettingsJsonParserImpl(coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenGibberishInputThenReturnEmptyDomainsAndRegex() = runTest {
        val result = testee.parseJson("invalid json")
        assertTrue(result.domains.list.isEmpty())
        assertTrue(result.matchingRegex.list.isEmpty())
    }

    @Test
    fun whenDomainsAndRegexMissingThenReturnEmptyDomainsAndRegex() = runTest {
        val result = testee.parseJson("{}")
        assertTrue(result.domains.list.isEmpty())
        assertTrue(result.matchingRegex.list.isEmpty())
    }

    @Test
    fun whenListsEmptyThenReturnEmptyDomainsAndRegex() = runTest {
        val result = testee.parseJson("web_local_storage_empty".loadJsonFile())
        assertTrue(result.domains.list.isEmpty())
        assertTrue(result.matchingRegex.list.isEmpty())
    }

    @Test
    fun whenListsHaveSingleEntryThenReturnSingleDomainAndRegex() = runTest {
        val result = testee.parseJson("web_local_storage_single_entry".loadJsonFile())
        assertEquals(1, result.domains.list.size)
        assertEquals(1, result.matchingRegex.list.size)
        assertEquals("example.com", result.domains.list[0])
        assertEquals("^_https://([a-zA-Z0-9.-]+\\.)?{domain}\u0000\u0001.+$", result.matchingRegex.list[0])
    }

    @Test
    fun whenListsHaveMultipleEntriesThenReturnMultipleDomainsAndRegex() = runTest {
        val result = testee.parseJson("web_local_storage_multiple_entries".loadJsonFile())
        assertEquals(3, result.domains.list.size)
        assertEquals(3, result.matchingRegex.list.size)

        assertEquals("example.com", result.domains.list[0])
        assertEquals("foo.com", result.domains.list[1])
        assertEquals("bar.com", result.domains.list[2])

        assertEquals("^_https://([a-zA-Z0-9.-]+\\.)?{domain}\u0000\u0001.+$", result.matchingRegex.list[0])
        assertEquals("^META:https://([a-zA-Z0-9.-]+\\.)?{domain}$", result.matchingRegex.list[1])
        assertEquals("^METAACCESS:https://([a-zA-Z0-9.-]+\\.)?{domain}$", result.matchingRegex.list[2])
    }

    private fun String.loadJsonFile(): String {
        return FileUtilities.loadText(
            WebLocalStorageSettingsJsonParserImplTest::class.java.classLoader!!,
            "json/$this.json",
        )
    }
}
