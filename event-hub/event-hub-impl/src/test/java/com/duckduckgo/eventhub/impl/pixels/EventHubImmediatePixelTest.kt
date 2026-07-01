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
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Immediate pixels use `trigger.type == "immediate"` with a trigger `source` and no period: each
 * matching event fires exactly one pixel right away, with no counter, period, persistence, dedup, or
 * foreground-gating. Ported from the Windows EventHub spec (EventHubImmediatePixelTests).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubImmediatePixelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository: EventHubRepository = mock()
    private val pixel: Pixel = mock()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)
    private lateinit var manager: RealEventHubPixelManager

    private val immediateConfig = """
        {
            "telemetry": {
                "webEvent_impression": {
                    "state": "enabled",
                    "trigger": { "type": "immediate", "source": "impression" },
                    "parameters": {}
                }
            }
        }
    """.trimIndent()

    private fun webEvent(type: String) = JSONObject().put("type", type)

    @SuppressLint("DenyListedApi")
    private fun activeManager(enabled: Boolean = true, settings: String = immediateConfig, foreground: Boolean = true) {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = false))
        manager = RealEventHubPixelManager(
            repository, pixel, timeProvider, coroutineTestRule.testScope,
            UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler), eventHubFeature,
        )
        if (foreground) manager.onAppForegrounded()
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = enabled, settings = settings))
    }

    @Test
    fun `immediate trigger fires one pixel per event`() {
        activeManager()

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel).enqueueFire(eq("webEvent_impression"), any(), any(), any())
    }

    @Test
    fun `immediate pixels are not deduplicated`() {
        activeManager()

        manager.handleWebEvent(webEvent("impression"), "tab1")
        manager.handleWebEvent(webEvent("impression"), "tab1")
        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, times(3)).enqueueFire(eq("webEvent_impression"), any(), any(), any())
    }

    @Test
    fun `immediate pixels fire without foreground`() {
        activeManager(foreground = false)

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel).enqueueFire(eq("webEvent_impression"), any(), any(), any())
    }

    @Test
    fun `immediate pixels do not persist state`() {
        activeManager()

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(repository, never()).savePixelState(any())
    }

    @Test
    fun `immediate ignores unknown event type`() {
        activeManager()

        manager.handleWebEvent(webEvent("something-else"), "tab1")

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `immediate does not fire when disabled`() {
        activeManager(enabled = false)

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    private class FakeCurrentTimeProvider : CurrentTimeProvider {
        var time: Long = 0L
        override fun currentTimeMillis(): Long = time
        override fun elapsedRealtime(): Long = time
        override fun localDateTimeNow(): java.time.LocalDateTime = java.time.LocalDateTime.now()
    }
}
