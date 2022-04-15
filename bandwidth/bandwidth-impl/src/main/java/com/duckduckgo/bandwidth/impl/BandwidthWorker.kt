/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.bandwidth.impl

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.duckduckgo.app.global.plugins.worker.WorkerInjectorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.waitlist.store.AtpWaitlistStateRepository
import com.duckduckgo.mobile.android.vpn.waitlist.store.WaitlistState.InBeta
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Module
@ContributesTo(AppScope::class)
class BandwidthSchedulerModule {
    @Provides
    @IntoSet
    fun provideBandwidthScheduler(workManager: WorkManager, atpWaitlistRepository: AtpWaitlistStateRepository): LifecycleObserver {
        return BandwidthScheduler(workManager, atpWaitlistRepository)
    }

    @Provides
    @IntoSet
    fun provideBandwidthWorkerInjectorPlugin(
        bandwidthCollector: BandwidthCollector
    ): WorkerInjectorPlugin {
        return BandwidthWorkerInjectorPlugin(bandwidthCollector)
    }
}

class BandwidthWorkerInjectorPlugin(
    private val bandwidthCollector: BandwidthCollector
) : WorkerInjectorPlugin {

    override fun inject(worker: ListenableWorker): Boolean {
        if (worker is BandwidthWorker) {
            worker.bandwidthCollector = bandwidthCollector
            return true
        }
        return false
    }
}

class BandwidthScheduler(
    private val workManager: WorkManager,
    private val atpWaitlistRepository: AtpWaitlistStateRepository
) : DefaultLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        if (atpWaitlistRepository.getState() != InBeta) return
        Timber.v("Scheduling Bandwidth Worker")
        val workerRequest = PeriodicWorkRequestBuilder<BandwidthWorker>(1, TimeUnit.HOURS)
            .addTag(BANDWIDTH_WORKER_TAG)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniquePeriodicWork(BANDWIDTH_WORKER_TAG, ExistingPeriodicWorkPolicy.KEEP, workerRequest)
    }

    companion object {
        const val BANDWIDTH_WORKER_TAG = "BANDWIDTH_WORKER_TAG"
    }
}

class BandwidthWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams), CoroutineScope {

    lateinit var bandwidthCollector: BandwidthCollector

    @WorkerThread
    override suspend fun doWork(): Result {

        bandwidthCollector.collect()

        Timber.i("Bandwidth job finished; returning SUCCESS")
        return Result.success()
    }
}
