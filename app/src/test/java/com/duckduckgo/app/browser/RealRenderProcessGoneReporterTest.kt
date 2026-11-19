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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_KILLED
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class RealRenderProcessGoneReporterTest {

    // A StandardTestDispatcher queues launches the way Dispatchers.Main posts them, so runCurrent() is "the next main-thread message".
    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val pixel: Pixel = mock()
    private val memoryCollector: MemoryCollector = mock()
    private val renderProcessGonePixelFeature = FakeFeatureToggleFactory.create(RenderProcessGonePixelFeature::class.java)

    private lateinit var testee: RealRenderProcessGoneReporter

    @Before
    fun setup() {
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        testee = RealRenderProcessGoneReporter(
            pixel,
            memoryCollector,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            renderProcessGonePixelFeature,
        )
    }

    @Test
    fun whenOneRendererDeathNotifiesSeveralWebViewsThenOnePixelCarriesTheirCount() {
        repeat(6) { testee.onRenderProcessGone(didCrash = false) }
        runNextMainMessage()

        verify(pixel, times(1)).fire(eq(WEB_RENDERER_GONE_KILLED), any(), any(), any())
        assertEquals("6-10", firedParams().single()["affected_webviews"])
    }

    @Test
    fun whenCallbacksAreStillBeingDeliveredThenNoPixelYet() {
        testee.onRenderProcessGone(didCrash = false)

        verify(pixel, never()).fire(any<Pixel.PixelName>(), any(), any(), any())
    }

    @Test
    fun whenRendererCrashedThenCrashPixelFired() {
        testee.onRenderProcessGone(didCrash = true)
        runNextMainMessage()

        verify(pixel).fire(eq(WEB_RENDERER_GONE_CRASH), any(), any(), any())
    }

    @Test
    fun whenTwoRenderersDieInSeparateMessagesThenEachFiresItsOwnPixel() {
        testee.onRenderProcessGone(didCrash = true)
        runNextMainMessage()
        testee.onRenderProcessGone(didCrash = false)
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        verify(pixel).fire(eq(WEB_RENDERER_GONE_CRASH), any(), any(), any())
        verify(pixel).fire(eq(WEB_RENDERER_GONE_KILLED), any(), any(), any())
        assertEquals(listOf("1", "2-3"), firedParams().map { it["affected_webviews"] })
    }

    @Test
    fun whenMoreThanTenWebViewsAffectedThenOpenTopBucket() {
        repeat(16) { testee.onRenderProcessGone(didCrash = false) }
        runNextMainMessage()

        assertEquals("11+", firedParams().single()["affected_webviews"])
    }

    @Test
    fun whenAppOpenAtDeathThenAppStateForeground() {
        testee.onOpen(isFreshLaunch = false)
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("foreground", firedParams().single()["app_state"])
    }

    @Test
    fun whenAppClosedBeforeDeathThenAppStateBackground() {
        testee.onOpen(isFreshLaunch = false)
        testee.onClose()
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("background", firedParams().single()["app_state"])
    }

    @Test
    fun whenRendererDiesThenDeviceRamBucketReported() {
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("4GB", firedParams().single()["device_ram_bucket"])
    }

    @Test
    fun whenKillSwitchDisabledThenParameterlessPixelFiresImmediatelyForEveryWebView() {
        setKillSwitch(enabled = false)

        repeat(3) { testee.onRenderProcessGone(didCrash = false) }

        verify(pixel, times(3)).fire(WEB_RENDERER_GONE_KILLED)
        runNextMainMessage()
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenKillSwitchDisabledAndRendererCrashedThenParameterlessCrashPixelFiredImmediately() {
        setKillSwitch(enabled = false)

        testee.onRenderProcessGone(didCrash = true)

        verify(pixel).fire(WEB_RENDERER_GONE_CRASH)
        runNextMainMessage()
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenKillSwitchDisabledMidDeathThenThatDeathStillFiresOneGroupedPixel() {
        testee.onRenderProcessGone(didCrash = false)
        setKillSwitch(enabled = false)
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("2-3", firedParams().single()["affected_webviews"])
    }

    @Test
    fun whenKillSwitchDisabledThenNextDeathUsesLegacyPixel() {
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()
        setKillSwitch(enabled = false)

        testee.onRenderProcessGone(didCrash = false)

        verify(pixel).fire(WEB_RENDERER_GONE_KILLED)
    }

    private fun setKillSwitch(enabled: Boolean) {
        renderProcessGonePixelFeature.self().setRawStoredState(Toggle.State(enable = enabled))
    }

    private fun runNextMainMessage() = coroutineTestRule.testScope.testScheduler.runCurrent()

    private fun firedParams(): List<Map<String, String>> {
        val captor = argumentCaptor<Map<String, String>>()
        verify(pixel, atLeastOnce()).fire(any<Pixel.PixelName>(), captor.capture(), any(), any())
        return captor.allValues
    }
}
