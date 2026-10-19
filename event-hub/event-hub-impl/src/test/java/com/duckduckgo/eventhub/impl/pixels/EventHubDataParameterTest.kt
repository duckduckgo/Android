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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.eventhub.impl.EventHubFeature
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * A `data` parameter forwards `data[dataKey]` from an event's payload as compact JSON, percent-encoded once and passed as an
 * already-encoded pixel parameter. An absent key leaves the parameter out. Immediate pixels read the event that fired them;
 * period pixels keep the value of the latest delivered event of the parameter's `source`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubDataParameterTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository = InMemoryEventHubRepository()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)
    private lateinit var manager: RealEventHubPixelManager

    private val immediateDataConfig = """
        {
            "telemetry": {
                "webEvent_login": {
                    "state": "enabled",
                    "trigger": { "type": "immediate_v2", "source": "login" },
                    "parameters": {
                        "loginState": { "template": "data", "dataKey": "loginState" }
                    }
                }
            }
        }
    """.trimIndent()

    private val periodDataConfig = """
        {
            "telemetry": {
                "yt": {
                    "state": "enabled",
                    "trigger": { "period": { "seconds": 60 } },
                    "parameters": {
                        "count": { "template": "counter", "source": "yt", "buckets": {"0-9": {"gte": 0, "lt": 10}, "10+": {"gte": 10}} },
                        "loginState": { "template": "data", "source": "yt", "dataKey": "loginState" }
                    }
                }
            }
        }
    """.trimIndent()

    @Test
    fun `immediate data param encodes string value`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": "logged-in" }"""), "tab1")

        assertEquals("%22logged-in%22", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param encodes object value`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": { "a": true } }"""), "tab1")

        assertEquals("%7B%22a%22%3Atrue%7D", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param encodes null value`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": null }"""), "tab1")

        assertEquals("null", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param encodes numbers and booleans as JSON`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": [3, 2.5, false] }"""), "tab1")

        assertEquals("%5B3%2C2.5%2Cfalse%5D", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param percent-encodes spaces and non-ASCII characters once`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": "a b+ü%" }"""), "tab1")

        assertEquals("%22a%20b%2B%C3%BC%25%22", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param keeps slashes and non-ASCII characters unescaped in the JSON`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": "a/b\u2014" }"""), "tab1")

        assertEquals("%22a%2Fb%E2%80%94%22", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param omitted when key absent`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "other": "x" }"""), "tab1")

        assertFalse(firedEncodedParams("webEvent_login").containsKey("loginState"))
    }

    @Test
    fun `immediate data param omitted when the payload is not an object`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(JSONObject().put("type", "login").put("data", "logged-in"), "tab1")

        assertFalse(firedEncodedParams("webEvent_login").containsKey("loginState"))
    }

    @Test
    fun `aggregate data param uses last value from matching source`() {
        activeManager(periodDataConfig)

        manager.handleWebEvent(eventWithData("yt", """{ "loginState": "a" }"""), "tab1")
        manager.handleWebEvent(eventWithData("yt", """{ "loginState": "b" }"""), "tab2")
        endPeriod()

        assertEquals("%22b%22", firedEncodedParams("yt")["loginState"])
    }

    @Test
    fun `aggregate data param is kept as compact JSON in the persisted state`() {
        activeManager(periodDataConfig)

        manager.handleWebEvent(eventWithData("yt", """{ "loginState": { "a": true } }"""), "tab1")

        assertEquals("""{"a":true}""", repository.getPixelState("yt")!!.params["loginState"]!!.lastDataValue)
    }

    @Test
    fun `aggregate data param keeps updating after its counter stops counting`() {
        activeManager(periodDataConfig)

        repeat(12) { index -> manager.handleWebEvent(eventWithData("yt", """{ "loginState": "v$index" }"""), "tab$index") }
        endPeriod()

        assertEquals("%22v11%22", firedEncodedParams("yt")["loginState"])
    }

    private fun eventWithData(type: String, dataJson: String) = JSONObject().put("type", type).put("data", JSONObject(dataJson))

    @SuppressLint("DenyListedApi")
    private fun activeManager(settings: String) {
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
        manager.onAppForegrounded()
    }

    private fun endPeriod() {
        timeProvider.time = 60_000L
        manager.onAppForegrounded()
    }

    private fun firedEncodedParams(pixelName: String): Map<String, String> {
        val encoded = argumentCaptor<Map<String, String>>()
        verify(pixel).enqueueFire(eq(pixelName), any(), encoded.capture(), any())
        return encoded.firstValue
    }
}
