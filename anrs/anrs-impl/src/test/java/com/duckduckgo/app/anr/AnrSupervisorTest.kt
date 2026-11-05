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
import org.junit.After
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
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class AnrSupervisorTest {

    private val anrDao: AnrDao = mock()
    private val anrsDatabase: AnrsDatabase = mock { on { arnDao() } doReturn anrDao }
    private val webViewVersionProvider: WebViewVersionProvider = mock { on { getFullVersion() } doReturn "1.0" }
    private val customTabDetector: CustomTabDetector = mock()
    private val appBuildConfig: AppBuildConfig = mock { on { versionName } doReturn "5.0.0" }

    private val runnable = AnrSupervisorRunnable(webViewVersionProvider, customTabDetector, appBuildConfig, anrsDatabase)
    private val testee = AnrSupervisor(runnable)

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
    fun whenReturningDuringStopGracePeriodThenOnOpenDoesNotBlock() {
        backgroundUntilStopGracePeriod()

        val took = measureTimeMillis { testee.onOpen(isFreshLaunch = false) }

        assertTrue("onOpen blocked for ${took}ms", took < 500)
    }

    @Test
    fun whenReturningDuringStopGracePeriodThenWatcherKeepsRunning() {
        backgroundUntilStopGracePeriod()

        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { !runnable.isStopped && watcherThread() != null }
    }

    @Test
    fun whenAppStaysInBackgroundThenWatcherStops() {
        backgroundUntilStopGracePeriod()

        awaitUntil(respondToChecks = true) { watcherThread() == null }

        assertTrue(runnable.isStopped)
    }

    @Test
    fun whenMainThreadRespondsThenNoAnrIsRecorded() {
        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = true) { watcherIsSleepingBetweenChecks() }

        verify(anrDao, never()).insert(any())
    }

    @Test
    fun whenMainThreadDoesNotRespondThenAnrIsRecorded() {
        testee.onOpen(isFreshLaunch = false)

        awaitUntil(respondToChecks = false) { mockingDetails(anrDao).invocations.isNotEmpty() }

        verify(anrDao).insert(any())
    }

    private fun backgroundUntilStopGracePeriod() {
        testee.onOpen(isFreshLaunch = false)
        awaitUntil(respondToChecks = false) { !runnable.isStopped }
        testee.onClose()
        awaitUntil(respondToChecks = true) { watcherIsIn("checkStopped") }
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
        return stack.any { it.methodName == "sleep" } && !watcherIsIn("checkStopped")
    }
}
