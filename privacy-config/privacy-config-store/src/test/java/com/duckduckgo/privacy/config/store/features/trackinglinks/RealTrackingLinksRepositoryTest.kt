/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.privacy.config.store.features.trackinglinks

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.runBlocking
import com.duckduckgo.privacy.config.store.*
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyList

class RealTrackingLinksRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    lateinit var testee: RealTrackingLinksRepository

    private val mockDatabase: PrivacyConfigDatabase = mock()
    private val mockTrackingLinksDao: TrackingLinksDao = mock()

    @Before
    fun before() {
        whenever(mockDatabase.trackingLinksDao()).thenReturn(mockTrackingLinksDao)
        testee = RealTrackingLinksRepository(
            mockDatabase,
            TestCoroutineScope(),
            coroutineRule.testDispatcherProvider
        )
    }

    @Test
    fun whenRepositoryIsCreatedThenValuesLoadedIntoMemory() {
        givenTrackingLinksDaoContainsEntities()

        testee = RealTrackingLinksRepository(
            mockDatabase,
            TestCoroutineScope(),
            coroutineRule.testDispatcherProvider
        )

        assertEquals(trackingLinksExceptionEntity.toTrackingLinksException(), testee.exceptions.first())
        assertEquals(ampLinkFormatEntity.format, testee.ampLinkFormats.first())
        assertEquals(ampKeywordEntity.keyword, testee.ampKeywords.first())
        assertEquals(trackingParameterEntity.parameter, testee.trackingParameters.first())
    }

    @Test
    fun whenUpdateAllThenUpdateAllCalled() = coroutineRule.runBlocking {
        testee = RealTrackingLinksRepository(
            mockDatabase,
            TestCoroutineScope(),
            coroutineRule.testDispatcherProvider
        )

        testee.updateAll(listOf(), listOf(), listOf(), listOf())

        verify(mockTrackingLinksDao).updateAll(anyList(), anyList(), anyList(), anyList())
    }

    @Test
    fun whenUpdateAllThenPreviousValuesAreCleared() = coroutineRule.runBlocking {
        givenTrackingLinksDaoContainsEntities()

        testee = RealTrackingLinksRepository(
            mockDatabase,
            TestCoroutineScope(),
            coroutineRule.testDispatcherProvider
        )
        assertEquals(1, testee.exceptions.size)
        assertEquals(1, testee.ampLinkFormats.size)
        assertEquals(1, testee.ampKeywords.size)
        assertEquals(1, testee.trackingParameters.size)

        reset(mockTrackingLinksDao)

        testee.updateAll(listOf(), listOf(), listOf(), listOf())

        assertEquals(0, testee.exceptions.size)
        assertEquals(0, testee.ampLinkFormats.size)
        assertEquals(0, testee.ampKeywords.size)
        assertEquals(0, testee.trackingParameters.size)
    }

    private fun givenTrackingLinksDaoContainsEntities() {
        whenever(mockTrackingLinksDao.getAllExceptions()).thenReturn(listOf(trackingLinksExceptionEntity))
        whenever(mockTrackingLinksDao.getAllAmpLinkFormats()).thenReturn(listOf(ampLinkFormatEntity))
        whenever(mockTrackingLinksDao.getAllAmpKeywords()).thenReturn(listOf(ampKeywordEntity))
        whenever(mockTrackingLinksDao.getAllTrackingParameters()).thenReturn(listOf(trackingParameterEntity))
    }

    companion object {
        val trackingLinksExceptionEntity = TrackingLinksExceptionEntity(
            domain = "domain",
            reason = "reason"
        )

        val ampLinkFormatEntity = AmpLinkFormatEntity(
            format = "format"
        )

        val ampKeywordEntity = AmpKeywordEntity(
            keyword = "keyword"
        )

        val trackingParameterEntity = TrackingParameterEntity(
            parameter = "parameter"
        )
    }
}
