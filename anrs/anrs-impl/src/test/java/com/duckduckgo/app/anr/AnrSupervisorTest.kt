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
import org.junit.Assert.assertNull
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
        // A run queued by onOpen starts only after the current one exits, so keep interrupting until none is left.
        testee.onClose()
        var quietSince = System.currentTimeMillis()
        while (System.currentTimeMillis() - quietSince < 200) {
            watcherThread()?.let {
                it.interrupt()
                quietSince = System.currentTimeMillis()
            }
            Thread.sleep(10)
        }
    }

    @Test
    fun whenReturningDuringStopGracePeriodThenOnOpenReturnsWhileWatcherIsStillInIt() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        assertTrue(watcherIsIn(NON_BLOCKING_GRACE_PERIOD))
    }

    @Test
    fun whenReturningDuringStopGracePeriodThenWatcherKeepsRunning() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }
        assertFalse(runnable.isStopped)
    }

    @Test
    fun whenReturningAndLeavingAgainDuringStopGracePeriodThenWatcherStops() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)
        testee.onClose()

        awaitUntil(respondToChecks = true) { watcherThread() == null }
        assertNoWatcherStartsWithin(STOP_GRACE_PERIOD_MILLIS + 500)
        assertTrue(runnable.isStopped)
    }

    @Test
    fun whenAppStaysInBackgroundThenWatcherStops() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()

        awaitUntil(respondToChecks = true) { watcherThread() == null }

        assertTrue(runnable.isStopped)
    }

    @Test
    fun whenReturningAfterWatcherStoppedThenWatcherRestarts() {
        setUp(nonBlockingLifecycle = true)
        backgroundUntilStopGracePeriod()
        awaitUntil(respondToChecks = true) { watcherThread() == null }

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
    fun whenFlagDisabledAndReturningDuringStopGracePeriodThenOnOpenWaitsForIt() {
        setUp(nonBlockingLifecycle = false)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        assertFalse(watcherIsIn(LEGACY_GRACE_PERIOD))
    }

    @Test
    fun whenFlagDisabledAndReturningDuringStopGracePeriodThenWatcherRestarts() {
        setUp(nonBlockingLifecycle = false)
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { !runnable.isStopped && watcherIsSleepingBetweenChecks() }
    }

    @Test
    fun whenFlagDisabledAndAppStaysInBackgroundThenWatcherStops() {
        setUp(nonBlockingLifecycle = false)
        backgroundUntilStopGracePeriod()

        awaitUntil(respondToChecks = true) { watcherThread() == null }

        assertTrue(runnable.isStopped)
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
        awaitUntil(respondToChecks = true) { watcherIsIn(NON_BLOCKING_GRACE_PERIOD) || watcherIsIn(LEGACY_GRACE_PERIOD) }
    }

    private fun assertNoWatcherStartsWithin(millis: Long) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            assertNull(watcherThread())
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
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
        return stack.any { it.methodName == "sleep" } && !watcherIsIn(NON_BLOCKING_GRACE_PERIOD) && !watcherIsIn(LEGACY_GRACE_PERIOD)
    }

    private companion object {
        const val NON_BLOCKING_GRACE_PERIOD = "exitAfterStopGracePeriod"
        const val LEGACY_GRACE_PERIOD = "checkStopped"
        const val STOP_GRACE_PERIOD_MILLIS = 2_000L
    }
}
