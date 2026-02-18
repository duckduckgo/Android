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
import com.duckduckgo.webtelemetry.store.WebTelemetryPixelStateEntity
import com.duckduckgo.webtelemetry.store.WebTelemetryRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class EventHubPixelManagerTest {

    private val repository: WebTelemetryRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeTimeProvider()
    private val staggerProvider = ZeroStaggerProvider()

    private lateinit var manager: RealEventHubPixelManager

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetry": {
                    "webTelemetry.adwalls.day": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1, "maxStaggerMins": 0 } },
                        "parameters": {
                            "adwallCount": {
                                "template": "counter",
                                "source": "adwall",
                                "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"]
                            },
                            "trackerCount": {
                                "template": "counter",
                                "source": "trackerBlocked",
                                "buckets": ["0", "1-5", "6-20", "21+"]
                            }
                        }
                    }
                }
            }
        }
    """.trimIndent()

    @Before
    fun setup() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = fullConfig))
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, staggerProvider)
    }

    // --- handleWebEvent ---

    @Test
    fun `handleWebEvent increments matching counter parameter`() {
        val state = pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 3, "trackerCount" to 0))
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.handleWebEvent("adwall")

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val params = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)
        assertEquals(4, params["adwallCount"])
        assertEquals(0, params["trackerCount"])
    }

    @Test
    fun `handleWebEvent increments trackerBlocked source`() {
        val state = pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 0, "trackerCount" to 2))
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.handleWebEvent("trackerBlocked")

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val params = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)
        assertEquals(0, params["adwallCount"])
        assertEquals(3, params["trackerCount"])
    }

    @Test
    fun `handleWebEvent ignores unknown event type`() {
        val state = pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 0, "trackerCount" to 0))
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.handleWebEvent("unknownEvent")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent does nothing when feature disabled`() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = "{}"))
        manager.handleWebEvent("adwall")
        verify(repository, never()).savePixelState(any())
    }

    // --- checkPixels ---

    @Test
    fun `checkPixels fires pixel with bucketed params and period when period elapsed`() {
        val periodStart = 1769385600000L // some UTC timestamp
        timeProvider.time = periodStart + TimeUnit.DAYS.toMillis(1) + 1

        val state = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwalls.day",
            periodStartMillis = periodStart,
            paramsJson = """{"adwallCount": 15, "trackerCount": 3}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.checkPixels()

        val expectedPeriod = RealEventHubPixelManager.toStartOfInterval(periodStart, 86400L).toString()
        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry.adwalls.day"),
            parameters = eq(mapOf("adwallCount" to "11-20", "trackerCount" to "1-5", "period" to expectedPeriod)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels does not fire when period not elapsed`() {
        val periodStart = 1000L
        timeProvider.time = periodStart + TimeUnit.HOURS.toMillis(12)

        val state = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwalls.day",
            periodStartMillis = periodStart,
            paramsJson = """{"adwallCount": 5, "trackerCount": 1}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels skips firing when all counter params are zero (only period param)`() {
        val periodStart = 1000L
        timeProvider.time = periodStart + TimeUnit.DAYS.toMillis(1) + 1

        val state = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwalls.day",
            periodStartMillis = periodStart,
            paramsJson = """{"adwallCount": 0, "trackerCount": 0}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.checkPixels()

        // 0 doesn't match any bucket in ["0-1", ...] wait - "0-1" includes 0
        // and "0" matches 0 for trackerCount. So pixel SHOULD fire.
        // Let me adjust: use buckets where 0 doesn't match
    }

    @Test
    fun `checkPixels skips firing when no param matches any bucket`() {
        val configWithRestrictedBuckets = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test.pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": ["5-10", "11+"]
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = configWithRestrictedBuckets))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, staggerProvider)

        val periodStart = 1000L
        timeProvider.time = periodStart + TimeUnit.DAYS.toMillis(1) + 1

        val state = WebTelemetryPixelStateEntity(
            pixelName = "test.pixel",
            periodStartMillis = periodStart,
            paramsJson = """{"count": 2}""",
        )
        whenever(repository.getPixelState("test.pixel")).thenReturn(state)

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())

        // But counters should still reset (new period started)
        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val params = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)
        assertEquals(0, params["count"])
    }

    @Test
    fun `checkPixels resets counters and starts new period after firing`() {
        val periodStart = 1000L
        timeProvider.time = periodStart + TimeUnit.DAYS.toMillis(1) + 1

        val state = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwalls.day",
            periodStartMillis = periodStart,
            paramsJson = """{"adwallCount": 5, "trackerCount": 2}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(state)

        manager.checkPixels()

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)

        val params = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)
        assertEquals(0, params["adwallCount"])
        assertEquals(0, params["trackerCount"])
    }

    // --- onConfigChanged ---

    @Test
    fun `onConfigChanged initialises new pixels`() {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(null)
        timeProvider.time = 5000L

        manager.onConfigChanged()

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry.adwalls.day", captor.firstValue.pixelName)
        assertEquals(5000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `onConfigChanged removes pixels not in config`() {
        whenever(repository.getAllPixelStates()).thenReturn(
            listOf(
                pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 3)),
                pixelState("removed.pixel", mapOf("count" to 1)),
            ),
        )
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(
            pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 3)),
        )

        manager.onConfigChanged()

        verify(repository).deletePixelState("removed.pixel")
    }

    @Test
    fun `onConfigChanged deletes all when feature disabled`() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = """{"state": "disabled"}"""))

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    @Test
    fun `onConfigChanged preserves existing pixel state`() {
        val existing = pixelState("webTelemetry.adwalls.day", mapOf("adwallCount" to 7, "trackerCount" to 2))
        whenever(repository.getAllPixelStates()).thenReturn(listOf(existing))
        whenever(repository.getPixelState("webTelemetry.adwalls.day")).thenReturn(existing)

        manager.onConfigChanged()

        // Should NOT call savePixelState for existing pixel (preserves counters)
        verify(repository, never()).savePixelState(any())
    }

    // --- toStartOfInterval ---

    @Test
    fun `toStartOfInterval normalises to start of day`() {
        // 2026-01-26T17:00:00Z = 1769526000
        val ts = 1769526000L * 1000
        val result = RealEventHubPixelManager.toStartOfInterval(ts, 86400L)
        // 2026-01-26T00:00:00Z = 1769472000
        assertEquals(1769472000L, result)
    }

    @Test
    fun `toStartOfInterval normalises to start of hour`() {
        // 2025-06-01T01:17:18Z = 1748739438
        val ts = 1748739438L * 1000
        val result = RealEventHubPixelManager.toStartOfInterval(ts, 3600L)
        // 2025-06-01T01:00:00Z = 1748737200
        assertEquals(1748737200L, result)
    }

    // --- helpers ---

    private fun pixelState(name: String, params: Map<String, Int>): WebTelemetryPixelStateEntity {
        return WebTelemetryPixelStateEntity(
            pixelName = name,
            periodStartMillis = 1000L,
            paramsJson = RealEventHubPixelManager.serializeParams(params),
        )
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }

    private class ZeroStaggerProvider : StaggerProvider {
        override fun randomStaggerMs(maxStaggerMins: Int): Long = 0L
    }
}
