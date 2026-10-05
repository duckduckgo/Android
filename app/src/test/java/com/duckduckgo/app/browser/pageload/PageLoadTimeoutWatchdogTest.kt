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

package com.duckduckgo.app.browser.pageload

import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Phase.COMMITTED_NO_CONTENT
import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Phase.NOT_COMMITTED
import com.duckduckgo.app.browser.pageload.PageLoadTimeoutWatchdog.Timeout
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PageLoadTimeoutWatchdogTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val rx = FakeRxBytesProvider()
    private val timeouts = mutableListOf<Timeout>()

    private fun watchdogTest(block: suspend TestScope.(PageLoadTimeoutWatchdog) -> Unit) =
        coroutineRule.testScope.runTest {
            val watchdog = RealPageLoadTimeoutWatchdog(backgroundScope, coroutineRule.testDispatcherProvider, rx)
            backgroundScope.launch { watchdog.timeouts.toList(timeouts) }
            runCurrent()
            block(watchdog)
        }

    private fun TestScope.advanceToMs(timeMs: Long) {
        testScheduler.advanceTimeBy(timeMs - testScheduler.currentTime)
        testScheduler.runCurrent()
    }

    private fun TestScope.advanceToSeconds(seconds: Long) = advanceToMs(seconds * 1_000)

    // Moves the clock one second at a time so the byte counter grows (or not) in step with it.
    private fun TestScope.advanceToSeconds(seconds: Long, rxBytesPerSecond: Long) {
        while (testScheduler.currentTime < seconds * 1_000) {
            rx.value += rxBytesPerSecond
            advanceToMs(testScheduler.currentTime + 1_000)
        }
    }

    @Test
    fun whenPageFinishesAfterOneSecondThenNoTimeout() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToMs(500)
        watchdog.onCommitted(URL)
        advanceToMs(1_000)
        watchdog.onFinished(URL)

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenRedirectChainFinishesAfterTenSecondsThenNoTimeout() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(3)
        watchdog.onRedirect(HOP_1)
        advanceToSeconds(6)
        watchdog.onRedirect(HOP_2)
        advanceToSeconds(9)
        watchdog.onRedirect(FINAL)
        advanceToSeconds(10)
        watchdog.onCommitted(FINAL)
        watchdog.onFirstContentVisible(FINAL)
        watchdog.onFinished(FINAL)

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenRedirectArrivesThenClockIsNotReset() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(40)
        watchdog.onRedirect(HOP_1)

        advanceToMs(44_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(45_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenServerStaysSilentThenTimeoutFiresAtExactlyFortyFiveSecondsNotCommitted() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)

        advanceToMs(44_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(45_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)

        advanceToSeconds(60)
        assertEquals(1, timeouts.size)
    }

    @Test
    fun whenServerCommitsAtFortySecondsThenNoTimeoutAtFortyFiveSeconds() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(40)
        watchdog.onCommitted(URL)
        advanceToSeconds(41)
        watchdog.onFirstContentVisible(URL)

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenServerCommitsAtFortySecondsWithoutContentThenTimeoutFiresAtNextIdleWindow() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(40)
        watchdog.onCommitted(URL)

        advanceToMs(59_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(60_000)
        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenContentIsVisibleEarlyThenWatchdogIsDisarmedEvenIfBytesStall() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)
        advanceToSeconds(2)
        watchdog.onFirstContentVisible(URL)

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenCommittedWithoutContentAndBytesFlowEveryWindowThenNoTimeout() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)

        advanceToSeconds(120, rxBytesPerSecond = 300)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenCommittedWithoutContentAndBytesStallThenTimeoutFiresAtFortyFiveSeconds() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)

        advanceToMs(44_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(45_000)
        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenBytesStopFlowingAfterFortyFiveSecondsThenTimeoutFiresAtFirstIdleWindow() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)
        advanceToSeconds(50, rxBytesPerSecond = 600)

        advanceToMs(74_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(75_000)
        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenBytesInWindowEqualThresholdThenWindowCountsAsIdle() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)
        advanceToSeconds(30)
        rx.value += 2_048

        advanceToSeconds(45)

        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenEngineReportsErrorBeforeConfirmThenNoTimeout() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(32)
        watchdog.onEngineError()

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenNavigationIsSupersededThenClockRestartsAndStaleSignalsAreIgnored() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(5)
        watchdog.onMainFrameRequest(OTHER_URL)
        advanceToSeconds(6)
        watchdog.onFinished(URL)
        advanceToSeconds(7)
        watchdog.onCommitted(URL)
        watchdog.onFirstContentVisible(URL)

        advanceToMs(49_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(50_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenMainFrameRequestIsRepeatedForTheSameUrlThenClockRestarts() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(40)
        watchdog.onMainFrameRequest(URL)

        advanceToMs(84_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(85_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenRedirectArrivesAfterTimeoutThenItIsIgnored() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(45)
        watchdog.onRedirect(HOP_1)
        advanceToSeconds(300)

        assertEquals(1, timeouts.size)
    }

    @Test
    fun whenRedirectArrivesWhileIdleThenWatchdogArms() = watchdogTest { watchdog ->
        watchdog.onRedirect(HOP_1)

        advanceToMs(44_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(45_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenRedirectTargetsNonWebUrlThenWatchdogIsDisarmed() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(2)
        watchdog.onRedirect("intent://example")

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenTrailingSlashDiffersThenUrlsStillMatch() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest("https://example.com")
        advanceToSeconds(1)
        watchdog.onFinished("https://example.com/")

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenTrafficStatsAreUnsupportedThenOnlyMilestonesCountAsProgress() = watchdogTest { watchdog ->
        rx.value = UNSUPPORTED
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(1)
        watchdog.onCommitted(URL)

        advanceToSeconds(44)
        assertTrue(timeouts.isEmpty())

        advanceToSeconds(45)
        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenTrafficStatsAreUnsupportedAndServerCommitsLateThenNoTimeoutAtConfirm() = watchdogTest { watchdog ->
        rx.value = UNSUPPORTED
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(40)
        watchdog.onCommitted(URL)

        advanceToSeconds(45)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenNavigationStartsAfterTimeoutThenWatchdogRearms() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(45)
        assertEquals(1, timeouts.size)

        advanceToSeconds(50)
        watchdog.onMainFrameRequest(OTHER_URL)

        advanceToMs(94_999)
        assertEquals(1, timeouts.size)

        advanceToMs(95_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED), Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenLoadProgressesAfterTimeoutThenSameNavigationDoesNotRearm() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(45)
        assertEquals(1, timeouts.size)

        watchdog.onRedirect(HOP_1)
        watchdog.onCommitted(HOP_1)

        advanceToSeconds(300)

        assertEquals(1, timeouts.size)
    }

    @Test
    fun whenNothingIsArmedThenCommitArmsAsFallback() = watchdogTest { watchdog ->
        advanceToSeconds(10)
        watchdog.onCommitted(URL)

        advanceToMs(54_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(55_000)
        assertEquals(listOf(Timeout(COMMITTED_NO_CONTENT)), timeouts)
    }

    @Test
    fun whenRedirectIsCancelledThenWatchdogIsDisarmed() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(2)
        watchdog.onRedirect(HOP_1)
        watchdog.onNavigationCancelled(HOP_1)

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenRedirectIsCancelledAfterAppReloadedThenNewNavigationStaysArmed() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(2)
        watchdog.onRedirect(HOP_1)
        watchdog.onMainFrameRequest(OTHER_URL)
        watchdog.onNavigationCancelled(HOP_1)

        advanceToMs(46_999)
        assertTrue(timeouts.isEmpty())

        advanceToMs(47_000)
        assertEquals(listOf(Timeout(NOT_COMMITTED)), timeouts)
    }

    @Test
    fun whenNavigationTargetsNonWebUrlThenWatchdogIsDisarmed() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(2)
        watchdog.onMainFrameRequest("about:blank")

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    @Test
    fun whenUserNavigatesAwayThenNoTimeout() = watchdogTest { watchdog ->
        watchdog.onMainFrameRequest(URL)
        advanceToSeconds(5)
        watchdog.onNavigatedAway()

        advanceToSeconds(120)

        assertTrue(timeouts.isEmpty())
    }

    private class FakeRxBytesProvider : RxBytesProvider {
        var value: Long = 0
        override fun uidRxBytes(): Long = value
    }

    private companion object {
        const val URL = "https://example.com/slow"
        const val OTHER_URL = "https://example.org/other"
        const val HOP_1 = "https://example.com/hop1"
        const val HOP_2 = "https://example.com/hop2"
        const val FINAL = "https://example.com/final"
        const val UNSUPPORTED = -1L
    }
}
