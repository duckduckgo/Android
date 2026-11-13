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

package com.duckduckgo.privacy.config.impl.observers

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.privacy.config.store.AllowlistRuleEntity
import com.duckduckgo.privacy.config.store.ContentBlockingExceptionEntity
import com.duckduckgo.privacy.config.store.PrivacyConfigDatabase
import com.duckduckgo.privacy.config.store.TrackerAllowlistEntity
import com.duckduckgo.privacy.config.store.UnprotectedTemporaryEntity
import com.duckduckgo.privacy.config.store.features.contentblocking.ContentBlockingDao
import com.duckduckgo.privacy.config.store.features.contentblocking.RealContentBlockingRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.RealTrackerAllowlistRepository
import com.duckduckgo.privacy.config.store.features.trackerallowlist.TrackerAllowlistDao
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.RealUnprotectedTemporaryRepository
import com.duckduckgo.privacy.config.store.features.unprotectedtemporary.UnprotectedTemporaryDao
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PirProcessPrivacyConfigLoaderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockTrackerAllowlistDao: TrackerAllowlistDao = mock()
    private val mockContentBlockingDao: ContentBlockingDao = mock()
    private val mockUnprotectedTemporaryDao: UnprotectedTemporaryDao = mock()

    private lateinit var trackerAllowlistRepository: RealTrackerAllowlistRepository
    private lateinit var contentBlockingRepository: RealContentBlockingRepository
    private lateinit var unprotectedTemporaryRepository: RealUnprotectedTemporaryRepository
    private lateinit var testee: PirProcessPrivacyConfigLoader

    @Before
    fun setup() {
        whenever(mockDatabase.trackerAllowlistDao()).thenReturn(mockTrackerAllowlistDao)
        whenever(mockDatabase.contentBlockingDao()).thenReturn(mockContentBlockingDao)
        whenever(mockDatabase.unprotectedTemporaryDao()).thenReturn(mockUnprotectedTemporaryDao)
        whenever(mockTrackerAllowlistDao.getAll()).thenReturn(
            listOf(TrackerAllowlistEntity("captcha-delivery.com", listOf(AllowlistRuleEntity("captcha-delivery.com", listOf("<all>"), "")))),
        )
        whenever(mockContentBlockingDao.getAll()).thenReturn(listOf(ContentBlockingExceptionEntity("example.com", "reason")))
        whenever(mockUnprotectedTemporaryDao.getAll()).thenReturn(listOf(UnprotectedTemporaryEntity("example.org", "reason")))

        val dispatcherProvider = coroutineRule.testDispatcherProvider
        val scope = coroutineRule.testScope
        trackerAllowlistRepository = RealTrackerAllowlistRepository(mockDatabase, scope, dispatcherProvider, isMainProcess = false)
        contentBlockingRepository = RealContentBlockingRepository(mockDatabase, scope, dispatcherProvider, isMainProcess = false)
        unprotectedTemporaryRepository = RealUnprotectedTemporaryRepository(mockDatabase, scope, dispatcherProvider, isMainProcess = false)

        testee = PirProcessPrivacyConfigLoader(
            trackerAllowlistRepository,
            contentBlockingRepository,
            unprotectedTemporaryRepository,
            scope,
            dispatcherProvider,
        )
    }

    @Test
    fun whenPirProcessCreatedThenTrackerAllowlistLoadedIntoMemory() {
        testee.onPirProcessCreated()

        assertEquals(setOf("captcha-delivery.com"), trackerAllowlistRepository.rulesByDomain.keys)
    }

    @Test
    fun whenPirProcessCreatedThenContentBlockingExceptionsLoadedIntoMemory() {
        testee.onPirProcessCreated()

        assertEquals(listOf("example.com"), contentBlockingRepository.exceptions.map { it.domain })
    }

    @Test
    fun whenPirProcessCreatedThenUnprotectedTemporaryExceptionsLoadedIntoMemory() {
        testee.onPirProcessCreated()

        assertEquals(listOf("example.org"), unprotectedTemporaryRepository.exceptions.map { it.domain })
    }

    @Test
    fun whenOneLoadFailsThenOtherExceptionsStillLoadedIntoMemory() {
        whenever(mockTrackerAllowlistDao.getAll()).thenThrow(IllegalStateException("db error"))

        testee.onPirProcessCreated()

        assertEquals(emptySet<String>(), trackerAllowlistRepository.rulesByDomain.keys)
        assertEquals(listOf("example.com"), contentBlockingRepository.exceptions.map { it.domain })
        assertEquals(listOf("example.org"), unprotectedTemporaryRepository.exceptions.map { it.domain })
    }
}
