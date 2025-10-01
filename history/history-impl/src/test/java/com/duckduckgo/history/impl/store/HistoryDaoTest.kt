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

package com.duckduckgo.history.impl.store

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.Month.JANUARY

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
        runTest {
            val historyEntry = HistoryEntryEntity(url = "url", title = "title", query = "query", isSerp = false)
            historyDao.insertHistoryEntry(historyEntry)

            val retrievedEntry = historyDao.getHistoryEntryByUrl("url")
            Assert.assertNotNull(retrievedEntry)
            Assert.assertEquals(historyEntry.url, retrievedEntry?.url)
        }
    }

    @Test
    fun whenInsertSameUrlWithSameDateTwiceThenOnlyOneEntryAndOneVisitAreStored() {
        runTest {
            historyDao.updateOrInsertVisit("url", "title", "query", false, LocalDateTime.of(2000, JANUARY, 1, 0, 0))
            historyDao.updateOrInsertVisit("url", "title", "query", false, LocalDateTime.of(2000, JANUARY, 1, 0, 0))

            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(1, historyEntriesWithVisits.count())
            Assert.assertEquals(1, historyEntriesWithVisits.first().visits.count())
        }
    }

    @Test
    fun whenInsertSameUrlWithDifferentDateTwiceThenOneEntryAndTwoVisitsAreStored() {
        runTest {
            historyDao.updateOrInsertVisit("url", "title", "query", false, LocalDateTime.of(2000, JANUARY, 1, 0, 0))
            historyDao.updateOrInsertVisit("url", "title", "query", false, LocalDateTime.of(2000, JANUARY, 2, 0, 0))

            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(1, historyEntriesWithVisits.count())
            Assert.assertEquals(2, historyEntriesWithVisits.first().visits.count())
        }
    }

    @Test
    fun whenInsertSameUrlWithDifferentDateAndDifferentTitleTwiceThenOneEntryAndTwoVisitsAreStored() {
        runTest {
            historyDao.updateOrInsertVisit("url", "title", "query", false, LocalDateTime.of(2000, JANUARY, 1, 0, 0))
            historyDao.updateOrInsertVisit("url", "title2", "query", false, LocalDateTime.of(2000, JANUARY, 2, 0, 0))

            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(1, historyEntriesWithVisits.count())
            Assert.assertEquals(2, historyEntriesWithVisits.first().visits.count())
            Assert.assertEquals("title2", historyEntriesWithVisits.first().historyEntry.title)
        }
    }

    @Test
    fun whenDeleteOldItemsWithNoOldEnoughItemsThenNothingIsDeleted() {
        runTest {
            val insertDate = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
            historyDao.updateOrInsertVisit("url", "title", "query", false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title2", "query2", false, insertDate)
            historyDao.deleteEntriesOlderThan(insertDate.minusMinutes(1))
            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(2, historyEntriesWithVisits.count())
            Assert.assertEquals(1, historyEntriesWithVisits.first().visits.count())
        }
    }

    @Test
    fun whenDeleteOldItemsWithOldEnoughItemsThenTheyAreDeleted() {
        runTest {
            val insertDate = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
            historyDao.updateOrInsertVisit("url", "title", "query", false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title2", "query2", false, insertDate)
            historyDao.deleteEntriesOlderThan(insertDate.plusMinutes(1))
            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(0, historyEntriesWithVisits.count())
        }
    }

    @Test
    fun whenDeleteOldItemsWithVisitsBothBeforeAndAfterDeletionTimestampThenDeleteOnlyOldVisitsButNotEntries() {
        runTest {
            val insertDate = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
            historyDao.updateOrInsertVisit("url", "title", "query", false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title2", "query2", false, insertDate.plusMinutes(5))
            historyDao.deleteEntriesOlderThan(insertDate.plusMinutes(1))
            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()
            Assert.assertEquals(1, historyEntriesWithVisits.count())
            Assert.assertEquals(1, historyEntriesWithVisits.first().visits.count())
        }
    }

    @Test
    fun whenDeleteEntriesByQueryThenCorrectEntriesDeleted() {
        runTest {
            val insertDate = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
            historyDao.updateOrInsertVisit("url1", "title1", "query1", false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title2", "query2", false, insertDate)
            historyDao.updateOrInsertVisit("url3", "title3", "query1", false, insertDate)

            historyDao.deleteEntriesByQuery("query1")

            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()

            Assert.assertEquals(1, historyEntriesWithVisits.count())
            Assert.assertNull(historyEntriesWithVisits.find { it.historyEntry.query == "query1" })
            Assert.assertNotNull(historyEntriesWithVisits.find { it.historyEntry.query == "query2" })
        }
    }

    @Test
    fun whenDeleteEntriesByUrlThenCorrectEntryDeleted() {
        runTest {
            val insertDate = LocalDateTime.of(2000, JANUARY, 1, 0, 0)
            historyDao.updateOrInsertVisit("url1", "title1", null, false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title2", null, false, insertDate)
            historyDao.updateOrInsertVisit("url3", "title3", null, false, insertDate)
            historyDao.updateOrInsertVisit("url2", "title4", null, false, insertDate)

            historyDao.deleteEntriesByUrl("url2")

            val historyEntriesWithVisits = historyDao.getHistoryEntriesWithVisits()

            Assert.assertEquals(2, historyEntriesWithVisits.count())
            Assert.assertNull(historyEntriesWithVisits.find { it.historyEntry.url == "url2" })
            Assert.assertNotNull(historyEntriesWithVisits.find { it.historyEntry.url == "url1" })
            Assert.assertNotNull(historyEntriesWithVisits.find { it.historyEntry.url == "url3" })
        }
    }
}
