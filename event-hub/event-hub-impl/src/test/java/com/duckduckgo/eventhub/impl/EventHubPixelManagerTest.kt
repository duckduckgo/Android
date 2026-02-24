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

package com.duckduckgo.eventhub.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.eventhub.store.EventHubConfigEntity
import com.duckduckgo.eventhub.store.EventHubPixelStateEntity
import com.duckduckgo.eventhub.store.EventHubRepository
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

    private val dayPixelConfigJson: String by lazy {
        val parsed = EventHubConfigParser.parse(fullConfig)
        EventHubConfigParser.serializePixelConfig(parsed.telemetry.first { it.name == "webTelemetry_adwallDetection_day" })
    }

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

        manager.handleWebEvent("adwall", "", "")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(4, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent ignores events past periodEnd`() {
        timeProvider.time = 200_000L
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0), periodEnd = 100_000L)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", "", "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent sets stopCounting when max bucket reached`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 39))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", "", "")

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

        manager.handleWebEvent("adwall", "", "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent does not increment when already at max bucket`() {
        val singleBucketConfig = """
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
                                    "buckets": {"0+": {"gte": 0}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = singleBucketConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val state = pixelState("test", mapOf("count" to 0))
        whenever(repository.getPixelState("test")).thenReturn(state)

        manager.handleWebEvent("evt", "", "")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent does not increment when count already in highest bucket`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 40))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", "", "")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(40, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent ignores unknown event type`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("unknownEvent", "", "")

        verify(repository, never()).savePixelState(any())
    }

    // --- deduplication: multiple events on same page should count as one ---

    @Test
    fun `same page same source - second event is deduplicated`() {
        val tab = "tab1"
        val url = "https://example.com/page1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", tab, url)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        // Second event from same page (e.g., different detector firing same source)
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(captor.firstValue)

        manager.handleWebEvent("adwall", tab, url)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `same page multiple events - all after first are deduplicated`() {
        val tab = "tab1"
        val url = "https://example.com/page1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", tab, url)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(6, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        // Multiple subsequent events from the same page
        for (i in 1..5) {
            org.mockito.Mockito.reset(repository)
            whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
            whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(captor.firstValue)

            manager.handleWebEvent("adwall", tab, url)

            verify(repository, never()).savePixelState(any())
        }
    }

    @Test
    fun `navigation to new page resets dedup - event on new page increments`() {
        val tab = "tab1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", tab, "https://example.com/page1")

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // Navigate to new page — different URL means new page visit
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", tab, "https://example.com/page2")

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `subframe event on same page is deduplicated`() {
        val tab = "tab1"
        val url = "https://example.com/page1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", tab, url)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        // Subframe event — same tab + same top-level URL = deduped
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(captor.firstValue)

        manager.handleWebEvent("adwall", tab, url)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `different source types on same page are not deduplicated against each other`() {
        val twoSourceConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test": {
                            "state": "enabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "adwallCount": {
                                    "template": "counter",
                                    "source": "adwall",
                                    "buckets": {"0+": {"gte": 0, "lt": 5}, "5+": {"gte": 5}}
                                },
                                "trackerCount": {
                                    "template": "counter",
                                    "source": "trackerBlocked",
                                    "buckets": {"0+": {"gte": 0, "lt": 5}, "5+": {"gte": 5}}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = twoSourceConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val tab = "tab1"
        val url = "https://example.com/page1"
        val state = pixelState("test", mapOf("adwallCount" to 0, "trackerCount" to 0))
        whenever(repository.getPixelState("test")).thenReturn(state)

        manager.handleWebEvent("adwall", tab, url)

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        val paramsAfterAdwall = RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)
        assertEquals(1, paramsAfterAdwall["adwallCount"])
        assertEquals(0, paramsAfterAdwall["trackerCount"])

        // Different source on same page — should NOT be deduped
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = twoSourceConfig))
        whenever(repository.getPixelState("test")).thenReturn(firstCaptor.firstValue)

        manager.handleWebEvent("trackerBlocked", tab, url)

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        val paramsAfterBoth = RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)
        assertEquals(1, paramsAfterBoth["adwallCount"])
        assertEquals(1, paramsAfterBoth["trackerCount"])
    }

    @Test
    fun `dedup is per-pixel - same source and page deduped independently across pixels`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = twoPixelConf))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val tab = "tab1"
        val url = "https://example.com/page1"
        val stateA = pixelState("pixel_a", mapOf("count" to 0))
        val stateB = pixelState("pixel_b", mapOf("count" to 0))
        whenever(repository.getPixelState("pixel_a")).thenReturn(stateA)
        whenever(repository.getPixelState("pixel_b")).thenReturn(stateB)

        // First event — both pixels should increment
        manager.handleWebEvent("evt", tab, url)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())
        val savedA = captor.allValues.find { it.pixelName == "pixel_a" }!!
        val savedB = captor.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(savedA.paramsJson)["count"])
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(savedB.paramsJson)["count"])

        // Second event from same page — both should be deduped
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = twoPixelConf))
        whenever(repository.getPixelState("pixel_a")).thenReturn(savedA)
        whenever(repository.getPixelState("pixel_b")).thenReturn(savedB)

        manager.handleWebEvent("evt", tab, url)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `empty pageId disables dedup`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        manager.handleWebEvent("adwall", "", "")

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // Second event also with empty URL — no dedup, both increment
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", "", "")

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `returning to same URL after navigating away counts as new visit`() {
        val tab = "tab1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        // Visit page1
        manager.handleWebEvent("adwall", tab, "https://example.com/page1")
        val first = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(first.firstValue.paramsJson)["count"])

        // Navigate to page2
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(first.firstValue)

        manager.handleWebEvent("adwall", tab, "https://example.com/page2")
        val second = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(second.firstValue.paramsJson)["count"])

        // Navigate back to page1 — this is a new visit, should increment
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(second.firstValue)

        manager.handleWebEvent("adwall", tab, "https://example.com/page1")
        val third = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(third.capture())
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(third.firstValue.paramsJson)["count"])
    }

    @Test
    fun `same URL in different tabs counts independently`() {
        val url = "https://example.com/page1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(state)

        // Tab 1 fires event
        manager.handleWebEvent("adwall", "tab1", url)
        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // Tab 2 fires same source on same URL — different tab, should count
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", "tab2", url)
        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])

        // Tab 1 fires again on same page — same tab+URL, should dedup
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = fullConfig))
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(secondCaptor.firstValue)

        manager.handleWebEvent("adwall", "tab1", url)
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
            configJson = dayPixelConfigJson,
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
            configJson = dayPixelConfigJson,
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

        val testPixelConfig = EventHubConfigParser.parse(configWithGap).telemetry.first()
        val state = EventHubPixelStateEntity(
            pixelName = "test",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 2}""",
            configJson = EventHubConfigParser.serializePixelConfig(testPixelConfig),
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
            configJson = dayPixelConfigJson,
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

    // --- config isolation: live config changes must not affect running pixel lifecycle ---

    private fun configWithBuckets(vararg buckets: Pair<String, String>): String {
        val bucketEntries = buckets.joinToString(",") { (name, body) -> "\"$name\": $body" }
        return """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": 120 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": { $bucketEntries }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }

    private val originalBuckets = arrayOf(
        "0-4" to """{"gte": 0, "lt": 5}""",
        "5+" to """{"gte": 5}""",
    )

    private val changedBuckets = arrayOf(
        "0-2" to """{"gte": 0, "lt": 3}""",
        "3+" to """{"gte": 3}""",
    )

    @Test
    fun `handleWebEvent uses stored config buckets, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        // count=4 is in "0-4" with original buckets, would be in "3+" with changed buckets
        val state = pixelState("test_pixel", mapOf("count" to 4), configJson = storedConfigJson)
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to different buckets
        val changedConfig = configWithBuckets(*changedBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedConfig))

        manager.handleWebEvent("evt", "", "")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val savedCount = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"]
        // Should increment to 5 — original config has "5+" at gte=5 so shouldStopCounting is false at 4
        assertEquals(5, savedCount)
    }

    @Test
    fun `handleWebEvent uses stored config source, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to use a different source
        val changedSourceConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": 120 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "different_source",
                                    "buckets": { "0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5} }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedSourceConfig))

        // Event matches stored source ("evt"), not live source ("different_source")
        manager.handleWebEvent("evt", "", "")

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent ignores event matching only live config source`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to use a different source
        val changedSourceConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": 120 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "new_source",
                                    "buckets": { "0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5} }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedSourceConfig))

        // "new_source" matches live config but NOT stored config — should be ignored
        manager.handleWebEvent("new_source", "", "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels fires pixel using stored config buckets, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        // count=4 → "0-4" with original buckets, but "3+" with changed buckets
        val state = EventHubPixelStateEntity(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 4}""",
            configJson = storedConfigJson,
        )
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to different buckets before firing
        val changedConfig = configWithBuckets(*changedBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedConfig))

        manager.checkPixels()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(seconds = 120),
        ).toString()

        // Must use original bucket "0-4", not changed bucket "3+"
        verify(pixel).enqueueFire(
            pixelName = eq("test_pixel"),
            parameters = eq(mapOf("count" to "0-4", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels uses stored config period for attributionPeriod, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val periodStart = 120_000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 1}""",
            configJson = storedConfigJson,
        )
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to a different period (1 hour instead of 120s)
        val changedPeriodConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "hours": 1 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": { "0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5} }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedPeriodConfig))

        manager.checkPixels()

        // Attribution period must be based on stored 120s period, not live 1hr
        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(seconds = 120),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("test_pixel"),
            parameters = eq(mapOf("count" to "0-4", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `new period after firing uses latest config, not stored config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = originalConfig))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 1}""",
            configJson = storedConfigJson,
        )
        whenever(repository.getPixelState("test_pixel")).thenReturn(state)

        // Change live config to 1 hour period before firing
        val changedPeriodConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "enabled",
                            "trigger": { "period": { "hours": 1 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": { "0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5} }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = changedPeriodConfig))

        manager.checkPixels()

        // The NEW period should use the latest config (1 hour = 3600s = 3600000ms)
        verify(repository).deletePixelState("test_pixel")
        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())

        val newState = captor.firstValue
        val expectedPeriodMillis = 3600L * 1000
        assertEquals(timeProvider.time + expectedPeriodMillis, newState.periodEndMillis)

        // And the stored config in the new period should reflect the latest config
        val newStoredConfig = EventHubConfigParser.parseSinglePixelConfig("test_pixel", newState.configJson)!!
        assertEquals(3600L, newStoredConfig.trigger.period.periodSeconds)
    }

    @Test
    fun `onConfigChanged stores config snapshot in new pixel state`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 5000L

        manager.onConfigChanged()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())

        val storedConfig = EventHubConfigParser.parseSinglePixelConfig(
            captor.firstValue.pixelName,
            captor.firstValue.configJson,
        )!!
        assertEquals("adwall", storedConfig.parameters["count"]!!.source)
        assertEquals(86400L, storedConfig.trigger.period.periodSeconds)
        assertEquals(7, storedConfig.parameters["count"]!!.buckets.size)
    }

    // --- multi-pixel config lifecycle ---

    private fun twoPixelConfig(periodSecondsA: Int, periodSecondsB: Int, bucketDef: String): String {
        return """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "pixel_a": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": $periodSecondsA } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": { $bucketDef }
                                }
                            }
                        },
                        "pixel_b": {
                            "state": "enabled",
                            "trigger": { "period": { "seconds": $periodSecondsB } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "evt",
                                    "buckets": { $bucketDef }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }

    @Test
    fun `multi-pixel lifecycle - each pixel uses its own config snapshot independently`() {
        val buckets = """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}"""

        // Step 1: config [1] loads — both pixels registered with 60s/120s periods
        val config1 = twoPixelConfig(60, 120, buckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = config1))
        manager = RealEventHubPixelManager(repository, pixel, timeProvider)

        timeProvider.time = 10_000L
        whenever(repository.getPixelState("pixel_a")).thenReturn(null)
        whenever(repository.getPixelState("pixel_b")).thenReturn(null)
        manager.onConfigChanged()

        val savedStates = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedStates.capture())
        val stateA1 = savedStates.allValues.find { it.pixelName == "pixel_a" }!!
        val stateB1 = savedStates.allValues.find { it.pixelName == "pixel_b" }!!

        // Both use config [1]
        val configA1 = EventHubConfigParser.parseSinglePixelConfig("pixel_a", stateA1.configJson)!!
        val configB1 = EventHubConfigParser.parseSinglePixelConfig("pixel_b", stateB1.configJson)!!
        assertEquals(60L, configA1.trigger.period.periodSeconds)
        assertEquals(120L, configB1.trigger.period.periodSeconds)

        // Step 2: config [2] loads — pixels A and B still use config [1]
        org.mockito.Mockito.reset(repository, pixel)
        val config2 = twoPixelConfig(90, 180, buckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = config2))

        // Simulate accumulated state on both pixels
        val stateA1WithCount = stateA1.copy(paramsJson = """{"count": 2}""")
        val stateB1WithCount = stateB1.copy(paramsJson = """{"count": 3}""")
        whenever(repository.getPixelState("pixel_a")).thenReturn(stateA1WithCount)
        whenever(repository.getPixelState("pixel_b")).thenReturn(stateB1WithCount)

        // Events still use config [1] stored in state
        manager.handleWebEvent("evt", "", "")

        val savedAfterEvent = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedAfterEvent.capture())
        val updatedA = savedAfterEvent.allValues.find { it.pixelName == "pixel_a" }!!
        val updatedB = savedAfterEvent.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(updatedA.paramsJson)["count"])
        assertEquals(4, RealEventHubPixelManager.parseParamsJson(updatedB.paramsJson)["count"])
        // Stored configs unchanged — still config [1]
        assertEquals(stateA1.configJson, updatedA.configJson)
        assertEquals(stateB1.configJson, updatedB.configJson)

        // Step 3: pixel A fires — new cycle uses config [2], pixel B still on config [1]
        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = config2))

        timeProvider.time = stateA1.periodEndMillis + 1
        whenever(repository.getPixelState("pixel_a")).thenReturn(updatedA)
        whenever(repository.getPixelState("pixel_b")).thenReturn(updatedB)

        manager.checkPixels()

        verify(repository).deletePixelState("pixel_a")
        verify(repository, never()).deletePixelState("pixel_b")

        val savedAfterFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(savedAfterFire.capture())
        val newStateA2 = savedAfterFire.firstValue
        assertEquals("pixel_a", newStateA2.pixelName)

        // New pixel A cycle uses config [2] (90s period)
        val configA2 = EventHubConfigParser.parseSinglePixelConfig("pixel_a", newStateA2.configJson)!!
        assertEquals(90L, configA2.trigger.period.periodSeconds)

        // Step 4: config [3] loads — pixel A on [2], pixel B still on [1]
        org.mockito.Mockito.reset(repository, pixel)
        val config3 = twoPixelConfig(45, 300, buckets)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = config3))
        whenever(repository.getPixelState("pixel_a")).thenReturn(newStateA2)
        whenever(repository.getPixelState("pixel_b")).thenReturn(updatedB)

        manager.handleWebEvent("evt", "", "")

        val savedAfterConfig3 = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedAfterConfig3.capture())
        val aAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_a" }!!
        val bAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_b" }!!
        // A still uses config [2], B still uses config [1]
        assertEquals(newStateA2.configJson, aAfter3.configJson)
        assertEquals(stateB1.configJson, bAfter3.configJson)

        // Step 5: pixel B fires — new cycle uses config [3]
        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigEntity()).thenReturn(EventHubConfigEntity(json = config3))

        timeProvider.time = stateB1.periodEndMillis + 1
        whenever(repository.getPixelState("pixel_a")).thenReturn(aAfter3)
        whenever(repository.getPixelState("pixel_b")).thenReturn(bAfter3)

        manager.checkPixels()

        verify(repository).deletePixelState("pixel_b")
        val savedAfterBFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(savedAfterBFire.capture())
        val newStateB3 = savedAfterBFire.firstValue
        assertEquals("pixel_b", newStateB3.pixelName)

        // New pixel B cycle uses config [3] (300s period)
        val configB3 = EventHubConfigParser.parseSinglePixelConfig("pixel_b", newStateB3.configJson)!!
        assertEquals(300L, configB3.trigger.period.periodSeconds)

        // Pixel A is still on config [2]
        val configAStill2 = EventHubConfigParser.parseSinglePixelConfig("pixel_a", aAfter3.configJson)!!
        assertEquals(90L, configAStill2.trigger.period.periodSeconds)
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
        periodStart: Long = 1000L,
        stopCounting: Set<String> = emptySet(),
        configJson: String? = null,
    ): EventHubPixelStateEntity {
        val resolvedConfigJson = configJson ?: run {
            val config = EventHubConfigParser.parse(repository.getEventHubConfigEntity().json)
            val pixelConfig = config.telemetry.find { it.name == name }
            pixelConfig?.let { EventHubConfigParser.serializePixelConfig(it) } ?: "{}"
        }
        return EventHubPixelStateEntity(
            pixelName = name,
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = RealEventHubPixelManager.serializeParams(params),
            stopCountingJson = RealEventHubPixelManager.serializeStopCounting(stopCounting),
            configJson = resolvedConfigJson,
        )
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }
}
