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

package com.duckduckgo.app.flipper.plugins

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.facebook.flipper.core.FlipperConnection
import com.facebook.flipper.core.FlipperObject
import com.facebook.flipper.core.FlipperPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import javax.inject.Inject
import kotlin.random.Random

@ContributesMultibinding(AppObjectGraph::class)
class TrackerProtectionFlipperPlugin @Inject constructor(
    vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : FlipperPlugin {

    private var job = ConflatedJob()
    private var connection: FlipperConnection? = null
    private val vpnTrackerDatabase = vpnDatabase.vpnTrackerDao()
    private val rows = CopyOnWriteArrayList<FlipperObject>()
    private val periodicSenderJob = ConflatedJob()
    private val senderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    override fun getId(): String {
        return "ddg-apptp-trackers"
    }

    override fun onConnect(connection: FlipperConnection?) {
        Timber.v("$id: connected")
        this.connection = connection

        periodicSenderJob += appCoroutineScope.launch(senderDispatcher) {
            while (isActive) {
                delay(Random.nextLong(PERIODIC_SEND_FREQUENCY_MS))
                sendRows()
            }
        }

        job += vpnTrackerDatabase.getLatestTracker()
            .onEach { tracker ->
                tracker?.let {
                    Timber.v("$id: sending $tracker")
                    FlipperObject.Builder()
                        .put("id", tracker.trackerId)
                        .put("timestamp", tracker.timestamp)
                        .put("domain", tracker.domain)
                        .put("company", tracker.companyDisplayName)
                        .put("appId", tracker.trackingApp.packageId)
                        .put("appName", tracker.trackingApp.appDisplayName)
                        .build()
                        .also { enqueueRow(it) }
                }
            }
            .flowOn(Dispatchers.IO)
            .launchIn(appCoroutineScope)
    }

    override fun onDisconnect() {
        Timber.v("$id: disconnected")
        connection = null
        job.cancel()
        periodicSenderJob.cancel()
    }

    override fun runInBackground(): Boolean {
        Timber.v("$id: running")
        return false
    }

    private fun enqueueRow(row: FlipperObject) {
        rows.add(row)
    }

    private fun sendRows() {
        while (rows.isNotEmpty()) {
            connection?.send("newData", rows.removeAt(0))
        }
        rows.clear()
    }

    companion object {
        private const val PERIODIC_SEND_FREQUENCY_MS: Long = 1_000
    }
}
