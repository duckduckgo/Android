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

package com.duckduckgo.app.browser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DuckDuckGoUrlDetectorTest {

    private lateinit var testee: DuckDuckGoUrlDetector

    @Before
    fun setup() {
        testee = DuckDuckGoUrlDetector()
    }

    @Test
    fun whenCheckingSearchTermThenIdentifiedAsNotDDGUrl() {
        assertFalse(testee.isDuckDuckGoUrl("foo"))
    }

    @Test
    fun whenCheckingNonDDGUrlThenIdentifiedAsNotDDGUrl() {
        assertFalse(testee.isDuckDuckGoUrl("example.com"))
    }

    @Test
    fun whenCheckingFullDDGUrlThenIdentifiedAsDDGUrl() {
        assertTrue(testee.isDuckDuckGoUrl("https://duckduckgo.com/?q=test%20search&tappv=android_0_2_0&t=ddg_android"))
    }

    @Test
    fun whenDDGUrlContainsQueryThenQueryCanBeExtracted() {
        val query = testee.extractQuery("https://duckduckgo.com?q=test%20search")
        assertEquals("test search", query)
    }

    @Test
    fun whenDDGUrlDoesNotContainsQueryThenQueryIsNull() {
        val query = testee.extractQuery("https://duckduckgo.com")
        assertNull(query)
    }

    @Test
    fun whenDDGUrlContainsQueryThenQueryDetected() {
        assertTrue(testee.isDuckDuckGoQueryUrl("https://duckduckgo.com?q=test%20search"))
    }

    @Test
    fun whenDDGUrlDoesNotContainsQueryThenQueryIsNotDetected() {
        assertFalse(testee.isDuckDuckGoQueryUrl("https://duckduckgo.com"))
    }

    @Test
    fun whenNonDDGUrlContainsQueryThenQueryIsNotDetected() {
        assertFalse(testee.isDuckDuckGoQueryUrl("https://example.com?q=test%20search"))
    }
}
