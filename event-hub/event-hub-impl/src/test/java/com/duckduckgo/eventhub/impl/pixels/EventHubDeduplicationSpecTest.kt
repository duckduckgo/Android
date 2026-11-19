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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.util.concurrent.TimeUnit

/**
 * Android's coverage of the cross-platform de-duplication specification
 * (`docs/event-hub/tests/deduplication.md` in `duckduckgo/ddg-workflow`). Each case ID from that document leads the test
 * name so coverage is greppable and a failure names the spec case. Events go through [RealEventHubPixelManager] and are
 * observed through the spec's immediate pixels, one per delivered event, and its counter pixel, fired when its period ends.
 *
 * D-NAT-1 is not applicable: Android has no native (tab-less) event stream.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubDeduplicationSpecTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository = InMemoryEventHubRepository()
    private val pixel = RecordingPixel()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)

    private lateinit var manager: RealEventHubPixelManager

    @SuppressLint("DenyListedApi")
    @Before
    fun setup() {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = true, settings = FIXTURE))
        manager = RealEventHubPixelManager(
            repository,
            pixel,
            timeProvider,
            coroutineTestRule.testScope,
            UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler),
            eventHubFeature,
            metricsHandler = mock(),
        )
        timeProvider.time = PERIOD_START
        manager.onAppForegrounded()
    }

    @Test
    fun `D-DEL-1 repeated occurrences on one page are delivered once`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        repeat(3) { probe(TAB_1, reason("overlay")) }

        assertDelivered(count = "1", probeImmediate("overlay"))
    }

    @Test
    fun `D-DEL-2 the payload is part of the key`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        probe(TAB_1, reason("redirect"))

        assertDelivered(count = "2", probeImmediate("overlay"), probeImmediate("redirect"))
    }

    @Test
    fun `D-DEL-3 event types de-duplicate independently`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        event("otherDetected", TAB_1, reason("overlay"))
        probe(TAB_1, reason("overlay"))

        assertDelivered(count = "1", probeImmediate("overlay"), RecordedPixel("dedupOther_immediate", mapOf("reason" to "\"overlay\"")))
    }

    @Test
    fun `D-DEL-4 every handler observes the same single delivery`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        probe(TAB_1, reason("overlay"))

        assertDelivered(count = "1", probeImmediate("overlay"))
    }

    // The JVM org.json keeps members in a HashMap, so both payloads iterate in the same order here. Android's keeps insertion
    // order, which is why keys are sorted; CanonicalJsonTest checks that sorting directly.
    @Test
    fun `D-DEL-5 payload member order does not change the key`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, JSONObject("""{"reason": "overlay", "stage": 1}"""))
        probe(TAB_1, JSONObject("""{"stage": 1, "reason": "overlay"}"""))

        assertDelivered(count = "1", probeImmediate("overlay"))
    }

    @Test
    fun `D-DEL-6 an omitted payload and an empty payload are one key`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, data = null)
        probe(TAB_1, JSONObject())

        assertDelivered(count = "1", probeImmediate(reason = null))
    }

    @Test
    fun `D-NAV-1 navigating to a different URL makes the next occurrence deliverable`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        manager.onNavigationStarted(TAB_1, URL_B)
        probe(TAB_1, reason("overlay"))
        manager.onNavigationStarted(TAB_1, URL_C)
        probe(TAB_1, reason("overlay"))

        assertDelivered(count = "3+", probeImmediate("overlay"), probeImmediate("overlay"), probeImmediate("overlay"))
    }

    @Test
    fun `D-NAV-2 a same-URL reload does not start a new page`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))

        assertDelivered(count = "1", probeImmediate("overlay"))
    }

    @Test
    fun `D-NAV-3 returning to a previously seen URL delivers again`() {
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))
        manager.onNavigationStarted(TAB_1, URL_B)
        manager.onNavigationStarted(TAB_1, URL_A)
        probe(TAB_1, reason("overlay"))

        assertDelivered(count = "2", probeImmediate("overlay"), probeImmediate("overlay"))
    }

    @Test
    fun `D-NAV-4 separate tabs are independent`() {
        listOf(TAB_1, TAB_2, TAB_3).forEach { tab ->
            manager.onNavigationStarted(tab, URL_A)
            probe(tab, reason("overlay"))
        }

        assertDelivered(count = "3+", probeImmediate("overlay"), probeImmediate("overlay"), probeImmediate("overlay"))
    }

    private fun probe(webViewId: String, data: Any?) = event("probeDetected", webViewId, data)

    private fun event(type: String, webViewId: String, data: Any?) {
        val message = JSONObject().put("type", type)
        if (data != null) message.put("data", data)
        manager.handleWebEvent(message, webViewId)
    }

    private fun reason(value: String) = JSONObject().put("reason", value)

    private fun probeImmediate(reason: String?) =
        RecordedPixel("dedupProbe_immediate", listOfNotNull(reason?.let { "reason" to "\"$it\"" }).toMap())

    /** Ends the counter's period and checks the complete set of requested pixels: [immediates] and the counter's [count]. */
    private fun assertDelivered(count: String, vararg immediates: RecordedPixel) {
        timeProvider.time = PERIOD_START + TimeUnit.DAYS.toMillis(1)
        manager.onAppForegrounded()

        val expected = immediates.toList() + RecordedPixel("dedupProbe_day", mapOf("count" to count))
        val requested = pixel.requests.map { RecordedPixel(it.name, it.values) }
        assertEquals(expected.sortedBy { it.toString() }, requested.sortedBy { it.toString() })
    }

    private data class RecordedPixel(val name: String, val values: Map<String, String>)

    private companion object {
        const val PERIOD_START = 1769385600000L // 2026-01-26T00:00:00Z
        const val TAB_1 = "tab1"
        const val TAB_2 = "tab2"
        const val TAB_3 = "tab3"
        const val URL_A = "https://a.example.com/"
        const val URL_B = "https://b.example.com/"
        const val URL_C = "https://c.example.com/"

        val FIXTURE = """
            {
                "telemetry": {
                    "dedupProbe_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "probeDetected" },
                        "parameters": { "reason": { "template": "data", "dataKey": "reason" } }
                    },
                    "dedupOther_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "otherDetected" },
                        "parameters": { "reason": { "template": "data", "dataKey": "reason" } }
                    },
                    "dedupProbe_day": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "count": {
                                "template": "counter",
                                "source": "probeDetected",
                                "buckets": {
                                    "1":  {"gte": 1, "lt": 2},
                                    "2":  {"gte": 2, "lt": 3},
                                    "3+": {"gte": 3}
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
    }
}
