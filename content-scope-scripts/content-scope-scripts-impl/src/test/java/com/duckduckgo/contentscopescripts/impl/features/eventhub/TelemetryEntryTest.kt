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

package com.duckduckgo.contentscopescripts.impl.features.eventhub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEntryTest {

    // MARK: - Bucketing

    @Test
    fun `bucket count first match wins`() {
        val buckets = listOf(
            BucketConfig(minInclusive = 0, maxExclusive = 1, name = "0"),
            BucketConfig(minInclusive = 1, maxExclusive = 3, name = "1-2"),
            BucketConfig(minInclusive = 3, maxExclusive = 6, name = "3-5"),
            BucketConfig(minInclusive = 40, maxExclusive = null, name = "40+"),
        )

        assertEquals("0", TelemetryEntry.bucketCount(0, buckets)?.name)
        assertEquals("1-2", TelemetryEntry.bucketCount(1, buckets)?.name)
        assertEquals("1-2", TelemetryEntry.bucketCount(2, buckets)?.name)
        assertEquals("3-5", TelemetryEntry.bucketCount(3, buckets)?.name)
        assertEquals("3-5", TelemetryEntry.bucketCount(5, buckets)?.name)
        assertEquals("40+", TelemetryEntry.bucketCount(40, buckets)?.name)
        assertEquals("40+", TelemetryEntry.bucketCount(100, buckets)?.name)
    }

    @Test
    fun `bucket count returns null when no match`() {
        val buckets = listOf(
            BucketConfig(minInclusive = 1, maxExclusive = 3, name = "1-2"),
        )
        assertNull(TelemetryEntry.bucketCount(0, buckets))
    }

    @Test
    fun `bucket count handles gap in buckets`() {
        val buckets = listOf(
            BucketConfig(minInclusive = 0, maxExclusive = 1, name = "0"),
            BucketConfig(minInclusive = 10, maxExclusive = null, name = "10+"),
        )
        assertNull(TelemetryEntry.bucketCount(5, buckets))
    }

    // MARK: - Period Calculation

    @Test
    fun `period to seconds computes correctly`() {
        assertEquals(86400, TelemetryEntry.periodToSeconds(mapOf("days" to 1)))
        assertEquals(3600, TelemetryEntry.periodToSeconds(mapOf("hours" to 1)))
        assertEquals(1800, TelemetryEntry.periodToSeconds(mapOf("minutes" to 30)))
        assertEquals(10, TelemetryEntry.periodToSeconds(mapOf("seconds" to 10)))
        assertEquals(
            86400 + 7200 + 180 + 4,
            TelemetryEntry.periodToSeconds(mapOf("days" to 1, "hours" to 2, "minutes" to 3, "seconds" to 4)),
        )
    }

    // MARK: - Attribution Period

    @Test
    fun `attribution period daily`() {
        // 2026-01-02T00:01:00Z → 1735776060 * 1000
        val startTimeMs = 1_735_776_060_000L
        val periodSeconds = 86400
        val result = TelemetryEntry.calculateAttributionPeriod(startTimeMs, periodSeconds)
        // 2026-01-03T00:00:00Z = 1735862400
        assertEquals(1_735_862_400L, result)
    }

    @Test
    fun `attribution period hourly`() {
        // 2026-01-02T17:15:00Z → 1735838100 * 1000
        val startTimeMs = 1_735_838_100_000L
        val periodSeconds = 3600
        val result = TelemetryEntry.calculateAttributionPeriod(startTimeMs, periodSeconds)
        // 2026-01-02T18:00:00Z = 1735840800
        assertEquals(1_735_840_800L, result)
    }

    @Test
    fun `attribution period on boundary`() {
        // 2026-01-03T00:00:00Z → 1735862400 * 1000
        val startTimeMs = 1_735_862_400_000L
        val periodSeconds = 86400
        val result = TelemetryEntry.calculateAttributionPeriod(startTimeMs, periodSeconds)
        // 2026-01-04T00:00:00Z = 1735948800
        assertEquals(1_735_948_800L, result)
    }

    // MARK: - Config Parsing

    @Test
    fun `from config parses valid config`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf(
                "period" to mapOf("days" to 1),
            ),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 0, "maxExclusive" to 1, "name" to "0"),
                        mapOf("minInclusive" to 1, "name" to "1+"),
                    ),
                ),
            ),
        )

        val entry = TelemetryEntry.fromConfig("testPixel", config, System.currentTimeMillis())
        assertNotNull(entry)
        assertEquals("testPixel", entry!!.name)
    }

    @Test
    fun `from config returns null for missing period`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "parameters" to emptyMap<String, Any>(),
        )
        assertNull(TelemetryEntry.fromConfig("test", config, System.currentTimeMillis()))
    }

    // MARK: - Event Handling

    @Test
    fun `handle event increments counter`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf("period" to mapOf("days" to 1)),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 0, "maxExclusive" to 10, "name" to "0-9"),
                        mapOf("minInclusive" to 10, "name" to "10+"),
                    ),
                ),
            ),
        )

        val now = System.currentTimeMillis()
        val entry = TelemetryEntry.fromConfig("test", config, now)!!

        entry.handleEvent("adwall", now + 1000)
        entry.handleEvent("adwall", now + 2000)
        entry.handleEvent("adwall", now + 3000)

        val pixel = entry.buildPixel()
        assertEquals("0-9", pixel["count"])
    }

    @Test
    fun `handle event ignores non-matching source`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf("period" to mapOf("days" to 1)),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 0, "maxExclusive" to 1, "name" to "0"),
                        mapOf("minInclusive" to 1, "name" to "1+"),
                    ),
                ),
            ),
        )

        val now = System.currentTimeMillis()
        val entry = TelemetryEntry.fromConfig("test", config, now)!!

        entry.handleEvent("something_else", now + 1000)

        val pixel = entry.buildPixel()
        assertEquals("0", pixel["count"])
    }

    @Test
    fun `handle event stops counting at max bucket`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf("period" to mapOf("days" to 1)),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 0, "maxExclusive" to 3, "name" to "0-2"),
                        mapOf("minInclusive" to 3, "name" to "3+"),
                    ),
                ),
            ),
        )

        val now = System.currentTimeMillis()
        val entry = TelemetryEntry.fromConfig("test", config, now)!!

        repeat(5) { entry.handleEvent("adwall", now + (it + 1) * 1000L) }

        val pixel = entry.buildPixel()
        assertEquals("3+", pixel["count"])

        // Counter should have stopped at 3
        val state = entry.toPersistedState()
        assertEquals(3, state.parameters["count"]?.data)
        assertTrue(state.parameters["count"]?.stopCounting ?: false)
    }

    @Test
    fun `build pixel returns empty when no bucket matches`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf("period" to mapOf("days" to 1)),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 1, "name" to "1+"),
                    ),
                ),
            ),
        )

        val now = System.currentTimeMillis()
        val entry = TelemetryEntry.fromConfig("test", config, now)!!

        val pixel = entry.buildPixel()
        assertTrue(pixel.isEmpty())
    }

    // MARK: - Persistence

    @Test
    fun `persisted state roundtrip`() {
        val config = mapOf<String, Any?>(
            "state" to "enabled",
            "trigger" to mapOf("period" to mapOf("hours" to 1)),
            "parameters" to mapOf(
                "count" to mapOf(
                    "template" to "counter",
                    "source" to "adwall",
                    "buckets" to listOf(
                        mapOf("minInclusive" to 0, "maxExclusive" to 5, "name" to "0-4"),
                    ),
                ),
            ),
        )

        val now = System.currentTimeMillis()
        val original = TelemetryEntry.fromConfig("test", config, now)!!
        original.handleEvent("adwall", now + 1000)

        val state = original.toPersistedState()
        val restored = TelemetryEntry.fromPersistedState(state)

        assertEquals("test", restored.name)
        assertEquals("0-4", restored.buildPixel()["count"])
    }
}
