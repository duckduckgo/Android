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
import com.duckduckgo.app.httpsupgrade.api.HttpsUpgradeService
import com.duckduckgo.app.httpsupgrade.db.HttpsWhitelistDao
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.ResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Call
import retrofit2.Response

class HttpsUpgraderTest {

    lateinit var testee: HttpsUpgrader

    private var mockHttpsBloomFilterFactory: HttpsBloomFilterFactory = mock()
    private var mockWhitelistDao: HttpsWhitelistDao = mock()
    private var mockUpgradeService: HttpsUpgradeService = mock()
    private var mockServiceCall: Call<List<String>> = mock()

    private var mockPixel: Pixel = mock()
    private var bloomFilter = BloomFilter(100, 0.01)

    @Before
    fun before() {
        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(bloomFilter)
        testee = HttpsUpgraderImpl(mockWhitelistDao, mockHttpsBloomFilterFactory, mockUpgradeService, mockPixel)
        testee.reloadData()
    }

    @Test
    fun whenHttpUriIsInLocalListThenShouldUpgrade() {
        bloomFilter.add("www.local.url")
        assertTrue(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpsUriThenShouldNotUpgrade() {
        assertFalse(testee.shouldUpgrade(Uri.parse("https://www.local.url")))
    }

    @Test
    fun whenHttpUriHasOnlyPartDomainInLocalListThenShouldNotUpgrade() {
        bloomFilter.add("local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpUriIsInLocalListAndInWhitelistThenShouldNotUpgrade() {
        bloomFilter.add("www.local.url")
        whenever(mockWhitelistDao.contains("www.local.url")).thenReturn(true)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpUriIsNotInLocalListButCanBeUpgradedByServiceThenShouldUpgrade() {
        whenever(mockServiceCall.execute()).thenReturn(Response.success(serviceResponse()))
        whenever(mockUpgradeService.upgradeListForPartialHost(any())).thenReturn(mockServiceCall)
        assertTrue(testee.shouldUpgrade(Uri.parse("http://service.url")))
    }

    @Test
    fun whenHttpUriIsNotInLocalListAndCannotBeUpgradedByServiceThenShouldNotUpgrade() {
        whenever(mockServiceCall.execute()).thenReturn(Response.success(serviceResponse()))
        whenever(mockUpgradeService.upgradeListForPartialHost(any())).thenReturn(mockServiceCall)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://unknown.com")))
    }

    @Test
    fun whenHttpUriIsNotInLocalListAndServiceRequestFailsThenShouldNotUpgrade() {
        whenever(mockServiceCall.execute()).thenReturn(Response.error(500, ResponseBody.create(null, "")))
        whenever(mockUpgradeService.upgradeListForPartialHost(any())).thenReturn(mockServiceCall)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://service.url")))
    }

    @Test
    fun whenBloomFilterIsNotLoadedAndUrlIsInServiceListThenShouldUpgrade() {
        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(null)
        whenever(mockServiceCall.execute()).thenReturn(Response.success(serviceResponse()))
        whenever(mockUpgradeService.upgradeListForPartialHost(any())).thenReturn(mockServiceCall)
        assertTrue(testee.shouldUpgrade(Uri.parse("http://service.url")))
    }

    @Test
    fun testWhenBloomFilterIsNotLoadedAndUrlNotInServiceListThenShouldNotUpgrade() {
        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(null)
        whenever(mockServiceCall.execute()).thenReturn(Response.success(serviceResponse()))
        whenever(mockUpgradeService.upgradeListForPartialHost(any())).thenReturn(mockServiceCall)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://unknown.com")))
    }

    private fun serviceResponse(): List<String> {
        return arrayListOf(
            "cfb1a171724ad0b8f108526d6a201667f74691e4",
            "cfb10e3da9ae3bc2ba7e4641c911987da63aa0a7",
            "cfb18efe21acd63556dd75bcae7003a2fff90752"
        )
    }
}
