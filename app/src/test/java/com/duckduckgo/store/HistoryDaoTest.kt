/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.history.impl.store.HistoryDao
import com.duckduckgo.history.impl.store.HistoryDatabase
import com.duckduckgo.history.impl.store.HistoryEntryEntity
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {

    private lateinit var historyDao: HistoryDao
    private lateinit var db: HistoryDatabase

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, HistoryDatabase::class.java).allowMainThreadQueries().build()
        historyDao = db.historyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGetHistoryEntryByUrl() {
        val historyEntry = HistoryEntryEntity(url = "url", title = "title", query = "query", isSerp = false)
        historyDao.insertHistoryEntry(historyEntry)

        val retrievedEntry = historyDao.getHistoryEntryByUrl("url")
        Assert.assertNotNull(retrievedEntry)
        Assert.assertEquals(historyEntry.url, retrievedEntry?.url)
    }

    @Test
    fun whenInsertSameUrlWithSameDateTwiceThenOnlyOneEntryAndOneVisitAreStored() {
        historyDao.updateOrInsertVisit("url", "title", "query", false, 1L)
        historyDao.updateOrInsertVisit("url", "title", "query", false, 1L)

        val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
        Assert.assertEquals(1, historyEntriesWithVisits.count())
        Assert.assertEquals(1, historyEntriesWithVisits.first().visits.count())
    }

    @Test
    fun whenInsertSameUrlWithDifferentDateTwiceThenOneEntryAndTwoVisitsAreStored() {
        historyDao.updateOrInsertVisit("url", "title", "query", false, 1L)
        historyDao.updateOrInsertVisit("url", "title", "query", false, 2L)

        val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
        Assert.assertEquals(1, historyEntriesWithVisits.count())
        Assert.assertEquals(2, historyEntriesWithVisits.first().visits.count())
    }
}
