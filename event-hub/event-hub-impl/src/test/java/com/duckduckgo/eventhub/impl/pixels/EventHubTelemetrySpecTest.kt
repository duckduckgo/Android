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
 * Android's coverage of the cross-platform telemetry specification (`docs/event-hub/tests/telemetry.md` in
 * `duckduckgo/ddg-workflow`). Each case ID from that document leads the test name so coverage is greppable and a failure
 * names the spec case. Events go through [RealEventHubPixelManager], then every period pixel's period ends once, and the
 * complete set of requested pixels is asserted, without `attributionPeriod`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubTelemetrySpecTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository = InMemoryEventHubRepository()
    private val pixel = RecordingPixel()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)

    private lateinit var manager: RealEventHubPixelManager
    private var pageCount = 0

    @Before
    fun setup() {
        start(FIXTURE)
    }

    @Test
    fun `T-CNT-1 occurrences across pages bucket at period end`() {
        repeat(3) { onNewPage { captcha() } }

        assertRequested(captchaDay("3-5"), captchaWeek("3-5"))
    }

    @Test
    fun `T-CNT-2 a zero count fires when a zero bucket exists`() {
        assertRequested(captchaDay("0"), captchaWeek("0"))
    }

    @Test
    fun `T-CNT-3 a count matching no bucket does not fire`() {
        onNewPage { event("unrelatedDetected") }

        assertRequested(captchaDay("0"), captchaWeek("0"))
    }

    @Test
    fun `T-CNT-4 a disabled pixel never fires`() {
        onNewPage { captcha() }

        assertRequested(captchaDay("1-2"), captchaWeek("1-2"))
    }

    @Test
    fun `T-CNT-5 a resolved data value does not fire a pixel whose counter matched no bucket`() {
        start(
            FIXTURE.withEntry(
                "webTelemetry_mixedSource_day",
                """
                {
                    "state": "enabled",
                    "trigger": { "period": { "days": 1 } },
                    "parameters": {
                        "count": { "template": "counter", "source": "captchaDetected", "buckets": { "1-2": {"gte": 1, "lt": 3}, "3+": {"gte": 3} } },
                        "reason": { "template": "data", "source": "adwallDetected", "dataKey": "reason" }
                    }
                }
                """,
            ),
        )
        onNewPage { adwall(reason("overlay")) }

        assertRequested(
            adwallDay("1-2", reason = "\"overlay\""),
            adwallImmediate(reason = "\"overlay\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-DAT-1 the last matching event of the period supplies the value`() {
        onNewPage { adwall(reason("overlay")) }
        onNewPage { adwall(reason("redirect")) }

        assertRequested(
            adwallDay("1-2", reason = "\"redirect\""),
            adwallImmediate(reason = "\"overlay\""),
            adwallImmediate(reason = "\"redirect\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-DAT-2 a matching event without the key leaves no value`() {
        onNewPage { adwall(reason("overlay")) }
        onNewPage { adwall(JSONObject()) }

        assertRequested(
            adwallDay("1-2", reason = null),
            adwallImmediate(reason = "\"overlay\""),
            adwallImmediate(reason = null),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-DAT-3 a non-string value is carried as compact JSON`() {
        onNewPage { adwall(JSONObject("""{"reason": {"a": true}}""")) }

        assertRequested(
            adwallDay("1-2", reason = """{"a":true}"""),
            adwallImmediate(reason = """{"a":true}"""),
            captchaDay("0"),
            captchaWeek("0"),
        )
        assertEquals(
            listOf("%7B%22a%22%3Atrue%7D", "%7B%22a%22%3Atrue%7D"),
            pixel.requests.mapNotNull { it.encodedParameters["reason"] },
        )
    }

    @Test
    fun `T-DAT-4 events of other types leave the value alone`() {
        onNewPage { adwall(reason("overlay")) }
        onNewPage { captcha() }

        assertRequested(
            adwallDay("1-2", reason = "\"overlay\""),
            adwallImmediate(reason = "\"overlay\""),
            captchaDay("1-2"),
            captchaWeek("1-2"),
        )
    }

    // The encoded parameters are put on the request URL as they are (Retrofit's @QueryMap(encoded = true)), so this is the
    // value that reaches the endpoint
    @Test
    fun `T-DAT-5 a value is encoded once on the wire`() {
        onNewPage { adwall(reason("overlay")) }

        assertRequested(
            adwallDay("1-2", reason = "\"overlay\""),
            adwallImmediate(reason = "\"overlay\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
        assertEquals(listOf("%22overlay%22", "%22overlay%22"), pixel.requests.mapNotNull { it.encodedParameters["reason"] })
    }

    @Test
    fun `T-IMM-1 one immediate pixel per event type and payload per page`() {
        onNewPage {
            adwall(reason("overlay"))
            adwall(reason("overlay"))
        }

        assertRequested(
            adwallDay("1-2", reason = "\"overlay\""),
            adwallImmediate(reason = "\"overlay\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-IMM-2 an event with no matching immediate trigger fires nothing immediately`() {
        onNewPage { captcha() }

        assertRequested(captchaDay("1-2"), captchaWeek("1-2"))
    }

    @Test
    fun `T-IMM-3 a disabled immediate pixel never fires`() {
        onNewPage { adwall(reason("overlay")) }

        assertRequested(
            adwallDay("1-2", reason = "\"overlay\""),
            adwallImmediate(reason = "\"overlay\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-IMM-4 distinct payloads on one page each fire`() {
        onNewPage {
            adwall(reason("overlay"))
            adwall(reason("redirect"))
        }

        assertRequested(
            adwallDay("1-2", reason = "\"redirect\""),
            adwallImmediate(reason = "\"overlay\""),
            adwallImmediate(reason = "\"redirect\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-IMM-5 a pixel declaring no parameters fires on the event alone`() {
        start(
            FIXTURE.withEntry(
                "webTelemetry_bareEvent_immediate",
                """{ "state": "enabled", "trigger": { "type": "immediate_v2", "source": "adwallDetected" }, "parameters": {} }""",
            ),
        )
        onNewPage { adwall(reason("overlay")) }

        assertRequested(
            RecordedPixel("webTelemetry_bareEvent_immediate", emptyMap()),
            adwallImmediate(reason = "\"overlay\""),
            adwallDay("1-2", reason = "\"overlay\""),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @Test
    fun `T-IMM-6 a pixel fires with no parameters when none of those it declares resolve`() {
        onNewPage { adwall(JSONObject()) }

        assertRequested(
            adwallImmediate(reason = null),
            adwallDay("1-2", reason = null),
            captchaDay("0"),
            captchaWeek("0"),
        )
    }

    @SuppressLint("DenyListedApi")
    private fun start(settings: String) {
        repository.deleteAllPixelStates()
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = true, settings = settings))
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

    private fun onNewPage(events: () -> Unit) {
        manager.onNavigationStarted(TAB, "https://page${++pageCount}.example.com/")
        events()
    }

    private fun captcha() = event("captchaDetected")

    private fun adwall(data: JSONObject) = event("adwallDetected", data)

    private fun event(type: String, data: JSONObject? = null) {
        val message = JSONObject().put("type", type)
        if (data != null) message.put("data", data)
        manager.handleWebEvent(message, TAB)
    }

    private fun reason(value: String) = JSONObject().put("reason", value)

    /** Ends every period pixel's period once, the longest being a week, and checks the complete set of requested pixels. */
    private fun assertRequested(vararg expected: RecordedPixel) {
        timeProvider.time = PERIOD_START + TimeUnit.DAYS.toMillis(7)
        manager.onAppForegrounded()

        val requested = pixel.requests.map { RecordedPixel(it.name, it.values) }
        assertEquals(expected.sortedBy { it.toString() }, requested.sortedBy { it.toString() })
    }

    private data class RecordedPixel(val name: String, val values: Map<String, String>)

    private fun captchaDay(count: String) = RecordedPixel("webTelemetry_captchaDetection_day", mapOf("count" to count))

    private fun captchaWeek(count: String) = RecordedPixel("webTelemetry_captchaDetection_week", mapOf("count" to count))

    private fun adwallDay(count: String, reason: String?) =
        RecordedPixel("webTelemetry_adwallDetection_day", listOfNotNull("count" to count, reason?.let { "reason" to it }).toMap())

    private fun adwallImmediate(reason: String?) =
        RecordedPixel("webTelemetry_adwallDetection_immediate", listOfNotNull(reason?.let { "reason" to it }).toMap())

    private fun String.withEntry(name: String, entry: String): String {
        val settings = JSONObject(this)
        settings.getJSONObject("telemetry").put(name, JSONObject(entry))
        return settings.toString()
    }

    private companion object {
        const val PERIOD_START = 1769385600000L // 2026-01-26T00:00:00Z
        const val TAB = "tab1"

        val CAPTCHA_BUCKETS = """
            { "0": {"gte": 0, "lt": 1}, "1-2": {"gte": 1, "lt": 3}, "3-5": {"gte": 3, "lt": 6}, "6+": {"gte": 6} }
        """.trimIndent()

        val FIXTURE = """
            {
                "telemetry": {
                    "webTelemetry_captchaDetection_day": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": { "count": { "template": "counter", "source": "captchaDetected", "buckets": $CAPTCHA_BUCKETS } }
                    },
                    "webTelemetry_captchaDetection_week": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 7 } },
                        "parameters": { "count": { "template": "counter", "source": "captchaDetected", "buckets": $CAPTCHA_BUCKETS } }
                    },
                    "webTelemetry_adwallDetection_day": {
                        "state": "enabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": {
                            "count": { "template": "counter", "source": "adwallDetected", "buckets": { "1-2": {"gte": 1, "lt": 3}, "3+": {"gte": 3} } },
                            "reason": { "template": "data", "source": "adwallDetected", "dataKey": "reason" }
                        }
                    },
                    "webTelemetry_adwallDetection_immediate": {
                        "state": "enabled",
                        "trigger": { "type": "immediate_v2", "source": "adwallDetected" },
                        "parameters": { "reason": { "template": "data", "dataKey": "reason" } }
                    },
                    "webTelemetry_disabledPixel_day": {
                        "state": "disabled",
                        "trigger": { "period": { "days": 1 } },
                        "parameters": { "count": { "template": "counter", "source": "captchaDetected", "buckets": { "0+": {"gte": 0} } } }
                    },
                    "webTelemetry_disabledPixel_immediate": {
                        "state": "disabled",
                        "trigger": { "type": "immediate_v2", "source": "adwallDetected" },
                        "parameters": { "reason": { "template": "data", "dataKey": "reason" } }
                    }
                }
            }
        """.trimIndent()
    }
}
