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

package com.duckduckgo.app.statistics.wideevents.db.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.statistics.wideevents.db.WideEventDatabase
import com.duckduckgo.app.statistics.wideevents.db.WideEventEntity
import com.duckduckgo.app.statistics.wideevents.db.WideEventEntity.BucketRounding.ROUND_DOWN
import com.duckduckgo.app.statistics.wideevents.db.WideEventEntityTypeConverters
import com.squareup.moshi.Moshi
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class WideEventsMigration7To8Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    private val converters = WideEventEntityTypeConverters(Moshi.Builder().build())

    @Test
    fun `migration backfills round down on intervals without rounding and preserves their other fields`() {
        testHelper.createDatabase(TEST_DB_NAME, 7).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = v7Intervals(TIMED_INTERVAL, UNBUCKETED_INTERVAL))
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, WideEventsMigration7To8).use { db ->
            assertEquals(
                listOf(TIMED_INTERVAL, UNBUCKETED_INTERVAL),
                db.readIntervals(id = 1),
            )
        }
    }

    @Test
    fun `migration resets unparsable intervals to an empty list so every row stays readable`() {
        testHelper.createDatabase(TEST_DB_NAME, 7).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = "not json")
            db.insertWideEvent(id = 2, activeIntervals = """{"name":"k"}""")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, WideEventsMigration7To8).use { db ->
            assertEquals("[]", db.readActiveIntervals(id = 1))
            assertEquals("[]", db.readActiveIntervals(id = 2))
        }
    }

    @Test
    fun `migration leaves rows without intervals untouched`() {
        testHelper.createDatabase(TEST_DB_NAME, 7).use { db ->
            db.insertWideEvent(id = 1, activeIntervals = "[]")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 8, true, WideEventsMigration7To8).use { db ->
            assertEquals("[]", db.readActiveIntervals(id = 1))
        }
    }

    /** Serializes [intervals] the way v7 stored them, which had no rounding field. */
    private fun v7Intervals(vararg intervals: WideEventEntity.WideEventInterval): String {
        val json = JSONArray(converters.fromWideEventIntervalList(intervals.toList()))
        for (index in 0 until json.length()) {
            json.getJSONObject(index).remove("rounding")
        }
        return json.toString()
    }

    private fun SupportSQLiteDatabase.readIntervals(id: Long): List<WideEventEntity.WideEventInterval> =
        converters.toWideEventIntervalList(readActiveIntervals(id = id)!!)

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"

        val TIMED_INTERVAL = WideEventEntity.WideEventInterval(
            name = "timed",
            startedAt = Instant.ofEpochMilli(1_000),
            timeout = Duration.ofMinutes(1),
            buckets = listOf(Duration.ofSeconds(1), Duration.ofSeconds(5)),
            rounding = ROUND_DOWN,
        )

        val UNBUCKETED_INTERVAL = WideEventEntity.WideEventInterval(
            name = "unbucketed",
            startedAt = Instant.ofEpochMilli(2_000),
            timeout = null,
            buckets = null,
            rounding = ROUND_DOWN,
        )
    }
}

private fun SupportSQLiteDatabase.insertWideEvent(
    id: Long,
    activeIntervals: String,
) {
    execSQL(
        """
        INSERT INTO wide_events (
            id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals,
            sampling_probability, meta_type, meta_version, is_first_daily_occurrence, app_version
        ) VALUES (
            ?, 'flow', 0, NULL, '[]', '[]', NULL,
            '{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}',
            ?, 1.0, 'android-flow', '1.0.0', 0, '5.300.0'
        )
        """.trimIndent(),
        arrayOf<Any?>(id, activeIntervals),
    )
}

private fun SupportSQLiteDatabase.readActiveIntervals(id: Long): String? =
    query("SELECT active_intervals FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
