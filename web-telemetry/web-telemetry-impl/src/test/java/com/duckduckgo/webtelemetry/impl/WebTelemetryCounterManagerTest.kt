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

package com.duckduckgo.webtelemetry.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.webtelemetry.store.WebTelemetryConfigEntity
import com.duckduckgo.webtelemetry.store.WebTelemetryCounterEntity
import com.duckduckgo.webtelemetry.store.WebTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class WebTelemetryCounterManagerTest {

    private val repository: WebTelemetryRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeTimeProvider()

    private lateinit var counterManager: RealWebTelemetryCounterManager

    private val enabledConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetryTypes": {
                    "adwall": {
                        "state": "enabled",
                        "template": "counter",
                        "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"],
                        "period": "day",
                        "pixel": "webTelemetry.adwallDetection.day"
                    }
                }
            }
        }
    """.trimIndent()

    @Before
    fun setup() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = enabledConfig))
        whenever(repository.getAllCounters()).thenReturn(emptyList())
        counterManager = RealWebTelemetryCounterManager(repository, pixel, timeProvider)
    }

    @Test
    fun `handleTelemetryEvent increments counter for known active type`() {
        whenever(repository.getCounter("adwall")).thenReturn(
            WebTelemetryCounterEntity(name = "adwall", counter = 5, timestampMillis = 1000L),
        )

        counterManager.handleTelemetryEvent("adwall")

        val captor = argumentCaptor<WebTelemetryCounterEntity>()
        verify(repository).saveCounter(captor.capture())
        assertEquals(6, captor.firstValue.counter)
        assertEquals("adwall", captor.firstValue.name)
    }

    @Test
    fun `handleTelemetryEvent creates counter if none exists`() {
        whenever(repository.getCounter("adwall")).thenReturn(null)
        timeProvider.time = 5000L

        counterManager.handleTelemetryEvent("adwall")

        val captor = argumentCaptor<WebTelemetryCounterEntity>()
        verify(repository).saveCounter(captor.capture())
        assertEquals(1, captor.firstValue.counter)
        assertEquals("adwall", captor.firstValue.name)
        assertEquals(5000L, captor.firstValue.timestampMillis)
    }

    @Test
    fun `handleTelemetryEvent ignores unknown type`() {
        counterManager.handleTelemetryEvent("unknownType")

        verify(repository, never()).saveCounter(any())
    }

    @Test
    fun `handleTelemetryEvent ignores disabled type`() {
        val disabledConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "disabled",
                            "template": "counter",
                            "buckets": ["0-1"],
                            "period": "day",
                            "pixel": "test.pixel"
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = disabledConfig))

        counterManager.handleTelemetryEvent("adwall")

        verify(repository, never()).saveCounter(any())
    }

    @Test
    fun `checkAndFireCounters fires pixel when day period elapsed`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 1

        whenever(repository.getCounter("adwall")).thenReturn(
            WebTelemetryCounterEntity(name = "adwall", counter = 15, timestampMillis = startTime),
        )

        counterManager.checkAndFireCounters()

        verify(pixel).fire(
            pixelName = eq("webTelemetry.adwallDetection.day"),
            parameters = eq(mapOf("count" to "11-20")),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )

        val captor = argumentCaptor<WebTelemetryCounterEntity>()
        verify(repository).saveCounter(captor.capture())
        assertEquals(0, captor.firstValue.counter)
    }

    @Test
    fun `checkAndFireCounters does not fire pixel when period not elapsed`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.HOURS.toMillis(12)

        whenever(repository.getCounter("adwall")).thenReturn(
            WebTelemetryCounterEntity(name = "adwall", counter = 15, timestampMillis = startTime),
        )

        counterManager.checkAndFireCounters()

        verify(pixel, never()).fire(
            pixelName = any<String>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `checkAndFireCounters does not fire pixel when no matching bucket`() {
        val startTime = 1000L
        val configWithRestrictedBuckets = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "adwall": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-5", "10-20"],
                            "period": "day",
                            "pixel": "test.pixel"
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = configWithRestrictedBuckets))
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 1

        whenever(repository.getCounter("adwall")).thenReturn(
            WebTelemetryCounterEntity(name = "adwall", counter = 7, timestampMillis = startTime),
        )

        counterManager.checkAndFireCounters()

        verify(pixel, never()).fire(
            pixelName = any<String>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )

        // Counter should still be reset
        val captor = argumentCaptor<WebTelemetryCounterEntity>()
        verify(repository).saveCounter(captor.capture())
        assertEquals(0, captor.firstValue.counter)
    }

    @Test
    fun `syncTelemetryState initialises new active types`() {
        whenever(repository.getAllCounters()).thenReturn(emptyList())
        timeProvider.time = 5000L

        counterManager.syncTelemetryState()

        val captor = argumentCaptor<WebTelemetryCounterEntity>()
        verify(repository).saveCounter(captor.capture())
        assertEquals("adwall", captor.firstValue.name)
        assertEquals(0, captor.firstValue.counter)
        assertEquals(5000L, captor.firstValue.timestampMillis)
    }

    @Test
    fun `syncTelemetryState removes counters for disabled types`() {
        whenever(repository.getAllCounters()).thenReturn(
            listOf(
                WebTelemetryCounterEntity(name = "adwall", counter = 3, timestampMillis = 1000L),
                WebTelemetryCounterEntity(name = "removedType", counter = 1, timestampMillis = 1000L),
            ),
        )

        counterManager.syncTelemetryState()

        verify(repository).deleteCounter("removedType")
    }

    @Test
    fun `syncTelemetryState preserves existing active counters`() {
        whenever(repository.getAllCounters()).thenReturn(
            listOf(
                WebTelemetryCounterEntity(name = "adwall", counter = 3, timestampMillis = 1000L),
            ),
        )

        counterManager.syncTelemetryState()

        verify(repository, never()).deleteCounter("adwall")
    }

    @Test
    fun `checkAndFireCounters handles week period`() {
        val weeklyConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetryTypes": {
                        "weekly": {
                            "state": "enabled",
                            "template": "counter",
                            "buckets": ["0-5", "6+"],
                            "period": "week",
                            "pixel": "pixel.weekly"
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = weeklyConfig))

        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(7) + 1

        whenever(repository.getCounter("weekly")).thenReturn(
            WebTelemetryCounterEntity(name = "weekly", counter = 3, timestampMillis = startTime),
        )

        counterManager.checkAndFireCounters()

        verify(pixel).fire(
            pixelName = eq("pixel.weekly"),
            parameters = eq(mapOf("count" to "0-5")),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `handleTelemetryEvent does nothing when feature disabled`() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = "{}"))

        counterManager.handleTelemetryEvent("adwall")

        verify(repository, never()).saveCounter(any())
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }
}
