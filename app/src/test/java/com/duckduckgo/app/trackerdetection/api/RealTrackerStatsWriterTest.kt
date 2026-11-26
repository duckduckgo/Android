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
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.api.RealTrackerStatsWriter.Companion.FLUSH_WINDOW_MS
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
class RealTrackerStatsWriterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    // The invalidation tracker opens its own IMMEDIATE transactions asynchronously, so only count EXCLUSIVE ones.
    private val writeTransactions = mutableListOf<String>()
    private lateinit var db: AppDatabase
    private lateinit var trackerDetectionDb: TrackerDetectionDatabase
    private lateinit var testee: RealTrackerStatsWriter

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> if (sql.startsWith("BEGIN EXCLUSIVE")) writeTransactions.add(sql) }, Runnable::run)
            .build()
        trackerDetectionDb = Room.inMemoryDatabaseBuilder(context, TrackerDetectionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        testee = RealTrackerStatsWriter(
            appDatabase = db,
            webTrackersBlockedDao = trackerDetectionDb.webTrackersBlockedDao(),
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @After
    fun after() {
        db.close()
        trackerDetectionDb.close()
    }

    @Test
    fun whenEventsQueuedThenNothingWrittenBeforeWindowElapses() {
        testee.incrementBlockedCount()
        testee.recordBlockedHistory("https://tracker.com/a.js", "Tracker Inc")
        testee.incrementNetworkCount("Tracker Inc")

        advance(FLUSH_WINDOW_MS - 1)

        assertEquals(0, db.privacyProtectionCountsDao().getTrackersBlockedCount())
        assertTrue(historyRows().isEmpty())
        assertTrue(writeTransactions.isEmpty())
    }

    @Test
    fun whenBurstQueuedThenFlushedInSingleTransactionWithAggregatedValues() {
        repeat(50) { index ->
            testee.recordBlockedHistory("https://tracker.com/$index.js", "Tracker Inc")
            testee.incrementBlockedCount()
            testee.incrementNetworkCount(if (index % 5 == 0) "Other Inc" else "Tracker Inc")
        }

        advance(FLUSH_WINDOW_MS)

        assertEquals(1, writeTransactions.size)
        assertEquals(50, db.privacyProtectionCountsDao().getTrackersBlockedCount())
        assertEquals(50, historyRows().size)
        assertEquals(mapOf("Tracker Inc" to 40, "Other Inc" to 10), leaderboard())
    }

    @Test
    fun whenBurstQueuedThenHistoryRowsKeepOrderAndRequestTimestamps() {
        val start = WebTrackerBlocked.timestamp()
        repeat(5) { testee.recordBlockedHistory("https://tracker.com/$it.js", "Tracker Inc") }
        val end = WebTrackerBlocked.timestamp()

        advance(FLUSH_WINDOW_MS)

        val rows = historyRows()
        assertEquals((0 until 5).map { "https://tracker.com/$it.js" }, rows.map { it.first })
        assertTrue(rows.all { it.second >= start && it.second <= end })
    }

    @Test
    fun whenCountersAlreadyExistThenBatchIsAddedToThem() {
        testee.incrementBlockedCount()
        testee.incrementNetworkCount("Tracker Inc")
        advance(FLUSH_WINDOW_MS)

        repeat(3) {
            testee.incrementBlockedCount()
            testee.incrementNetworkCount("Tracker Inc")
        }
        advance(FLUSH_WINDOW_MS)

        assertEquals(4, db.privacyProtectionCountsDao().getTrackersBlockedCount())
        assertEquals(mapOf("Tracker Inc" to 4), leaderboard())
        assertEquals(2, writeTransactions.size)
    }

    @Test
    fun whenEventsArriveAfterFlushThenNextWindowFlushesThem() {
        testee.incrementBlockedCount()
        advance(FLUSH_WINDOW_MS)
        testee.incrementBlockedCount()

        advance(FLUSH_WINDOW_MS - 1)
        assertEquals(1, db.privacyProtectionCountsDao().getTrackersBlockedCount())

        advance(1)
        assertEquals(2, db.privacyProtectionCountsDao().getTrackersBlockedCount())
    }

    @Test
    fun whenAllHistoryDeletedThenQueuedAndPersistedHistoryIsDroppedButCountsAreKept() {
        testee.recordBlockedHistory("https://tracker.com/a.js", "Tracker Inc")
        testee.incrementBlockedCount()
        testee.incrementNetworkCount("Tracker Inc")
        advance(FLUSH_WINDOW_MS)
        testee.recordBlockedHistory("https://tracker.com/b.js", "Tracker Inc")
        testee.incrementBlockedCount()

        runBlocking { testee.deleteAllHistory() }
        advance(FLUSH_WINDOW_MS)

        assertTrue(historyRows().isEmpty())
        assertEquals(2, db.privacyProtectionCountsDao().getTrackersBlockedCount())
        assertEquals(mapOf("Tracker Inc" to 1), leaderboard())
    }

    private fun advance(millis: Long) {
        coroutineRule.testScope.advanceTimeBy(millis)
        coroutineRule.testScope.runCurrent()
    }

    private fun historyRows(): List<Pair<String, String>> =
        trackerDetectionDb.query("SELECT trackerUrl, timestamp FROM web_trackers_blocked ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) to cursor.getString(1) else null }.toList()
        }

    private fun leaderboard(): Map<String, Int> =
        db.query("SELECT networkName, count FROM network_leaderboard", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(0) to cursor.getInt(1) else null }.toMap()
        }
}
