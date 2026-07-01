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
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/**
 * A `data`-template parameter forwards `webEvent.data[dataKey]` into the pixel as the compact-JSON value
 * %-encoded (without double-encoding), carried as an already-encoded pixel parameter. Absent keys are
 * omitted. Immediate pixels use the triggering message's data (no `source`); aggregate pixels use the
 * last value seen on a matching `source`. Ported from the Windows EventHub spec (EventHubDataParameterTests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubDataParameterTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository = FakeEventHubRepository()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)
    private lateinit var manager: RealEventHubPixelManager

    private val immediateDataConfig = """
        {
            "telemetry": {
                "webEvent_login": {
                    "state": "enabled",
                    "trigger": { "type": "immediate", "source": "login" },
                    "parameters": {
                        "loginState": { "template": "data", "dataKey": "loginState" }
                    }
                }
            }
        }
    """.trimIndent()

    private fun eventWithData(type: String, dataJson: String) =
        JSONObject().put("type", type).put("data", JSONObject(dataJson))

    @SuppressLint("DenyListedApi")
    private fun activeManager(settings: String) {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = false))
        manager = RealEventHubPixelManager(
            repository, pixel, timeProvider, coroutineTestRule.testScope,
            UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler), eventHubFeature,
        )
        manager.onAppForegrounded()
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = true, settings = settings))
    }

    private fun firedEncodedParams(pixelName: String): Map<String, String> {
        val encoded = argumentCaptor<Map<String, String>>()
        verify(pixel).enqueueFire(eq(pixelName), any(), encoded.capture(), any())
        return encoded.firstValue
    }

    @Test
    fun `immediate data param encodes string value`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": "logged-in" }"""), "tab1")

        assertEquals("%22logged-in%22", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param encodes object value`() {
        val config = """
            {
                "telemetry": {
                    "webEvent_login": {
                        "state": "enabled",
                        "trigger": { "type": "immediate", "source": "login" },
                        "parameters": { "payload": { "template": "data", "dataKey": "payload" } }
                    }
                }
            }
        """.trimIndent()
        activeManager(config)

        manager.handleWebEvent(eventWithData("login", """{ "payload": { "a": true } }"""), "tab1")

        assertEquals("%7B%22a%22%3Atrue%7D", firedEncodedParams("webEvent_login")["payload"])
    }

    @Test
    fun `immediate data param encodes null value`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "loginState": null }"""), "tab1")

        assertEquals("null", firedEncodedParams("webEvent_login")["loginState"])
    }

    @Test
    fun `immediate data param omitted when key absent`() {
        activeManager(immediateDataConfig)

        manager.handleWebEvent(eventWithData("login", """{ "other": "x" }"""), "tab1")

        assertFalse(firedEncodedParams("webEvent_login").containsKey("loginState"))
    }

    @Test
    fun `aggregate data param uses last value from matching source`() {
        val config = """
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
        activeManager(config)
        manager.onConfigChanged()

        manager.handleWebEvent(eventWithData("yt", """{ "loginState": "a" }"""), "tab1")
        manager.handleWebEvent(eventWithData("yt", """{ "loginState": "b" }"""), "tab2")
        timeProvider.time = 60_000L
        manager.onAppForegrounded()

        assertEquals("%22b%22", firedEncodedParams("yt")["loginState"])
    }

    private class FakeCurrentTimeProvider : CurrentTimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
        override fun elapsedRealtime(): Long = time
        override fun localDateTimeNow(): java.time.LocalDateTime = java.time.LocalDateTime.now()
    }

    private class FakeEventHubRepository : EventHubRepository {
        private val states = linkedMapOf<String, PixelState>()
        override fun getPixelState(name: String): PixelState? = states[name]
        override fun getAllPixelStates(): List<PixelState> = states.values.toList()
        override fun savePixelState(state: PixelState) {
            states[state.pixelName] = state
        }
        override fun deletePixelState(name: String) {
            states.remove(name)
        }
        override fun deleteAllPixelStates() = states.clear()
    }
}
