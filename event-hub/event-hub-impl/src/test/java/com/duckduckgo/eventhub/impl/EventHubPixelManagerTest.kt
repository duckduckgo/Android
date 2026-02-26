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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.eventhub.api.WebEventContext
import com.duckduckgo.eventhub.impl.store.EventHubPixelStateEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@OptIn(ExperimentalCoroutinesApi::class)
class EventHubPixelManagerTest {

    private val repository: EventHubRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeTimeProvider()
    private val foregroundState = FakeAppForegroundStateProvider()
    private val NO_CONTEXT = WebEventContext(tabId = "", documentUrl = "")

    private lateinit var testScope: TestScope
    private lateinit var dispatcherProvider: DispatcherProvider
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
        val dispatcher = StandardTestDispatcher()
        testScope = TestScope(dispatcher)
        dispatcherProvider = mock<DispatcherProvider>().also {
            whenever(it.io()).thenReturn(dispatcher)
        }
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)
    }

    @org.junit.After
    fun tearDown() {
        testScope.cancel()
    }

    // --- handleWebEvent ---

    @Test
    fun `handleWebEvent increments matching counter`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 3))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(4, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent ignores events past periodEnd`() {
        timeProvider.time = 200_000L
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0), periodEnd = 100_000L)
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent sets stopCounting when max bucket reached`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 39))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(40, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent skips param with stopCounting set`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 50), stopCounting = setOf("count"))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(singleBucketConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val state = pixelState("test", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("evt", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent does not increment when count already in highest bucket`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 40))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(40, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
        val stopCounting = RealEventHubPixelManager.parseStopCountingJson(captor.firstValue.stopCountingJson)
        assertTrue("count" in stopCounting)
    }

    @Test
    fun `handleWebEvent ignores unknown event type`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("unknownEvent", NO_CONTEXT)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent ignores events when feature disabled`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)
        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")

        manager.handleWebEvent("adwall", NO_CONTEXT)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent with empty URL disables dedup`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // URL now empty — dedup disabled, should increment again
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = ""))

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])
    }

    // --- deduplication: multiple events on same page should count as one ---

    @Test
    fun `same page same source - second event is deduplicated`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(captor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `same page multiple events - all after first are deduplicated`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(6, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        for (i in 1..5) {
            org.mockito.Mockito.reset(repository)
            whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
            stubPixelStates(captor.firstValue)

            manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

            verify(repository, never()).savePixelState(any())
        }
    }

    @Test
    fun `navigation to new page resets dedup - event on new page increments`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // Navigate — same tab, different URL
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page2"))

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `subframe event on same page is deduplicated`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])

        // Subframe event — same tab + same URL = deduped
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(captor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

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
        whenever(repository.getEventHubConfigJson()).thenReturn(twoSourceConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val state = pixelState("test", mapOf("adwallCount" to 0, "trackerCount" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        val paramsAfterAdwall = RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)
        assertEquals(1, paramsAfterAdwall["adwallCount"])
        assertEquals(0, paramsAfterAdwall["trackerCount"])

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(twoSourceConfig)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent("trackerBlocked", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        val paramsAfterBoth = RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)
        assertEquals(1, paramsAfterBoth["adwallCount"])
        assertEquals(1, paramsAfterBoth["trackerCount"])
    }

    @Test
    fun `dedup is per-pixel - same source and page deduped independently across pixels`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        whenever(repository.getEventHubConfigJson()).thenReturn(twoPixelConf)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val stateA = pixelState("pixel_a", mapOf("count" to 0))
        val stateB = pixelState("pixel_b", mapOf("count" to 0))
        stubPixelStates(stateA, stateB)

        manager.handleWebEvent("evt", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())
        val savedA = captor.allValues.find { it.pixelName == "pixel_a" }!!
        val savedB = captor.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(savedA.paramsJson)["count"])
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(savedB.paramsJson)["count"])

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(twoPixelConf)
        stubPixelStates(savedA, savedB)

        manager.handleWebEvent("evt", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `empty tabId disables dedup`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "", documentUrl = "https://example.com/page1"))

        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "", documentUrl = "https://example.com/page1"))

        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `returning to same URL after navigating away counts as new visit`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))
        val first = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(first.firstValue.paramsJson)["count"])

        // Navigate to page2
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page2"))
        val second = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(second.firstValue.paramsJson)["count"])

        // Navigate back to page1
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(second.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))
        val third = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(third.capture())
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(third.firstValue.paramsJson)["count"])
    }

    @Test
    fun `same URL in different tabs counts independently`() {
        val url = "https://example.com/page1"
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        // Tab 1 fires event
        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = url))
        val firstCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(firstCaptor.firstValue.paramsJson)["count"])

        // Tab 2 fires same source on same URL — different tab, should count
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab2", documentUrl = url))
        val secondCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(secondCaptor.firstValue.paramsJson)["count"])

        // Tab 1 fires again on same page — same tab + same URL, should dedup
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(secondCaptor.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = url))
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
        stubPixelStates(state)

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
        stubPixelStates(state)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(configWithGap)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val testPixelConfig = EventHubConfigParser.parse(configWithGap).telemetry.first()
        val state = EventHubPixelStateEntity(
            pixelName = "test",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 2}""",
            configJson = EventHubConfigParser.serializePixelConfig(testPixelConfig),
        )
        stubPixelStates(state)

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
        stubPixelStates(state)

        manager.checkPixels()

        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `checkPixels still fires pixel that is disabled in current config`() {
        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val originalConfig = configWithBuckets(*originalBuckets)
        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = EventHubPixelStateEntity(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 2}""",
            configJson = storedConfigJson,
        )
        stubPixelStates(state)

        // Current config has the pixel DISABLED
        val disabledConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "disabled",
                            "trigger": { "period": { "seconds": 120 } },
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
        whenever(repository.getEventHubConfigJson()).thenReturn(disabledConfig)

        manager.checkPixels()

        // Should STILL fire using stored config (per design: "still fire the telemetry, as it was enabled when initialised")
        verify(pixel).enqueueFire(
            pixelName = eq("test_pixel"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `checkPixels does not re-register disabled pixel after firing`() {
        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        // Pixel disabled in current config
        val disabledPixelConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "webTelemetry_adwallDetection_day": {
                            "state": "disabled",
                            "trigger": { "period": { "days": 1 } },
                            "parameters": {
                                "count": {
                                    "template": "counter",
                                    "source": "adwall",
                                    "buckets": { "0": {"gte": 0, "lt": 1}, "1-2": {"gte": 1, "lt": 3}, "3-5": {"gte": 3, "lt": 6}, "6-10": {"gte": 6, "lt": 11}, "11-20": {"gte": 11, "lt": 21}, "21-39": {"gte": 21, "lt": 40}, "40+": {"gte": 40} }
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        whenever(repository.getEventHubConfigJson()).thenReturn(disabledPixelConfig)

        manager.checkPixels()

        // Fires the pixel
        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        // Deletes old state
        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        // Does NOT re-register (pixel disabled in current config)
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels does not fire any telemetry when feature disabled`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5), periodStart = 1000L, periodEnd = 2000L)
        stubPixelStates(state)
        timeProvider.time = 3000L

        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
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
        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    @Test
    fun `onConfigChanged does not re-register existing pixel`() {
        val existingState = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 3))
        stubPixelStates(existingState)

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged does not register disabled pixel`() {
        val disabledPixelConfig = """
            {
                "state": "enabled",
                "settings": {
                    "telemetry": {
                        "test_pixel": {
                            "state": "disabled",
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
        whenever(repository.getEventHubConfigJson()).thenReturn(disabledPixelConfig)
        whenever(repository.getPixelState("test_pixel")).thenReturn(null)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged treats absent feature as disabled`() {
        whenever(repository.getEventHubConfigJson()).thenReturn("{}")

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    @Test
    fun `onConfigChanged treats missing feature in config as disabled`() {
        whenever(repository.getEventHubConfigJson()).thenReturn("""{"unrelated": true}""")

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
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        // count=4 is in "0-4" with original buckets, would be in "3+" with changed buckets
        val state = pixelState("test_pixel", mapOf("count" to 4), configJson = storedConfigJson)
        stubPixelStates(state)

        // Change live config to different buckets
        val changedConfig = configWithBuckets(*changedBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(changedConfig)

        manager.handleWebEvent("evt", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val savedCount = RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"]
        // Should increment to 5 — original config has "5+" at gte=5 so shouldStopCounting is false at 4
        assertEquals(5, savedCount)
    }

    @Test
    fun `handleWebEvent uses stored config source, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        stubPixelStates(state)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(changedSourceConfig)

        // Event matches stored source ("evt"), not live source ("different_source")
        manager.handleWebEvent("evt", NO_CONTEXT)

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent ignores event matching only live config source`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        stubPixelStates(state)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(changedSourceConfig)

        // "new_source" matches live config but NOT stored config — should be ignored
        manager.handleWebEvent("new_source", NO_CONTEXT)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels fires pixel using stored config buckets, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

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
        stubPixelStates(state)

        // Change live config to different buckets before firing
        val changedConfig = configWithBuckets(*changedBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(changedConfig)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

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
        stubPixelStates(state)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(changedPeriodConfig)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

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
        stubPixelStates(state)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(changedPeriodConfig)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(config1)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(config2)

        // Simulate accumulated state on both pixels
        val stateA1WithCount = stateA1.copy(paramsJson = """{"count": 2}""")
        val stateB1WithCount = stateB1.copy(paramsJson = """{"count": 3}""")
        stubPixelStates(stateA1WithCount, stateB1WithCount)

        // Events still use config [1] stored in state
        manager.handleWebEvent("evt", NO_CONTEXT)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(config2)

        timeProvider.time = stateA1.periodEndMillis + 1
        stubPixelStates(updatedA, updatedB)

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
        whenever(repository.getEventHubConfigJson()).thenReturn(config3)
        stubPixelStates(newStateA2, updatedB)

        manager.handleWebEvent("evt", NO_CONTEXT)

        val savedAfterConfig3 = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedAfterConfig3.capture())
        val aAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_a" }!!
        val bAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_b" }!!
        // A still uses config [2], B still uses config [1]
        assertEquals(newStateA2.configJson, aAfter3.configJson)
        assertEquals(stateB1.configJson, bAfter3.configJson)

        // Step 5: pixel B fires — new cycle uses config [3]
        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(config3)

        timeProvider.time = stateB1.periodEndMillis + 1
        stubPixelStates(aAfter3, bAfter3)

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

    // --- robustness ---

    @Test
    fun `handleWebEvent skips pixel with malformed stored configJson`() {
        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = 1000L,
            periodEndMillis = Long.MAX_VALUE,
            paramsJson = """{"count": 0}""",
            configJson = "not valid json",
        )
        stubPixelStates(state)

        manager.handleWebEvent("adwall", NO_CONTEXT)

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels skips pixel with malformed stored configJson`() {
        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = 1000L,
            periodEndMillis = 2000L,
            paramsJson = """{"count": 5}""",
            configJson = "{broken",
        )
        stubPixelStates(state)
        timeProvider.time = 3000L

        manager.checkPixels()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `config round-trip preserves all fields`() {
        val config = EventHubConfigParser.parse(fullConfig)
        val pixelConfig = config.telemetry.first()

        val serialized = EventHubConfigParser.serializePixelConfig(pixelConfig)
        val restored = EventHubConfigParser.parseSinglePixelConfig(pixelConfig.name, serialized)!!

        assertEquals(pixelConfig.name, restored.name)
        assertEquals(pixelConfig.state, restored.state)
        assertEquals(pixelConfig.trigger.period.days, restored.trigger.period.days)
        assertEquals(pixelConfig.trigger.period.periodSeconds, restored.trigger.period.periodSeconds)
        assertEquals(pixelConfig.parameters.size, restored.parameters.size)

        val originalParam = pixelConfig.parameters["count"]!!
        val restoredParam = restored.parameters["count"]!!
        assertEquals(originalParam.template, restoredParam.template)
        assertEquals(originalParam.source, restoredParam.source)
        assertEquals(originalParam.buckets.size, restoredParam.buckets.size)
        assertEquals(originalParam.buckets["0"]!!.gte, restoredParam.buckets["0"]!!.gte)
        assertEquals(originalParam.buckets["0"]!!.lt, restoredParam.buckets["0"]!!.lt)
        assertEquals(originalParam.buckets["40+"]!!.gte, restoredParam.buckets["40+"]!!.gte)
        assertEquals(originalParam.buckets["40+"]!!.lt, restoredParam.buckets["40+"]!!.lt)
    }

    @Test
    fun `parseParamsJson with malformed JSON returns empty map`() {
        val result = RealEventHubPixelManager.parseParamsJson("not json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseStopCountingJson with malformed JSON returns empty set`() {
        val result = RealEventHubPixelManager.parseStopCountingJson("{bad")
        assertTrue(result.isEmpty())
    }

    // --- privacy protections independence: eventHub must NOT be disabled by per-site protections ---

    @Test
    fun `handleWebEvent processes events regardless of per-site protection state`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://unprotected-site.example.com"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent only checks remote config state, not privacy protections`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://tracker-heavy-site.example.com"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(6, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `handleWebEvent accumulates across sites with different protection levels`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://protected-site.example.com"))

        val first = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(first.firstValue.paramsJson)["count"])

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab2", documentUrl = "https://unprotected-site.example.com"))

        val second = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(second.firstValue.paramsJson)["count"])
    }

    @Test
    fun `checkPixels fires telemetry accumulated from both protected and unprotected sites`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 8}""",
            configJson = dayPixelConfigJson,
        )
        stubPixelStates(state)

        manager.checkPixels()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = eq(mapOf("count" to "6-10", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `onConfigChanged initialises pixels regardless of privacy protection state`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 42_000L

        manager.onConfigChanged()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_adwallDetection_day", captor.firstValue.pixelName)
        assertEquals(42_000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `handleWebEvent only rejects events when remote config state is disabled`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")
        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com"))
        verify(repository, never()).savePixelState(any())

        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com"))
        verify(repository).savePixelState(any())
    }

    // --- fire button state persistence: eventHub state must survive data clearing ---

    @Test
    fun `pixel state counters persist across fire button - accumulated counts carry forward`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/before-fire"))

        val preFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(6, RealEventHubPixelManager.parseParamsJson(preFire.firstValue.paramsJson)["count"])

        // Simulate fire button: tabs cleared, new tab created — repository state untouched
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(preFire.firstValue)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "newTab1", documentUrl = "https://example.com/after-fire"))

        val postFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(postFire.capture())
        assertEquals(7, RealEventHubPixelManager.parseParamsJson(postFire.firstValue.paramsJson)["count"])
    }

    @Test
    fun `period timing is unaffected by fire button`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 5000L

        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 3), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/before"))

        val preFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(periodStart, preFire.firstValue.periodStartMillis)
        assertEquals(periodEnd, preFire.firstValue.periodEndMillis)

        // Simulate fire button: new tabs, but repository state (including period) persists
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(preFire.firstValue)

        timeProvider.time = 10_000L
        manager.handleWebEvent("adwall", WebEventContext(tabId = "newTab", documentUrl = "https://example.com/after"))

        val postFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(postFire.capture())
        assertEquals(periodStart, postFire.firstValue.periodStartMillis)
        assertEquals(periodEnd, postFire.firstValue.periodEndMillis)
    }

    @Test
    fun `checkPixels fires total accumulated count spanning fire button`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        // 12 events accumulated: some before fire button, some after — all persisted
        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 12}""",
            configJson = dayPixelConfigJson,
        )
        stubPixelStates(state)

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
    fun `stopCounting flags persist across fire button`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 50), stopCounting = setOf("count"))
        stubPixelStates(state)

        // After fire button, new tab sends same event type — stopCounting still applied
        manager.handleWebEvent("adwall", WebEventContext(tabId = "newTab", documentUrl = "https://example.com/post-fire"))

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `config state persists across fire button`() {
        // Pixel was registered with specific config snapshot — fire button must not erase it
        val originalConfig = configWithBuckets(*originalBuckets)
        whenever(repository.getEventHubConfigJson()).thenReturn(originalConfig)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val originalPixelConfig = EventHubConfigParser.parse(originalConfig).telemetry.first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 2), configJson = storedConfigJson)
        stubPixelStates(state)

        // Simulate fire button: new tab, but the stored config snapshot is preserved
        manager.handleWebEvent("evt", WebEventContext(tabId = "newTab1", documentUrl = "https://example.com/post-fire"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(storedConfigJson, captor.firstValue.configJson)
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
    }

    @Test
    fun `multi-pixel state all persists across fire button`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        whenever(repository.getEventHubConfigJson()).thenReturn(twoPixelConf)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        val stateA = pixelState("pixel_a", mapOf("count" to 2))
        val stateB = pixelState("pixel_b", mapOf("count" to 3))
        stubPixelStates(stateA, stateB)

        // Fire button happened — new tabs, same repository state
        manager.handleWebEvent("evt", WebEventContext(tabId = "newTab1", documentUrl = "https://example.com/post-fire"))

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())
        val savedA = captor.allValues.find { it.pixelName == "pixel_a" }!!
        val savedB = captor.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(savedA.paramsJson)["count"])
        assertEquals(4, RealEventHubPixelManager.parseParamsJson(savedB.paramsJson)["count"])
    }

    @Test
    fun `dedup state allows new tab events after fire button`() {
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0))
        stubPixelStates(state)

        // Event on tab1 before fire button
        manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page1"))

        val first = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, RealEventHubPixelManager.parseParamsJson(first.firstValue.paramsJson)["count"])

        // Fire button: tab1 destroyed, new tab created with different tabId
        org.mockito.Mockito.reset(repository)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        stubPixelStates(first.firstValue)

        // Same URL in new tab — different tabId means not deduped
        manager.handleWebEvent("adwall", WebEventContext(tabId = "newTab1", documentUrl = "https://example.com/page1"))

        val second = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, RealEventHubPixelManager.parseParamsJson(second.firstValue.paramsJson)["count"])
    }

    @Test
    fun `full lifecycle across fire button - accumulate, fire button, accumulate, check`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 5000L

        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        // Pre-fire: 3 events on different pages
        for (i in 1..3) {
            org.mockito.Mockito.reset(repository)
            whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
            val currentState = if (i == 1) {
                state
            } else {
                pixelState("webTelemetry_adwallDetection_day", mapOf("count" to i - 1), periodStart = periodStart, periodEnd = periodEnd)
            }
            stubPixelStates(currentState)
            manager.handleWebEvent("adwall", WebEventContext(tabId = "tab1", documentUrl = "https://example.com/page$i"))
        }

        val preFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(3, RealEventHubPixelManager.parseParamsJson(preFire.firstValue.paramsJson)["count"])

        // Fire button — new tabs
        // Post-fire: 2 more events
        for (i in 1..2) {
            org.mockito.Mockito.reset(repository)
            whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
            val currentState = pixelState(
                "webTelemetry_adwallDetection_day",
                mapOf("count" to 2 + i),
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
            stubPixelStates(currentState)
            manager.handleWebEvent("adwall", WebEventContext(tabId = "newTab1", documentUrl = "https://other.com/page$i"))
        }

        val postFire = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(postFire.capture())
        assertEquals(5, RealEventHubPixelManager.parseParamsJson(postFire.firstValue.paramsJson)["count"])

        // Period elapses — checkPixels fires the total
        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        timeProvider.time = periodEnd + 1
        val finalState = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 5}""",
            configJson = dayPixelConfigJson,
        )
        stubPixelStates(finalState)

        manager.checkPixels()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = eq(mapOf("count" to "3-5", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
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

    // --- scheduled firing (scheduleFireTelemetry / timer management) ---

    @Test
    fun `startNewPeriod schedules a timer that fires the pixel`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val savedState = captor.firstValue
        assertTrue(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(savedState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(savedState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `scheduleFireTelemetry does not double-schedule same pixel`() {
        manager.scheduleFireTelemetry("test_pixel", 5000L)
        assertTrue(manager.hasScheduledTimer("test_pixel"))

        manager.scheduleFireTelemetry("test_pixel", 10000L)
        assertTrue(manager.hasScheduledTimer("test_pixel"))
    }

    @Test
    fun `cancelScheduledFire removes pending timer`() {
        manager.scheduleFireTelemetry("test_pixel", 60_000L)
        assertTrue(manager.hasScheduledTimer("test_pixel"))

        manager.cancelScheduledFire("test_pixel")
        assertFalse(manager.hasScheduledTimer("test_pixel"))
    }

    @Test
    fun `cancelScheduledFire on non-existent timer is a no-op`() {
        manager.cancelScheduledFire("nonexistent")
        assertFalse(manager.hasScheduledTimer("nonexistent"))
    }

    @Test
    fun `onConfigChanged cancels all timers when feature disabled`() {
        manager.scheduleFireTelemetry("pixel_a", 60_000L)
        manager.scheduleFireTelemetry("pixel_b", 120_000L)
        assertTrue(manager.hasScheduledTimer("pixel_a"))
        assertTrue(manager.hasScheduledTimer("pixel_b"))

        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")
        manager.onConfigChanged()

        assertFalse(manager.hasScheduledTimer("pixel_a"))
        assertFalse(manager.hasScheduledTimer("pixel_b"))
    }

    @Test
    fun `fireTelemetry cancels existing timer before re-registering`() {
        val periodStart = 1000L
        val periodEnd = periodStart + 60_000L
        timeProvider.time = periodEnd + 1

        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 3), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        manager.scheduleFireTelemetry("webTelemetry_adwallDetection_day", 1000L)
        assertTrue(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))

        manager.checkPixels()

        assertTrue(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))
        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels reschedules timers for active pixels`() {
        val periodEnd = timeProvider.time + 60_000L
        val state = pixelState("webTelemetry_adwallDetection_day", mapOf("count" to 0), periodEnd = periodEnd)
        stubPixelStates(state)

        assertFalse(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))

        manager.checkPixels()

        assertTrue(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))
    }

    @Test
    fun `checkPixels does not reschedule timers when feature disabled`() {
        whenever(repository.getEventHubConfigJson()).thenReturn("""{"state": "disabled"}""")

        manager.checkPixels()

        assertFalse(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))
    }

    @Test
    fun `scheduled timer fires zero-count pixel without any web events`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        val savedState = captor.firstValue
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(savedState.paramsJson)["count"])

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(savedState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(savedState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `scheduled timer starts new cycle after firing`() {
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val initCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(initialState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        testScope.testScheduler.runCurrent()

        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        val newCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(newCaptor.capture())
        assertEquals(timeProvider.time, newCaptor.firstValue.periodStartMillis)
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(newCaptor.firstValue.paramsJson)["count"])
        assertTrue(manager.hasScheduledTimer("webTelemetry_adwallDetection_day"))
    }

    @Test
    fun `multi-pixel timers are scheduled independently with correct delays`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        whenever(repository.getEventHubConfigJson()).thenReturn(twoPixelConf)
        manager = RealEventHubPixelManager(repository, pixel, timeProvider, testScope, dispatcherProvider, foregroundState)

        timeProvider.time = 1000L
        whenever(repository.getPixelState("pixel_a")).thenReturn(null)
        whenever(repository.getPixelState("pixel_b")).thenReturn(null)
        manager.onConfigChanged()

        assertTrue(manager.hasScheduledTimer("pixel_a"))
        assertTrue(manager.hasScheduledTimer("pixel_b"))

        val savedStates = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedStates.capture())
        val stateA = savedStates.allValues.find { it.pixelName == "pixel_a" }!!
        val stateB = savedStates.allValues.find { it.pixelName == "pixel_b" }!!

        assertEquals(1000L + 60_000L, stateA.periodEndMillis)
        assertEquals(1000L + 120_000L, stateB.periodEndMillis)
    }

    // --- foreground-gated cycles ---

    @Test
    fun `timer fires while backgrounded - pixel enqueued but no new period started`() {
        foregroundState.isInForeground = false

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
        stubPixelStates(state)

        manager.checkPixels()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `timer fires while foregrounded - pixel enqueued and new period starts immediately`() {
        foregroundState.isInForeground = true

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
        stubPixelStates(state)

        manager.checkPixels()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `checkPixels on foreground starts new period after background fire`() {
        foregroundState.isInForeground = true

        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 50_000L

        manager.checkPixels()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_adwallDetection_day", captor.firstValue.pixelName)
        assertEquals(50_000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `scheduled timer does not start new period when backgrounded`() {
        foregroundState.isInForeground = true
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val initCaptor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(initialState)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        foregroundState.isInForeground = false

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_adwallDetection_day"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `full background-foreground lifecycle - fire in background, recover on foreground`() {
        foregroundState.isInForeground = false

        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = EventHubPixelStateEntity(
            pixelName = "webTelemetry_adwallDetection_day",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            paramsJson = """{"count": 7}""",
            configJson = dayPixelConfigJson,
        )
        stubPixelStates(state)

        manager.checkPixels()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        verify(repository).deletePixelState("webTelemetry_adwallDetection_day")
        verify(repository, never()).savePixelState(any())

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getEventHubConfigJson()).thenReturn(fullConfig)
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState("webTelemetry_adwallDetection_day")).thenReturn(null)

        foregroundState.isInForeground = true
        timeProvider.time = periodEnd + 5000L

        manager.checkPixels()

        val captor = argumentCaptor<EventHubPixelStateEntity>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_adwallDetection_day", captor.firstValue.pixelName)
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
        assertEquals(0, RealEventHubPixelManager.parseParamsJson(captor.firstValue.paramsJson)["count"])
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
            val config = EventHubConfigParser.parse(repository.getEventHubConfigJson())
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

    private fun stubPixelStates(vararg states: EventHubPixelStateEntity) {
        whenever(repository.getAllPixelStates()).thenReturn(states.toList())
        for (state in states) {
            whenever(repository.getPixelState(state.pixelName)).thenReturn(state)
        }
    }

    private class FakeTimeProvider : TimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
    }

    private class FakeAppForegroundStateProvider : AppForegroundStateProvider {
        override var isInForeground: Boolean = true
    }
}
