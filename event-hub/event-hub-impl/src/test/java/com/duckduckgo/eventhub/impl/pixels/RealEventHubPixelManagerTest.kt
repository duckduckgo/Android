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

package com.duckduckgo.eventhub.impl.pixels

import android.annotation.SuppressLint
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
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

@OptIn(ExperimentalCoroutinesApi::class)
class RealEventHubPixelManagerTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository: EventHubRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)

    private fun webEventData(type: String) = JSONObject().put("type", type)

    @SuppressLint("DenyListedApi")
    private fun configureFeature(enabled: Boolean = true, settings: String? = fullConfig) {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = enabled, settings = settings))
    }

    private lateinit var manager: RealEventHubPixelManager

    @SuppressLint("DenyListedApi")
    private fun createManager() {
        val savedState = eventHubFeature.self().getRawStoredState()
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = false))
        manager = RealEventHubPixelManager(
            repository, pixel, timeProvider, coroutineTestRule.testScope,
            UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler), eventHubFeature,
        )
        manager.onAppForegrounded()
        eventHubFeature.self().setRawStoredState(savedState ?: Toggle.State(enable = false))
    }

    private val dayPixelConfig: TelemetryPixelConfig by lazy {
        EventHubConfigParser.parseTelemetry(fullConfig).first { it.name == "webTelemetry_testPixel1" }
    }

    private val fullConfig = """
        {
            "telemetry": {
                "webTelemetry_testPixel1": {
                    "state": "enabled",
                    "trigger": { "period": { "days": 1 } },
                    "parameters": {
                        "count": {
                            "template": "counter",
                            "source": "test",
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
    """.trimIndent()

    @Before
    fun setup() {
        configureFeature()
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        createManager()
    }

    // --- handleWebEvent ---

    @Test
    fun `handleWebEvent increments matching counter`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 3))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(4, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent ignores events past periodEnd`() {
        timeProvider.time = 200_000L
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0), periodEnd = 100_000L)
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent ignores events exactly at periodEnd`() {
        val periodEnd = 100_000L
        timeProvider.time = periodEnd
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0), periodEnd = periodEnd)
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent accepts events one millisecond before periodEnd`() {
        val periodEnd = 100_000L
        timeProvider.time = periodEnd - 1
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0), periodEnd = periodEnd)
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository).savePixelState(any())
    }

    @Test
    fun `handleWebEvent can increment multiple times below max bucket`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        val captor = argumentCaptor<PixelState>()
        manager.handleWebEvent(webEventData("test"), "")

        verify(repository).savePixelState(captor.capture())
        assertEquals(1, captor.firstValue.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(captor.firstValue)

        manager.handleWebEvent(webEventData("test"), "")
        verify(repository).savePixelState(captor.capture())
        assertEquals(2, captor.secondValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent does not increment through max bucket`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 39))
        stubPixelStates(state)

        val captor = argumentCaptor<PixelState>()
        manager.handleWebEvent(webEventData("test"), "")

        verify(repository).savePixelState(captor.capture())
        assertEquals(40, captor.firstValue.params.mapValues { it.value.value }["count"])

        // re-stub so second call sees saved state (count=40, stopCounting=["count"])
        org.mockito.Mockito.reset(repository)

        manager.handleWebEvent(webEventData("test"), "")
        verify(repository, never()).savePixelState(captor.capture())
        // stopCounting prevents further changes
        assertEquals(40, captor.firstValue.params.mapValues { it.value.value }["count"])

        manager.handleWebEvent(webEventData("test"), "")
        verify(repository, never()).savePixelState(captor.capture())
        assertEquals(40, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent skips param with stopCounting set`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 50), stopCounting = setOf("count"))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent does not increment when already at max bucket`() {
        val singleBucketConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = singleBucketConfig)
        createManager()

        val state = pixelState("test", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("evt"), "")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        val params = captor.firstValue.params
        assertEquals(0, params["count"]?.value)
        assertTrue(params["count"]?.stopCounting == true)
    }

    @Test
    fun `handleWebEvent does not increment when count already in highest bucket`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 40))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        val params = captor.firstValue.params
        assertEquals(40, params["count"]?.value)
        assertTrue(params["count"]?.stopCounting == true)
    }

    @Test
    fun `handleWebEvent ignores unknown event type`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("unknownEvent"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `handleWebEvent ignores events when feature disabled`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)
        configureFeature(enabled = false)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository, never()).savePixelState(any())
    }

    // --- deduplication: multiple events on same page should count as one ---

    @Test
    fun `same page same source - second event is deduplicated`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, captor.firstValue.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(captor.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `same page multiple events - all after first are deduplicated`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(6, captor.firstValue.params.mapValues { it.value.value }["count"])

        for (i in 1..5) {
            org.mockito.Mockito.reset(repository)
            stubPixelStates(captor.firstValue)

            manager.handleWebEvent(webEventData("test"), "tab1")

            verify(repository, never()).savePixelState(any())
        }
    }

    @Test
    fun `navigation to new page resets dedup - event on new page increments`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.onNavigationStarted("tab1", "https://example.com/page1")
        manager.handleWebEvent(webEventData("test"), "tab1")

        val firstCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, firstCaptor.firstValue.params.mapValues { it.value.value }["count"])

        // Navigate — same tab, different URL
        org.mockito.Mockito.reset(repository)
        stubPixelStates(firstCaptor.firstValue)

        manager.onNavigationStarted("tab1", "https://example.com/page2")
        manager.handleWebEvent(webEventData("test"), "tab1")

        val secondCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, secondCaptor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `subframe event on same page is deduplicated`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, captor.firstValue.params.mapValues { it.value.value }["count"])

        // Subframe event — same tab + same URL = deduped
        org.mockito.Mockito.reset(repository)
        stubPixelStates(captor.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `different source types on same page are not deduplicated against each other`() {
        val twoSourceConfig = """
            {
                "telemetry": {
                    "test": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "testCount": {
                                "template": "counter",
                                "source": "test",
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
        """.trimIndent()
        configureFeature(settings = twoSourceConfig)
        createManager()

        val state = pixelState("test", mapOf("testCount" to 0, "trackerCount" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val firstCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(firstCaptor.capture())
        val paramsAfterTest = firstCaptor.firstValue.params.mapValues { it.value.value }
        assertEquals(1, paramsAfterTest["testCount"])
        assertEquals(0, paramsAfterTest["trackerCount"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent(webEventData("trackerBlocked"), "tab1")

        val secondCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(secondCaptor.capture())
        val paramsAfterBoth = secondCaptor.firstValue.params.mapValues { it.value.value }
        assertEquals(1, paramsAfterBoth["testCount"])
        assertEquals(1, paramsAfterBoth["trackerCount"])
    }

    @Test
    fun `dedup is per-pixel - same source and page deduped independently across pixels`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        configureFeature(settings = twoPixelConf)
        createManager()

        val stateA = pixelState("pixel_a", mapOf("count" to 0))
        val stateB = pixelState("pixel_b", mapOf("count" to 0))
        stubPixelStates(stateA, stateB)

        manager.handleWebEvent(webEventData("evt"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())
        val savedA = captor.allValues.find { it.pixelName == "pixel_a" }!!
        val savedB = captor.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(1, savedA.params.mapValues { it.value.value }["count"])
        assertEquals(1, savedB.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(savedA, savedB)

        manager.handleWebEvent(webEventData("evt"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `empty webViewId disables dedup`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "")

        val firstCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, firstCaptor.firstValue.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent(webEventData("test"), "")

        val secondCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, secondCaptor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `returning to same URL after navigating away counts as new visit`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.onNavigationStarted("tab1", "https://example.com/page1")
        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, first.firstValue.params.mapValues { it.value.value }["count"])

        // Navigate to page2
        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.onNavigationStarted("tab1", "https://example.com/page2")
        manager.handleWebEvent(webEventData("test"), "tab1")
        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, second.firstValue.params.mapValues { it.value.value }["count"])

        // Navigate back to page1
        org.mockito.Mockito.reset(repository)
        stubPixelStates(second.firstValue)

        manager.onNavigationStarted("tab1", "https://example.com/page1")
        manager.handleWebEvent(webEventData("test"), "tab1")
        val third = argumentCaptor<PixelState>()
        verify(repository).savePixelState(third.capture())
        assertEquals(3, third.firstValue.params.mapValues { it.value.value }["count"])
    }

    // --- onNavigationStarted: proactive dedup clearing ---

    @Test
    fun `onNavigationStarted clears dedup so returning to same URL is not deduplicated`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        // Event on page A
        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, first.firstValue.params.mapValues { it.value.value }["count"])

        // Navigate to page B (no event fires) — onNavigationStarted called by JsInjectorPlugin
        manager.onNavigationStarted("tab1", "https://example.com/pageB")

        // Navigate back to page A (no event fires) — onNavigationStarted called again
        manager.onNavigationStarted("tab1", "https://example.com/pageA")

        // Event on page A again — must NOT be deduplicated
        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, second.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `onNavigationStarted with same URL does not clear dedup`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())

        // onNavigationStarted with same URL (e.g. reload) — should not clear dedup
        manager.onNavigationStarted("tab1", "https://example.com/page1")

        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onNavigationStarted with empty webViewId is a no-op`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())

        // onNavigationStarted with empty webViewId — should not affect state
        manager.onNavigationStarted("", "https://example.com/page2")

        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        // Same tab, same URL — still deduped because onNavigationStarted was a no-op
        manager.handleWebEvent(webEventData("test"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onNavigationStarted only clears dedup for navigated tab`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        // Events on tab1 and tab2
        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())

        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab2")
        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())

        // Navigate tab1 to a different URL
        manager.onNavigationStarted("tab1", "https://example.com/page2")

        // Tab2 should still be deduped (onNavigationStarted only cleared tab1)
        org.mockito.Mockito.reset(repository)
        stubPixelStates(second.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab2")
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `returning to same URL is not deduplicated when intermediate page had different event type`() {
        val twoSourceConfig = """
            {
                "telemetry": {
                    "test": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "testCount": {
                                "template": "counter",
                                "source": "test",
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
        """.trimIndent()
        configureFeature(settings = twoSourceConfig)
        createManager()

        val state = pixelState("test", mapOf("testCount" to 0, "trackerCount" to 0))
        stubPixelStates(state)

        // "test" event on page1
        manager.onNavigationStarted("tab1", "https://example.com/page1")
        manager.handleWebEvent(webEventData("test"), "tab1")
        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, first.firstValue.params.mapValues { it.value.value }["testCount"])

        // Navigate to page2 — only "trackerBlocked" fires (not "test")
        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.onNavigationStarted("tab1", "https://example.com/page2")
        manager.handleWebEvent(webEventData("trackerBlocked"), "tab1")
        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())
        assertEquals(1, second.firstValue.params.mapValues { it.value.value }["trackerCount"])

        // Navigate back to page1 — "test" fires again; must NOT be deduplicated
        org.mockito.Mockito.reset(repository)
        stubPixelStates(second.firstValue)

        manager.onNavigationStarted("tab1", "https://example.com/page1")
        manager.handleWebEvent(webEventData("test"), "tab1")
        val third = argumentCaptor<PixelState>()
        verify(repository).savePixelState(third.capture())
        assertEquals(2, third.firstValue.params.mapValues { it.value.value }["testCount"])
    }

    @Test
    fun `different tabs count independently`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        // Tab 1 fires event
        manager.handleWebEvent(webEventData("test"), "tab1")
        val firstCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(firstCaptor.capture())
        assertEquals(1, firstCaptor.firstValue.params.mapValues { it.value.value }["count"])

        // Tab 2 fires same source — different tab, should count
        org.mockito.Mockito.reset(repository)
        stubPixelStates(firstCaptor.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab2")
        val secondCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(secondCaptor.capture())
        assertEquals(2, secondCaptor.firstValue.params.mapValues { it.value.value }["count"])

        // Tab 1 fires again — same tab, should dedup
        org.mockito.Mockito.reset(repository)
        stubPixelStates(secondCaptor.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab1")
        verify(repository, never()).savePixelState(any())
    }

    // --- checkPixels ---

    @Test
    fun `checkPixels fires pixel with bucketed count and attributionPeriod when period elapsed`() {
        val periodStart = 1769385600000L // 2026-01-26T00:00:00Z
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(15)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
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

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels fires pixel exactly at periodEnd`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels does not fire one millisecond before periodEnd`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd - 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels skips firing when no bucket matches`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val configWithGap = """
            {
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
        """.trimIndent()
        configureFeature(settings = configWithGap)
        createManager()

        val testPixelConfig = EventHubConfigParser.parseTelemetry(configWithGap).first()
        val state = PixelState(
            pixelName = "test",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(2)),
            config = testPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels resets state and starts new period after firing`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(repository).deletePixelState("webTelemetry_testPixel1")
        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
        assertEquals(0, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `checkPixels still fires pixel that is disabled in current config`() {
        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val originalConfig = configWithBuckets(*originalBuckets)
        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()

        val state = PixelState(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(2)),
            config = originalPixelConfig,
        )
        stubPixelStates(state)

        // Current config has the pixel DISABLED
        val disabledConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = disabledConfig)

        manager.onAppForegrounded()

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

        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        // Pixel disabled in current config
        val disabledPixelConfig = """
            {
                "telemetry": {
                    "webTelemetry_testPixel1": {
                        "state": "disabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "count": {
                                "template": "counter",
                                "source": "test",
                                "buckets": { "0": {"gte": 0, "lt": 1}, "1-2": {"gte": 1, "lt": 3}, "3-5": {"gte": 3, "lt": 6}, "6-10": {"gte": 6, "lt": 11}, "11-20": {"gte": 11, "lt": 21}, "21-39": {"gte": 21, "lt": 40}, "40+": {"gte": 40} }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        configureFeature(settings = disabledPixelConfig)

        manager.onAppForegrounded()

        // Fires the pixel
        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        // Deletes old state
        verify(repository).deletePixelState("webTelemetry_testPixel1")
        // Does NOT re-register (pixel disabled in current config)
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels does not fire any telemetry when feature disabled`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5), periodStart = 1000L, periodEnd = 2000L)
        stubPixelStates(state)
        timeProvider.time = 3000L

        configureFeature(enabled = false)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    // --- onConfigChanged ---

    @Test
    fun `onConfigChanged initialises new pixels`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 5000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_testPixel1", captor.firstValue.pixelName)
        assertEquals(5000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `onConfigChanged deletes all when feature disabled`() {
        configureFeature(enabled = false)

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    @Test
    fun `onConfigChanged clears dedup state when feature disabled`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")
        verify(repository).savePixelState(any())

        // Disable feature — should clear dedup state
        configureFeature(enabled = false)
        manager.onConfigChanged()

        // Re-enable and re-create state
        org.mockito.Mockito.reset(repository)
        configureFeature()
        val freshState = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(freshState)

        // Same tab + same source — must NOT be deduplicated (dedup was cleared)
        manager.handleWebEvent(webEventData("test"), "tab1")
        verify(repository).savePixelState(any())
    }

    @Test
    fun `onConfigChanged does not re-register existing pixel`() {
        val existingState = pixelState("webTelemetry_testPixel1", mapOf("count" to 3))
        stubPixelStates(existingState)

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged does not register disabled pixel`() {
        val disabledPixelConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = disabledPixelConfig)
        whenever(repository.getPixelState("test_pixel")).thenReturn(null)
        createManager()

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged treats absent feature as disabled`() {
        configureFeature(enabled = false)

        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    @Test
    fun `onConfigChanged with null settings does not register any pixels`() {
        configureFeature(settings = null)

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged does not duplicate period when pixel already exists`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5))
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(state)

        manager.onConfigChanged()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged and checkPixels do not both start period for same pixel`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository, times(1)).savePixelState(captor.capture())

        org.mockito.Mockito.reset(repository)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(captor.firstValue))
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(captor.firstValue)

        manager.onAppForegrounded()

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `onConfigChanged clears state and timers atomically when feature disabled`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()
        verify(repository).savePixelState(any())

        configureFeature(enabled = false)
        manager.onConfigChanged()

        verify(repository).deleteAllPixelStates()
    }

    // --- config isolation: live config changes must not affect running pixel lifecycle ---

    private fun configWithBuckets(vararg buckets: Pair<String, String>): String {
        val bucketEntries = buckets.joinToString(",") { (name, body) -> "\"$name\": $body" }
        return """
            {
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
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        // count=4 is in "0-4" with original buckets, would be in "3+" with changed buckets
        val state = pixelState("test_pixel", mapOf("count" to 4), configJson = storedConfigJson)
        stubPixelStates(state)

        // Change live config to different buckets
        val changedConfig = configWithBuckets(*changedBuckets)
        configureFeature(settings = changedConfig)

        manager.handleWebEvent(webEventData("evt"), "")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        val savedCount = captor.firstValue.params.mapValues { it.value.value }["count"]
        // Should increment to 5 — original config has "5+" at gte=5 so shouldStopCounting is false at 4
        assertEquals(5, savedCount)
    }

    @Test
    fun `handleWebEvent uses stored config source, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        stubPixelStates(state)

        // Change live config to use a different source
        val changedSourceConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = changedSourceConfig)

        // Event matches stored source ("evt"), not live source ("different_source")
        manager.handleWebEvent(webEventData("evt"), "")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent ignores event matching only live config source`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 0), configJson = storedConfigJson)
        stubPixelStates(state)

        // Change live config to use a different source
        val changedSourceConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = changedSourceConfig)

        // "new_source" matches live config but NOT stored config — should be ignored
        manager.handleWebEvent(webEventData("new_source"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels fires pixel using stored config buckets, not live config`() {
        val originalConfig = configWithBuckets(*originalBuckets)
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()

        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        // count=4 → "0-4" with original buckets, but "3+" with changed buckets
        val state = PixelState(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(4)),
            config = originalPixelConfig,
        )
        stubPixelStates(state)

        // Change live config to different buckets before firing
        val changedConfig = configWithBuckets(*changedBuckets)
        configureFeature(settings = changedConfig)

        manager.onAppForegrounded()

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
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()

        val periodStart = 120_000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(1)),
            config = originalPixelConfig,
        )
        stubPixelStates(state)

        // Change live config to a different period (1 hour instead of 120s)
        val changedPeriodConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = changedPeriodConfig)

        manager.onAppForegrounded()

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
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()

        val periodStart = 1000L
        val periodEnd = periodStart + 120_000L
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "test_pixel",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(1)),
            config = originalPixelConfig,
        )
        stubPixelStates(state)

        // Change live config to 1 hour period before firing
        val changedPeriodConfig = """
            {
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
        """.trimIndent()
        configureFeature(settings = changedPeriodConfig)

        manager.onAppForegrounded()

        // The NEW period should use the latest config (1 hour = 3600s = 3600000ms)
        verify(repository).deletePixelState("test_pixel")
        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())

        val newState = captor.firstValue
        val expectedPeriodMillis = 3600L * 1000
        assertEquals(timeProvider.time + expectedPeriodMillis, newState.periodEndMillis)

        // And the stored config in the new period should reflect the latest config
        assertEquals(3600L, newState.config.trigger.period.periodSeconds)
    }

    @Test
    fun `onConfigChanged stores config snapshot in new pixel state`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 5000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())

        val storedConfig = captor.firstValue.config
        assertEquals("test", storedConfig.parameters["count"]!!.source)
        assertEquals(86400L, storedConfig.trigger.period.periodSeconds)
        assertEquals(7, storedConfig.parameters["count"]!!.buckets.size)
    }

    // --- multi-pixel config lifecycle ---

    private fun twoPixelConfig(periodSecondsA: Int, periodSecondsB: Int, bucketDef: String): String {
        return """
            {
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
        """.trimIndent()
    }

    @Test
    fun `multi-pixel lifecycle - each pixel uses its own config snapshot independently`() {
        val buckets = """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}"""

        // Step 1: config [1] loads — both pixels registered with 60s/120s periods
        val config1 = twoPixelConfig(60, 120, buckets)
        configureFeature(settings = config1)
        createManager()

        timeProvider.time = 10_000L
        whenever(repository.getPixelState("pixel_a")).thenReturn(null)
        whenever(repository.getPixelState("pixel_b")).thenReturn(null)
        manager.onConfigChanged()

        val savedStates = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedStates.capture())
        val stateA1 = savedStates.allValues.find { it.pixelName == "pixel_a" }!!
        val stateB1 = savedStates.allValues.find { it.pixelName == "pixel_b" }!!

        // Both use config [1]
        assertEquals(60L, stateA1.config.trigger.period.periodSeconds)
        assertEquals(120L, stateB1.config.trigger.period.periodSeconds)

        // Step 2: config [2] loads — pixels A and B still use config [1]
        org.mockito.Mockito.reset(repository, pixel)
        val config2 = twoPixelConfig(90, 180, buckets)
        configureFeature(settings = config2)

        // Simulate accumulated state on both pixels
        val stateA1WithCount = stateA1.copy(params = mapOf("count" to ParamState(2)))
        val stateB1WithCount = stateB1.copy(params = mapOf("count" to ParamState(3)))
        stubPixelStates(stateA1WithCount, stateB1WithCount)
        manager.onConfigChanged()
        org.mockito.Mockito.reset(repository, pixel)
        stubPixelStates(stateA1WithCount, stateB1WithCount)

        // Events still use config [1] stored in state
        manager.handleWebEvent(webEventData("evt"), "")

        val savedAfterEvent = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedAfterEvent.capture())
        val updatedA = savedAfterEvent.allValues.find { it.pixelName == "pixel_a" }!!
        val updatedB = savedAfterEvent.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(3, updatedA.params["count"]?.value)
        assertEquals(4, updatedB.params["count"]?.value)
        // Stored configs unchanged — still config [1]
        assertEquals(stateA1.config, updatedA.config)
        assertEquals(stateB1.config, updatedB.config)

        // Step 3: pixel A fires — new cycle uses config [2], pixel B still on config [1]
        org.mockito.Mockito.reset(repository, pixel)

        timeProvider.time = stateA1.periodEndMillis + 1
        stubPixelStates(updatedA, updatedB)

        manager.onAppForegrounded()

        verify(repository).deletePixelState("pixel_a")
        verify(repository, never()).deletePixelState("pixel_b")

        val savedAfterFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(savedAfterFire.capture())
        val newStateA2 = savedAfterFire.firstValue
        assertEquals("pixel_a", newStateA2.pixelName)

        // New pixel A cycle uses config [2] (90s period)
        assertEquals(90L, newStateA2.config.trigger.period.periodSeconds)

        // Step 4: config [3] loads — pixel A on [2], pixel B still on [1]
        org.mockito.Mockito.reset(repository, pixel)
        val config3 = twoPixelConfig(45, 300, buckets)
        configureFeature(settings = config3)
        stubPixelStates(newStateA2, updatedB)
        manager.onConfigChanged()
        org.mockito.Mockito.reset(repository, pixel)
        stubPixelStates(newStateA2, updatedB)

        manager.handleWebEvent(webEventData("evt"), "")

        val savedAfterConfig3 = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedAfterConfig3.capture())
        val aAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_a" }!!
        val bAfter3 = savedAfterConfig3.allValues.find { it.pixelName == "pixel_b" }!!
        // A still uses config [2], B still uses config [1]
        assertEquals(newStateA2.config, aAfter3.config)
        assertEquals(stateB1.config, bAfter3.config)

        // Step 5: pixel B fires — new cycle uses config [3]
        org.mockito.Mockito.reset(repository, pixel)

        timeProvider.time = stateB1.periodEndMillis + 1
        stubPixelStates(aAfter3, bAfter3)

        manager.onAppForegrounded()

        verify(repository).deletePixelState("pixel_b")
        val savedAfterBFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(savedAfterBFire.capture())
        val newStateB3 = savedAfterBFire.firstValue
        assertEquals("pixel_b", newStateB3.pixelName)

        // New pixel B cycle uses config [3] (300s period)
        assertEquals(300L, newStateB3.config.trigger.period.periodSeconds)

        // Pixel A is still on config [2]
        assertEquals(90L, aAfter3.config.trigger.period.periodSeconds)
    }

    // --- robustness ---

    @Test
    fun `handleWebEvent skips pixel with malformed stored configJson`() {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState(any())).thenReturn(null)

        manager.handleWebEvent(webEventData("test"), "")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `checkPixels skips pixel with malformed stored configJson`() {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState(any())).thenReturn(null)
        timeProvider.time = 3000L

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `config round-trip preserves all fields`() {
        val configs = EventHubConfigParser.parseTelemetry(fullConfig)
        val pixelConfig = configs.first()

        val serialized = EventHubConfigParser.serializePixelConfig(pixelConfig)!!
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
        val result = RealEventHubRepository.parseParamsJson("not json")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseParamsJson preserves stopCounting flag`() {
        val json = """{"count": {"value": 5, "stopCounting": true}, "other": {"value": 2}}"""
        val result = RealEventHubRepository.parseParamsJson(json)
        assertEquals(5, result["count"]?.value)
        assertTrue(result["count"]?.stopCounting == true)
        assertEquals(2, result["other"]?.value)
        assertFalse(result["other"]?.stopCounting == true)
    }

    // --- privacy protections independence: eventHub must NOT be disabled by per-site protections ---

    @Test
    fun `handleWebEvent processes events regardless of per-site protection state`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(1, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent only checks remote config state, not privacy protections`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(6, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `handleWebEvent accumulates across sites with different protection levels`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, first.firstValue.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        manager.handleWebEvent(webEventData("test"), "tab2")

        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, second.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `checkPixels fires telemetry accumulated from both protected and unprotected sites`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(8)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = eq(mapOf("count" to "6-10", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `onConfigChanged initialises pixels regardless of privacy protection state`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 42_000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_testPixel1", captor.firstValue.pixelName)
        assertEquals(42_000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `handleWebEvent only rejects events when remote config state is disabled`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        configureFeature(enabled = false)
        manager.handleWebEvent(webEventData("test"), "tab1")
        verify(repository, never()).savePixelState(any())

        org.mockito.Mockito.reset(repository)
        stubPixelStates(state)
        configureFeature()
        manager.onConfigChanged()

        manager.handleWebEvent(webEventData("test"), "tab1")
        verify(repository).savePixelState(any())
    }

    // --- fire button state persistence: eventHub state must survive data clearing ---

    @Test
    fun `pixel state counters persist across fire button - accumulated counts carry forward`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5))
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val preFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(6, preFire.firstValue.params.mapValues { it.value.value }["count"])

        // Simulate fire button: tabs cleared, new tab created — repository state untouched
        org.mockito.Mockito.reset(repository)
        stubPixelStates(preFire.firstValue)

        manager.handleWebEvent(webEventData("test"), "newTab1")

        val postFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(postFire.capture())
        assertEquals(7, postFire.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `period timing is unaffected by fire button`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 5000L

        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 3), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        manager.handleWebEvent(webEventData("test"), "tab1")

        val preFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(periodStart, preFire.firstValue.periodStartMillis)
        assertEquals(periodEnd, preFire.firstValue.periodEndMillis)

        // Simulate fire button: new tabs, but repository state (including period) persists
        org.mockito.Mockito.reset(repository)
        stubPixelStates(preFire.firstValue)

        timeProvider.time = 10_000L
        manager.handleWebEvent(webEventData("test"), "newTab")

        val postFire = argumentCaptor<PixelState>()
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
        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(12)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        manager.onAppForegrounded()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = eq(mapOf("count" to "11-20", "attributionPeriod" to expectedAttribution)),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `stopCounting flags persist across fire button`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 50), stopCounting = setOf("count"))
        stubPixelStates(state)

        // After fire button, new tab sends same event type — stopCounting still applied
        manager.handleWebEvent(webEventData("test"), "newTab")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `config state persists across fire button`() {
        // Pixel was registered with specific config snapshot — fire button must not erase it
        val originalConfig = configWithBuckets(*originalBuckets)
        configureFeature(settings = originalConfig)
        createManager()

        val originalPixelConfig = EventHubConfigParser.parseTelemetry(originalConfig).first()
        val storedConfigJson = EventHubConfigParser.serializePixelConfig(originalPixelConfig)

        val state = pixelState("test_pixel", mapOf("count" to 2), configJson = storedConfigJson)
        stubPixelStates(state)

        // Simulate fire button: new tab, but the stored config snapshot is preserved
        manager.handleWebEvent(webEventData("evt"), "newTab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(originalPixelConfig, captor.firstValue.config)
        assertEquals(3, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `multi-pixel state all persists across fire button`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        configureFeature(settings = twoPixelConf)
        createManager()

        val stateA = pixelState("pixel_a", mapOf("count" to 2))
        val stateB = pixelState("pixel_b", mapOf("count" to 3))
        stubPixelStates(stateA, stateB)

        // Fire button happened — new tabs, same repository state
        manager.handleWebEvent(webEventData("evt"), "newTab1")

        val captor = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(captor.capture())
        val savedA = captor.allValues.find { it.pixelName == "pixel_a" }!!
        val savedB = captor.allValues.find { it.pixelName == "pixel_b" }!!
        assertEquals(3, savedA.params.mapValues { it.value.value }["count"])
        assertEquals(4, savedB.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `dedup state allows new tab events after fire button`() {
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0))
        stubPixelStates(state)

        // Event on tab1 before fire button
        manager.handleWebEvent(webEventData("test"), "tab1")

        val first = argumentCaptor<PixelState>()
        verify(repository).savePixelState(first.capture())
        assertEquals(1, first.firstValue.params.mapValues { it.value.value }["count"])

        // Fire button: tab1 destroyed, new tab created with different webViewId
        org.mockito.Mockito.reset(repository)
        stubPixelStates(first.firstValue)

        // Same URL in new tab — different webViewId means not deduped
        manager.handleWebEvent(webEventData("test"), "newTab1")

        val second = argumentCaptor<PixelState>()
        verify(repository).savePixelState(second.capture())
        assertEquals(2, second.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `full lifecycle across fire button - accumulate, fire button, accumulate, check`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 5000L

        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        // Pre-fire: 3 events on different pages
        for (i in 1..3) {
            org.mockito.Mockito.reset(repository)
            val currentState = if (i == 1) {
                state
            } else {
                pixelState("webTelemetry_testPixel1", mapOf("count" to i - 1), periodStart = periodStart, periodEnd = periodEnd)
            }
            stubPixelStates(currentState)
            manager.onNavigationStarted("tab1", "https://example.com/page$i")
            manager.handleWebEvent(webEventData("test"), "tab1")
        }

        val preFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(preFire.capture())
        assertEquals(3, preFire.firstValue.params.mapValues { it.value.value }["count"])

        // Fire button — new tabs
        // Post-fire: 2 more events
        for (i in 1..2) {
            org.mockito.Mockito.reset(repository)
            val currentState = pixelState(
                "webTelemetry_testPixel1",
                mapOf("count" to 2 + i),
                periodStart = periodStart,
                periodEnd = periodEnd,
            )
            stubPixelStates(currentState)
            manager.onNavigationStarted("newTab1", "https://other.com/page$i")
            manager.handleWebEvent(webEventData("test"), "newTab1")
        }

        val postFire = argumentCaptor<PixelState>()
        verify(repository).savePixelState(postFire.capture())
        assertEquals(5, postFire.firstValue.params.mapValues { it.value.value }["count"])

        // Period elapses — checkPixels fires the total
        org.mockito.Mockito.reset(repository, pixel)
        timeProvider.time = periodEnd + 1
        val finalState = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(finalState)

        manager.onAppForegrounded()

        val expectedAttribution = RealEventHubPixelManager.calculateAttributionPeriod(
            periodStart,
            TelemetryPeriodConfig(days = 1),
        ).toString()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
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

    // --- scheduled firing (centralized scheduler) ---

    @Test
    fun `startNewPeriod schedules a timer that fires the pixel`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        val savedState = captor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(savedState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(savedState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `onConfigChanged cancels all timers when feature disabled - no pixel fires after delay`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        configureFeature(settings = twoPixelConf)
        createManager()

        timeProvider.time = 1000L
        whenever(repository.getPixelState("pixel_a")).thenReturn(null)
        whenever(repository.getPixelState("pixel_b")).thenReturn(null)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(repository, times(2)).savePixelState(any())

        configureFeature(enabled = false)
        manager.onConfigChanged()

        org.mockito.Mockito.reset(pixel)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(121_000L)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels fires expired pixel and starts new period`() {
        val periodStart = 1000L
        val periodEnd = periodStart + 60_000L
        timeProvider.time = periodEnd + 1

        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 3), periodStart = periodStart, periodEnd = periodEnd)
        stubPixelStates(state)

        manager.onAppForegrounded()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        verify(repository).savePixelState(any())
    }

    @Test
    fun `checkPixels schedules timer for active pixel that fires after delay`() {
        val periodEnd = timeProvider.time + 60_000L
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 5), periodEnd = periodEnd)
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())

        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(state, null)
        timeProvider.time = periodEnd + 1
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(60_001L)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels does not fire pixel when period has not elapsed`() {
        val periodEnd = timeProvider.time + 60_000L
        val state = pixelState("webTelemetry_testPixel1", mapOf("count" to 0), periodEnd = periodEnd)
        stubPixelStates(state)

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `checkPixels does not reschedule timers when feature disabled`() {
        configureFeature(enabled = false)

        manager.onAppForegrounded()

        verify(repository, never()).getAllPixelStates()
    }

    @Test
    fun `scheduled timer fires zero-count pixel without any web events`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        val savedState = captor.firstValue
        assertEquals(0, savedState.params.mapValues { it.value.value }["count"])

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(savedState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(savedState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
    }

    @Test
    fun `scheduled timer starts new cycle after firing`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(repository).deletePixelState("webTelemetry_testPixel1")
        val newCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(newCaptor.capture())
        assertEquals(timeProvider.time, newCaptor.firstValue.periodStartMillis)
        assertEquals(0, newCaptor.firstValue.params.mapValues { it.value.value }["count"])
    }

    @Test
    fun `multi-pixel timers are scheduled independently with correct delays`() {
        val twoPixelConf = twoPixelConfig(60, 120, """"0-4": {"gte": 0, "lt": 5}, "5+": {"gte": 5}""")
        configureFeature(settings = twoPixelConf)
        createManager()

        timeProvider.time = 1000L
        whenever(repository.getPixelState("pixel_a")).thenReturn(null)
        whenever(repository.getPixelState("pixel_b")).thenReturn(null)
        manager.onConfigChanged()

        val savedStates = argumentCaptor<PixelState>()
        verify(repository, org.mockito.kotlin.times(2)).savePixelState(savedStates.capture())
        val stateA = savedStates.allValues.find { it.pixelName == "pixel_a" }!!
        val stateB = savedStates.allValues.find { it.pixelName == "pixel_b" }!!

        assertEquals(1000L + 60_000L, stateA.periodEndMillis)
        assertEquals(1000L + 120_000L, stateB.periodEndMillis)
    }

    @Test
    fun `timer does not fire pixel when feature is disabled before timer expires`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        configureFeature(enabled = false)

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `timer does not fire when pixel state is missing at expiry`() {
        timeProvider.time = 1000L
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)

        coroutineTestRule.testScope.testScheduler.advanceTimeBy(1001L)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `new period can be started after feature disable and re-enable`() {
        timeProvider.time = 1000L
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()
        verify(repository).savePixelState(any())

        configureFeature(enabled = false)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()
        verify(repository).deleteAllPixelStates()

        org.mockito.Mockito.reset(repository)
        configureFeature()
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)

        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(repository).savePixelState(any())
    }

    // --- race condition: timer expiry vs checkPixels ---

    @Test
    fun `checkPixels cancels timer so expired coroutine does not double-fire`() {
        timeProvider.time = 1000L

        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        timeProvider.time = initialState.periodEndMillis + 1

        manager.onAppForegrounded()
        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())

        // checkPixels fired the expired pixel and started a new period;
        // update mock so the centralized scheduler sees the fresh (unexpired) state
        val newCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(newCaptor.capture())
        whenever(repository.getAllPixelStates()).thenReturn(listOf(newCaptor.firstValue))

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        // Still only 1 fire — the old scheduler was cancelled by the centralized check
        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `stale timer coroutine does not interfere with newly scheduled timer`() {
        timeProvider.time = 1000L

        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState, null)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        timeProvider.time = initialState.periodEndMillis + 1

        // checkPixels fires the pixel, which starts a new period with a new timer
        manager.onAppForegrounded()
        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())

        // Run any pending continuations — the stale coroutine must not fire again
        coroutineTestRule.testScope.testScheduler.runCurrent()

        // Still only 1 fire
        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())
        // New period was saved (proves the new timer was created)
        verify(repository).savePixelState(any())
    }

    @Test
    fun `disabling feature before timer fires prevents pixel fire`() {
        timeProvider.time = 1000L
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(repository).savePixelState(any())

        // Disable feature before timer fires
        configureFeature(enabled = false)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        org.mockito.Mockito.reset(pixel)
        val periodMillis = TimeUnit.DAYS.toMillis(1)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `stale timer does not fire newly created period state`() {
        timeProvider.time = 1000L
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        timeProvider.time = initialState.periodEndMillis + 1
        manager.onAppForegrounded()

        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())

        // Update mock so the centralized scheduler sees the new (unexpired) state
        val newCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(newCaptor.capture())
        whenever(repository.getAllPixelStates()).thenReturn(listOf(newCaptor.firstValue))

        val periodMillis = java.util.concurrent.TimeUnit.DAYS.toMillis(1)
        org.mockito.Mockito.reset(pixel)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `stale timer from previous period does not fire after checkPixels restarts period`() {
        timeProvider.time = 1000L
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.runCurrent()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        timeProvider.time = initialState.periodEndMillis + 1
        manager.onAppForegrounded()
        coroutineTestRule.testScope.testScheduler.runCurrent()
        verify(pixel, times(1)).enqueueFire(any<String>(), any(), any(), any())

        // Update mock so the centralized scheduler sees the new (unexpired) state
        val newCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(newCaptor.capture())
        whenever(repository.getAllPixelStates()).thenReturn(listOf(newCaptor.firstValue))

        org.mockito.Mockito.reset(pixel)
        val periodMillis = TimeUnit.DAYS.toMillis(1)
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    // --- foreground-gated cycles ---

    @Test
    fun `timer fires while backgrounded - pixel enqueued and new period starts on foreground`() {
        manager.onAppBackgrounded()

        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(state, null)

        manager.onAppForegrounded()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
        verify(repository).deletePixelState("webTelemetry_testPixel1")
        verify(repository).savePixelState(any())
    }

    @Test
    fun `timer fires while foregrounded - pixel enqueued and new period starts immediately`() {
        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(5)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(state, null)

        manager.onAppForegrounded()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        verify(repository).deletePixelState("webTelemetry_testPixel1")
        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `onAppForegrounded starts new period when no pixel state exists`() {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 50_000L

        manager.onAppForegrounded()

        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_testPixel1", captor.firstValue.pixelName)
        assertEquals(50_000L, captor.firstValue.periodStartMillis)
    }

    @Test
    fun `scheduled timer does not start new period when backgrounded`() {
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = 1000L

        manager.onConfigChanged()

        val initCaptor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(initCaptor.capture())
        val initialState = initCaptor.firstValue

        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(initialState)
        whenever(repository.getAllPixelStates()).thenReturn(listOf(initialState))

        manager.onAppBackgrounded()

        val periodMillis = TimeUnit.DAYS.toMillis(1)
        timeProvider.time = 1000L + periodMillis
        coroutineTestRule.testScope.testScheduler.advanceTimeBy(periodMillis + 1)
        coroutineTestRule.testScope.testScheduler.runCurrent()

        verify(pixel).enqueueFire(
            pixelName = eq("webTelemetry_testPixel1"),
            parameters = any(),
            encodedParameters = eq(emptyMap()),
            type = eq(Count),
        )
        verify(repository).deletePixelState("webTelemetry_testPixel1")
        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `full background-foreground lifecycle - fire in background, recover on foreground`() {
        // Phase 1: app goes to background with an expired pixel
        manager.onAppBackgrounded()

        val periodStart = 1000L
        val periodEnd = periodStart + TimeUnit.DAYS.toMillis(1)
        timeProvider.time = periodEnd + 1

        val state = PixelState(
            pixelName = "webTelemetry_testPixel1",
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            params = mapOf("count" to ParamState(7)),
            config = dayPixelConfig,
        )
        stubPixelStates(state)

        // Scheduled timer fires while backgrounded — pixel enqueued, no new period
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(state)
        manager.onConfigChanged()
        coroutineTestRule.testScope.testScheduler.advanceUntilIdle()

        verify(pixel).enqueueFire(any<String>(), any(), any(), any())
        verify(repository).deletePixelState("webTelemetry_testPixel1")
        verify(repository, never()).savePixelState(any())

        // Phase 2: app returns to foreground — new period must start
        org.mockito.Mockito.reset(repository, pixel)
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        whenever(repository.getPixelState("webTelemetry_testPixel1")).thenReturn(null)
        timeProvider.time = periodEnd + 5000L

        manager.onAppForegrounded()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
        val captor = argumentCaptor<PixelState>()
        verify(repository).savePixelState(captor.capture())
        assertEquals("webTelemetry_testPixel1", captor.firstValue.pixelName)
        assertEquals(timeProvider.time, captor.firstValue.periodStartMillis)
        assertEquals(0, captor.firstValue.params.mapValues { it.value.value }["count"])
    }

    // --- helpers ---

    private fun pixelState(
        name: String,
        params: Map<String, Int>,
        periodEnd: Long = Long.MAX_VALUE,
        periodStart: Long = 1000L,
        stopCounting: Set<String> = emptySet(),
        configJson: String? = null,
    ): PixelState {
        val config = if (configJson != null) {
            EventHubConfigParser.parseSinglePixelConfig(name, configJson)!!
        } else {
            val settingsJson = eventHubFeature.self().getSettings() ?: error("no settings")
            val configs = EventHubConfigParser.parseTelemetry(settingsJson)
            configs.find { it.name == name } ?: error("no config for $name")
        }
        val paramStates = params.mapValues { (key, value) ->
            ParamState(value = value, stopCounting = key in stopCounting)
        }
        return PixelState(
            pixelName = name,
            periodStartMillis = periodStart,
            periodEndMillis = periodEnd,
            config = config,
            params = paramStates,
        )
    }

    private fun stubPixelStates(vararg states: PixelState) {
        whenever(repository.getAllPixelStates()).thenReturn(states.toList())
        for (state in states) {
            whenever(repository.getPixelState(state.pixelName)).thenReturn(state)
        }
    }

    private class FakeCurrentTimeProvider : CurrentTimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
        override fun elapsedRealtime(): Long = time
        override fun localDateTimeNow(): java.time.LocalDateTime = java.time.LocalDateTime.now()
    }
}
