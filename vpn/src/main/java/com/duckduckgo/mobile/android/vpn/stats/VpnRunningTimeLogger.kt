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

package com.duckduckgo.mobile.android.vpn.stats

import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.utils.ConflatedJob
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(VpnObjectGraph::class)
@SingleInstanceIn(VpnObjectGraph::class)
class VpnRunningTimeLogger @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val vpnDatabase: VpnDatabase
) : VpnServiceCallbacks {

    private val job = ConflatedJob()
    private var lastSavedTimestamp = 0L

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            while (isActive) {
                writeRunningTimeToDatabase(timeSinceLastRunningTimeSave())
                delay(30_000)
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        job.cancel()
        // vpn stopped, ensure we log the stopped timestamp
        coroutineScope.launch(dispatcherProvider.io()) {
            writeRunningTimeToDatabase(timeSinceLastRunningTimeSave())
        }
    }

    @WorkerThread
    private fun writeRunningTimeToDatabase(runningTimeSinceLastSaveMillis: Long) {
        vpnDatabase.vpnRunningStatsDao().upsert(runningTimeSinceLastSaveMillis)
        lastSavedTimestamp = SystemClock.elapsedRealtime()
    }

    private fun timeSinceLastRunningTimeSave(): Long {
        val timeNow = SystemClock.elapsedRealtime()
        return if (lastSavedTimestamp == 0L) 0 else timeNow - lastSavedTimestamp
    }
}
