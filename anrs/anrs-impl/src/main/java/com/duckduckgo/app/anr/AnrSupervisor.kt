/*
 * Copyright (c) 2021 DuckDuckGo
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

import android.os.Debug
import android.os.Handler
import android.os.Looper
import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.browser.api.WebViewVersionProvider
import com.duckduckgo.customtabs.api.CustomTabDetector
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import logcat.logcat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
class AnrSupervisor @Inject constructor(
    private val anrSupervisorRunnable: AnrSupervisorRunnable,
) : BrowserLifecycleObserver {

    @VisibleForTesting
    internal val executor = Executors.newSingleThreadExecutor()

    override fun onOpen(isFreshLaunch: Boolean) {
        synchronized(anrSupervisorRunnable) {
            if (!Debug.isDebuggerConnected() && anrSupervisorRunnable.claimRunOnOpen()) {
                executor.execute(anrSupervisorRunnable)
            }
        }
    }

    override fun onClose() {
        anrSupervisorRunnable.stop()
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun Any.wait(timeout: Long = 0) = (this as Object).wait(timeout)

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
internal fun Any.notifyAll() = (this as Object).notifyAll()

class AnrSupervisorRunnable @Inject constructor(
    private val webViewVersionProvider: WebViewVersionProvider,
    private val customTabDetector: CustomTabDetector,
    private val appBuildConfig: AppBuildConfig,
    anrsDatabase: AnrsDatabase,
    anrSupervisorFeature: AnrSupervisorFeature,
) : Runnable {

    private val anrDao = anrsDatabase.arnDao()
    private val handler = Handler(Looper.getMainLooper())

    // stop flag
    private var stopped = AtomicBoolean(true)

    // Guarded by this. Only used with the non-blocking lifecycle.
    private var running = false

    // Read once so a remote config change can't switch lifecycle semantics under a running watcher.
    private val nonBlockingLifecycle by lazy { anrSupervisorFeature.self().isEnabled() }

    val isStopped
        get() = stopped.get()

    /** Called under this runnable's lock; re-arms a live run, or claims a new one and returns true so the caller starts it. */
    fun claimRunOnOpen(): Boolean {
        if (!nonBlockingLifecycle) return isStopped
        // Re-arm a run that is still alive (e.g. in its stop grace period) instead of queueing a second one.
        unstop()
        if (running) return false
        running = true
        return true
    }

    override fun run() {
        Thread.currentThread().name = ANR_WATCHER_THREAD_NAME
        if (nonBlockingLifecycle) runNonBlocking() else runLegacy()
    }

    private fun runNonBlocking() {
        var exitDecided = false
        try {
            while (!Thread.interrupted()) {
                checkForAnr()
                exitDecided = exitAfterStopGracePeriod()
                if (exitDecided) break
                Thread.sleep(ANR_CHALLENGE_PERIOD_MILLIS)
            }
        } catch (e: InterruptedException) {
            logcat { "AnrSupervisor interrupted" }
        } finally {
            // A decided exit already cleared running under the lock; clearing it again could undo a run onOpen has since started.
            if (!exitDecided) synchronized(this) { running = false }
        }
        logcat { "AnrSupervisor stopped" }
    }

    // Sleeps without holding the lock, which AnrSupervisor.onOpen takes on the main thread.
    private fun exitAfterStopGracePeriod(): Boolean {
        if (!stopped.get()) return false
        Thread.sleep(STOP_GRACE_PERIOD_MILLIS)
        synchronized(this) {
            if (!stopped.get()) return false
            running = false
            return true
        }
    }

    private fun runLegacy() {
        unstop()

        while (!Thread.interrupted()) {
            try {
                checkForAnr()
                checkStopped()
                Thread.sleep(ANR_CHALLENGE_PERIOD_MILLIS)
            } catch (e: InterruptedException) {
                break
            }
        }

        stop()
        logcat { "AnrSupervisor stopped" }
    }

    fun stop() {
        logcat { "AnrSupervisor stopping..." }
        stopped.set(true)
    }

    private fun unstop() {
        logcat { "AnrSupervisor revert stopping..." }
        stopped.set(false)
    }

    @Throws(InterruptedException::class)
    private fun checkForAnr() {
        logcat { "AnrSupervisor checking for ANRs..." }

        val callback = Callback()
        synchronized(callback) {
            handler.post(callback)
            callback.wait(ANR_THRESHOLD_MILLIS)

            if (callback.isCalled) {
                logcat { "UI Thread responded within ${ANR_THRESHOLD_MILLIS}ms" }
            } else {
                val e = AnrException(handler.looper.thread)
                logcat { "In custom tab: ${customTabDetector.isCustomTab()}. ANR Detected: ${e.threadStateMap}." }

                anrDao.insert(
                    e.asAnrData(
                        webView = webViewVersionProvider.getFullVersion(),
                        customTab = customTabDetector.isCustomTab(),
                        appVersion = appBuildConfig.versionName,
                    ).asAnrEntity(),
                )

                // wait until thread responds again
                callback.wait()
            }
        }
    }

    @Synchronized
    @Throws(InterruptedException::class)
    private fun checkStopped() {
        if (stopped.get()) {
            Thread.sleep(STOP_GRACE_PERIOD_MILLIS)
            if (stopped.get()) {
                throw InterruptedException()
            }
        }
    }

    companion object {
        private const val ANR_WATCHER_THREAD_NAME = "ANR_WATCHER_THREAD_NAME"
        private const val ANR_CHALLENGE_PERIOD_MILLIS = 5_000L
        private const val ANR_THRESHOLD_MILLIS = 2_000L
        private const val STOP_GRACE_PERIOD_MILLIS = 2_000L
    }
}

class Callback : Runnable {
    @get:Synchronized
    var isCalled = false
        private set

    @Synchronized
    override fun run() {
        isCalled = true
        this.notifyAll()
    }
}
