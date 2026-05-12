/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.fire.store

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealTabVisitedSitesRepositoryTest {

    private val mockDao: TabVisitedSitesDao = mock()
    private lateinit var testee: RealTabVisitedSitesRepository

    @Before
    fun setup() {
        testee = RealTabVisitedSitesRepository(dao = mockDao)
    }

    @Test
    fun whenRecordVisitedSiteThenDaoInsertCalled() = runTest {
        testee.recordVisitedSite("tab1", "example.com")

        verify(mockDao).insert(TabVisitedSiteEntity(tabId = "tab1", domain = "example.com"))
    }

    @Test
    fun whenGetVisitedSitesThenReturnSetFromDao() = runTest {
        whenever(mockDao.getVisitedSites("tab1")).thenReturn(listOf("example.com", "test.com"))

        val result = testee.getVisitedSites("tab1")

        assertEquals(setOf("example.com", "test.com"), result)
    }

    @Test
    fun whenGetVisitedSitesForEmptyTabThenReturnEmptySet() = runTest {
        whenever(mockDao.getVisitedSites("tab1")).thenReturn(emptyList())

        val result = testee.getVisitedSites("tab1")

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenClearTabThenDaoClearTabCalled() = runTest {
        testee.clearTab("tab1")

        verify(mockDao).clearTab("tab1")
    }

    @Test
    fun whenClearAllThenDaoClearAllCalled() = runTest {
        testee.clearAll()

        verify(mockDao).clearAll()
    }
}
