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

package com.duckduckgo.app.trackerdetection.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebTrackersBlockedDaoTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: TrackerDetectionDatabase
    private lateinit var dao: WebTrackersBlockedDao

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TrackerDetectionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.webTrackersBlockedDao()
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenCountingBetweenTwoTimesThenStartIsIncludedAndEndIsExcluded() = runTest {
        dao.insertAll(
            listOf(
                tracker(timestamp = "2026-01-01T23:59:59"),
                tracker(timestamp = "2026-01-02T00:00:00"),
                tracker(timestamp = "2026-01-05T12:00:00"),
                tracker(timestamp = "2026-01-09T00:00:00"),
            ),
        )

        assertEquals(2, dao.getTrackersCountBetween(startTime = "2026-01-02T00:00:00", endTime = "2026-01-09T00:00:00"))
    }

    @Test
    fun whenOldDataDeletedThenOnlyRowsBeforeTheCutoffAreRemoved() = runTest {
        dao.insertAll(
            listOf(
                tracker(timestamp = "2026-01-01T23:59:59"),
                tracker(timestamp = "2026-01-02T00:00:00"),
                tracker(timestamp = "2026-01-05T12:00:00"),
            ),
        )

        dao.deleteOldDataUntil("2026-01-02T00:00:00")

        assertEquals(0, dao.getTrackersCountBetween(startTime = ALL_TIME_START, endTime = "2026-01-02T00:00:00"))
        assertEquals(2, dao.countAll())
    }

    @Test
    fun whenAllDeletedThenRowsAreGoneAndLegacyImportCountsAsDone() = runTest {
        dao.insertAll(listOf(tracker(), tracker()))
        assertFalse(dao.isLegacyImportDone())

        dao.deleteAll()

        assertEquals(0, dao.countAll())
        assertTrue(dao.isLegacyImportDone())
    }

    @Test
    fun whenLegacyImportAlreadyDoneThenMarkingItAgainAndDeletingAllStillSucceed() = runTest {
        dao.markLegacyImportDone()

        dao.markLegacyImportDone()
        dao.deleteAll()

        assertTrue(dao.isLegacyImportDone())
    }

    private suspend fun WebTrackersBlockedDao.countAll(): Int = getTrackersCountBetween(startTime = ALL_TIME_START, endTime = ALL_TIME_END)

    private fun tracker(timestamp: String = "2026-01-05T12:00:00") =
        WebTrackerBlocked(trackerUrl = "https://tracker.com/a.js", trackerCompany = "Tracker Inc", timestamp = timestamp)

    private companion object {
        const val ALL_TIME_START = "0000-01-01T00:00:00"
        const val ALL_TIME_END = "9999-12-31T23:59:59"
    }
}
