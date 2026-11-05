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

package com.duckduckgo.app.anr

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.anrs.store.AnrDao
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class AnrSupervisorTest {

    private val anrDao: AnrDao = mock()
    private val anrsDatabase: AnrsDatabase = mock { on { arnDao() } doReturn anrDao }
    private val webViewVersionProvider: WebViewVersionProvider = mock { on { getFullVersion() } doReturn "1.0" }
    private val customTabDetector: CustomTabDetector = mock()
    private val appBuildConfig: AppBuildConfig = mock { on { versionName } doReturn "5.0.0" }

    private lateinit var runnable: AnrSupervisorRunnable
    private lateinit var testee: AnrSupervisor

    @After
    fun tearDown() {
        testee.executor.shutdownNow()
        assertTrue(testee.executor.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun whenReturningDuringStopGracePeriodThenOnOpenDoesNotBlockAndWatcherKeepsRunning() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        assertTrue(watcherIsIn(NON_BLOCKING_GRACE_PERIOD))
        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }
        assertFalse(runnable.isStopped)
    }

    @Test
    fun whenReturningAndLeavingAgainDuringStopGracePeriodThenWatcherStops() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)
        testee.onClose()

        awaitWatcherStopped()
    }

    @Test
    fun whenReturningAndLeavingAgainBetweenChecksThenWatcherStops() {
        setUp(nonBlockingLifecycle = true)
        testee.onOpen(isFreshLaunch = false)
        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }

        testee.onClose()
        testee.onOpen(isFreshLaunch = false)
        testee.onClose()

        awaitWatcherStopped()
    }

    @Test
    fun whenAppStaysInBackgroundThenWatcherStopsAndRestartsOnReturn() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()
        awaitWatcherStopped()

        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }
        assertFalse(runnable.isStopped)
    }

    @Test
    fun whenMainThreadRespondsThenNoAnrIsRecorded() {
        setUp(nonBlockingLifecycle = true)
        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }

        verify(anrDao, never()).insert(any())
    }

    @Test
    fun whenMainThreadDoesNotRespondThenAnrIsRecorded() {
        setUp(nonBlockingLifecycle = true)
        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = false) { mockingDetails(anrDao).invocations.isNotEmpty() }

        verify(anrDao).insert(any())
    }

    @Test
    fun whenFlagDisabledAndReturningDuringStopGracePeriodThenOnOpenWaitsForItAndWatcherRestarts() {
        setUp(nonBlockingLifecycle = false)
        backgroundUntilStopGracePeriod()

        val took = measureTimeMillis { testee.onOpen(isFreshLaunch = false) }

        assertTrue("onOpen took ${took}ms", took >= 500)
        assertFalse(watcherIsIn(LEGACY_GRACE_PERIOD))
        awaitUntil(respondToChecks = true) { !runnable.isStopped && watcherIsSleepingBetweenChecks() }
    }

    @Test
    fun whenFlagDisabledAndAppStaysInBackgroundThenWatcherStops() {
        setUp(nonBlockingLifecycle = false)
        backgroundUntilStopGracePeriod()

        awaitWatcherStopped()
    }

    @Test
    fun whenFlagDisabledAndMainThreadDoesNotRespondThenAnrIsRecorded() {
        setUp(nonBlockingLifecycle = false)
        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = false) { mockingDetails(anrDao).invocations.isNotEmpty() }

        verify(anrDao).insert(any())
    }

    private fun setUp(nonBlockingLifecycle: Boolean) {
        val feature = FakeFeatureToggleFactory.create(AnrSupervisorFeature::class.java).apply {
            self().setRawStoredState(State(enable = nonBlockingLifecycle))
        }
        runnable = AnrSupervisorRunnable(webViewVersionProvider, customTabDetector, appBuildConfig, anrsDatabase, feature)
        testee = AnrSupervisor(runnable)
    }

    private fun backgroundUntilStopGracePeriod() {
        testee.onOpen(isFreshLaunch = false)
        awaitUntil(respondToChecks = false) { !runnable.isStopped }
        testee.onClose()
        awaitUntil(respondToChecks = true) { watcherIsInGracePeriod() }
    }

    // The executor is single-threaded, so this barrier completes only once every queued watcher run has exited.
    private fun awaitWatcherStopped() {
        val barrier = testee.executor.submit {}
        awaitUntil(respondToChecks = true, timeoutMs = 10_000) { barrier.isDone }
        assertTrue(runnable.isStopped)
    }

    private fun awaitUntil(respondToChecks: Boolean, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within ${timeoutMs}ms" }
            if (respondToChecks) shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
    }

    private fun watcherThread(): Thread? = Thread.getAllStackTraces().entries
        .firstOrNull { (_, stack) -> stack.any { it.className == AnrSupervisorRunnable::class.java.name && it.methodName == "run" } }
        ?.key

    private fun watcherIsIn(method: String): Boolean =
        watcherThread()?.stackTrace?.any { it.className == AnrSupervisorRunnable::class.java.name && it.methodName == method } == true

    private fun watcherIsSleepingBetweenChecks(): Boolean {
        val stack = watcherThread()?.stackTrace ?: return false
        return stack.any { it.methodName == "sleep" } && !watcherIsInGracePeriod()
    }

    private fun watcherIsInGracePeriod(): Boolean = watcherIsIn(NON_BLOCKING_GRACE_PERIOD) || watcherIsIn(LEGACY_GRACE_PERIOD)

    private companion object {
        const val NON_BLOCKING_GRACE_PERIOD = "exitAfterStopGracePeriod"
        const val LEGACY_GRACE_PERIOD = "checkStopped"
    }
}
