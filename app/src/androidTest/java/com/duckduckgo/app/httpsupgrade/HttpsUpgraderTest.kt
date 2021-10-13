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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.httpsupgrade.store.HttpsFalsePositivesDao
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.runBlocking
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.duckduckgo.privacy.config.api.Https
import com.duckduckgo.privacy.config.api.PrivacyFeatureName
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class HttpsUpgraderTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    lateinit var testee: HttpsUpgrader

    private var mockHttpsBloomFilterFactory: HttpsBloomFilterFactory = mock()
    private var mockBloomFalsePositiveListDao: HttpsFalsePositivesDao = mock()
    private var mockUserAllowlistDao: UserWhitelistDao = mock()

    private var mockFeatureToggle: FeatureToggle = mock()
    private var mockHttps: Https = mock()
    private var bloomFilter = BloomFilter(100, 0.01)

    @Before
    fun before() {

        whenever(mockHttpsBloomFilterFactory.create()).thenReturn(bloomFilter)
        coroutineRule.runBlocking {
            whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName())).thenReturn(true)
        }
        testee = HttpsUpgraderImpl(mockHttpsBloomFilterFactory, mockBloomFalsePositiveListDao, mockUserAllowlistDao, mockFeatureToggle, mockHttps)
        testee.reloadData()
    }

    @Test
    fun whenFeatureIsDisableTheShouldNotUpgrade() = coroutineRule.runBlocking {
        whenever(mockFeatureToggle.isFeatureEnabled(PrivacyFeatureName.HttpsFeatureName())).thenReturn(false)
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpUrlIsInExceptionListThenShouldNotUpgrade() = coroutineRule.runBlocking {
        whenever(mockHttps.isAnException("http://www.local.url")).thenReturn(true)
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpUriIsInBloomThenShouldUpgrade() = coroutineRule.runBlocking {
        bloomFilter.add("www.local.url")
        assertTrue(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpUriIsNotInBloomThenShouldNotUpgrade() = coroutineRule.runBlocking {
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.differentlocal.url")))
    }

    @Test
    fun whenHttpsUriThenShouldNotUpgrade() = coroutineRule.runBlocking {
        bloomFilter.add("www.local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("https://www.local.url")))
    }

    @Test
    fun whenHttpUriHasOnlyPartDomainInLocalListThenShouldNotUpgrade() = coroutineRule.runBlocking {
        bloomFilter.add("local.url")
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }

    @Test
    fun whenHttpDomainIsUserWhitelistedThenShouldNotUpgrade() = coroutineRule.runBlocking {
        bloomFilter.add("www.local.url")
        whenever(mockUserAllowlistDao.contains("www.local.url")).thenReturn(true)
        assertFalse(testee.shouldUpgrade(Uri.parse("http://www.local.url")))
    }
}
