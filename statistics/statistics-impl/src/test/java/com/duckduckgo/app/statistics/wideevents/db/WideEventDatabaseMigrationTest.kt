/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.statistics.wideevents.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class WideEventDatabaseMigrationTest {

    @get:Rule
    val testHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        WideEventDatabase::class.java,
        emptyList(),
    )

    private var database: WideEventDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    @Test
    fun whenMigratingFromV1ToV2ThenExistingEventsGetDefaultSamplingProbability() = runTest {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """INSERT INTO wide_events
                   (name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals)
                   VALUES ('test-event', 1700000000000, NULL, '[]', '[]', NULL,
                   '{"type":"OnTimeout","duration":604800000,"status":"unknown","metadata":{}}', '[]')""",
            )
            close()
        }

        val dao = getMigratedDao()
        val eventId = dao.getActiveWideEventIdsByName("test-event").single()
        val entity = dao.getWideEventById(eventId)!!
        assertEquals(1.0f, entity.samplingProbability)
    }

    @Test
    fun whenMigratingFromV1ToV2ThenExistingEventsArePreserved() = runTest {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            execSQL(
                """INSERT INTO wide_events
                   (name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals)
                   VALUES ('event-1', 1700000000000, 'settings', '[{"key":"k1","value":"v1"}]', '[]', NULL,
                   '{"type":"OnTimeout","duration":604800000,"status":"unknown","metadata":{}}', '[]')""",
            )
            execSQL(
                """INSERT INTO wide_events
                   (name, created_at, flow_entry_point, metadata, steps, status, cleanup_policy, active_intervals)
                   VALUES ('event-2', 1700000001000, NULL, '[]', '[{"name":"step_a","success":true}]', 'success',
                   '{"type":"OnProcessStart","ignore_if_interval_timeout_present":false,"status":"unknown","metadata":{}}',
                   '[]')""",
            )
            close()
        }

        val dao = getMigratedDao()

        val event1 = dao.getActiveWideEventIdsByName("event-1").single().let { dao.getWideEventById(it)!! }
        assertEquals("event-1", event1.name)
        assertEquals(Instant.ofEpochMilli(1700000000000L), event1.createdAt)
        assertEquals("settings", event1.flowEntryPoint)
        assertEquals(listOf(WideEventEntity.MetadataEntry("k1", "v1")), event1.metadata)
        assertEquals(1.0f, event1.samplingProbability)

        val completedIds = dao.getCompletedWideEventIds()
        assertEquals(1, completedIds.size)
        val event2 = dao.getWideEventById(completedIds.single())!!
        assertEquals("event-2", event2.name)
        assertEquals(WideEventEntity.WideEventStatus.SUCCESS, event2.status)
        assertEquals(listOf(WideEventEntity.WideEventStep("step_a", true)), event2.steps)
        assertEquals(1.0f, event2.samplingProbability)
    }

    @Test
    fun whenMigratingFromV1ToV2ThenNewEventsCanHaveCustomSamplingProbability() = runTest {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            close()
        }

        val dao = getMigratedDao()

        val id = dao.insertWideEvent(
            WideEventEntity(
                name = "sampled-event",
                createdAt = Instant.ofEpochMilli(1700000000000L),
                flowEntryPoint = null,
                metadata = emptyList(),
                steps = emptyList(),
                status = null,
                cleanupPolicy = WideEventEntity.CleanupPolicy.OnTimeout(
                    duration = Duration.ofDays(7),
                    status = WideEventEntity.WideEventStatus.UNKNOWN,
                ),
                activeIntervals = emptyList(),
                samplingProbability = 0.1f,
            ),
        )

        val entity = dao.getWideEventById(id)!!
        assertTrue(abs(entity.samplingProbability - 0.1f) < 0.001f)
    }

    @Test
    fun whenMigratingFromV1ToV2ThenValidatesAgainstCurrentSchema() {
        testHelper.createDatabase(TEST_DB_NAME, 1).apply {
            close()
        }

        val db = getMigratedDatabase()
        db.openHelper.writableDatabase
    }

    private fun getMigratedDao(): WideEventDao {
        return getMigratedDatabase().wideEventDao()
    }

    private fun getMigratedDatabase(): WideEventDatabase {
        testHelper.runMigrationsAndValidate(TEST_DB_NAME, 2, true, WideEventDatabase.MIGRATION_1_2).close()

        return Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            WideEventDatabase::class.java,
            TEST_DB_NAME,
        ).addMigrations(*WideEventDatabase.ALL_MIGRATIONS)
            .addTypeConverter(WideEventEntityTypeConverters(Moshi.Builder().build()))
            .allowMainThreadQueries()
            .build()
            .also { database = it }
    }

    companion object {
        private const val TEST_DB_NAME = "wide_event_migration_test"
    }
}
