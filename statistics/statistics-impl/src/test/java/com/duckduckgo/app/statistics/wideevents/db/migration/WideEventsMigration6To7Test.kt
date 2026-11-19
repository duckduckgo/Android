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
import com.duckduckgo.app.statistics.wideevents.db.WideEventEntityTypeConverters
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class WideEventsMigration6To7Test {
    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    private val converters = WideEventEntityTypeConverters(Moshi.Builder().build())

    @Test
    fun `migration attributes process-start policies of open and completed events to the main process`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(id = 1, status = null, cleanupPolicy = PROCESS_START_POLICY)
            db.insertWideEvent(id = 2, status = "success", cleanupPolicy = PROCESS_START_POLICY)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            val openPolicy = db.readProcessStartPolicy(id = 1)
            val completedPolicy = db.readProcessStartPolicy(id = 2)

            assertEquals("main", openPolicy.ownerProcessName)
            assertEquals("main", completedPolicy.ownerProcessName)
        }
    }

    @Test
    fun `migration preserves the other process-start policy fields`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(id = 1, cleanupPolicy = PROCESS_START_POLICY)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            val policy = db.readProcessStartPolicy(id = 1)

            assertEquals(true, policy.ignoreIfIntervalTimeoutPresent)
            assertEquals(WideEventEntity.WideEventStatus.CANCELLED, policy.status)
            assertEquals(mapOf("reason" to "test"), policy.metadata)
        }
    }

    @Test
    fun `migration leaves timeout policies untouched`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(id = 1, cleanupPolicy = TIMEOUT_POLICY)
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            assertEquals(TIMEOUT_POLICY, db.readString(id = 1, column = "cleanup_policy"))
        }
    }

    @Test
    fun `migration resets an unparsable policy to the default so every row stays readable`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(id = 1, status = null, cleanupPolicy = "not json")
            db.insertWideEvent(id = 2, status = "success", cleanupPolicy = "")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            assertEquals(DEFAULT_TIMEOUT_POLICY, db.readTimeoutPolicy(id = 1))
            assertEquals(DEFAULT_TIMEOUT_POLICY, db.readTimeoutPolicy(id = 2))
        }
    }

    @Test
    fun `migration resets a policy of unknown type to the default`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(id = 1, cleanupPolicy = """{"type":"OnSomethingElse","status":"unknown","metadata":{}}""")
            db.insertWideEvent(id = 2, cleanupPolicy = """{"status":"unknown","metadata":{}}""")
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            assertEquals(DEFAULT_TIMEOUT_POLICY, db.readTimeoutPolicy(id = 1))
            assertEquals(DEFAULT_TIMEOUT_POLICY, db.readTimeoutPolicy(id = 2))
        }
    }

    @Test
    fun `migration preserves existing event data and daily occurrences`() {
        testHelper.createDatabase(TEST_DB_NAME, 6).use { db ->
            db.insertWideEvent(
                id = 1,
                name = "event-1",
                flowEntryPoint = "settings",
                status = "failure",
                cleanupPolicy = PROCESS_START_POLICY,
                metaType = "android-event-1",
                metaVersion = "1.2.3",
                isFirstDailyOccurrence = true,
                appVersion = "5.300.0",
            )
            db.execSQL(
                "INSERT INTO wide_event_daily_occurrences (dedup_key, last_occurrence_date) VALUES (?, ?)",
                arrayOf<Any?>("flow:success", "2026-01-15"),
            )
        }

        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 7, true, WideEventsMigration6To7).use { db ->
            assertEquals("event-1", db.readString(id = 1, column = "name"))
            assertEquals("settings", db.readString(id = 1, column = "flow_entry_point"))
            assertEquals("failure", db.readString(id = 1, column = "status"))
            assertEquals("android-event-1", db.readString(id = 1, column = "meta_type"))
            assertEquals("1.2.3", db.readString(id = 1, column = "meta_version"))
            assertEquals("1", db.readString(id = 1, column = "is_first_daily_occurrence"))
            assertEquals("5.300.0", db.readString(id = 1, column = "app_version"))
            assertEquals("2026-01-15", db.readLastOccurrenceDate(dedupKey = "flow:success"))
        }
    }

    private fun SupportSQLiteDatabase.readProcessStartPolicy(id: Long): WideEventEntity.CleanupPolicy.OnProcessStart =
        converters.toCleanupPolicy(readString(id = id, column = "cleanup_policy")) as WideEventEntity.CleanupPolicy.OnProcessStart

    private fun SupportSQLiteDatabase.readTimeoutPolicy(id: Long): WideEventEntity.CleanupPolicy.OnTimeout =
        converters.toCleanupPolicy(readString(id = id, column = "cleanup_policy")) as WideEventEntity.CleanupPolicy.OnTimeout

    private companion object {
        const val TEST_DB_NAME = "wide_events_migration_test"

        // Keys are the Kotlin property names, which is what the reflective adapter persisted before v7.
        const val PROCESS_START_POLICY =
            """{"type":"OnProcessStart","ignoreIfIntervalTimeoutPresent":true,"status":"cancelled","metadata":{"reason":"test"}}"""
        const val TIMEOUT_POLICY = """{"type":"OnTimeout","duration":600000,"status":"unknown","metadata":{}}"""

        // Matches the WideEventClient.flowStart default cleanup policy.
        val DEFAULT_TIMEOUT_POLICY = WideEventEntity.CleanupPolicy.OnTimeout(
            duration = Duration.ofDays(7),
            status = WideEventEntity.WideEventStatus.UNKNOWN,
            metadata = emptyMap(),
        )
    }
}

private fun SupportSQLiteDatabase.insertWideEvent(
    id: Long,
    cleanupPolicy: String,
    name: String = "flow",
    flowEntryPoint: String? = null,
    status: String? = null,
    metaType: String = "android-flow",
    metaVersion: String = "1.0.0",
    isFirstDailyOccurrence: Boolean = false,
    appVersion: String = "5.300.0",
) {
    execSQL(
        """
        INSERT INTO wide_events (
            id, name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals,
            sampling_probability, meta_type, meta_version, is_first_daily_occurrence, app_version
        ) VALUES (?, ?, 0, ?, '[]', '[]', ?, ?, '[]', 1.0, ?, ?, ?, ?)
        """.trimIndent(),
        arrayOf<Any?>(id, name, flowEntryPoint, status, cleanupPolicy, metaType, metaVersion, if (isFirstDailyOccurrence) 1 else 0, appVersion),
    )
}

private fun SupportSQLiteDatabase.readString(
    id: Long,
    column: String,
): String? =
    query("SELECT $column FROM wide_events WHERE id = ?", arrayOf(id)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }

private fun SupportSQLiteDatabase.readLastOccurrenceDate(dedupKey: String): String? =
    query("SELECT last_occurrence_date FROM wide_event_daily_occurrences WHERE dedup_key = ?", arrayOf(dedupKey)).use { cursor ->
        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
    }
