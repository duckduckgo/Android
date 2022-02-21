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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import androidx.annotation.WorkerThread
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.model.VpnTracker
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Binds
import dagger.Module
import dagger.SingleInstanceIn
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.random.Random

interface AppTrackerRecorder {
    fun insertTracker(vpnTracker: VpnTracker)
}

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
@SingleInstanceIn(VpnScope::class)
class BatchedAppTrackerRecorder @Inject constructor(vpnDatabase: VpnDatabase) : VpnServiceCallbacks, AppTrackerRecorder {

    private val batchedTrackers = mutableListOf<VpnTracker>()
    private val dao = vpnDatabase.vpnTrackerDao()
    private val insertionDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val periodicInsertionJob = ConflatedJob()

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.i("Batched app tracker recorder starting")

        periodicInsertionJob += coroutineScope.launch(insertionDispatcher) {
            while (isActive) {
                flushInMemoryTrackersToDatabase()
                delay(Random.nextLong(PERIODIC_INSERTION_FREQUENCY_MS))
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        Timber.i("Batched app tracker recorder stopped")
        periodicInsertionJob.cancel()
        coroutineScope.launch(insertionDispatcher) {
            flushInMemoryTrackersToDatabase()
        }
    }

    @WorkerThread
    private fun flushInMemoryTrackersToDatabase() {
        val toInsert = mutableListOf<VpnTracker>()
        synchronized(batchedTrackers) {
            if (batchedTrackers.isEmpty()) {
                return
            }
            toInsert.addAll(batchedTrackers)
            batchedTrackers.clear()
        }

        dao.insert(toInsert)
        Timber.v("Inserted %d trackers from memory into db", toInsert.size)
    }

    override fun insertTracker(vpnTracker: VpnTracker) {
        synchronized(batchedTrackers) {
            batchedTrackers.add(vpnTracker)
        }
    }

    companion object {
        private const val PERIODIC_INSERTION_FREQUENCY_MS: Long = 1_000
    }
}

@Module
@ContributesTo(VpnScope::class)
abstract class AppTrackerRecorderModule {

    @Binds
    @SingleInstanceIn(VpnScope::class)
    abstract fun providesAppTrackerRecorder(batchedAppTrackerRecorder: BatchedAppTrackerRecorder): AppTrackerRecorder
}
