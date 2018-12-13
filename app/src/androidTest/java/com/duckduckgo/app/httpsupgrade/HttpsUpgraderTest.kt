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

package com.duckduckgo.app.httpsupgrade

import android.net.Uri
import com.duckduckgo.app.httpsupgrade.api.HttpsBloomFilterFactory
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class HttpsUpgraderTest {

    lateinit var testee: HttpsUpgrader

    private var mockHttpsBloomFilterFactory: HttpsBloomFilterFactory = mock()
    private var mockWhitelistDao: HttpsWhitelistDao = mock()
    private var bloomFilter = BloomFilter(100, 0.01)

    @Before
    fun before() {
        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(bloomFilter)
        testee = HttpsUpgraderImpl(mockWhitelistDao, mockHttpsBloomFilterFactory)
        testee.reloadData()
    }

    @Test
    fun whenUriIsHttpsThenShouldNotUpgrade() {
        assertFalse(testee.shouldUpgrade(Uri.parse("https://www.example.com")))
    }

    @Test
    fun whenHttpUriIsNotInBloomFilterThenShouldNotUpgrade() {
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

    @Test
    fun whenHttpUriIsInBloomFilterThenShouldUpgrade() {
        bloomFilter.add("www.example.com")
        assertTrue(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

    @Test
    fun whenHttpUriHasOnlyPartDomainInBloomFilterThenShouldNotUpgrade() {
        bloomFilter.add("example.com")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

    @Test
    fun whenHttpUriIsInBloomFilterAndInWhitelistThenShouldNotUpgrade() {
        bloomFilter.add("www.example.com")
        whenever(mockWhitelistDao.contains("www.example.com")).thenReturn(true)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.example.com")))
    }

}
