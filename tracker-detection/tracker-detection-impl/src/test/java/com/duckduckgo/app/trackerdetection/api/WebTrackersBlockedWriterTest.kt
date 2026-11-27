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

package com.duckduckgo.app.trackerdetection.api

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.trackerdetection.api.WebTrackersBlockedWriter.Companion.FLUSH_WINDOW_MS
import com.duckduckgo.app.trackerdetection.db.TrackerDetectionDatabase
import com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WebTrackersBlockedWriterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: TrackerDetectionDatabase
    private lateinit var testee: WebTrackersBlockedWriter

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TrackerDetectionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testee = WebTrackersBlockedWriter(
            webTrackersBlockedDao = db.webTrackersBlockedDao(),
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenRowsQueuedThenNothingWrittenBeforeWindowElapses() {
        testee.record("https://tracker.com/a.js", "Tracker Inc")

        advance(FLUSH_WINDOW_MS - 1)

        assertTrue(historyRows().isEmpty())
    }

    @Test
    fun whenBurstQueuedThenAllRowsFlushedWhenWindowElapses() {
        repeat(50) { index -> testee.record("https://tracker.com/$index.js", "Tracker Inc") }

        advance(FLUSH_WINDOW_MS)

        assertEquals(50, historyRows().size)
    }

    @Test
    fun whenBurstQueuedThenRowsKeepOrderAndRequestTimestamps() {
        val start = WebTrackerBlocked.timestamp()
        repeat(5) { testee.record("https://tracker.com/$it.js", "Tracker Inc") }
        val end = WebTrackerBlocked.timestamp()

        advance(FLUSH_WINDOW_MS)

        val rows = historyRows()
        assertEquals((0 until 5).map { "https://tracker.com/$it.js" }, rows.map { it.first })
        assertTrue(rows.all { it.second >= start && it.second <= end })
    }

    @Test
    fun whenRowsArriveAfterFlushThenNextWindowFlushesThem() {
        testee.record("https://tracker.com/a.js", "Tracker Inc")
        advance(FLUSH_WINDOW_MS)
        testee.record("https://tracker.com/b.js", "Tracker Inc")

        advance(FLUSH_WINDOW_MS - 1)
        assertEquals(1, historyRows().size)

        advance(1)
        assertEquals(2, historyRows().size)
    }

    @Test
    fun whenAllDeletedThenQueuedAndPersistedRowsAreDropped() {
        testee.record("https://tracker.com/a.js", "Tracker Inc")
        advance(FLUSH_WINDOW_MS)
        testee.record("https://tracker.com/b.js", "Tracker Inc")

        runBlocking { testee.deleteAll() }
        advance(FLUSH_WINDOW_MS)

        assertTrue(historyRows().isEmpty())
    }

    private fun advance(millis: Long) {
        coroutineRule.testScope.advanceTimeBy(millis)
        coroutineRule.testScope.runCurrent()
    }

    private fun historyRows(): List<Pair<String, String>> =
        db.query("SELECT trackerUrl, timestamp FROM web_trackers_blocked ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) to cursor.getString(1) else null }.toList()
        }
}
