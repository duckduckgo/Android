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

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.db.TabPageContextDao
import com.duckduckgo.app.tabs.db.TabPageContextEntity
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabPageContextDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var tabsDao: TabsDao
    private lateinit var testee: TabPageContextDao

    @Before
    fun before() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        tabsDao = database.tabsDao()
        testee = database.tabPageContextDao()
    }

    @After
    fun after() {
        database.close()
    }

    @Test
    fun whenInsertThenRetrieveByTabId() = runTest {
        insertTab("tab1")
        val entity = entity("tab1", "https://example.com", "page content", 1000L)

        testee.insertOrReplace(entity)

        val result = testee.getByTabIds(listOf("tab1"))
        assertEquals(1, result.size)
        assertEquals(entity, result[0])
    }

    @Test
    fun whenInsertSameTabIdThenReplaceExisting() = runTest {
        insertTab("tab1")
        testee.insertOrReplace(entity("tab1", "https://old.com", "old content", 1000L))

        val updated = entity("tab1", "https://new.com", "new content", 2000L)
        testee.insertOrReplace(updated)

        val result = testee.getByTabIds(listOf("tab1"))
        assertEquals(1, result.size)
        assertEquals("https://new.com", result[0].url)
        assertEquals("new content", result[0].serializedPageContext)
        assertEquals(2000L, result[0].collectedAt)
    }

    @Test
    fun whenQueryMultipleTabsThenReturnMatchingEntries() = runTest {
        insertTab("tab1")
        insertTab("tab2")
        insertTab("tab3")
        testee.insertOrReplace(entity("tab1", "https://one.com", "content1", 1000L))
        testee.insertOrReplace(entity("tab2", "https://two.com", "content2", 2000L))
        testee.insertOrReplace(entity("tab3", "https://three.com", "content3", 3000L))

        val result = testee.getByTabIds(listOf("tab1", "tab3"))
        assertEquals(2, result.size)
        val tabIds = result.map { it.tabId }.toSet()
        assertEquals(setOf("tab1", "tab3"), tabIds)
    }

    @Test
    fun whenQueryNonExistentTabsThenReturnEmpty() = runTest {
        val result = testee.getByTabIds(listOf("nonexistent"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenDeleteAllThenTableIsEmpty() = runTest {
        insertTab("tab1")
        insertTab("tab2")
        testee.insertOrReplace(entity("tab1", "https://one.com", "content1", 1000L))
        testee.insertOrReplace(entity("tab2", "https://two.com", "content2", 2000L))

        testee.deleteAll()

        val result = testee.getByTabIds(listOf("tab1", "tab2"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenTabDeletedThenCascadeDeletesPageContext() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", position = 0)
        tabsDao.insertTab(tab)
        testee.insertOrReplace(entity("tab1", "https://example.com", "content", 1000L))

        tabsDao.deleteTab(tab)

        val result = testee.getByTabIds(listOf("tab1"))
        assertTrue(result.isEmpty())
    }

    @Test
    fun whenAllTabsDeletedThenCascadeDeletesAllPageContexts() = runTest {
        insertTab("tab1")
        insertTab("tab2")
        testee.insertOrReplace(entity("tab1", "https://one.com", "content1", 1000L))
        testee.insertOrReplace(entity("tab2", "https://two.com", "content2", 2000L))

        tabsDao.deleteAllTabs()

        val result = testee.getByTabIds(listOf("tab1", "tab2"))
        assertTrue(result.isEmpty())
    }

    private fun insertTab(tabId: String, position: Int = 0) {
        tabsDao.insertTab(TabEntity(tabId = tabId, position = position))
    }

    private fun entity(tabId: String, url: String, content: String, collectedAt: Long) =
        TabPageContextEntity(tabId = tabId, url = url, serializedPageContext = content, collectedAt = collectedAt)
}
