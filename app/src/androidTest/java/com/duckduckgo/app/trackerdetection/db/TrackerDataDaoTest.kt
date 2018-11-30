/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.model.DisconnectTracker
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackerDataDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TrackerDataDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        dao = db.trackerDataDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenModelIsEmptyThenCountIsZero() {
        assertEquals(0, dao.count())
    }

    @Test
    fun whenTrackerInsertedThenCountIsOne() {
        dao.insertAll(listOf(createTracker(trackerUrl)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenTrackerInsertedThenContainsTracker() {
        val tracker = createTracker(trackerUrl)
        dao.insertAll(listOf(tracker))
        assertTrue(dao.getAll().contains(tracker))
    }

    @Test
    fun whenSecondUniqueTrackerInsertedThenCountIsTwo() {
        dao.insertAll(listOf(createTracker(trackerUrl)))
        dao.insertAll(listOf(createTracker(anotherTrackerUrl)))
        assertEquals(2, dao.count())
    }

    @Test
    fun whenSecondDuplicateTrackerInsertedThenCountIsOne() {
        dao.insertAll(listOf(createTracker(trackerUrl)))
        dao.insertAll(listOf(createTracker(trackerUrl)))
        assertEquals(1, dao.count())
    }

    @Test
    fun whenAllUpdatedThenPreviousValuesAreReplaced() {
        val initialTracker = createTracker(trackerUrl)
        val replacementTracker = createTracker(anotherTrackerUrl)

        dao.insertAll(listOf(initialTracker))
        dao.updateAll(listOf(replacementTracker))
        assertEquals(1, dao.count())
        assertTrue(dao.getAll().contains(replacementTracker))
    }

    @Test
    fun whenhAllDeletedThenCountIsZero() {
        val tracker = createTracker(trackerUrl)
        dao.insertAll(listOf(tracker))
        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    private fun createTracker(url: String): DisconnectTracker {
        return DisconnectTracker(url, "", "", "")
    }

    companion object {
        var trackerUrl = "tracker.com"
        var anotherTrackerUrl = "anotherTracker.com"
    }
}
