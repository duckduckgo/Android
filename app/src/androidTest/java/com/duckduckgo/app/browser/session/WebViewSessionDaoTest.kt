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

package com.duckduckgo.app.browser.session

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.tabs.db.TabsDao
import com.duckduckgo.app.tabs.model.TabEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WebViewSessionDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: WebViewSessionDao
    private lateinit var tabsDao: TabsDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.webViewSessionDao()
        tabsDao = db.tabsDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun whenSessionUpsertedThenGetReturnsSameBytes() = runTest {
        val tabId = "tab-1"
        tabsDao.insertTab(TabEntity(tabId = tabId))
        val bytes = byteArrayOf(1, 2, 3, 4, 5)

        dao.upsert(WebViewSessionEntity(tabId, bytes, savedAt = 1000L))

        val loaded = dao.get(tabId)
        assertEquals(tabId, loaded?.tabId)
        assertArrayEquals(bytes, loaded?.sessionBundle)
        assertEquals(1000L, loaded?.savedAt)
    }

    @Test
    fun whenUpsertReplacesExistingThenLatestBytesAreReturned() = runTest {
        val tabId = "tab-1"
        tabsDao.insertTab(TabEntity(tabId = tabId))
        dao.upsert(WebViewSessionEntity(tabId, byteArrayOf(1), savedAt = 1000L))

        dao.upsert(WebViewSessionEntity(tabId, byteArrayOf(2, 2), savedAt = 2000L))

        val loaded = dao.get(tabId)
        assertArrayEquals(byteArrayOf(2, 2), loaded?.sessionBundle)
        assertEquals(2000L, loaded?.savedAt)
    }

    @Test
    fun whenSessionDeletedThenGetReturnsNull() = runTest {
        val tabId = "tab-1"
        tabsDao.insertTab(TabEntity(tabId = tabId))
        dao.upsert(WebViewSessionEntity(tabId, byteArrayOf(1), savedAt = 1000L))

        dao.delete(tabId)

        assertNull(dao.get(tabId))
    }

    @Test
    fun whenDeleteAllThenRowsRemoved() = runTest {
        tabsDao.insertTab(TabEntity(tabId = "a"))
        tabsDao.insertTab(TabEntity(tabId = "b"))
        dao.upsert(WebViewSessionEntity("a", byteArrayOf(1), savedAt = 1000L))
        dao.upsert(WebViewSessionEntity("b", byteArrayOf(2), savedAt = 2000L))

        dao.deleteAll()

        assertNull(dao.get("a"))
        assertNull(dao.get("b"))
    }

    @Test
    fun whenTabIsDeletedThenSessionRowIsCascadeDeleted() = runTest {
        val tabId = "tab-1"
        val tab = TabEntity(tabId = tabId)
        tabsDao.insertTab(tab)
        dao.upsert(WebViewSessionEntity(tabId, byteArrayOf(1), savedAt = 1000L))

        tabsDao.deleteTab(tab)

        assertNull(dao.get(tabId))
    }
}
