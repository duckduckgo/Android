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
import com.duckduckgo.eventhub.impl.metrics.ExperimentMetricsHandler
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineDispatcher
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
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever

/**
 * An `immediate_v2` trigger fires one pixel for each delivered event of its `source`: after the hub's de-duplication, with no
 * period, no persisted state and no foreground gating.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EventHubImmediatePixelTest {

    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val repository: EventHubRepository = mock()
    private val pixel: Pixel = mock()
    private val metricsHandler: ExperimentMetricsHandler = mock()
    private val timeProvider = FakeCurrentTimeProvider()
    private val eventHubFeature: EventHubFeature = FakeFeatureToggleFactory.create(EventHubFeature::class.java)
    private lateinit var manager: RealEventHubPixelManager

    private val immediateConfig = immediateEntry(name = "webEvent_impression", type = "immediate_v2")

    @Test
    fun `immediate trigger fires one pixel per event`() {
        activeManager()

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel).enqueueFire(eq("webEvent_impression"), any(), any(), any())
    }

    @Test
    fun `repeats on one page fire once and fire again on a different page`() {
        activeManager()

        manager.onNavigationStarted("tab1", "https://a.example.com/")
        repeat(3) { manager.handleWebEvent(webEvent("impression"), "tab1") }
        manager.onNavigationStarted("tab1", "https://b.example.com/")
        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, times(2)).enqueueFire(eq("webEvent_impression"), any(), any(), any())
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

        manager.onConfigChanged()
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
    fun `immediate does not fire when the hub is disabled`() {
        activeManager(enabled = false)

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    @Test
    fun `an entry disabled in the latest config stops firing at once`() {
        activeManager()
        manager.handleWebEvent(webEvent("impression"), "tab1")

        setSettings(immediateConfig.replace("\"enabled\"", "\"disabled\""))
        manager.onConfigChanged()
        manager.onNavigationStarted("tab1", "https://b.example.com/")
        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, times(1)).enqueueFire(eq("webEvent_impression"), any(), any(), any())
    }

    @Test
    fun `an event still queued when the hub is switched off fires nothing`() {
        val testScheduler = coroutineTestRule.testScope.testScheduler
        activeManager(dispatcher = StandardTestDispatcher(testScheduler))

        manager.handleWebEvent(webEvent("impression"), "tab1")
        setSettings(immediateConfig, enabled = false)
        manager.onConfigChanged()
        testScheduler.runCurrent()

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
        verifyBlocking(metricsHandler, never()) { onEvent(any()) }
    }

    @Test
    fun `plain immediate trigger never fires`() {
        activeManager(settings = immediateEntry(name = "webEvent_legacy", type = "immediate"))

        manager.handleWebEvent(webEvent("impression"), "tab1")

        verify(pixel, never()).enqueueFire(any<String>(), any(), any(), any())
    }

    private fun webEvent(type: String) = JSONObject().put("type", type)

    private fun immediateEntry(name: String, type: String) = """
        {
            "telemetry": {
                "$name": {
                    "state": "enabled",
                    "trigger": { "type": "$type", "source": "impression" },
                    "parameters": {}
                }
            }
        }
    """.trimIndent()

    @SuppressLint("DenyListedApi")
    private fun setSettings(settings: String, enabled: Boolean = true) {
        eventHubFeature.self().setRawStoredState(Toggle.State(enable = enabled, settings = settings))
    }

    private fun activeManager(
        enabled: Boolean = true,
        settings: String = immediateConfig,
        foreground: Boolean = true,
        dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(coroutineTestRule.testScope.testScheduler),
    ) {
        whenever(repository.getAllPixelStates()).thenReturn(emptyList())
        setSettings(settings, enabled)
        manager = RealEventHubPixelManager(
            repository,
            pixel,
            timeProvider,
            coroutineTestRule.testScope,
            dispatcher,
            eventHubFeature,
            metricsHandler,
        )
        if (foreground) manager.onAppForegrounded()
    }
}
