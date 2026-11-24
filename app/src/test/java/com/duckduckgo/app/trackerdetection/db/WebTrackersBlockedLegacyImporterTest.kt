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
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class WebTrackersBlockedLegacyImporterTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private lateinit var appDatabase: AppDatabase
    private lateinit var trackerDetectionDatabase: TrackerDetectionDatabase
    private lateinit var dao: WebTrackersBlockedDao
    private lateinit var testee: WebTrackersBlockedLegacyImporter

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        trackerDetectionDatabase = Room.inMemoryDatabaseBuilder(context, TrackerDetectionDatabase::class.java).allowMainThreadQueries().build()
        dao = trackerDetectionDatabase.webTrackersBlockedDao()
        testee = WebTrackersBlockedLegacyImporter(
            appDatabase = appDatabase,
            trackerDetectionDatabase = trackerDetectionDatabase,
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @After
    fun after() {
        appDatabase.close()
        trackerDetectionDatabase.close()
    }

    @Test
    fun whenLegacyRowsExistThenTheyAreCopiedAndTheLegacyTableIsDropped() {
        val legacyRows = listOf(
            Row("https://tracker.com/a.js", "Tracker Inc", "2026-01-01T10:00:00"),
            Row("https://other.com/b.js", "Other Inc", "2026-01-02T11:30:00"),
        )
        createLegacyTable(legacyRows)

        runImporter()

        assertEquals(legacyRows, rows())
        assertFalse(hasLegacyTable())
    }

    @Test
    fun whenLegacyImportAlreadyDoneThenNoRowsAreAddedAndTheLegacyTableIsStillDropped() {
        createLegacyTable(listOf(Row("https://tracker.com/a.js", "Tracker Inc", "2026-01-01T10:00:00")))
        dao.markLegacyImportDone()

        runImporter()

        assertTrue(rows().isEmpty())
        assertFalse(hasLegacyTable())
    }

    @Test
    fun whenThereIsNoLegacyTableThenRowsAreUnchanged() {
        val liveRow = Row("https://tracker.com/live.js", "Tracker Inc", "2026-01-03T09:00:00")
        dao.insertAll(listOf(liveRow.toEntity()))

        runImporter()

        assertEquals(listOf(liveRow), rows())
        assertFalse(hasLegacyTable())
    }

    @Test
    fun whenLogClearedBeforeImportThenClearedRowsAreNotBroughtBack() {
        createLegacyTable(listOf(Row("https://tracker.com/a.js", "Tracker Inc", "2026-01-01T10:00:00")))

        runBlocking { dao.deleteAll() }
        runImporter()

        assertTrue(rows().isEmpty())
        assertFalse(hasLegacyTable())
    }

    @Test
    fun whenLiveRowsWereWrittenBeforeImportThenImportedRowsAreAddedAlongsideThem() {
        val legacyRows = listOf(
            Row("https://tracker.com/a.js", "Tracker Inc", "2026-01-01T10:00:00"),
            Row("https://tracker.com/b.js", "Tracker Inc", "2026-01-01T10:00:01"),
        )
        val liveRows = listOf(
            Row("https://live.com/1.js", "Live Inc", "2026-01-03T09:00:00"),
            Row("https://live.com/2.js", "Live Inc", "2026-01-03T09:00:01"),
        )
        createLegacyTable(legacyRows)
        dao.insertAll(liveRows.map { it.toEntity() })

        runImporter()

        assertEquals(liveRows + legacyRows, rows())
    }

    @Test
    fun whenLegacyTableHoldsMoreThanOneBatchThenEveryRowIsImported() {
        val legacyRows = (1..1_234).map { Row("https://tracker.com/$it.js", "Tracker Inc", "2026-01-01T10:00:00") }
        createLegacyTable(legacyRows)

        runImporter()

        val imported = rows()
        assertEquals(legacyRows.size, imported.size)
        assertEquals(legacyRows.toSet(), imported.toSet())
        assertFalse(hasLegacyTable())
    }

    @Test
    fun whenImportFailsPartwayThenNothingIsKeptAndTheNextStartImportsEveryRow() {
        val legacyRows = (1..600).map { Row("https://tracker.com/$it.js", "Tracker Inc", "2026-01-01T10:00:00") }
        createLegacyTable(legacyRows)
        trackerDetectionDatabase.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_after_first_batch BEFORE INSERT ON web_trackers_blocked " +
                "WHEN (SELECT COUNT(*) FROM web_trackers_blocked) >= 500 BEGIN SELECT RAISE(ABORT, 'disk full'); END",
        )

        runImporter()

        assertTrue(rows().isEmpty())
        assertFalse(dao.isLegacyImportDone())
        assertTrue(hasLegacyTable())

        trackerDetectionDatabase.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_after_first_batch")
        runImporter()

        assertEquals(legacyRows.toSet(), rows().toSet())
        assertFalse(hasLegacyTable())
    }

    private fun runImporter() {
        testee.onCreate(mock())
        coroutineRule.testScope.runCurrent()
    }

    private fun createLegacyTable(rows: List<Row>) {
        val db = appDatabase.openHelper.writableDatabase
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `web_trackers_blocked` (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "trackerUrl TEXT NOT NULL, trackerCompany TEXT NOT NULL, timestamp TEXT NOT NULL)",
        )
        rows.forEach { row ->
            db.execSQL(
                "INSERT INTO web_trackers_blocked (trackerUrl, trackerCompany, timestamp) VALUES (?, ?, ?)",
                arrayOf(row.trackerUrl, row.trackerCompany, row.timestamp),
            )
        }
    }

    private fun hasLegacyTable(): Boolean =
        appDatabase.query("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'web_trackers_blocked'", null).use { it.moveToFirst() }

    private fun rows(): List<Row> =
        trackerDetectionDatabase.query("SELECT trackerUrl, trackerCompany, timestamp FROM web_trackers_blocked ORDER BY id", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) Row(cursor.getString(0), cursor.getString(1), cursor.getString(2)) else null }.toList()
        }

    private data class Row(val trackerUrl: String, val trackerCompany: String, val timestamp: String) {
        fun toEntity() = WebTrackerBlocked(trackerUrl = trackerUrl, trackerCompany = trackerCompany, timestamp = timestamp)
    }
}
