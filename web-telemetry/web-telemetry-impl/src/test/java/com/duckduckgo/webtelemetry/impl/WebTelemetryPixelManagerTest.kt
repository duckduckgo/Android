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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class WebTelemetryPixelManagerTest {

    private val repository: WebTelemetryRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeTimeProvider()
    private val jitterProvider = ZeroJitterProvider()

    private lateinit var manager: RealWebTelemetryPixelManager

    private val fullConfig = """
        {
            "state": "enabled",
            "settings": {
                "telemetryTypes": {
                    "adwall": {
                        "state": "enabled",
                        "template": "counter",
                        "targets": [
                            { "pixel": "webTelemetry.adwall.day", "param": "adwall_count" },
                            { "pixel": "webTelemetry.adwall.week", "param": "adwall_count" }
                        ]
                    },
                    "trackerBlocked": {
                        "state": "enabled",
                        "template": "counter",
                        "targets": [
                            { "pixel": "webTelemetry.adwall.day", "param": "tracker_count" }
                        ]
                    }
                },
                "pixels": {
                    "webTelemetry.adwall.day": {
                        "trigger": {
                            "period": { "days": 1, "jitterMaxPercent": 0 }
                        },
                        "parameters": {
                            "adwall_count": {
                                "type": "counter",
                                "buckets": ["0-1", "2-3", "4-5", "6-10", "11-20", "21-39", "40+"]
                            },
                            "tracker_count": {
                                "type": "counter",
                                "buckets": ["0", "1-5", "6-20", "21+"]
                            }
                        }
                    },
                    "webTelemetry.adwall.week": {
                        "trigger": {
                            "period": { "days": 7, "jitterMaxPercent": 0 }
                        },
                        "parameters": {
                            "adwall_count": {
                                "type": "counter",
                                "buckets": ["0-5", "6-20", "21-50", "51+"]
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
        manager = RealWebTelemetryPixelManager(repository, pixel, timeProvider, jitterProvider)
    }

    // --- handleTelemetryEvent ---

    @Test
    fun `handleTelemetryEvent increments counter on both target pixels`() {
        val dayState = pixelState("webTelemetry.adwall.day", mapOf("adwall_count" to 3, "tracker_count" to 0))
        val weekState = pixelState("webTelemetry.adwall.week", mapOf("adwall_count" to 10))
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(weekState)

        manager.handleTelemetryEvent("adwall")

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())

        val savedDay = captor.allValues.find { it.pixelName == "webTelemetry.adwall.day" }!!
        val dayParams = RealWebTelemetryPixelManager.parseParamsJson(savedDay.paramsJson)
        assertEquals(4, dayParams["adwall_count"])
        assertEquals(0, dayParams["tracker_count"])

        val savedWeek = captor.allValues.find { it.pixelName == "webTelemetry.adwall.week" }!!
        val weekParams = RealWebTelemetryPixelManager.parseParamsJson(savedWeek.paramsJson)
        assertEquals(11, weekParams["adwall_count"])
    }

    @Test
    fun `handleTelemetryEvent for trackerBlocked only targets day pixel`() {
        val dayState = pixelState("webTelemetry.adwall.day", mapOf("adwall_count" to 0, "tracker_count" to 2))
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)

        manager.handleTelemetryEvent("trackerBlocked")

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry.adwall.day", captor.firstValue.pixelName)

        val params = RealWebTelemetryPixelManager.parseParamsJson(captor.firstValue.paramsJson)
        assertEquals(0, params["adwall_count"])
        assertEquals(3, params["tracker_count"])
    }

    @Test
    fun `handleTelemetryEvent ignores unknown type`() {
        manager.handleTelemetryEvent("unknownType")
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleTelemetryEvent ignores disabled feature`() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = "{}"))
        manager.handleTelemetryEvent("adwall")
        verify(repository, never()).savePixelState(any())
    }

    // --- checkPixels ---

    @Test
    fun `checkPixels fires daily pixel with multi-param buckets when period elapsed`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 1

        val dayState = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwall.day",
            timestampMillis = startTime,
            jitterSeconds = 0.0,
            paramsJson = """{"adwall_count": 15, "tracker_count": 3}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(null)

        manager.checkPixels()

        verify(pixel).fire(
            pixelName = eq("webTelemetry.adwall.day"),
            parameters = eq(mapOf("adwall_count" to "11-20", "tracker_count" to "1-5")),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels does not fire when period not elapsed`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.HOURS.toMillis(12)

        val dayState = pixelState("webTelemetry.adwall.day", mapOf("adwall_count" to 5, "tracker_count" to 1))
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState.copy(timestampMillis = startTime))
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(null)

        manager.checkPixels()

        verify(pixel, never()).fire(
            pixelName = any<String>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `checkPixels resets params and re-rolls jitter after firing`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 1

        val dayState = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwall.day",
            timestampMillis = startTime,
            jitterSeconds = 0.0,
            paramsJson = """{"adwall_count": 5, "tracker_count": 2}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(null)

        manager.checkPixels()

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())

        val saved = captor.firstValue
        assertEquals("webTelemetry.adwall.day", saved.pixelName)
        assertEquals(timeProvider.time, saved.timestampMillis)

        val params = RealWebTelemetryPixelManager.parseParamsJson(saved.paramsJson)
        assertEquals(0, params["adwall_count"])
        assertEquals(0, params["tracker_count"])
    }

    @Test
    fun `checkPixels skips pixel when no param matches any bucket`() {
        val startTime = 1000L
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 1

        val dayState = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwall.day",
            timestampMillis = startTime,
            jitterSeconds = 0.0,
            paramsJson = """{"adwall_count": 0, "tracker_count": 0}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(null)

        manager.checkPixels()

        // adwall_count=0 matches "0-1", tracker_count=0 matches "0"
        // so pixel SHOULD fire in this case
        verify(pixel).fire(
            pixelName = eq("webTelemetry.adwall.day"),
            parameters = eq(mapOf("adwall_count" to "0-1", "tracker_count" to "0")),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels respects jitter offset`() {
        val startTime = 1000L
        val jitterSeconds = 3600.0 // +1 hour
        timeProvider.time = startTime + TimeUnit.DAYS.toMillis(1) + 100 // just past 24h but not past 24h + 1h

        val dayState = WebTelemetryPixelStateEntity(
            pixelName = "webTelemetry.adwall.day",
            timestampMillis = startTime,
            jitterSeconds = jitterSeconds,
            paramsJson = """{"adwall_count": 5, "tracker_count": 0}""",
        )
        whenever(repository.getPixelState("webTelemetry.adwall.day")).thenReturn(dayState)
        whenever(repository.getPixelState("webTelemetry.adwall.week")).thenReturn(null)

        manager.checkPixels()

        verify(pixel, never()).fire(
            pixelName = any<String>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    // --- syncPixelState ---

    @Test
    fun `syncPixelState initialises new pixels`() {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        timeProvider.time = 5000L

        manager.syncPixelState()

        val captor = argumentCaptor<WebTelemetryPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())

        val dayPixel = captor.allValues.find { it.pixelName == "webTelemetry.adwall.day" }!!
        assertEquals(5000L, dayPixel.timestampMillis)
        val dayParams = RealWebTelemetryPixelManager.parseParamsJson(dayPixel.paramsJson)
        assertEquals(0, dayParams["adwall_count"])
        assertEquals(0, dayParams["tracker_count"])

        val weekPixel = captor.allValues.find { it.pixelName == "webTelemetry.adwall.week" }!!
        val weekParams = RealWebTelemetryPixelManager.parseParamsJson(weekPixel.paramsJson)
        assertEquals(0, weekParams["adwall_count"])
    }

    @Test
    fun `syncPixelState removes pixels not in config`() {
        whenever(repository.getAllPixelStates()).thenReturn(
            listOf(
                pixelState("webTelemetry.adwall.day", mapOf("adwall_count" to 3, "tracker_count" to 0)),
                pixelState("removed.pixel", mapOf("count" to 1)),
            ),
        )

        manager.syncPixelState()

        verify(repository).deletePixelState("removed.pixel")
    }

    @Test
    fun `syncPixelState deletes all when feature disabled`() {
        whenever(repository.getConfigEntity()).thenReturn(WebTelemetryConfigEntity(json = """{"state": "disabled"}"""))

        manager.syncPixelState()

        verify(repository).deleteAllPixelStates()
    }

    // --- helpers ---

    private fun pixelState(name: String, params: Map<String, Int>): WebTelemetryPixelStateEntity {
        return WebTelemetryPixelStateEntity(
            pixelName = name,
            timestampMillis = 1000L,
            jitterSeconds = 0.0,
            paramsJson = RealWebTelemetryPixelManager.serializeParams(params),
        )
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }

    private class ZeroJitterProvider : JitterProvider {
        override fun generateJitter(config: PixelConfig): Double = 0.0
    }
}
