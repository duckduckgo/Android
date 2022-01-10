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

import android.os.Handler
import android.os.Looper
import com.duckduckgo.app.anrs.store.AnrsDatabase
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AnrSupervisor @Inject constructor(
    private val anrSupervisorRunnable: AnrSupervisorRunnable
) : BrowserLifecycleObserver {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onOpen(isFreshLaunch: Boolean) {
        synchronized(anrSupervisorRunnable) {
            if (anrSupervisorRunnable.isStopped) {
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
    anrsDatabase: AnrsDatabase
) : Runnable {

    private val anrDao = anrsDatabase.arnDao()
    private val handler = Handler(Looper.getMainLooper())
    // stop flag
    private var stopped = AtomicBoolean(true)

    val isStopped
        get() = stopped.get()

    override fun run() {
        Thread.currentThread().name = ANR_WATCHER_THREAD_NAME

        unstop()

        while (!Thread.interrupted()) {
            try {
                Timber.v("AnrSupervisor checking for ANRs...")

                val callback = Callback()
                synchronized(callback) {
                    handler.post(callback)
                    callback.wait(ANR_THRESHOLD_MILLIS)

                    if (callback.isCalled) {
                        Timber.d("UI Thread responded withint ${ANR_THRESHOLD_MILLIS}ms")
                    } else {
                        val e = AnrException(handler.looper.thread)
                        Timber.e("ANR Detected: ${e.threadStateMap}")

                        anrDao.insert(e.asExceptionData().asAnrEntity())

                        // wait until thread responds again
                        callback.wait()
                    }
                }
                checkStopped()
                Thread.sleep(ANR_CHALLENGE_PERIOD_MILLIS)
            } catch (e: InterruptedException) {
                break
            }
        }

        stop()
        Timber.v("AnrSupervisor stopped")
    }

    fun stop() {
        Timber.v("AnrSupervisor stopping...")
        stopped.set(true)
    }

    private fun unstop() {
        Timber.v("AnrSupervisor revert stopping...")
        stopped.set(false)
    }

    @Synchronized
    @Throws(InterruptedException::class)
    private fun checkStopped() {
        if (stopped.get()) {
            Thread.sleep(2_000)
            if (stopped.get()) {
                throw InterruptedException()
            }
        }
    }

    companion object {
        private const val ANR_WATCHER_THREAD_NAME = "ANR_WATCHER_THREAD_NAME"
        private const val ANR_CHALLENGE_PERIOD_MILLIS = 5_000L
        private const val ANR_THRESHOLD_MILLIS = 2_000L
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
