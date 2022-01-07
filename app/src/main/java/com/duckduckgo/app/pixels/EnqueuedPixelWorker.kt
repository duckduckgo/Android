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

package com.duckduckgo.app.pixels

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.*
import com.duckduckgo.app.fire.UnsentForgetAllPixelStore
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = LifecycleObserver::class
)
@SingleInstanceIn(AppScope::class)
class EnqueuedPixelWorker @Inject constructor(
    private val workManager: WorkManager,
    private val pixel: Provider<Pixel>,
    private val unsentForgetAllPixelStore: UnsentForgetAllPixelStore,
) : LifecycleEventObserver {

    private var launchedByFireAction: Boolean = false

    override fun onStateChanged(
        source: LifecycleOwner,
        event: Lifecycle.Event
    ) {
        if (event == Lifecycle.Event.ON_CREATE) {
            scheduleWorker(workManager)
            launchedByFireAction = isLaunchByFireAction()
        } else if (event == Lifecycle.Event.ON_START) {
            if (launchedByFireAction) {
                // skip the next on_start if branch
                Timber.i("Suppressing app launch pixel")
                launchedByFireAction = false
                return
            }
            Timber.i("Sending app launch pixel")
            pixel.get().fire(AppPixelName.APP_LAUNCH)
        }
    }

    private fun isLaunchByFireAction(): Boolean {
        val timeDifferenceMillis = System.currentTimeMillis() - unsentForgetAllPixelStore.lastClearTimestamp
        if (timeDifferenceMillis <= APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD) {
            Timber.i("The app was re-launched as a result of the fire action being triggered (happened ${timeDifferenceMillis}ms ago)")
            return true
        }
        return false
    }

    private fun submitUnsentFirePixels() {
        val count = unsentForgetAllPixelStore.pendingPixelCountClearData
        Timber.i("Found $count unsent clear data pixels")
        if (count > 0) {
            for (i in 1..count) {
                pixel.get().fire(AppPixelName.FORGET_ALL_EXECUTED)
            }
            unsentForgetAllPixelStore.resetCount()
        }
    }

    class RealEnqueuedPixelWorker(
        val context: Context,
        parameters: WorkerParameters
    ) : CoroutineWorker(context, parameters) {
        lateinit var pixel: Pixel
        lateinit var enqueuedPixelWorker: EnqueuedPixelWorker

        override suspend fun doWork(): Result {
            Timber.v("Sending enqueued pixels")

            enqueuedPixelWorker.submitUnsentFirePixels()

            return Result.success()
        }
    }

    companion object {
        private const val APP_RESTART_CAUSED_BY_FIRE_GRACE_PERIOD: Long = 10_000L
        private const val WORKER_SEND_ENQUEUED_PIXELS = "com.duckduckgo.pixels.enqueued.worker"

        private fun scheduleWorker(workManager: WorkManager) {
            Timber.v("Scheduling the EnqueuedPixelWorker")

            val request = PeriodicWorkRequestBuilder<RealEnqueuedPixelWorker>(2, TimeUnit.HOURS)
                .addTag(WORKER_SEND_ENQUEUED_PIXELS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(WORKER_SEND_ENQUEUED_PIXELS, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

@ContributesMultibinding(AppScope::class)
class EnqueuedPixelWorkerInjectorPlugin @Inject constructor(
    private val pixel: Provider<Pixel>,
    private val enqueuedPixelWorker: Provider<EnqueuedPixelWorker>
) : WorkerInjectorPlugin {
    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is EnqueuedPixelWorker.RealEnqueuedPixelWorker) {
            worker.pixel = pixel.get()
            worker.enqueuedPixelWorker = enqueuedPixelWorker.get()

            return true
        }

        return false
    }
}
