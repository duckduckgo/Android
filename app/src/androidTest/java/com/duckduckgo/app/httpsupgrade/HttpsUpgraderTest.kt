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
import com.duckduckgo.app.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Call

class HttpsUpgraderTest {

    lateinit var testee: HttpsUpgrader

    private var mockHttpsBloomFilterFactory: HttpsBloomFilterFactory = mock()
    private var mockBloomFalsePositiveListDao: HttpsFalsePositivesDao = mock()
    private var mockUserAllowlistDao: UserWhitelistDao = mock()
    private var mockServiceCall: Call<List<String>> = mock()

    private var mockPixel: Pixel = mock()
    private var bloomFilter = BloomFilter(100, 0.01)

    @Before
    fun before() {
        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(bloomFilter)
        testee = HttpsUpgraderImpl(mockHttpsBloomFilterFactory, mockBloomFalsePositiveListDao, mockUserAllowlistDao, mockPixel)
        testee.reloadData()
    }

    @Test
    fun whenHttpUriIsInBloomThenShouldUpgrade() {
        bloomFilter.add("www.local.url")
        assertTrue(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
        mockPixel.fire(Pixel.AppPixelName.HTTPS_LOCAL_UPGRADE)
    }

    @Test
    fun whenHttpUriIsNotInBloomThenShouldNotUpgrade() {
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.differentlocal.url")))
        mockPixel.fire(Pixel.AppPixelName.HTTPS_NO_UPGRADE)
    }

    @Test
    fun whenHttpsUriThenShouldNotUpgrade() {
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("https://www.local.url")))
        mockPixel.fire(Pixel.AppPixelName.HTTPS_NO_UPGRADE)
    }

    @Test
    fun whenHttpUriHasOnlyPartDomainInLocalListThenShouldNotUpgrade() {
        bloomFilter.add("local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
        mockPixel.fire(Pixel.AppPixelName.HTTPS_NO_LOOKUP)
    }

    @Test
    fun whenHttpDomainIsUserWhitelistedThenShouldNotUpgrade() {
        bloomFilter.add("www.local.url")
        whenever(mockUserAllowlistDao.contains("www.local.url")).thenReturn(true)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
        mockPixel.fire(Pixel.AppPixelName.HTTPS_NO_LOOKUP)
    }
}
