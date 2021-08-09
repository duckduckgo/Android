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
import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.facebook.flipper.core.FlipperConnection
import com.facebook.flipper.core.FlipperObject
import com.facebook.flipper.core.FlipperPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppObjectGraph::class)
class TrackerProtectionFlipperPlugin @Inject constructor(
    vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope
) : FlipperPlugin {

    private var job: Job? = null
    private var connection: FlipperConnection? = null
    private val vpnTrackerDatabase = vpnDatabase.vpnTrackerDao()

    override fun getId(): String {
        return "ddg-apptp-trackers"
    }

    override fun onConnect(connection: FlipperConnection?) {
        Timber.v("$id: connected")
        this.connection = connection

        job = vpnTrackerDatabase.getLatestTracker()
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
                        .also { newRow(it) }
                }
            }
            .launchIn(appCoroutineScope)
    }

    override fun onDisconnect() {
        Timber.v("$id: disconnected")
        connection = null
        job?.cancel()
    }

    override fun runInBackground(): Boolean {
        Timber.v("$id: running")
        return false
    }

    private fun newRow(row: FlipperObject) {
        connection?.send("newData", row)
    }
}
