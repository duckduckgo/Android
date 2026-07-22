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

package com.duckduckgo.tabs.db

import com.duckduckgo.app.tabs.db.RealTabPageContextRepository
import com.duckduckgo.app.tabs.db.TabPageContextDao
import com.duckduckgo.app.tabs.db.TabPageContextEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealTabPageContextRepositoryTest {

    private val mockDao: TabPageContextDao = mock()
    private lateinit var testee: RealTabPageContextRepository

    @Before
    fun setup() {
        testee = RealTabPageContextRepository(dao = mockDao)
    }

    @Test
    fun whenStorePageContextThenDaoInsertOrReplaceCalled() = runTest {
        testee.storePageContext("tab1", "https://example.com", """{"title":"Test"}""")

        val captor = argumentCaptor<TabPageContextEntity>()
        verify(mockDao).insertOrReplace(captor.capture())

        val entity = captor.firstValue
        assertEquals("tab1", entity.tabId)
        assertEquals("https://example.com", entity.url)
        assertEquals("""{"title":"Test"}""", entity.serializedPageContext)
        assertTrue(entity.collectedAt > 0)
    }

    @Test
    fun whenGetPageContextsThenReturnMappedResults() = runTest {
        val entities = listOf(
            TabPageContextEntity("tab1", "https://one.com", "content1", 1000L),
            TabPageContextEntity("tab2", "https://two.com", "content2", 2000L),
        )
        whenever(mockDao.getByTabIds(listOf("tab1", "tab2"))).thenReturn(entities)

        val result = testee.getPageContexts(listOf("tab1", "tab2"))

        assertEquals(2, result.size)
        assertEquals("https://one.com", result["tab1"]?.url)
        assertEquals("content1", result["tab1"]?.serializedPageContext)
        assertEquals(1000L, result["tab1"]?.collectedAt)
        assertEquals("https://two.com", result["tab2"]?.url)
        assertEquals("content2", result["tab2"]?.serializedPageContext)
    }

    @Test
    fun whenGetPageContextsForNonExistentTabsThenReturnEmptyMap() = runTest {
        whenever(mockDao.getByTabIds(listOf("nonexistent"))).thenReturn(emptyList())

        val result = testee.getPageContexts(listOf("nonexistent"))

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenDeleteAllThenDaoDeleteAllCalled() = runTest {
        testee.deleteAll()

        verify(mockDao).deleteAll()
    }
}
