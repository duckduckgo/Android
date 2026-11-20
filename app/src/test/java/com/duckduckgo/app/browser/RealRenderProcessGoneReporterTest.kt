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

import android.app.ApplicationExitInfo
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_CRASH
import com.duckduckgo.app.browser.WebViewPixelName.WEB_RENDERER_GONE_KILLED
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class RealRenderProcessGoneReporterTest {

    // A StandardTestDispatcher queues launches the way Dispatchers.Main posts them, so runCurrent() is "the next main-thread message".
    @get:Rule
    val coroutineTestRule = CoroutineTestRule(StandardTestDispatcher())

    private val pixel: Pixel = mock()
    private val memoryCollector: MemoryCollector = mock()
    private val renderProcessGonePixelFeature = FakeFeatureToggleFactory.create(RenderProcessGonePixelFeature::class.java)
    private val exitInfoProvider = FakeRendererExitInfoProvider()
    private val timeProvider = FakeTimeProvider()

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
            exitInfoProvider,
            timeProvider,
        )
    }

    @Test
    fun whenOneRendererDeathNotifiesSeveralWebViewsThenOnePixelCarriesTheirCount() {
        repeat(6) { testee.onRenderProcessGone(didCrash = false) }
        runNextMainMessage()

        verify(pixel, times(1)).enqueueFire(eq(WEB_RENDERER_GONE_KILLED), any(), any(), any())
        assertEquals("6-10", firedParams().single()["affected_webviews"])
    }

    @Test
    fun whenRendererDiesThenPixelIsStoredUntilSent() {
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        verify(pixel).enqueueFire(eq(WEB_RENDERER_GONE_KILLED), any(), any(), any())
        verifyNoMoreInteractions(pixel)
    }

    @Test
    fun whenCallbacksAreStillBeingDeliveredThenNoPixelYet() {
        testee.onRenderProcessGone(didCrash = false)

        verifyNoInteractions(pixel)
    }

    @Test
    fun whenRendererCrashedThenCrashPixelFired() {
        testee.onRenderProcessGone(didCrash = true)
        runNextMainMessage()

        verify(pixel).enqueueFire(eq(WEB_RENDERER_GONE_CRASH), any(), any(), any())
    }

    @Test
    fun whenTwoRenderersDieInSeparateMessagesThenEachFiresItsOwnPixel() {
        testee.onRenderProcessGone(didCrash = true)
        runNextMainMessage()
        testee.onRenderProcessGone(didCrash = false)
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        verify(pixel).enqueueFire(eq(WEB_RENDERER_GONE_CRASH), any(), any(), any())
        verify(pixel).enqueueFire(eq(WEB_RENDERER_GONE_KILLED), any(), any(), any())
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

    @Test
    fun whenAppInForegroundThenNoTimeInBackground() {
        testee.onOpen(isFreshLaunch = false)
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertFalse(firedParams().single().containsKey("time_in_background"))
    }

    @Test
    fun whenRendererDiesAfterAppClosedThenTimeSinceCloseBucketed() {
        testee.onOpen(isFreshLaunch = false)
        testee.onClose()
        timeProvider.wallClock += 5 * 60_000
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("1m-10m", firedParams().single()["time_in_background"])
    }

    @Test
    fun whenAppNeverOpenedThenTimeInBackgroundSaysSo() {
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("never_opened", firedParams().single()["time_in_background"])
    }

    @Test
    fun whenSystemRecordedTheExitThenReasonAndMemoryReported() {
        exitInfoProvider.lookup = found(reason = ApplicationExitInfo.REASON_LOW_MEMORY, pssKb = 300 * 1024, timestamp = 0)
        timeProvider.wallClock = 1_000_000

        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        val params = firedParams().single()
        assertEquals("low_memory", params["exit_reason"])
        assertEquals("200-400MB", params["renderer_pss"])
        assertEquals(1_000_000L, exitInfoProvider.requestedCallbackTime)
    }

    @Test
    fun whenSystemNeverSampledRendererMemoryThenMemoryUnknown() {
        exitInfoProvider.lookup = found(reason = ApplicationExitInfo.REASON_SIGNALED, pssKb = 0, timestamp = 0)

        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("signaled", firedParams().single()["exit_reason"])
        assertEquals("unknown", firedParams().single()["renderer_pss"])
    }

    @Test
    fun whenCallbackDeliveredAfterAppReopenedThenStateAtRecordedDeathTimeReported() {
        testee.onOpen(isFreshLaunch = false)
        timeProvider.wallClock = 1_000
        testee.onClose()
        exitInfoProvider.lookup = found(reason = ApplicationExitInfo.REASON_SIGNALED, pssKb = 0, timestamp = 31_000)
        timeProvider.wallClock = 600_000
        testee.onOpen(isFreshLaunch = false)

        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("background", firedParams().single()["app_state"])
        assertEquals("10s-1m", firedParams().single()["time_in_background"])
    }

    @Test
    fun whenRecordedDeathWasBeforeAppClosedThenForeground() {
        testee.onOpen(isFreshLaunch = false)
        exitInfoProvider.lookup = found(reason = ApplicationExitInfo.REASON_CRASH_NATIVE, pssKb = 0, timestamp = 500)
        timeProvider.wallClock = 1_000
        testee.onClose()
        timeProvider.wallClock = 2_000

        testee.onRenderProcessGone(didCrash = true)
        runNextMainMessage()

        assertEquals("foreground", firedParams().single()["app_state"])
        assertFalse(firedParams().single().containsKey("time_in_background"))
    }

    @Test
    fun whenNoExitRecordFoundThenReasonNotFoundAndNoMemory() {
        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("not_found", firedParams().single()["exit_reason"])
        assertFalse(firedParams().single().containsKey("renderer_pss"))
    }

    @Test
    fun whenSeveralRenderersCouldMatchThenReasonAmbiguousAndNoMemory() {
        exitInfoProvider.lookup = RendererExitLookup.Ambiguous

        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertEquals("ambiguous", firedParams().single()["exit_reason"])
        assertFalse(firedParams().single().containsKey("renderer_pss"))
    }

    @Test
    fun whenExitRecordsUnsupportedThenNoExitParams() {
        exitInfoProvider.isSupported = false

        testee.onRenderProcessGone(didCrash = false)
        runNextMainMessage()

        assertFalse(firedParams().single().containsKey("exit_reason"))
        assertFalse(firedParams().single().containsKey("renderer_pss"))
    }

    private fun found(reason: Int, pssKb: Long, timestamp: Long) =
        RendererExitLookup.Found(pid = 1, exit = RendererExit(reason = reason, pssKb = pssKb, timestampMillis = timestamp))

    private fun setKillSwitch(enabled: Boolean) {
        renderProcessGonePixelFeature.self().setRawStoredState(Toggle.State(enable = enabled))
    }

    private fun runNextMainMessage() = coroutineTestRule.testScope.testScheduler.runCurrent()

    private fun firedParams(): List<Map<String, String>> {
        val captor = argumentCaptor<Map<String, String>>()
        verify(pixel, atLeastOnce()).enqueueFire(any<Pixel.PixelName>(), captor.capture(), any(), any())
        return captor.allValues
    }

    private class FakeRendererExitInfoProvider : RendererExitInfoProvider {
        override var isSupported = true
        var lookup: RendererExitLookup = RendererExitLookup.NotFound
        var requestedCallbackTime: Long? = null

        override fun findRendererExit(callbackTimeMillis: Long): RendererExitLookup {
            requestedCallbackTime = callbackTimeMillis
            return lookup
        }
    }

    private class FakeTimeProvider : CurrentTimeProvider {
        var wallClock = 0L

        override fun elapsedRealtime(): Long = 0
        override fun currentTimeMillis(): Long = wallClock
        override fun localDateTimeNow(): LocalDateTime = LocalDateTime.now()
    }
}
