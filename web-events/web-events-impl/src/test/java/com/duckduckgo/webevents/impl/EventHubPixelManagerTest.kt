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

package com.duckduckgo.webevents.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.webevents.store.EventHubConfigEntity
import com.duckduckgo.webevents.store.EventHubPixelStateEntity
import com.duckduckgo.webevents.store.EventHubRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

class EventHubPixelManagerTest {

    private val repository: EventHubRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeTimeProvider()

    private lateinit var manager: RealEventHubPixelManager

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetry": {
                    "webTelemetry_adwallDetection_day": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "count": {
                                "template": "counter",
                                "source": "adwall",
                                "buckets": {
                                    "0":     {"gte": 0,  "lt": 1},
                                    "1-2":   {"gte": 1,  "lt": 3},
                                    "3-5":   {"gte": 3,  "lt": 6},
                                    "6-10":  {"gte": 6,  "lt": 11},
                                    "11-20": {"gte": 11, "lt": 21},
                                    "21-39": {"gte": 21, "lt": 40},
                                    "40+":   {"gte": 40}
                                }
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    @Before
    fun setup() {
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)
    }

    // --- handleWebEvent ---

    @Test
    fun `handleWebEvent increments matching counter`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 3))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(4, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent ignores events past periodEnd`() {
        timeProvider.time = 200_000L
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0), periodEnd = 100_000L)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent sets stopCounting when max bucket reached`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 39))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(40, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent skips param with stopCounting set`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 50), stopCounting = setOf("count"))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent ignores unknown event type`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("unknownEvent")

        verify(repository, never()).savePixelState(any())
    }

    // --- checkPixels ---

    @Test
    fun `checkPixels fires pixel with bucketed count and attributionPeriod when period elapsed`() {
        val periodStart = 1769385600000L // 2026-01-26T00:00:00Z
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 15}""",
        )
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.checkPixels()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = eq(mapOf("count" to "11-20", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels does not fire when period not elapsed`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodStart + TimeUnit.HOURS.toMillis(12)

        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 5}""",
        )
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels skips firing when no bucket matches`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val configWithGap = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": {"5-9": {"gte": 5, "lt": 10}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = configWithGap))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val state = EventHubPixelStateEntity(
            pixelName = "test",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 2}""",
        )
        whenever(repository.getPixelState("test")).thenReturn(state)

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels resets state and starts new period after firing`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 5}""",
        )
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.checkPixels()

        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    // --- onConfigChanged ---

    @Test
    fun `onConfigChanged initialises new pixels`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 5000L

        manager.onConfigChanged()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_adwallDetection_day", captor.firstValue.pixelName)
        assertEquals(5000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `onConfigChanged deletes all when feature disabled`() {
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = """{"state": "disabled"}"""))

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    // --- calculateAttributionPeriod ---

    @Test
    fun `calculateAttributionPeriod for daily period`() {
        // 2026-01-02T00:01:00Z = 1767312060
        val startMillis = 1767312060L * 1000
        val period = TelemetryPeriodConfig(days = 1)
        val result = RealEventHubPixelManager.calculateAttributionPeriod(startMillis, period)
        // toStartOfInterval => 2026-01-02T00:00:00Z = 1767312000
        assertEquals(1767312000L, result)
    }

    @Test
    fun `calculateAttributionPeriod for hourly period`() {
        // 2026-01-02T17:15:00Z = 1767374100
        val startMillis = 1767374100L * 1000
        val period = TelemetryPeriodConfig(hours = 1)
        val result = RealEventHubPixelManager.calculateAttributionPeriod(startMillis, period)
        // toStartOfInterval => 2026-01-02T17:00:00Z = 1767373200
        assertEquals(1767373200L, result)
    }

    // --- helpers ---

    private fun pixelState(
        name: String,
        params: Map<String, Int>,
        periodEnd: Long = Long.MAX_VALUE,
        stopCounting: Set<String> = emptySet(),
    ): EventHubPixelStateEntity {
        return EventHubPixelStateEntity(
            pixelName = name,
            periodStartMillis = 1000L,
            periodEndMillis = periodEnd,
            paramsJson = RealEventHubPixelManager.serializeParams(params),
            stopCountingJson = RealEventHubPixelManager.serializeStopCounting(stopCounting),
        )
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }
}
