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

package com.duckduckgo.mobile.android.vpn.heartbeat

import android.content.Context
import android.os.Process
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnObjectGraph
import com.duckduckgo.mobile.android.vpn.di.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@ContributesMultibinding(
    scope = VpnObjectGraph::class,
    boundType = VpnServiceCallbacks::class
)
@VpnScope
class VpnServiceHeartbeatImpl @Inject constructor(
    private val context: Context,
    private val vpnDatabase: VpnDatabase,
    private val dispatcherProvider: DispatcherProvider
) : VpnServiceCallbacks {

    private var job: Job? = null
    private val isRunning = AtomicBoolean(false)

    private suspend fun storeHeartbeat(type: String) = withContext(dispatcherProvider.io()) {
        Timber.v("(${Process.myPid()}) - Sending heartbeat $type")
        vpnDatabase.vpnHeartBeatDao().insertType(type)
    }

    companion object {
        private const val HEART_BEAT_PERIOD_MINUTES: Long = 7
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        Timber.v("onVpnStarted called")
        job?.cancel()
        job = coroutineScope.launch {
            isRunning.set(true)
            while (isRunning.get()) {
                storeHeartbeat(VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE)
                delay(TimeUnit.MINUTES.toMillis(HEART_BEAT_PERIOD_MINUTES))
            }
        }
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        Timber.v("onVpnStopped called")
        when (vpnStopReason) {
            VpnStopReason.Error, VpnStopReason.Revoked -> Timber.v("HB monitor: sudden vpn stopped $vpnStopReason")
            VpnStopReason.SelfStop -> {
                Timber.v("HB monitor: self stopped $vpnStopReason")
                isRunning.set(false)
                coroutineScope.launch { storeHeartbeat(VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_STOPPED) }
            }
        }
    }
}
